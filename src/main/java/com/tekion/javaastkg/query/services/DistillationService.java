package com.tekion.javaastkg.query.services;

import com.tekion.javaastkg.query.ContextDistiller;
import com.tekion.javaastkg.query.QueryExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for the distillation step in the query processing pipeline.
 * Filters and refines context for better LLM processing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DistillationService {
    
    private final ContextDistiller contextDistiller;
    
    /**
     * Executes the distillation step
     */
    @Async("stepExecutor")
    public CompletableFuture<QueryExecutionContext> distill(QueryExecutionContext context) {
        log.debug("Executing distillation [{}]", context.getExecutionId());
        
        try {
            if (context.getRetrievalResult() == null) {
                log.warn("No retrieval result available for distillation [{}]", context.getExecutionId());
                return CompletableFuture.completedFuture(context);
            }
            
            List<ContextDistiller.RelevantContext> distilledContext = contextDistiller.distill(
                    context.getOriginalQuery(),
                    context.getRetrievalResult().getGraphContext()
            );
            
            context.setDistilledContext(distilledContext);
            
            // Add distillation metadata
            context.getMetadata().put("distilledContextCount", distilledContext.size());
            context.getMetadata().put("distillationRatio", 
                    context.getRetrievalResult().getGraphContext().getMethods().isEmpty() ? 0.0 :
                    (double) distilledContext.size() / context.getRetrievalResult().getGraphContext().getMethods().size());
            
            // Log detailed information about distilled results
            log.info("Distillation completed - {} relevant contexts out of {} candidates [{}]", 
                    distilledContext.size(), 
                    context.getRetrievalResult().getGraphContext().getMethods().size(),
                    context.getExecutionId());
            
            // Log top 5 most relevant contexts for visibility
            distilledContext.stream()
                    .limit(5)
                    .forEach(ctx -> log.info("Top context: {} - {} (Reason: {})", 
                            ctx.getCandidate().getType(),
                            ctx.getCandidate().getName(),
                            ctx.getRelevanceReason()));
            
            return CompletableFuture.completedFuture(context);
        } catch (Exception e) {
            log.error("Distillation failed [{}]", context.getExecutionId(), e);
            context.getMetadata().put("distillationError", e.getMessage());
            return CompletableFuture.failedFuture(new RuntimeException("Distillation step failed", e));
        }
    }
}