package com.tekion.javaastkg.agents.tools;

import com.tekion.javaastkg.agents.models.*;
import com.tekion.javaastkg.service.Neo4jService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 🔍 Semantic Code Hunter Tool
 * 
 * This tool specializes in finding code through semantic understanding:
 * - Uses natural language queries to find relevant code
 * - Leverages Neo4j full-text search and vector similarity
 * - Understands intent and context to improve search accuracy
 * - Returns ranked results with semantic relevance scores
 * 
 * Domain Focus: FINDING the most relevant code based on semantic meaning
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SemanticCodeHunter {
    
    private final Neo4jService neo4jService;
    
    /**
     * 🎯 Hunt for code using semantic understanding
     */
    public SemanticSearchResult huntCode(SemanticSearchRequest searchRequest) {
        
        log.debug("🔍 Starting semantic code hunt: '{}'", searchRequest.getQuery());
        
        try {
            // Step 1: Analyze search query and extract semantic features
            SearchContext context = analyzeSearchContext(searchRequest);
            
            // Step 2: Execute multi-modal search
            List<CodeMatch> matches = executeSemanticSearch(context);
            
            // Step 3: Rank and filter results
            List<CodeMatch> rankedMatches = rankAndFilterMatches(matches, context);
            
            // Step 4: Enhance matches with additional context
            List<CodeMatch> enrichedMatches = enrichMatches(rankedMatches, context);
            
            // Step 5: Build result
            return SemanticSearchResult.builder()
                .query(searchRequest.getQuery())
                .matches(enrichedMatches)
                .totalMatches(matches.size())
                .searchContext(context)
                .executionTimeMs(System.currentTimeMillis() - context.getStartTime())
                .successful(true)
                .timestamp(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("❌ Semantic search failed", e);
            
            return SemanticSearchResult.builder()
                .query(searchRequest.getQuery())
                .matches(List.of())
                .totalMatches(0)
                .successful(false)
                .errorMessage("Search failed: " + e.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
        }
    }
    
    /**
     * 🧠 Analyze search context and extract semantic features
     */
    private SearchContext analyzeSearchContext(SemanticSearchRequest request) {
        
        SearchContext.SearchContextBuilder contextBuilder = SearchContext.builder()
            .startTime(System.currentTimeMillis())
            .originalQuery(request.getQuery())
            .searchMode(request.getSearchMode() != null ? request.getSearchMode() : SearchMode.SEMANTIC)
            .maxResults(request.getMaxResults() != null ? request.getMaxResults() : 20);
        
        // Extract search terms and keywords
        List<String> searchTerms = extractSearchTerms(request.getQuery());
        contextBuilder.searchTerms(searchTerms);
        
        // Identify entity types mentioned in query
        Set<EntityType> targetEntityTypes = identifyTargetEntityTypes(request.getQuery());
        contextBuilder.targetEntityTypes(targetEntityTypes);
        
        // Determine search strategy based on query characteristics
        SearchStrategy strategy = determineSearchStrategy(request.getQuery(), searchTerms);
        contextBuilder.searchStrategy(strategy);
        
        return contextBuilder.build();
    }
    
    /**
     * 🔍 Execute multi-modal semantic search
     */
    private List<CodeMatch> executeSemanticSearch(SearchContext context) {
        
        List<CodeMatch> allMatches = new ArrayList<>();
        
        // 1. Full-text search using Lucene
        if (context.getSearchStrategy().isUseFullText()) {
            List<CodeMatch> fullTextMatches = executeFullTextSearch(context);
            allMatches.addAll(fullTextMatches);
            log.debug("📝 Full-text search found {} matches", fullTextMatches.size());
        }
        
        // 2. Vector similarity search (if available)
        if (context.getSearchStrategy().isUseVectorSearch()) {
            List<CodeMatch> vectorMatches = executeVectorSearch(context);
            allMatches.addAll(vectorMatches);
            log.debug("🎯 Vector search found {} matches", vectorMatches.size());
        }
        
        // 3. Structural pattern search
        if (context.getSearchStrategy().isUseStructuralSearch()) {
            List<CodeMatch> structuralMatches = executeStructuralSearch(context);
            allMatches.addAll(structuralMatches);
            log.debug("🏗️ Structural search found {} matches", structuralMatches.size());
        }
        
        // 4. Relationship-based search
        if (context.getSearchStrategy().isUseRelationshipSearch()) {
            List<CodeMatch> relationshipMatches = executeRelationshipSearch(context);
            allMatches.addAll(relationshipMatches);
            log.debug("🔗 Relationship search found {} matches", relationshipMatches.size());
        }
        
        return allMatches;
    }
    
    /**
     * 📝 Execute full-text search using Neo4j Lucene
     */
    private List<CodeMatch> executeFullTextSearch(SearchContext context) {
        
        List<CodeMatch> matches = new ArrayList<>();
        
        // Build Lucene query
        String luceneQuery = buildLuceneQuery(context);
        log.debug("🔍 Executing Lucene query: {}", luceneQuery);
        
        try {
            // Search different node types
            for (EntityType entityType : context.getTargetEntityTypes()) {
                List<Map<String, Object>> results = neo4jService.executeFullTextSearch(
                    entityType.name().toLowerCase(), luceneQuery, context.getMaxResults());
                
                for (Map<String, Object> result : results) {
                    CodeMatch match = createCodeMatchFromResult(result, MatchType.FULL_TEXT, entityType);
                    if (match != null) {
                        matches.add(match);
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("⚠️ Full-text search failed: {}", e.getMessage());
        }
        
        return matches;
    }
    
    /**
     * 🎯 Execute vector similarity search (placeholder for future implementation)
     */
    private List<CodeMatch> executeVectorSearch(SearchContext context) {
        // Vector search implementation would go here
        // For now, return empty list as this requires vector embeddings
        log.debug("🎯 Vector search not yet implemented");
        return List.of();
    }
    
    /**
     * 🏗️ Execute structural pattern search
     */
    private List<CodeMatch> executeStructuralSearch(SearchContext context) {
        
        List<CodeMatch> matches = new ArrayList<>();
        
        // Search for class patterns
        if (context.getTargetEntityTypes().contains(EntityType.CLASS)) {
            matches.addAll(searchClassPatterns(context));
        }
        
        // Search for method patterns
        if (context.getTargetEntityTypes().contains(EntityType.METHOD)) {
            matches.addAll(searchMethodPatterns(context));
        }
        
        return matches;
    }
    
    /**
     * 🔗 Execute relationship-based search
     */
    private List<CodeMatch> executeRelationshipSearch(SearchContext context) {
        
        List<CodeMatch> matches = new ArrayList<>();
        
        // Find related entities through graph relationships
        for (String searchTerm : context.getSearchTerms()) {
            try {
                List<Map<String, Object>> relatedEntities = neo4jService.findRelatedEntities(
                    searchTerm, 2); // 2 hops max
                
                for (Map<String, Object> entity : relatedEntities) {
                    CodeMatch match = createCodeMatchFromResult(entity, MatchType.RELATIONSHIP, null);
                    if (match != null) {
                        matches.add(match);
                    }
                }
                
            } catch (Exception e) {
                log.warn("⚠️ Relationship search failed for term '{}': {}", searchTerm, e.getMessage());
            }
        }
        
        return matches;
    }
    
    /**
     * 📊 Rank and filter matches based on relevance
     */
    private List<CodeMatch> rankAndFilterMatches(List<CodeMatch> matches, SearchContext context) {
        
        // Remove duplicates based on unique identifier
        Map<String, CodeMatch> uniqueMatches = matches.stream()
            .collect(Collectors.toMap(
                match -> match.getUniqueId(),
                match -> match,
                (existing, replacement) -> existing.getSemanticScore() >= replacement.getSemanticScore() 
                    ? existing : replacement
            ));
        
        // Sort by semantic score (descending)
        List<CodeMatch> sortedMatches = uniqueMatches.values().stream()
            .sorted((a, b) -> Double.compare(b.getSemanticScore(), a.getSemanticScore()))
            .limit(context.getMaxResults())
            .collect(Collectors.toList());
        
        log.debug("📊 Ranked {} unique matches from {} total", sortedMatches.size(), matches.size());
        return sortedMatches;
    }
    
    /**
     * ✨ Enhance matches with additional context
     */
    private List<CodeMatch> enrichMatches(List<CodeMatch> matches, SearchContext context) {
        
        return matches.stream()
            .map(match -> {
                try {
                    // Add documentation if available
                    String documentation = neo4jService.getEntityDocumentation(match.getEntityId());
                    if (documentation != null && !documentation.trim().isEmpty()) {
                        match.setDocumentation(documentation);
                    }
                    
                    // Add usage examples if available
                    List<String> usageExamples = neo4jService.getEntityUsageExamples(match.getEntityId(), 3);
                    match.setUsageExamples(usageExamples);
                    
                    return match;
                    
                } catch (Exception e) {
                    log.warn("⚠️ Failed to enrich match {}: {}", match.getEntityId(), e.getMessage());
                    return match;
                }
            })
            .collect(Collectors.toList());
    }
    
    // Helper methods
    
    private List<String> extractSearchTerms(String query) {
        return Arrays.stream(query.toLowerCase().split("\\s+"))
            .filter(term -> term.length() > 2)
            .filter(term -> !isStopWord(term))
            .collect(Collectors.toList());
    }
    
    private Set<EntityType> identifyTargetEntityTypes(String query) {
        Set<EntityType> types = new HashSet<>();
        
        String lowerQuery = query.toLowerCase();
        if (lowerQuery.contains("class") || lowerQuery.contains("service") || lowerQuery.contains("controller")) {
            types.add(EntityType.CLASS);
        }
        if (lowerQuery.contains("method") || lowerQuery.contains("function")) {
            types.add(EntityType.METHOD);
        }
        if (lowerQuery.contains("field") || lowerQuery.contains("property")) {
            types.add(EntityType.FIELD);
        }
        
        // Default to all types if none specified
        if (types.isEmpty()) {
            types.addAll(Arrays.asList(EntityType.values()));
        }
        
        return types;
    }
    
    private SearchStrategy determineSearchStrategy(String query, List<String> searchTerms) {
        return SearchStrategy.builder()
            .useFullText(true)  // Always use full-text
            .useVectorSearch(false)  // Not implemented yet
            .useStructuralSearch(hasStructuralPatterns(query))
            .useRelationshipSearch(searchTerms.size() > 1)  // Use for multi-term queries
            .build();
    }
    
    private boolean hasStructuralPatterns(String query) {
        return query.matches(".*\\b[A-Z][a-zA-Z0-9]*\\b.*") ||  // PascalCase
               query.matches(".*\\b[a-z][a-zA-Z0-9]*\\(\\)\\b.*");    // method calls
    }
    
    private String buildLuceneQuery(SearchContext context) {
        StringBuilder query = new StringBuilder();
        
        for (int i = 0; i < context.getSearchTerms().size(); i++) {
            if (i > 0) query.append(" AND ");
            query.append("(");
            query.append("name:*").append(context.getSearchTerms().get(i)).append("*");
            query.append(" OR ");
            query.append("signature:*").append(context.getSearchTerms().get(i)).append("*");
            query.append(" OR ");
            query.append("documentation:*").append(context.getSearchTerms().get(i)).append("*");
            query.append(")");
        }
        
        return query.toString();
    }
    
    private List<CodeMatch> searchClassPatterns(SearchContext context) {
        // Implementation for class pattern search
        return List.of();
    }
    
    private List<CodeMatch> searchMethodPatterns(SearchContext context) {
        // Implementation for method pattern search
        return List.of();
    }
    
    private CodeMatch createCodeMatchFromResult(Map<String, Object> result, MatchType matchType, EntityType entityType) {
        try {
            return CodeMatch.builder()
                .entityId(result.get("id").toString())
                .entityType(entityType != null ? entityType : EntityType.CLASS)
                .name(result.getOrDefault("name", "Unknown").toString())
                .signature(result.getOrDefault("signature", "").toString())
                .filePath(result.getOrDefault("filePath", "").toString())
                .startLine(((Number) result.getOrDefault("startLine", 0)).intValue())
                .endLine(((Number) result.getOrDefault("endLine", 0)).intValue())
                .matchType(matchType)
                .semanticScore(calculateSemanticScore(result, matchType))
                .uniqueId(generateUniqueId(result))
                .build();
        } catch (Exception e) {
            log.warn("⚠️ Failed to create CodeMatch from result: {}", e.getMessage());
            return null;
        }
    }
    
    private double calculateSemanticScore(Map<String, Object> result, MatchType matchType) {
        double baseScore = switch (matchType) {
            case FULL_TEXT -> 0.8;
            case VECTOR_SIMILARITY -> 0.9;
            case STRUCTURAL -> 0.7;
            case RELATIONSHIP -> 0.6;
        };
        
        // Boost score based on result properties
        if (result.containsKey("score")) {
            double searchScore = ((Number) result.get("score")).doubleValue();
            baseScore = Math.min(1.0, baseScore * searchScore);
        }
        
        return baseScore;
    }
    
    private String generateUniqueId(Map<String, Object> result) {
        return result.get("id").toString() + ":" + 
               result.getOrDefault("filePath", "") + ":" +
               result.getOrDefault("startLine", 0);
    }
    
    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of("the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by");
        return stopWords.contains(word);
    }
    
    // Supporting data classes
    
    @lombok.Data
    @lombok.Builder
    public static class SearchContext {
        private long startTime;
        private String originalQuery;
        private List<String> searchTerms;
        private Set<EntityType> targetEntityTypes;
        private SearchMode searchMode;
        private SearchStrategy searchStrategy;
        private int maxResults;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class SearchStrategy {
        private boolean useFullText;
        private boolean useVectorSearch;
        private boolean useStructuralSearch;
        private boolean useRelationshipSearch;
    }
    
    public enum SearchMode {
        SEMANTIC, EXACT, FUZZY, HYBRID
    }
    
    public enum MatchType {
        FULL_TEXT, VECTOR_SIMILARITY, STRUCTURAL, RELATIONSHIP
    }
}