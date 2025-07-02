package com.tekion.javaastkg.query.services;

import com.tekion.javaastkg.query.ContextDistiller;
import com.tekion.javaastkg.query.QueryExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

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
    public QueryExecutionContext distill(QueryExecutionContext context) {
        log.debug("Executing distillation [{}]", context.getExecutionId());
        
        try {
            if (context.getRetrievalResult() == null) {
                log.warn("No retrieval result available for distillation [{}]", context.getExecutionId());
                return context;
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
            
            log.debug("Distillation completed - {} relevant contexts [{}]", 
                    distilledContext.size(), context.getExecutionId());
            
            return context;
        } catch (Exception e) {
            log.error("Distillation failed [{}]", context.getExecutionId(), e);
            context.getMetadata().put("distillationError", e.getMessage());
            throw new RuntimeException("Distillation step failed", e);
        }
    }
}