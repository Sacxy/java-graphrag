package com.tekion.javaastkg.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for asynchronous execution in the query processing pipeline.
 * Replaces LangGraph4j's async execution with Spring's native async support.
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {
    
    /**
     * Main executor for query processing orchestration
     */
    @Bean("queryProcessingExecutor")
    public ThreadPoolTaskExecutor queryProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("QueryProcessor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        // Add custom rejected execution handling
        executor.setRejectedExecutionHandler(new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                log.warn("Query processing task rejected, executing in caller thread");
                if (!executor.isShutdown()) {
                    r.run();
                }
            }
        });
        
        executor.initialize();
        return executor;
    }
    
    /**
     * Executor for individual pipeline steps
     */
    @Bean("stepExecutor")
    public ThreadPoolTaskExecutor stepExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("Step-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        // Custom rejected execution handling for steps
        executor.setRejectedExecutionHandler(new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                log.warn("Step execution task rejected, executing in caller thread");
                if (!executor.isShutdown()) {
                    r.run();
                }
            }
        });
        
        executor.initialize();
        return executor;
    }
    
    /**
     * Executor for retrieval operations (potentially I/O intensive)
     */
    @Bean("retrievalExecutor")
    public ThreadPoolTaskExecutor retrievalExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(150);
        executor.setThreadNamePrefix("Retrieval-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
    
    /**
     * Executor for LLM operations (potentially long-running)
     */
    @Bean("llmExecutor")
    public ThreadPoolTaskExecutor llmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("LLM-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120); // Longer timeout for LLM operations
        executor.initialize();
        return executor;
    }
}