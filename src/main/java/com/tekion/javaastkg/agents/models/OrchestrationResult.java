package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 🧠 Orchestration Result - Complete result of intelligent orchestration
 * 
 * Contains coordinated results from multiple tools and comprehensive analysis:
 * - Synthesized insights from all tools
 * - Orchestration execution summary
 * - Tool coordination metrics
 * - Final recommendations and actions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrchestrationResult {
    
    /**
     * 📝 Summary of the orchestration execution
     */
    private String executionSummary;
    
    /**
     * 🎯 Key findings and insights
     */
    private List<SynthesizedInsight> keyInsights;
    
    /**
     * 📊 Results from individual tools
     */
    private ToolExecutionResults toolResults;
    
    /**
     * 🔄 Orchestration execution flow
     */
    private List<OrchestrationStep> executionFlow;
    
    /**
     * 💡 Final recommendations
     */
    private List<OrchestrationRecommendation> recommendations;
    
    /**
     * 📈 Quality and confidence metrics
     */
    private OrchestrationQualityMetrics qualityMetrics;
    
    /**
     * 🎯 Answer to the original user query
     */
    private String userQueryAnswer;
    
    /**
     * 📋 Supporting evidence and data
     */
    private Map<String, Object> supportingEvidence;
    
    /**
     * 🚀 Performance metrics of the orchestration
     */
    private OrchestrationPerformanceMetrics performanceMetrics;
    
    /**
     * 🔄 Next recommended actions
     */
    private List<NextAction> nextActions;
    
    /**
     * 📊 Overall orchestration confidence
     */
    private double confidence;
    
    /**
     * 🕒 When this orchestration was completed
     */
    @Builder.Default
    private LocalDateTime completedAt = LocalDateTime.now();
    
    /**
     * 📋 Additional metadata
     */
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SynthesizedInsight {
        private String title;
        private String description;
        private InsightType type;
        private double confidence;
        private List<String> supportingEvidence;
        private List<String> sourcingTools;
        private String businessImpact;
        private String technicalImplication;
        
        public enum InsightType {
            ARCHITECTURAL_FINDING,
            PERFORMANCE_INSIGHT,
            SECURITY_CONCERN,
            QUALITY_ISSUE,
            BUSINESS_IMPACT,
            TECHNICAL_DEBT,
            IMPROVEMENT_OPPORTUNITY,
            RISK_ASSESSMENT
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolExecutionResults {
        private IntentClassificationResult queryClassification;
        private SemanticSearchResult semanticSearch;
        private StructuralAnalysisResult structuralAnalysis;
        private ExecutionPathResult executionPath;
        private ContextEnrichmentResult contextEnrichment;
        private NarrativeGenerationResult narrativeGeneration;
        private List<String> executedTools;
        private List<String> skippedTools;
        private Map<String, String> toolExecutionReasons;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrchestrationStep {
        private int stepNumber;
        private String toolName;
        private String purpose;
        private String inputSummary;
        private String outputSummary;
        private long executionTimeMs;
        private boolean successful;
        private String failureReason;
        private Map<String, Object> metrics;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrchestrationRecommendation {
        private String title;
        private String description;
        private RecommendationType type;
        private double priority;
        private String rationale;
        private List<String> prerequisites;
        private String implementationGuidance;
        private String expectedBenefit;
        private Map<String, Object> additionalData;
        
        public enum RecommendationType {
            IMMEDIATE_ACTION,
            ARCHITECTURE_IMPROVEMENT,
            PERFORMANCE_OPTIMIZATION,
            SECURITY_ENHANCEMENT,
            DOCUMENTATION_UPDATE,
            TECHNICAL_DEBT_REDUCTION,
            MONITORING_ADDITION,
            TEAM_COLLABORATION
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrchestrationQualityMetrics {
        private double completeness;
        private double accuracy;
        private double coherence;
        private double relevance;
        private double actionability;
        private double toolSynergy;
        private String overallGrade;
        private List<String> qualityFactors;
        private List<String> limitationFactors;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrchestrationPerformanceMetrics {
        private long totalExecutionTimeMs;
        private long averageToolExecutionTimeMs;
        private double parallelizationEfficiency;
        private int totalToolsExecuted;
        private int successfulToolExecutions;
        private int failedToolExecutions;
        private Map<String, Long> toolExecutionTimes;
        private String performanceGrade;
    }
    
    /**
     * 🎯 Get high-confidence insights
     */
    public List<SynthesizedInsight> getHighConfidenceInsights() {
        return keyInsights != null ? keyInsights.stream()
            .filter(insight -> insight.getConfidence() > 0.8)
            .toList() : List.of();
    }
    
    /**
     * 💡 Get high-priority recommendations
     */
    public List<OrchestrationRecommendation> getHighPriorityRecommendations() {
        return recommendations != null ? recommendations.stream()
            .filter(rec -> rec.getPriority() > 0.8)
            .toList() : List.of();
    }
    
    /**
     * 🚀 Get immediate action items
     */
    public List<OrchestrationRecommendation> getImmediateActions() {
        return recommendations != null ? recommendations.stream()
            .filter(rec -> rec.getType() == OrchestrationRecommendation.RecommendationType.IMMEDIATE_ACTION)
            .toList() : List.of();
    }
    
    /**
     * 📊 Get orchestration success rate
     */
    public double getSuccessRate() {
        if (performanceMetrics == null) return 0.0;
        
        int total = performanceMetrics.getTotalToolsExecuted();
        int successful = performanceMetrics.getSuccessfulToolExecutions();
        
        return total > 0 ? (double) successful / total : 0.0;
    }
    
    /**
     * 🔄 Get executed tools summary
     */
    public String getExecutedToolsSummary() {
        if (toolResults == null || toolResults.getExecutedTools() == null) {
            return "No tool execution data available";
        }
        
        return String.format("Executed %d tools: %s", 
            toolResults.getExecutedTools().size(),
            String.join(", ", toolResults.getExecutedTools()));
    }
    
    /**
     * 📈 Get overall orchestration score
     */
    public double getOverallScore() {
        if (qualityMetrics == null) return 0.0;
        
        double completeness = qualityMetrics.getCompleteness();
        double accuracy = qualityMetrics.getAccuracy();
        double coherence = qualityMetrics.getCoherence();
        double relevance = qualityMetrics.getRelevance();
        double actionability = qualityMetrics.getActionability();
        
        return (completeness + accuracy + coherence + relevance + actionability) / 5.0;
    }
    
    /**
     * 🎯 Get insights by type
     */
    public Map<String, List<SynthesizedInsight>> getInsightsByType() {
        return keyInsights != null ? keyInsights.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                insight -> insight.getType().name()
            )) : Map.of();
    }
    
    /**
     * 📊 Get execution timing summary
     */
    public String getExecutionTimingSummary() {
        if (performanceMetrics == null) return "No timing data available";
        
        return String.format("Total: %dms, Average per tool: %dms, Efficiency: %.1f%%",
            performanceMetrics.getTotalExecutionTimeMs(),
            performanceMetrics.getAverageToolExecutionTimeMs(),
            performanceMetrics.getParallelizationEfficiency() * 100);
    }
    
    /**
     * 🎯 Get business impact summary
     */
    public String getBusinessImpactSummary() {
        if (keyInsights == null) return "No insights available";
        
        long businessImpactInsights = keyInsights.stream()
            .filter(insight -> insight.getBusinessImpact() != null)
            .count();
        
        return String.format("Found %d insights with business impact from %d total insights",
            businessImpactInsights, keyInsights.size());
    }
    
    /**
     * 🔍 Get detailed analysis availability
     */
    public Map<String, Boolean> getAnalysisAvailability() {
        if (toolResults == null) return Map.of();
        
        return Map.of(
            "queryClassification", toolResults.getQueryClassification() != null,
            "semanticSearch", toolResults.getSemanticSearch() != null,
            "structuralAnalysis", toolResults.getStructuralAnalysis() != null,
            "executionPath", toolResults.getExecutionPath() != null,
            "contextEnrichment", toolResults.getContextEnrichment() != null,
            "narrativeGeneration", toolResults.getNarrativeGeneration() != null
        );
    }
    
    /**
     * ❌ Create error result
     */
    public static OrchestrationResult error(String message) {
        return OrchestrationResult.builder()
            .executionSummary("Orchestration failed: " + message)
            .keyInsights(List.of())
            .recommendations(List.of())
            .userQueryAnswer("Unable to process query due to error: " + message)
            .confidence(0.0)
            .qualityMetrics(OrchestrationQualityMetrics.builder()
                .completeness(0.0)
                .accuracy(0.0)
                .coherence(0.0)
                .relevance(0.0)
                .actionability(0.0)
                .toolSynergy(0.0)
                .overallGrade("ERROR")
                .qualityFactors(List.of())
                .limitationFactors(List.of("Orchestration failed"))
                .build())
            .performanceMetrics(OrchestrationPerformanceMetrics.builder()
                .totalExecutionTimeMs(0)
                .totalToolsExecuted(0)
                .successfulToolExecutions(0)
                .failedToolExecutions(0)
                .performanceGrade("ERROR")
                .build())
            .nextActions(List.of())
            .build();
    }
    
    /**
     * ✅ Check if orchestration was successful
     */
    public boolean isSuccessful() {
        return confidence > 0.5 && 
               getOverallScore() > 0.6 && 
               getSuccessRate() > 0.5;
    }
}