package com.tekion.javaastkg.query.services;

import com.tekion.javaastkg.query.QueryExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for the refinement step in the query processing pipeline.
 * Prepares context for regeneration based on verification feedback.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RefinementService {
    
    /**
     * Executes the refinement step
     */
    @Async("stepExecutor")
    public CompletableFuture<QueryExecutionContext> refine(QueryExecutionContext context) {
        log.debug("Executing refinement iteration {} [{}]", 
                context.getRefinementCount(), context.getExecutionId());
        
        try {
            // Add refinement metadata for the generation service to use
            context.getMetadata().put("isRefinement", true);
            context.getMetadata().put("refinementIteration", context.getRefinementCount());
            context.getMetadata().put("previousErrors", context.getVerificationErrors());
            
            // Clear verification state for re-verification
            context.setVerified(false);
            
            log.debug("Refinement preparation completed [{}]", context.getExecutionId());
            
            return CompletableFuture.completedFuture(context);
        } catch (Exception e) {
            log.error("Refinement failed [{}]", context.getExecutionId(), e);
            context.getMetadata().put("refinementError", e.getMessage());
            return CompletableFuture.failedFuture(new RuntimeException("Refinement step failed", e));
        }
    }
}