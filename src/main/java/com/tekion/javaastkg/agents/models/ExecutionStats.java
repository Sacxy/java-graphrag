package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics about execution performance
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStats {
    private long totalExecutionTimeMs;
    private long searchTimeMs;
    private long processingTimeMs;
    private int totalResults;
    private int filteredResults;
    private double averageConfidence;
    private boolean parallelExecution;
}