package com.tekion.javaastkg.query;

import com.tekion.javaastkg.model.QueryModels;
import com.tekion.javaastkg.query.services.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Main query orchestrator that coordinates the entire query processing pipeline.
 * Replaces LangGraph4j with a Spring-native orchestration approach using
 * individual services and a fluent pipeline builder.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class QueryOrchestrator {
    
    private final RetrievalService retrievalService;
    private final DistillationService distillationService;
    private final GenerationService generationService;
    private final VerificationService verificationService;
    private final RefinementService refinementService;
    private final FinalizationService finalizationService;
    
    @Value("${query.refinement.max-iterations:3}")
    private int maxRefinementIterations;
    
    /**
     * Main query processing method
     */
    @Async("queryProcessingExecutor")
    public CompletableFuture<QueryModels.QueryResult> processQuery(String query) {
        String executionId = UUID.randomUUID().toString();
        log.info("Starting query processing: {} [{}]", query, executionId);
        
        QueryExecutionContext context = QueryExecutionContext.builder()
                .originalQuery(query)
                .executionId(executionId)
                .startTime(LocalDateTime.now())
                .maxRefinements(maxRefinementIterations)
                .build();
        
        return QueryPipeline.create(context)
                .step("retrieve", retrievalService::retrieve)
                .step("distill", distillationService::distill)
                .step("generate", generationService::generate)
                .step("verify", verificationService::verify)
                .conditionalStep("refine", 
                        ctx -> !ctx.isVerified() && ctx.canRefine(), 
                        refinementService::refine)
                .step("finalize", finalizationService::finalize)
                .executeAsync()
                .exceptionally(this::handleExecutionError);
    }
    
    /**
     * Handles execution errors gracefully
     */
    private QueryModels.QueryResult handleExecutionError(Throwable throwable) {
        log.error("Query processing failed", throwable);
        
        return QueryModels.QueryResult.builder()
                .summary("An error occurred while processing your query: " + throwable.getMessage())
                .components(List.of())
                .relationships(List.of())
                .confidence(0.0)
                .processingTimeMs(0L)
                .timestamp(LocalDateTime.now())
                .metadata(Map.of(
                        "error", true,
                        "errorMessage", throwable.getMessage(),
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
            return handleExecutionError(e);
        }
    }
}