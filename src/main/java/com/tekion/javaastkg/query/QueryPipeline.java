package com.tekion.javaastkg.query;

import com.tekion.javaastkg.model.QueryModels;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Fluent pipeline builder for query processing workflow.
 * Provides a clean, readable way to define the execution pipeline.
 * Note: This is NOT a Spring component - it's a builder pattern class.
 */
@Slf4j
public class QueryPipeline {
    
    private final List<PipelineStep> steps = new ArrayList<>();
    private final QueryExecutionContext context;
    
    private QueryPipeline(QueryExecutionContext context) {
        this.context = context;
    }
    
    /**
     * Creates a new pipeline instance
     */
    public static QueryPipeline create(QueryExecutionContext context) {
        return new QueryPipeline(context);
    }
    
    /**
     * Adds a mandatory step to the pipeline
     */
    public QueryPipeline step(String name, Function<QueryExecutionContext, QueryExecutionContext> stepFunction) {
        steps.add(new PipelineStep(name, stepFunction, ctx -> true));
        return this;
    }
    
    /**
     * Adds a conditional step to the pipeline
     */
    public QueryPipeline conditionalStep(String name, 
                                       Predicate<QueryExecutionContext> condition,
                                       Function<QueryExecutionContext, QueryExecutionContext> stepFunction) {
        steps.add(new PipelineStep(name, stepFunction, condition));
        return this;
    }
    
    /**
     * Executes the pipeline asynchronously
     */
    public CompletableFuture<QueryModels.QueryResult> executeAsync() {
        log.info("Starting pipeline execution for query: {} [{}]", 
                context.getOriginalQuery(), context.getExecutionId());
        
        return CompletableFuture
                .supplyAsync(() -> context)
                .thenCompose(this::executeStepsSequentially)
                .thenApply(ctx -> {
                    // Finalize the result
                    QueryModels.QueryResult result = ctx.getGeneratedResult();
                    if (result != null) {
                        result.setProcessingTimeMs(ctx.getProcessingTimeMs());
                        result.setTimestamp(LocalDateTime.now());
                        
                        // Add execution metadata
                        result.getMetadata().putAll(ctx.getMetadata());
                        result.getMetadata().put("executionId", ctx.getExecutionId());
                        result.getMetadata().put("completedSteps", ctx.getCompletedSteps());
                        result.getMetadata().put("refinementCount", ctx.getRefinementCount());
                        result.getMetadata().put("verified", ctx.isVerified());
                    }
                    
                    log.info("Pipeline execution completed for query: {} [{}]", 
                            ctx.getOriginalQuery(), ctx.getExecutionId());
                    return result;
                })
                .exceptionally(this::handleExecutionError);
    }
    
    /**
     * Executes all steps sequentially
     */
    private CompletableFuture<QueryExecutionContext> executeStepsSequentially(QueryExecutionContext ctx) {
        CompletableFuture<QueryExecutionContext> pipeline = CompletableFuture.completedFuture(ctx);
        
        for (PipelineStep step : steps) {
            pipeline = pipeline.thenCompose(currentCtx -> executeStep(step, currentCtx));
        }
        
        return pipeline;
    }
    
    /**
     * Executes a single pipeline step
     */
    private CompletableFuture<QueryExecutionContext> executeStep(PipelineStep step, QueryExecutionContext ctx) {
        if (!step.condition.test(ctx)) {
            log.debug("Skipping step '{}' - condition not met [{}]", step.name, ctx.getExecutionId());
            return CompletableFuture.completedFuture(ctx);
        }
        
        return CompletableFuture
                .supplyAsync(() -> {
                    log.debug("Executing step: {} [{}]", step.name, ctx.getExecutionId());
                    long startTime = System.currentTimeMillis();
                    
                    try {
                        QueryExecutionContext result = step.function.apply(ctx);
                        result.markStepComplete(step.name);
                        
                        long duration = System.currentTimeMillis() - startTime;
                        log.debug("Step '{}' completed in {}ms [{}]", step.name, duration, ctx.getExecutionId());
                        
                        return result;
                    } catch (Exception e) {
                        log.error("Step '{}' failed [{}]", step.name, ctx.getExecutionId(), e);
                        throw new RuntimeException("Step failed: " + step.name, e);
                    }
                })
                .thenCompose(result -> {
                    // Handle conditional routing after verify step
                    if ("verify".equals(step.name) && !result.isVerified() && result.canRefine()) {
                        log.info("Verification failed, entering refinement loop [{}]", result.getExecutionId());
                        return executeRefinementLoop(result);
                    }
                    return CompletableFuture.completedFuture(result);
                });
    }
    
