package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Coverage metrics for graph exploration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExplorationCoverage {
    private int totalNodesExplored;
    private int totalEdgesTraversed;
    private double coveragePercentage;
    private int maxDepthReached;
    private boolean boundedByLimit;
}