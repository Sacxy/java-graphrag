package com.tekion.javaastkg.query.services;

import com.tekion.javaastkg.model.QueryModels;
import com.tekion.javaastkg.query.QueryExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service responsible for the finalization step in the query processing pipeline.
 * Prepares the final result with metadata and confidence scoring.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FinalizationService {
    
    /**
     * Executes the finalization step
     */
    @Async("stepExecutor")
    public QueryExecutionContext finalize(QueryExecutionContext context) {
        log.debug("Executing finalization [{}]", context.getExecutionId());
        
        try {
            if (context.getGeneratedResult() == null) {
                log.warn("No generated result available for finalization [{}]", context.getExecutionId());
                return context;
            }
            
            QueryModels.QueryResult result = context.getGeneratedResult();
            
            // Add metadata about the process
            Map<String, Object> metadata = new HashMap<>(result.getMetadata());
            metadata.put("refinementIterations", context.getRefinementCount());
            metadata.put("verified", context.isVerified());
            metadata.put("executionId", context.getExecutionId());
            metadata.put("completedSteps", context.getCompletedSteps());
            
            // Add context quality metrics
            if (context.getRetrievalResult() != null) {
                metadata.put("contextCandidates", 
                        context.getRetrievalResult().getGraphContext().getMethods().size());
            }
            if (context.getDistilledContext() != null) {
                metadata.put("relevantContexts", context.getDistilledContext().size());
            }
            
            result.setMetadata(metadata);
            
            // Calculate confidence based on verification and context quality
            double confidence = calculateConfidence(context);
            result.setConfidence(confidence);
            
            context.setGeneratedResult(result);
            
            log.debug("Finalization completed - confidence: {:.2f} [{}]", 
                    confidence, context.getExecutionId());
            
            return context;
        } catch (Exception e) {
            log.error("Finalization failed [{}]", context.getExecutionId(), e);
            context.getMetadata().put("finalizationError", e.getMessage());
            throw new RuntimeException("Finalization step failed", e);
        }
    }
    
    /**
     * Calculates confidence score based on various factors
     */
    private double calculateConfidence(QueryExecutionContext context) {
        double confidence = 0.5; // Base confidence
        
        // Factor in verification status
        if (context.isVerified()) {
            confidence += 0.3;
        }
        
        // Factor in context quality
        int contextCount = context.getDistilledContext() != null ? 
                context.getDistilledContext().size() : 0;
        if (contextCount > 5) {
            confidence += 0.15;
        } else if (contextCount > 2) {
            confidence += 0.1;
        }
        
        // Factor in refinement iterations (fewer is better)
        if (context.getRefinementCount() == 0) {
            confidence += 0.05;
        } else {
            confidence -= context.getRefinementCount() * 0.05;
        }
        
        // Factor in retrieval success
        if (context.getRetrievalResult() != null && 
            !context.getRetrievalResult().getTopMethodIds().isEmpty()) {
            confidence += 0.1;
        }
        
        return Math.max(0.0, Math.min(1.0, confidence));
    }
}