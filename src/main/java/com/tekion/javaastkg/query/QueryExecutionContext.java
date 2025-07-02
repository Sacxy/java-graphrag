package com.tekion.javaastkg.query;

import com.tekion.javaastkg.model.QueryModels;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Execution context that carries state through the query processing pipeline.
 * This replaces the LangGraph4j state management with a simple, Spring-native approach.
 */
@Data
@Builder
public class QueryExecutionContext {
    private final String originalQuery;
    private final String executionId;
    private final LocalDateTime startTime;
    
    // Step results
    private QueryModels.RetrievalResult retrievalResult;
    private List<ContextDistiller.RelevantContext> distilledContext;
    private QueryModels.QueryResult generatedResult;
    private boolean verified;
    @Builder.Default
    private List<String> verificationErrors = new ArrayList<>();
    
    // Flow control
    @Builder.Default
    private int refinementCount = 0;
    @Builder.Default
    private int maxRefinements = 3;
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    // Async tracking
    @Builder.Default
    private final Map<String, CompletableFuture<?>> stepFutures = new ConcurrentHashMap<>();
    @Builder.Default
    private final List<String> completedSteps = new CopyOnWriteArrayList<>();
    
    /**
     * Checks if refinement is still possible
     */
    public boolean canRefine() {
        return refinementCount < maxRefinements && !verified;
    }
    
    /**
     * Marks a pipeline step as completed
     */
    public void markStepComplete(String stepName) {
        completedSteps.add(stepName);
        metadata.put(stepName + "_completedAt", LocalDateTime.now());
    }
    
    /**
     * Increments the refinement count
     */
    public void incrementRefinementCount() {
        this.refinementCount++;
    }
    
    /**
     * Adds verification error
     */
    public void addVerificationError(String error) {
        this.verificationErrors.add(error);
    }
    
    /**
     * Calculates processing time in milliseconds
     */
    public long getProcessingTimeMs() {
        return System.currentTimeMillis() - 
               (startTime.toEpochSecond(java.time.ZoneOffset.UTC) * 1000);
    }
    
    /**
     * Creates a copy of this context for safe concurrent access
     */
    public QueryExecutionContext copy() {
        return QueryExecutionContext.builder()
                .originalQuery(this.originalQuery)
                .executionId(this.executionId)
                .startTime(this.startTime)
                .retrievalResult(this.retrievalResult)
                .distilledContext(this.distilledContext != null ? new ArrayList<>(this.distilledContext) : null)
                .generatedResult(this.generatedResult)
                .verified(this.verified)
                .verificationErrors(new ArrayList<>(this.verificationErrors))
                .refinementCount(this.refinementCount)
                .maxRefinements(this.maxRefinements)
                .metadata(new HashMap<>(this.metadata))
                .build();
    }
}