package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 🔍 Execution Path Request - Input for execution path tracing
 * 
 * Represents different types of execution path analysis requests:
 * - Following method call chains and data flow
 * - Tracing execution paths through specific scenarios
 * - Analyzing control flow and decision points
 * - Impact analysis for specific operations
 * 
 * Domain Focus: TRACING the flow of execution through code paths
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionPathRequest {
    
    /**
     * 🎯 Starting point for execution tracing
     */
    private String startingMethod;
    
    /**
     * 🏁 Optional ending point for path tracing
     */
    private String targetMethod;
    
    /**
     * 🔍 Type of execution path to trace
     */
    private PathTraceType traceType;
    
    /**
     * 📊 Maximum depth to trace (prevent infinite recursion)
     */
    @Builder.Default
    private int maxDepth = 10;
    
    /**
     * 🎭 Include specific types of calls/operations
     */
    private List<String> includePatterns;
    
    /**
     * 🚫 Exclude specific types of calls/operations
     */
    private List<String> excludePatterns;
    
    /**
     * ⚙️ Additional tracing parameters
     */
    private Map<String, Object> traceParameters;
    
    /**
     * 🎯 Focus areas for tracing
     */
    private List<TraceFocus> focusAreas;
    
    public enum PathTraceType {
        /**
         * 📞 Follow method call chains
         */
        METHOD_CALLS("Trace method call sequences and dependencies"),
        
        /**
         * 📊 Follow data flow through methods
         */
        DATA_FLOW("Trace how data flows through the system"),
        
        /**
         * 🔀 Analyze control flow and decision points
         */
        CONTROL_FLOW("Trace control flow and decision paths"),
        
        /**
         * 🎯 Trace specific execution scenarios
         */
        SCENARIO_BASED("Trace execution for specific scenarios"),
        
        /**
         * 💥 Analyze impact of changes or operations
         */
        IMPACT_ANALYSIS("Trace impact of changes through execution paths"),
        
        /**
         * 🔄 Trace complete end-to-end workflows
         */
        END_TO_END("Trace complete workflow from start to finish");
        
        private final String description;
        
        PathTraceType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum TraceFocus {
        PERFORMANCE_CRITICAL,
        ERROR_HANDLING,
        SECURITY_CHECKS,
        BUSINESS_LOGIC,
        DATA_VALIDATION,
        EXTERNAL_INTEGRATIONS
    }
    
    /**
     * 📞 Create request for method call tracing
     */
    public static ExecutionPathRequest methodCallTrace(String startingMethod) {
        return ExecutionPathRequest.builder()
            .startingMethod(startingMethod)
            .traceType(PathTraceType.METHOD_CALLS)
            .maxDepth(8)
            .build();
    }
    
    /**
     * 📊 Create request for data flow tracing
     */
    public static ExecutionPathRequest dataFlowTrace(String startingMethod, String targetMethod) {
        return ExecutionPathRequest.builder()
            .startingMethod(startingMethod)
            .targetMethod(targetMethod)
            .traceType(PathTraceType.DATA_FLOW)
            .maxDepth(10)
            .build();
    }
    
    /**
     * 🔀 Create request for control flow analysis
     */
    public static ExecutionPathRequest controlFlowTrace(String startingMethod) {
        return ExecutionPathRequest.builder()
            .startingMethod(startingMethod)
            .traceType(PathTraceType.CONTROL_FLOW)
            .maxDepth(6)
            .build();
    }
    
    /**
     * 🔄 Create request for end-to-end workflow tracing
     */
    public static ExecutionPathRequest endToEndTrace(String startingMethod, String targetMethod) {
        return ExecutionPathRequest.builder()
            .startingMethod(startingMethod)
            .targetMethod(targetMethod)
            .traceType(PathTraceType.END_TO_END)
            .maxDepth(15)
            .build();
    }
}