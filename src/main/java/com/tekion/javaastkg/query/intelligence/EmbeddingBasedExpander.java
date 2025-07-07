package com.tekion.javaastkg.query.intelligence;

import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Expands query terms using embedding-based semantic similarity.
 * Searches against pre-computed embeddings in the knowledge graph to find semantically similar terms.
 */
@Service
@Slf4j
public class EmbeddingBasedExpander {

    private final Driver neo4jDriver;
    private final SessionConfig sessionConfig;
    private final EmbeddingModel embeddingModel;
    private final ExecutorService executorService;
    
    @Value("${query_optimization.semantic_expansion.embedding_similarity_threshold:0.65}")
    private float similarityThreshold;
    
    @Value("${query_optimization.semantic_expansion.max_embedding_expansions:15}")
    private int maxEmbeddingExpansions;
    
    @Value("${query_optimization.semantic_expansion.embedding_search_limit:30}")
    private int embeddingSearchLimit;

    public EmbeddingBasedExpander(Driver neo4jDriver,
                                  SessionConfig sessionConfig,
                                  @Qualifier("queryEmbeddingModel") EmbeddingModel embeddingModel) {
        this.neo4jDriver = neo4jDriver;
        this.sessionConfig = sessionConfig;
        this.embeddingModel = embeddingModel;
        this.executorService = Executors.newFixedThreadPool(3);
    }