    /**
     * Handles the refinement loop when verification fails
     */
    private CompletableFuture<QueryExecutionContext> executeRefinementLoop(QueryExecutionContext ctx) {
        if (!ctx.canRefine()) {
            log.warn("Cannot refine further - max iterations reached [{}]", ctx.getExecutionId());
            return CompletableFuture.completedFuture(ctx);
        }
        
        log.debug("Starting refinement iteration {} [{}]", 
                ctx.getRefinementCount() + 1, ctx.getExecutionId());
        
        ctx.incrementRefinementCount();
        
        return CompletableFuture
                .supplyAsync(() -> ctx)
                .thenCompose(refinedCtx -> {
                    // Find and execute refinement step
                    for (PipelineStep step : steps) {
                        if ("refine".equals(step.name)) {
                            return executeStep(step, refinedCtx);
                        }
                    }
                    return CompletableFuture.completedFuture(refinedCtx);
                })
                .thenCompose(refinedCtx -> {
                    // Re-execute generate step
                    for (PipelineStep step : steps) {
                        if ("generate".equals(step.name)) {
                            return executeStep(step, refinedCtx);
                        }
                    }
                    return CompletableFuture.completedFuture(refinedCtx);
                })
                .thenCompose(generatedCtx -> {
                    // Re-execute verify step
                    for (PipelineStep step : steps) {
                        if ("verify".equals(step.name)) {
                            return executeStep(step, generatedCtx);
                        }
                    }
                    return CompletableFuture.completedFuture(generatedCtx);
                })
                .thenCompose(verifiedCtx -> {
                    // Recursive call if still not verified and can refine
                    if (!verifiedCtx.isVerified() && verifiedCtx.canRefine()) {
                        log.debug("Refinement iteration {} failed, trying again [{}]", 
                                verifiedCtx.getRefinementCount(), verifiedCtx.getExecutionId());
                        return executeRefinementLoop(verifiedCtx);
                    }
                    
                    if (verifiedCtx.isVerified()) {
                        log.info("Refinement successful after {} iterations [{}]", 
                                verifiedCtx.getRefinementCount(), verifiedCtx.getExecutionId());
                    } else {
                        log.warn("Refinement failed after {} iterations [{}]", 
                                verifiedCtx.getRefinementCount(), verifiedCtx.getExecutionId());
                    }
                    
                    return CompletableFuture.completedFuture(verifiedCtx);
                });
    }
    
    /**
     * Handles execution errors
     */
    private QueryModels.QueryResult handleExecutionError(Throwable throwable) {
        log.error("Pipeline execution failed [{}]", context.getExecutionId(), throwable);
        
        return QueryModels.QueryResult.builder()
                .query(context.getOriginalQuery())
                .summary("An error occurred while processing your query: " + throwable.getMessage())
                .components(new ArrayList<>())
                .relationships(new ArrayList<>())
                .confidence(0.0)
                .processingTimeMs(context.getProcessingTimeMs())
                .timestamp(LocalDateTime.now())
                .metadata(Map.of(
                        "error", true,
                        "errorMessage", throwable.getMessage(),
                        "executionId", context.getExecutionId(),
                        "failedSteps", context.getCompletedSteps()
                ))
                .build();
    }
    
    /**
     * Represents a single step in the pipeline
     */
    @Data
    private static class PipelineStep {
        private final String name;
        private final Function<QueryExecutionContext, QueryExecutionContext> function;
        private final Predicate<QueryExecutionContext> condition;
    }
}