package com.tekion.javaastkg.query;

import com.tekion.javaastkg.model.QueryModels;
import com.tekion.javaastkg.query.services.GenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Main query orchestrator that coordinates query processing.
 * Uses HybridRetriever for structured results and GenerationService for natural language summaries.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class QueryOrchestrator {
    
    private final HybridRetriever hybridRetriever;
    private final GenerationService generationService;
    
    /**
     * Main query processing method
     */
    @Async("queryProcessingExecutor")
    public CompletableFuture<QueryModels.QueryResult> processQuery(String query) {
        log.info("Processing query: {}", query);

        // EMERGENCY FIX: Add input validation
        if (query == null || query.trim().isEmpty()) {
            log.warn("Received null or empty query");
            return CompletableFuture.completedFuture(createErrorResult(query, "Empty query provided"));
        }

        try {
            // 1. Get structured results from hybrid retriever
            QueryModels.RetrievalResult retrievalResult = hybridRetriever.retrieve(query);
            
            log.info("QUERY_ORCHESTRATOR: Retrieved {} top method IDs", retrievalResult.getTopMethodIds().size());
            log.info("QUERY_ORCHESTRATOR: Retrieved {} methods, {} classes in graph context", 
                    retrievalResult.getGraphContext().getMethods().size(),
                    retrievalResult.getGraphContext().getClasses().size());
            log.info("QUERY_ORCHESTRATOR: Score map has {} entries", retrievalResult.getScoreMap().size());
            
            // Log some top-scoring methods and classes
            retrievalResult.getGraphContext().getMethods().stream()
                    .limit(3)
                    .forEach(method -> log.info("QUERY_ORCHESTRATOR: Top method - {} (id: {})", 
                            method.getName(), method.getId()));
            
            retrievalResult.getGraphContext().getClasses().stream()
                    .limit(3)
                    .forEach(clazz -> log.info("QUERY_ORCHESTRATOR: Top class - {} (id: {})", 
                            clazz.getName(), clazz.getId()));
            
            // 2. Generate natural language summary
            String naturalLanguageSummary = generationService.generateNaturalSummary(query, retrievalResult);
            
            // 3. Build combined response with both structured data and natural language
            QueryModels.QueryResult result = buildQueryResult(query, retrievalResult, naturalLanguageSummary);
            
            log.info("Query processing completed successfully for: {}", query);
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Query processing failed for: {}", query, e);
            return CompletableFuture.completedFuture(handleExecutionError(e, query));
        }
    }
    
    /**
     * Builds the final query result combining structured retrieval data with natural language summary
     */
    private QueryModels.QueryResult buildQueryResult(String query, 
                                                    QueryModels.RetrievalResult retrievalResult,
                                                    String naturalLanguageSummary) {
        
        // Initialize empty lists for the case when retrievalResult is null or empty
        List<QueryModels.RelevantComponent> components = new ArrayList<>();
        List<QueryModels.RelationshipInsight> relationships = new ArrayList<>();
        
        if (retrievalResult != null && retrievalResult.getGraphContext() != null) {
            // Convert retrieved methods to components
            List<QueryModels.RelevantComponent> methodComponents = retrievalResult.getGraphContext().getMethods().stream()
                    .map(method -> QueryModels.RelevantComponent.builder()
                            .type("method")
                            .signature(method.getSignature())
                            .name(method.getName())
                            .summary(method.getClassName() != null ? "Method in " + method.getClassName() : "Method")
                            .relevanceScore(retrievalResult.getScoreMap().getOrDefault(method.getId(), 0.5))
                            .businessTags(method.getBusinessTags())
                            .metadata(method.getMetadata())
                            .build())
                    .toList();
            
            // Add class components
            List<QueryModels.RelevantComponent> classComponents = retrievalResult.getGraphContext().getClasses().stream()
                    .map(clazz -> QueryModels.RelevantComponent.builder()
                            .type("class")
                            .signature(clazz.getFullName())
                            .name(clazz.getName())
                            .summary(clazz.getType() + (clazz.isInterface() ? " interface" : " class"))
                            .relevanceScore(retrievalResult.getScoreMap().getOrDefault(clazz.getId(), 0.5))
                            .metadata(clazz.getMetadata())
                            .build())
                    .toList();
            
            // Combine all components
            components.addAll(methodComponents);
            components.addAll(classComponents);
            
            // Convert relationships
            relationships = retrievalResult.getGraphContext().getRelationships().stream()
                    .map(rel -> QueryModels.RelationshipInsight.builder()
                            .description("Relationship: " + rel.getType())
                            .fromComponent(rel.getFromId())
                            .toComponent(rel.getToId())
                            .relationshipType(rel.getType())
                            .build())
                    .toList();
        }
        
        return QueryModels.QueryResult.builder()
                .query(query)
                .summary(naturalLanguageSummary)
                .components(components)
                .relationships(relationships)
                .confidence(calculateConfidence(retrievalResult))
                .processingTimeMs(System.currentTimeMillis())
                .timestamp(LocalDateTime.now())
                .metadata(Map.of(
                        "retrievedNodes", components.size(),
                        "avgRelevanceScore", retrievalResult != null && retrievalResult.getScoreMap() != null ? 
                            retrievalResult.getScoreMap().values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0) : 0.0,
                        "methodCount", components.stream().mapToInt(c -> "method".equals(c.getType()) ? 1 : 0).sum(),
                        "classCount", components.stream().mapToInt(c -> "class".equals(c.getType()) ? 1 : 0).sum()
                ))
                .build();
    }
    
    /**
     * Calculates confidence based on retrieval quality
     */
    private double calculateConfidence(QueryModels.RetrievalResult retrievalResult) {
        if (retrievalResult.getScoreMap().isEmpty()) {
            return 0.1;
        }
        
        double avgScore = retrievalResult.getScoreMap().values().stream()
                .mapToDouble(Double::doubleValue)
                .average().orElse(0.0);
                
        int nodeCount = retrievalResult.getGraphContext().getMethods().size() + 
                       retrievalResult.getGraphContext().getClasses().size();
        
        // Base confidence on average score and number of results
        double confidence = avgScore * 0.7;
        if (nodeCount > 3) confidence += 0.1;
        if (nodeCount > 5) confidence += 0.1;
        
        return Math.max(0.1, Math.min(1.0, confidence));
    }
    
    /**
     * EMERGENCY FIX: Creates error result for input validation failures
     */
    private QueryModels.QueryResult createErrorResult(String query, String errorMessage) {
        return QueryModels.QueryResult.builder()
                .query(query != null ? query : "")
                .summary("I apologize, but I cannot process your request: " + errorMessage)
                .components(List.of())
                .relationships(List.of())
                .confidence(0.0)
                .processingTimeMs(0L)
                .timestamp(LocalDateTime.now())
                .metadata(Map.of(
                        "error", true,
                        "errorMessage", errorMessage,
                        "errorType", "ValidationError"
                ))
                .build();
    }

    /**
     * Handles execution errors gracefully
     */
    private QueryModels.QueryResult handleExecutionError(Throwable throwable, String query) {
        return QueryModels.QueryResult.builder()
                .query(query)
                .summary("I apologize, but I encountered an error while processing your query: " + throwable.getMessage())
                .components(List.of())
                .relationships(List.of())
                .confidence(0.0)
                .processingTimeMs(0L)
                .timestamp(LocalDateTime.now())
                .metadata(Map.of(
                        "error", true,
                        "errorMessage", throwable.getMessage() != null ? throwable.getMessage() : "Unknown error",
                        "errorType", throwable.getClass().getSimpleName()
                ))
                .build();
    }
    
    /**
     * Legacy compatibility method for existing callers
     */
    public QueryModels.QueryResult query(String userQuery) {
        try {
            return processQuery(userQuery).get();
        } catch (Exception e) {
            log.error("Synchronous query processing failed", e);
            return handleExecutionError(e, userQuery);
        }
    }
}