package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 🔍 Execution Path Result - Complete result of execution path tracing
 * 
 * Contains the traced execution path and comprehensive analysis:
 * - Sequential execution steps and flow
 * - Performance and complexity analysis
 * - Risk assessment and potential issues
 * - Recommendations for optimization
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionPathResult {
    
    /**
     * 🛤️ The traced execution path as sequential steps
     */
    private List<ExecutionStep> executionPath;
    
    /**
     * 📊 Analysis and insights about the execution path
     */
    private PathAnalysis pathAnalysis;
    
    /**
     * 🎯 Performance metrics and bottlenecks
     */
    private Map<String, Double> performanceMetrics;
    
    /**
     * ⚠️ Identified risks and potential issues
     */
    private List<PathRisk> identifiedRisks;
    
    /**
     * 💡 Recommendations for improvement
     */
    private List<PathRecommendation> recommendations;
    
    /**
     * 🔀 Alternative execution paths discovered
     */
    private List<AlternativePath> alternativePaths;
    
    /**
     * 📈 Confidence in the traced path accuracy
     */
    private double confidence;
    
    /**
     * 📝 Summary explanation of the execution flow
     */
    private String executionSummary;
    
    /**
     * 🕒 When this analysis was performed
     */
    @Builder.Default
    private LocalDateTime analyzedAt = LocalDateTime.now();
    
    /**
     * 📋 Additional metadata about the tracing
     */
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PathAnalysis {
        private int totalSteps;
        private int maxDepthReached;
        private double averageComplexity;
        private int externalDependencies;
        private int conditionalBranches;
        private String dominantPattern;
        private List<String> involvedClasses;
        private Map<String, Integer> stepTypeDistribution;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PathRisk {
        private RiskType type;
        private String description;
        private String location;
        private double severity;
        private String impact;
        private String mitigation;
        
        public enum RiskType {
            PERFORMANCE_BOTTLENECK,
            EXTERNAL_DEPENDENCY_FAILURE,
            CIRCULAR_CALL_RISK,
            EXCEPTION_HANDLING_GAP,
            RESOURCE_LEAK_POTENTIAL,
            SECURITY_VULNERABILITY,
            DATA_CONSISTENCY_RISK
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PathRecommendation {
        private RecommendationType type;
        private String title;
        private String description;
        private String targetLocation;
        private double priority;
        private String expectedBenefit;
        
        public enum RecommendationType {
            PERFORMANCE_OPTIMIZATION,
            ERROR_HANDLING_IMPROVEMENT,
            CODE_SIMPLIFICATION,
            CACHING_OPPORTUNITY,
            PARALLEL_EXECUTION,
            VALIDATION_ENHANCEMENT,
            MONITORING_ADDITION
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlternativePath {
        private String pathName;
        private List<String> keySteps;
        private String description;
        private double efficiency;
        private String tradeoffs;
    }
    
    /**
     * 🎯 Get critical steps in the execution path
     */
    public List<ExecutionStep> getCriticalSteps() {
        return executionPath != null ? executionPath.stream()
            .filter(step -> step.isPerformanceCritical() || step.hasExternalDependencies())
            .toList() : List.of();
    }
    
    /**
     * ⚠️ Get high-severity risks
     */
    public List<PathRisk> getHighSeverityRisks() {
        return identifiedRisks != null ? identifiedRisks.stream()
            .filter(risk -> risk.getSeverity() > 0.7)
            .toList() : List.of();
    }
    
    /**
     * 💡 Get high-priority recommendations
     */
    public List<PathRecommendation> getHighPriorityRecommendations() {
        return recommendations != null ? recommendations.stream()
            .filter(rec -> rec.getPriority() > 0.7)
            .toList() : List.of();
    }
    
    /**
     * 📊 Calculate overall path complexity
     */
    public double getOverallComplexity() {
        if (pathAnalysis == null) return 0.0;
        
        double baseComplexity = pathAnalysis.getAverageComplexity();
        double depthPenalty = pathAnalysis.getMaxDepthReached() > 10 ? 0.2 : 0.0;
        double branchPenalty = pathAnalysis.getConditionalBranches() > 5 ? 0.1 : 0.0;
        
        return Math.min(1.0, baseComplexity + depthPenalty + branchPenalty);
    }
    
    /**
     * ❌ Create error result
     */
    public static ExecutionPathResult error(String message) {
        return ExecutionPathResult.builder()
            .executionPath(List.of())
            .confidence(0.0)
            .executionSummary("Error: " + message)
            .pathAnalysis(PathAnalysis.builder()
                .totalSteps(0)
                .maxDepthReached(0)
                .build())
            .identifiedRisks(List.of())
            .recommendations(List.of())
            .build();
    }
    
    /**
     * ✅ Check if tracing was successful
     */
    public boolean isSuccessful() {
        return executionPath != null && !executionPath.isEmpty() && confidence > 0.5;
    }
}