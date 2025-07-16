package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Result of structural code exploration and pattern detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuralAnalysisResult {
    private FocusedSubgraph focusedGraph;
    private List<ArchitecturalPattern> patterns;
    private List<StructuralInsight> insights;
    private Map<String, Double> importanceScores;
    private ExplorationCoverage explorationCoverage;
    private double confidence;
    private String explanation;
    private List<NextAction> nextActions;
    private Map<String, Object> metadata;
    
    public boolean hasHighValuePatterns() {
        return patterns != null && patterns.stream().anyMatch(p -> p.getConfidence() > 0.8);
    }
    
    public List<String> getCriticalNodes() {
        return importanceScores != null ? importanceScores.entrySet().stream()
            .filter(entry -> entry.getValue() > 0.8)
            .map(Map.Entry::getKey)
            .toList() : List.of();
    }
    
    public static StructuralAnalysisResult error(String message) {
        return StructuralAnalysisResult.builder()
            .focusedGraph(FocusedSubgraph.empty())
            .patterns(List.of())
            .insights(List.of())
            .confidence(0.0)
            .explanation("Error: " + message)
            .build();
    }
}