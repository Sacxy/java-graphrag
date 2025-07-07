package com.tekion.javaastkg.query.intelligence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Expands query terms using graph relationships in the knowledge graph.
 * Discovers related terms through code relationships like calls, extends, implements, etc.
 */
@Service
@Slf4j
public class GraphRelationshipExpander {

    private final Driver neo4jDriver;
    private final SessionConfig sessionConfig;
    private final ExecutorService executorService;
    
    @org.springframework.beans.factory.annotation.Value("${query_optimization.graph_expansion.max_relationship_depth:2}")
    private int maxRelationshipDepth;

    @org.springframework.beans.factory.annotation.Value("${query_optimization.graph_expansion.max_graph_expansions:20}")
    private int maxGraphExpansions;

    @org.springframework.beans.factory.annotation.Value("${query_optimization.graph_expansion.relationship_types:CALLS,CONTAINS,EXTENDS,IMPLEMENTS}")
    private String[] relationshipTypes;

    public GraphRelationshipExpander(Driver neo4jDriver, SessionConfig sessionConfig) {
        this.neo4jDriver = neo4jDriver;
        this.sessionConfig = sessionConfig;
        this.executorService = Executors.newFixedThreadPool(3);
    }

    /**
     * Finds related terms through graph relationships
     */
    public List<String> findRelatedTerms(List<String> queryTerms) {
        if (queryTerms == null || queryTerms.isEmpty()) {
            return Collections.emptyList();
        }
        
        log.debug("Finding related terms through graph relationships for: {}", queryTerms);
        
        Set<RelatedTerm> allRelatedTerms = new HashSet<>();
        
        // Search for each term in parallel
        List<CompletableFuture<Set<RelatedTerm>>> futures = queryTerms.stream()
            .map(term -> CompletableFuture.supplyAsync(() -> findRelatedTermsForSingle(term), executorService))
            .collect(Collectors.toList());
        
        // Combine results
        for (CompletableFuture<Set<RelatedTerm>> future : futures) {
            try {
                allRelatedTerms.addAll(future.join());
            } catch (Exception e) {
                log.error("Failed to get related terms", e);
            }
        }
        
        // Sort by relevance and extract terms
        List<String> relatedTerms = allRelatedTerms.stream()
            .sorted(Comparator.comparing(RelatedTerm::getDistance)
                .thenComparing(Comparator.comparing(RelatedTerm::getScore).reversed()))
            .limit(maxGraphExpansions)
            .map(RelatedTerm::getTerm)
            .distinct()
            .collect(Collectors.toList());
        
        log.debug("Found {} related terms through graph relationships", relatedTerms.size());
        return relatedTerms;
    }
    
    /**
     * Finds related terms for a single query term
     */
    private Set<RelatedTerm> findRelatedTermsForSingle(String queryTerm) {
        Set<RelatedTerm> relatedTerms = new HashSet<>();
        
        // Find through direct relationships
        relatedTerms.addAll(findThroughDirectRelationships(queryTerm));
        
        // Find through hierarchical relationships
        relatedTerms.addAll(findThroughHierarchy(queryTerm));
        
        // Find through call patterns
        relatedTerms.addAll(findThroughCallPatterns(queryTerm));
        
        // Find through package siblings
        relatedTerms.addAll(findPackageSiblings(queryTerm));
        
        return relatedTerms;
    }
    
    /**
     * Finds related components through direct relationships
     */
    private List<RelatedTerm> findThroughDirectRelationships(String queryTerm) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String relationshipPattern = Arrays.stream(relationshipTypes)
                .map(type -> ":" + type)
                .collect(Collectors.joining("|"));
            
            String query = String.format("""
                MATCH (n)
                WHERE (n:Method OR n:Class OR n:Interface)
                  AND (
                    n.name CONTAINS $queryTerm 
                    OR (n:Method AND n.signature CONTAINS $queryTerm)
                    OR (n:Method AND n.className CONTAINS $queryTerm)
                    OR ((n:Class OR n:Interface) AND n.fullName CONTAINS $queryTerm)
                  )
                MATCH path = (n)-[r%s*1..%d]-(related)
                WHERE related:Method OR related:Class OR related:Interface
                WITH DISTINCT related, 
                     type(r) as relationshipType,
                     length(path) as distance,
                     count(path) as pathCount
                RETURN related.name as relatedTerm,
                       COALESCE(related.className, related.packageName) as context,
                       relationshipType,
                       distance,
                       pathCount as score,
                       labels(related)[0] as nodeType
                ORDER BY distance ASC, score DESC
                LIMIT $limit
                """, relationshipPattern, maxRelationshipDepth);
            
