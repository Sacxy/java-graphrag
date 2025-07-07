package com.tekion.javaastkg.query.services;

import com.tekion.javaastkg.model.QueryModels;
import com.tekion.javaastkg.query.HybridRetriever;
import com.tekion.javaastkg.query.QueryExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for the retrieval step in the query processing pipeline.
 * Performs hybrid retrieval combining semantic search and graph traversal.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RetrievalService {
    
    private final HybridRetriever hybridRetriever;
    
    /**
     * Executes the retrieval step
     */
    @Async("retrievalExecutor")
    public CompletableFuture<QueryExecutionContext> retrieve(QueryExecutionContext context) {
        log.debug("Executing retrieval for query: {} [{}]", 
                context.getOriginalQuery(), context.getExecutionId());
        
        try {
            QueryModels.RetrievalResult result = hybridRetriever.retrieve(context.getOriginalQuery());
            context.setRetrievalResult(result);
            
            // Add retrieval metadata
            context.getMetadata().put("retrievedMethodCount", result.getTopMethodIds().size());
            context.getMetadata().put("retrievalScore", result.getTopMethodIds().isEmpty() ? 0.0 : 1.0);
            
            log.debug("Retrieval completed - found {} methods [{}]", 
                    result.getTopMethodIds().size(), context.getExecutionId());
            
            return CompletableFuture.completedFuture(context);
        } catch (Exception e) {
            log.error("Retrieval failed [{}]", context.getExecutionId(), e);
            context.getMetadata().put("retrievalError", e.getMessage());
            return CompletableFuture.failedFuture(new RuntimeException("Retrieval step failed", e));
        }
    }
}