    /**
     * Finds semantically similar terms using embedding similarity
     */
    public List<String> findSemanticallySimilarTerms(String queryTerm, float threshold) {
        if (queryTerm == null || queryTerm.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        log.debug("Finding semantically similar terms for: {}", queryTerm);
        
        try {
            // Generate embedding for query term
            float[] queryEmbedding = embeddingModel.embed(queryTerm).content().vector();
            
            // Search across all embedding types in parallel
            List<CompletableFuture<List<SimilarTerm>>> searchFutures = Arrays.asList(
                CompletableFuture.supplyAsync(() -> searchMethodEmbeddings(queryEmbedding, threshold), executorService),
                CompletableFuture.supplyAsync(() -> searchClassEmbeddings(queryEmbedding, threshold), executorService),
                CompletableFuture.supplyAsync(() -> searchDescriptionTerms(queryEmbedding, threshold), executorService)
            );
            
            // Combine and deduplicate results
            Set<SimilarTerm> allSimilarTerms = new HashSet<>();
            for (CompletableFuture<List<SimilarTerm>> future : searchFutures) {
                allSimilarTerms.addAll(future.join());
            }
            
            // Sort by similarity score and extract terms
            List<String> similarTerms = allSimilarTerms.stream()
                .sorted(Comparator.comparing(SimilarTerm::getScore).reversed())
                .limit(maxEmbeddingExpansions)
                .map(SimilarTerm::getTerm)
                .distinct()
                .collect(Collectors.toList());
            
            log.debug("Found {} similar terms for '{}'", similarTerms.size(), queryTerm);
            return similarTerms;
            
        } catch (Exception e) {
            log.error("Failed to find similar terms for '{}'", queryTerm, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Finds similar terms for multiple query terms
     */
    public Map<String, List<String>> findSimilarTermsForMultiple(List<String> queryTerms) {
        Map<String, List<String>> similarTermsMap = new HashMap<>();
        
        for (String term : queryTerms) {
            List<String> similarTerms = findSemanticallySimilarTerms(term, similarityThreshold);
            if (!similarTerms.isEmpty()) {
                similarTermsMap.put(term, similarTerms);
            }
        }
        
        return similarTermsMap;
    }
    
    /**
     * Searches method embeddings for similar terms
     */
    private List<SimilarTerm> searchMethodEmbeddings(float[] queryVector, float threshold) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                CALL db.index.vector.queryNodes('method_embeddings', $topK, $queryVector)
                YIELD node, score
                WHERE score >= $threshold
                RETURN DISTINCT node.name as term, 
                       node.className as context, 
                       score,
                       'method' as type
                ORDER BY score DESC
                """;
            
            Map<String, Object> params = Map.of(
                "topK", embeddingSearchLimit,
                "queryVector", queryVector,
                "threshold", threshold
            );
            
            return session.run(query, params)
                .list(record -> SimilarTerm.builder()
                    .term(record.get("term").asString())
                    .context(record.get("context").asString())
                    .score(record.get("score").asFloat())
                    .type(record.get("type").asString())
                    .build());
                    
        } catch (Exception e) {
            log.debug("Method embedding search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Searches class embeddings for similar terms
     */
    private List<SimilarTerm> searchClassEmbeddings(float[] queryVector, float threshold) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                CALL db.index.vector.queryNodes('class_embeddings', $topK, $queryVector)
                YIELD node, score
                WHERE score >= $threshold
                RETURN DISTINCT node.name as term, 
                       node.packageName as context, 
                       score,
                       labels(node)[0] as type
                ORDER BY score DESC
                """;
            
            Map<String, Object> params = Map.of(
                "topK", embeddingSearchLimit,
                "queryVector", queryVector,
                "threshold", threshold
            );
            
            return session.run(query, params)
                .list(record -> SimilarTerm.builder()
                    .term(record.get("term").asString())
                    .context(record.get("context").asString())
                    .score(record.get("score").asFloat())
                    .type(record.get("type").asString().toLowerCase())
                    .build());
                    
        } catch (Exception e) {
            log.debug("Class embedding search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Searches description embeddings for terms mentioned in descriptions
     */
    private List<SimilarTerm> searchDescriptionTerms(float[] queryVector, float threshold) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                CALL db.index.vector.queryNodes('description_embeddings', $topK, $queryVector)
                YIELD node, score
                WHERE score >= $threshold
                MATCH (code)-[:HAS_DESCRIPTION]->(node)
                RETURN DISTINCT code.name as term,
                       COALESCE(code.className, code.packageName) as context,
                       score,
                       labels(code)[0] as type
                ORDER BY score DESC
                """;
            
            Map<String, Object> params = Map.of(
                "topK", embeddingSearchLimit,
                "queryVector", queryVector,
                "threshold", threshold
            );
            
            return session.run(query, params)
                .list(record -> SimilarTerm.builder()
                    .term(record.get("term").asString())
                    .context(record.get("context").asString())
                    .score(record.get("score").asFloat())
                    .type(record.get("type").asString().toLowerCase())
                    .build());
                    
        } catch (Exception e) {
            log.debug("Description embedding search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Finds conceptually related code elements based on embedding similarity
     */
    public List<CodeElement> findRelatedCodeElements(String concept) {
        log.debug("Finding code elements related to concept: {}", concept);
        
        try {
            // Generate embedding for the concept
            float[] conceptEmbedding = embeddingModel.embed(concept).content().vector();
            
            // Search for related elements
            List<CodeElement> relatedElements = new ArrayList<>();
            
            try (Session session = neo4jDriver.session(sessionConfig)) {
                // Search across all node types
                String unifiedQuery = """
                    // Search method embeddings
                    CALL {
                        CALL db.index.vector.queryNodes('method_embeddings', $topK, $queryVector)
                        YIELD node, score
                        WHERE score >= $threshold
                        RETURN node, score, 'method' as nodeType
                        UNION
                        CALL db.index.vector.queryNodes('class_embeddings', $topK, $queryVector)
                        YIELD node, score
                        WHERE score >= $threshold
                        RETURN node, score, 'class' as nodeType
                    }
                    RETURN node.id as id,
                           node.name as name,
                           CASE nodeType
                               WHEN 'method' THEN node.signature
                               WHEN 'class' THEN node.fullName
                           END as signature,
                           CASE nodeType
                               WHEN 'method' THEN node.className
                               WHEN 'class' THEN node.packageName
                           END as context,
                           nodeType,
                           score
                    ORDER BY score DESC
                    LIMIT $limit
                    """;
                
                Map<String, Object> params = Map.of(
                    "topK", embeddingSearchLimit,
                    "queryVector", conceptEmbedding,
                    "threshold", similarityThreshold,
                    "limit", maxEmbeddingExpansions
                );
                
                relatedElements = session.run(unifiedQuery, params)
                    .list(record -> CodeElement.builder()
                        .id(record.get("id").asString())
                        .name(record.get("name").asString())
                        .signature(record.get("signature").asString())
                        .context(record.get("context").asString())
                        .type(record.get("nodeType").asString())
                        .similarityScore(record.get("score").asFloat())
                        .build());
            }
            
            log.debug("Found {} related code elements for concept '{}'", relatedElements.size(), concept);
            return relatedElements;
            
        } catch (Exception e) {
            log.error("Failed to find related code elements for concept '{}'", concept, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Expands query with embedding-based alternatives
     */
    public QueryExpansionResult expandQueryWithEmbeddings(String query, QueryIntentAnalyzer.QueryIntent intent) {
        log.debug("Expanding query with embeddings: {}", query);
        
        // Extract key terms from query
        List<String> keyTerms = extractKeyTerms(query);
        
        // Find similar terms for each key term
        Map<String, List<String>> termExpansions = new HashMap<>();
        Map<String, List<CodeElement>> relatedElements = new HashMap<>();
        
        for (String term : keyTerms) {
            // Get similar terms
            List<String> similarTerms = findSemanticallySimilarTerms(term, similarityThreshold);
            if (!similarTerms.isEmpty()) {
                termExpansions.put(term, similarTerms);
            }
            
            // Get related code elements based on intent
            if (shouldFindRelatedElements(intent)) {
                List<CodeElement> elements = findRelatedCodeElements(term);
                if (!elements.isEmpty()) {
                    relatedElements.put(term, elements);
                }
            }
        }
        
        return QueryExpansionResult.builder()
            .originalQuery(query)
            .keyTerms(keyTerms)
            .termExpansions(termExpansions)
            .relatedCodeElements(relatedElements)
            .expansionCount(termExpansions.values().stream().mapToInt(List::size).sum())
            .build();
    }
    
    /**
     * Extracts key terms from a query
     */
    private List<String> extractKeyTerms(String query) {
        // Simple tokenization - in production, use more sophisticated NLP
        return Arrays.stream(query.split("\\s+"))
            .filter(term -> term.length() > 2)
            .filter(term -> !isStopWord(term))
            .map(String::toLowerCase)
            .distinct()
            .collect(Collectors.toList());
    }
    
    /**
     * Checks if a word is a stop word
     */
    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of(
            "the", "is", "at", "which", "on", "and", "a", "an",
            "as", "are", "been", "have", "has", "had", "do", "does",
            "did", "will", "would", "should", "could", "may", "might",
            "can", "could", "been", "being", "having", "with", "about",
            "against", "between", "into", "through", "during", "before",
            "after", "above", "below", "to", "from", "up", "down",
            "in", "out", "on", "off", "over", "under", "again"
        );
        return stopWords.contains(word.toLowerCase());
    }
    
    /**
     * Determines if related elements should be found based on intent
     */
    private boolean shouldFindRelatedElements(QueryIntentAnalyzer.QueryIntent intent) {
        if (intent == null) {
            return true;
        }
        
        // Find related elements for discovery and implementation intents
        return intent.getPrimaryIntent() == QueryIntentAnalyzer.IntentType.DISCOVERY ||
               intent.getPrimaryIntent() == QueryIntentAnalyzer.IntentType.IMPLEMENTATION;
    }

    /**
     * Data class for similar terms
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class SimilarTerm {
        private String term;
        private String context;
        private float score;
        private String type;
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SimilarTerm that = (SimilarTerm) o;
            return Objects.equals(term, that.term) && Objects.equals(type, that.type);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(term, type);
        }
    }
    
    /**
     * Data class for code elements
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeElement {
        private String id;
        private String name;
        private String signature;
        private String context;
        private String type;
        private float similarityScore;
    }
    
    /**
     * Data class for query expansion results
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryExpansionResult {
        private String originalQuery;
        private List<String> keyTerms;
        private Map<String, List<String>> termExpansions;
        private Map<String, List<CodeElement>> relatedCodeElements;
        private int expansionCount;
    }
}