package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Defines how the orchestrator should execute tools for a given query
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStrategy {
    private WorkflowType workflowType;
    private boolean parallel;
    private ResourceRequirements resourceRequirements;
    private long timeoutMs;
    private RetryStrategy retryStrategy;
    private List<HumanInteractionPoint> humanInteractionPoints;
    
    /**
     * 🎯 Strategy types for intent execution
     */
    public enum StrategyType {
        SEMANTIC_SEARCH_FIRST("Start with semantic search, then enrich context"),
        STRUCTURAL_ANALYSIS_FIRST("Start with structural analysis, then semantic search"),
        EXECUTION_TRACE_FIRST("Start with execution tracing, then context"),
        CONTEXT_ENRICHMENT_FIRST("Start with context enrichment, then search"),
        HYBRID_DEEP_DIVE("Use multiple tools in parallel for comprehensive analysis");
        
        private final String description;
        
        StrategyType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public static ExecutionStrategy defaultStrategy() {
        return ExecutionStrategy.builder()
            .workflowType(WorkflowType.SIMPLE_CHAIN)
            .parallel(false)
            .timeoutMs(60000) // 1 minute
            .build();
    }
    
    public static ExecutionStrategy exploratory() {
        return ExecutionStrategy.builder()
            .workflowType(WorkflowType.EXPLORATORY)
            .parallel(true)
            .timeoutMs(180000) // 3 minutes
            .build();
    }
    
    public static ExecutionStrategy surgical() {
        return ExecutionStrategy.builder()
            .workflowType(WorkflowType.SURGICAL)
            .parallel(false)
            .timeoutMs(120000) // 2 minutes
            .build();
    }
}