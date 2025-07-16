package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resource requirements for executing a strategy
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceRequirements {
    private int maxTokens;
    private long maxExecutionTimeMs;
    private int maxToolCalls;
    private int maxParallelExecutions;
    
    public static ResourceRequirements standard() {
        return ResourceRequirements.builder()
            .maxTokens(150000)
            .maxExecutionTimeMs(120000)
            .maxToolCalls(8)
            .maxParallelExecutions(3)
            .build();
    }
    
    public static ResourceRequirements light() {
        return ResourceRequirements.builder()
            .maxTokens(50000)
            .maxExecutionTimeMs(60000)
            .maxToolCalls(4)
            .maxParallelExecutions(2)
            .build();
    }
    
    public static ResourceRequirements heavy() {
        return ResourceRequirements.builder()
            .maxTokens(200000)
            .maxExecutionTimeMs(300000)
            .maxToolCalls(12)
            .maxParallelExecutions(4)
            .build();
    }
}