            Map<String, Object> params = Map.of(
                "queryTerm", queryTerm,
                "limit", maxGraphExpansions
            );
            
            return session.run(query, params)
                .list(record -> RelatedTerm.builder()
                    .term(record.get("relatedTerm").asString())
                    .context(record.get("context").asString())
                    .relationshipType(record.get("relationshipType").asString())
                    .distance(record.get("distance").asInt())
                    .score(record.get("score").asInt())
                    .nodeType(record.get("nodeType").asString())
                    .build());
                    
        } catch (Exception e) {
            log.debug("Direct relationship search failed for '{}': {}", queryTerm, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Finds related terms through class hierarchy
     */
    private List<RelatedTerm> findThroughHierarchy(String queryTerm) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                // Find classes matching the query term
                MATCH (n:Class|Interface)
                WHERE n.name CONTAINS $queryTerm OR n.fullName CONTAINS $queryTerm
                
                // Find parent and child classes
                OPTIONAL MATCH (n)-[:EXTENDS]->(parent)
                OPTIONAL MATCH (child)-[:EXTENDS]->(n)
                OPTIONAL MATCH (n)-[:IMPLEMENTS]->(interface)
                OPTIONAL MATCH (implementor)-[:IMPLEMENTS]->(n)
                
                WITH collect(DISTINCT parent) + collect(DISTINCT child) + 
                     collect(DISTINCT interface) + collect(DISTINCT implementor) as related
                UNWIND related as r
                
                WHERE r IS NOT NULL
                RETURN DISTINCT r.name as relatedTerm,
                       r.packageName as context,
                       CASE 
                           WHEN (n)-[:EXTENDS]->(r) THEN 'parent_class'
                           WHEN (r)-[:EXTENDS]->(n) THEN 'child_class'
                           WHEN (n)-[:IMPLEMENTS]->(r) THEN 'implements'
                           WHEN (r)-[:IMPLEMENTS]->(n) THEN 'implementor'
                       END as relationshipType,
                       1 as distance,
                       1.0 as score,
                       labels(r)[0] as nodeType
                LIMIT $limit
                """;
            
            Map<String, Object> params = Map.of(
                "queryTerm", queryTerm,
                "limit", maxGraphExpansions / 2
            );
            
            return session.run(query, params)
                .list(record -> RelatedTerm.builder()
                    .term(record.get("relatedTerm").asString())
                    .context(record.get("context").asString())
                    .relationshipType(record.get("relationshipType").asString())
                    .distance(record.get("distance").asInt())
                    .score(record.get("score").asDouble())
                    .nodeType(record.get("nodeType").asString())
                    .build());
                    
        } catch (Exception e) {
            log.debug("Hierarchy search failed for '{}': {}", queryTerm, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Finds related terms through call patterns
     */
    private List<RelatedTerm> findThroughCallPatterns(String queryTerm) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                // Find methods that frequently appear together in call chains
                MATCH (m1:Method)
                WHERE m1.name CONTAINS $queryTerm OR m1.signature CONTAINS $queryTerm
                MATCH (m1)-[:CALLS]->(m2:Method)-[:CALLS]->(m3:Method)
                
                WITH m2, m3, count(*) as coOccurrences
                WHERE coOccurrences > 1
                
                RETURN m2.name as relatedTerm,
                       m2.className as context,
                       'call_pattern' as relationshipType,
                       2 as distance,
                       coOccurrences as score,
                       'Method' as nodeType
                       
                UNION
                
                RETURN m3.name as relatedTerm,
                       m3.className as context,
                       'call_pattern' as relationshipType,
                       2 as distance,
                       coOccurrences as score,
                       'Method' as nodeType
                       
                ORDER BY score DESC
                LIMIT $limit
                """;
            
            Map<String, Object> params = Map.of(
                "queryTerm", queryTerm,
                "limit", maxGraphExpansions / 2
            );
            
            return session.run(query, params)
                .list(record -> RelatedTerm.builder()
                    .term(record.get("relatedTerm").asString())
                    .context(record.get("context").asString())
                    .relationshipType(record.get("relationshipType").asString())
                    .distance(record.get("distance").asInt())
                    .score(record.get("score").asDouble())
                    .nodeType(record.get("nodeType").asString())
                    .build());
                    
        } catch (Exception e) {
            log.debug("Call pattern search failed for '{}': {}", queryTerm, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Finds sibling classes in the same package
     */
    private List<RelatedTerm> findPackageSiblings(String queryTerm) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                // Find classes in the same package
                MATCH (n:Class|Interface)
                WHERE n.name CONTAINS $queryTerm
                WITH n.packageName as pkg
                WHERE pkg IS NOT NULL
                
                MATCH (sibling:Class|Interface)
                WHERE sibling.packageName = pkg 
                  AND sibling.name <> n.name
                  AND NOT sibling.name CONTAINS 'Test'
                  AND NOT sibling.name CONTAINS 'Mock'
                  
                RETURN DISTINCT sibling.name as relatedTerm,
                       sibling.packageName as context,
                       'package_sibling' as relationshipType,
                       1 as distance,
                       1.0 as score,
                       labels(sibling)[0] as nodeType
                LIMIT $limit
                """;
            
            Map<String, Object> params = Map.of(
                "queryTerm", queryTerm,
                "limit", maxGraphExpansions / 3
            );
            
            return session.run(query, params)
                .list(record -> RelatedTerm.builder()
                    .term(record.get("relatedTerm").asString())
                    .context(record.get("context").asString())
                    .relationshipType(record.get("relationshipType").asString())
                    .distance(record.get("distance").asInt())
                    .score(record.get("score").asDouble())
                    .nodeType(record.get("nodeType").asString())
                    .build());
                    
        } catch (Exception e) {
            log.debug("Package sibling search failed for '{}': {}", queryTerm, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Finds commonly co-occurring terms in the codebase
     */
    public List<CoOccurringTerm> findCoOccurringTerms(List<String> queryTerms) {
        if (queryTerms.size() < 2) {
            return Collections.emptyList();
        }
        
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                // Find methods/classes that contain references to multiple query terms
                MATCH (n:Method|Class|Interface)
                WHERE ALL(term IN $queryTerms WHERE 
                    n.name CONTAINS term OR 
                    n.signature CONTAINS term OR 
                    n.fullName CONTAINS term)
                    
                // Find other terms that frequently appear with these
                MATCH (n)-[*1..2]-(related:Method|Class|Interface)
                WHERE NOT related.name IN $queryTerms
                
                WITH related.name as term,
                     count(DISTINCT n) as coOccurrenceCount,
                     collect(DISTINCT labels(related)[0])[0] as type
                     
                RETURN term,
                       coOccurrenceCount,
                       type
                ORDER BY coOccurrenceCount DESC
                LIMIT $limit
                """;
            
            Map<String, Object> params = Map.of(
                "queryTerms", queryTerms,
                "limit", maxGraphExpansions
            );
            
            return session.run(query, params)
                .list(record -> CoOccurringTerm.builder()
                    .term(record.get("term").asString())
                    .coOccurrenceCount(record.get("coOccurrenceCount").asInt())
                    .type(record.get("type").asString())
                    .build());
                    
        } catch (Exception e) {
            log.error("Failed to find co-occurring terms", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Expands query based on graph analysis
     */
    public GraphExpansionResult expandWithGraphAnalysis(List<String> queryTerms) {
        log.debug("Expanding query with graph analysis: {}", queryTerms);
        
        // Find related terms
        List<String> relatedTerms = findRelatedTerms(queryTerms);
        
        // Find co-occurring terms
        List<CoOccurringTerm> coOccurringTerms = findCoOccurringTerms(queryTerms);
        
        // Find domain patterns
        List<DomainPattern> domainPatterns = findDomainPatterns(queryTerms);
        
        return GraphExpansionResult.builder()
            .originalTerms(queryTerms)
            .relatedTerms(relatedTerms)
            .coOccurringTerms(coOccurringTerms)
            .domainPatterns(domainPatterns)
            .totalExpansions(relatedTerms.size() + coOccurringTerms.size())
            .build();
    }
    
    /**
     * Finds domain-specific patterns in the graph
     */
    private List<DomainPattern> findDomainPatterns(List<String> queryTerms) {
        List<DomainPattern> patterns = new ArrayList<>();
        
        // Look for common architectural patterns
        patterns.addAll(findArchitecturalPatterns(queryTerms));
        
        // Look for design patterns
        patterns.addAll(findDesignPatterns(queryTerms));
        
        return patterns;
    }
    
    /**
     * Finds architectural patterns like MVC, layered architecture, etc.
     */
    private List<DomainPattern> findArchitecturalPatterns(List<String> queryTerms) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                // Look for common architectural patterns
                MATCH (n:Class|Interface)
                WHERE ANY(term IN $queryTerms WHERE n.name CONTAINS term)
                
                // Check for Controller-Service-Repository pattern
                OPTIONAL MATCH (n)-[:CALLS|CONTAINS*1..3]-(service:Class)
                WHERE service.name ENDS WITH 'Service'
                OPTIONAL MATCH (service)-[:CALLS|CONTAINS*1..3]-(repo:Class)
                WHERE repo.name ENDS WITH 'Repository' OR repo.name ENDS WITH 'DAO'
                
                WITH n, 
                     collect(DISTINCT service.name) as services,
                     collect(DISTINCT repo.name) as repositories
                WHERE size(services) > 0 OR size(repositories) > 0
                
                RETURN 'MVC/Layered' as patternType,
                       services + repositories as relatedComponents,
                       size(services) + size(repositories) as strength
                LIMIT 5
                """;
            
            Map<String, Object> params = Map.of("queryTerms", queryTerms);
            
            return session.run(query, params)
                .list(record -> DomainPattern.builder()
                    .patternType(record.get("patternType").asString())
                    .relatedComponents(record.get("relatedComponents").asList(Value::asString))
                    .strength(record.get("strength").asInt())
                    .build());
                    
        } catch (Exception e) {
            log.debug("Architectural pattern search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Finds design patterns like Factory, Observer, etc.
     */
    private List<DomainPattern> findDesignPatterns(List<String> queryTerms) {
        List<DomainPattern> patterns = new ArrayList<>();
        
        // Check for Factory pattern
        if (queryTerms.stream().anyMatch(term -> term.toLowerCase().contains("factory"))) {
            patterns.add(DomainPattern.builder()
                .patternType("Factory")
                .relatedComponents(Arrays.asList("create", "build", "getInstance", "newInstance"))
                .strength(5)
                .build());
        }
        
        // Check for Observer pattern
        if (queryTerms.stream().anyMatch(term -> 
                term.toLowerCase().contains("observer") || 
                term.toLowerCase().contains("listener"))) {
            patterns.add(DomainPattern.builder()
                .patternType("Observer")
                .relatedComponents(Arrays.asList("notify", "update", "subscribe", "unsubscribe", "publish"))
                .strength(5)
                .build());
        }
        
        return patterns;
    }

    /**
     * Data class for related terms
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RelatedTerm {
        private String term;
        private String context;
        private String relationshipType;
        private int distance;
        private double score;
        private String nodeType;
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RelatedTerm that = (RelatedTerm) o;
            return Objects.equals(term, that.term);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(term);
        }
    }
    
    /**
     * Data class for co-occurring terms
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoOccurringTerm {
        private String term;
        private int coOccurrenceCount;
        private String type;
    }
    
    /**
     * Data class for domain patterns
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DomainPattern {
        private String patternType;
        private List<String> relatedComponents;
        private int strength;
    }
    
    /**
     * Data class for graph expansion results
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphExpansionResult {
        private List<String> originalTerms;
        private List<String> relatedTerms;
        private List<CoOccurringTerm> coOccurringTerms;
        private List<DomainPattern> domainPatterns;
        private int totalExpansions;
    }
}