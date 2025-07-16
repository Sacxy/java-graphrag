package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 📖 Narrative Generation Result - Complete result of narrative generation
 * 
 * Contains generated narratives and comprehensive generation analysis:
 * - Human-readable flow narratives
 * - Generation quality assessment
 * - Alternative narrative options
 * - Improvement recommendations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NarrativeGenerationResult {
    
    /**
     * 📖 Primary generated narrative
     */
    private FlowNarrative primaryNarrative;
    
    /**
     * 📚 Alternative narrative versions
     */
    private List<FlowNarrative> alternativeNarratives;
    
    /**
     * 📊 Generation quality assessment
     */
    private GenerationQualityAssessment qualityAssessment;
    
    /**
     * 🎯 Narrative adaptation suggestions
     */
    private List<NarrativeAdaptation> adaptationSuggestions;
    
    /**
     * 💡 Content improvement recommendations
     */
    private List<ContentImprovement> contentImprovements;
    
    /**
     * 📈 Audience engagement metrics
     */
    private AudienceEngagementMetrics engagementMetrics;
    
    /**
     * 🔍 Source analysis summary
     */
    private SourceAnalysisSummary sourceAnalysis;
    
    /**
     * 📊 Overall generation confidence
     */
    private double confidence;
    
    /**
     * 📝 Generation process summary
     */
    private String generationSummary;
    
    /**
     * 🕒 When this generation was performed
     */
    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();
    
    /**
     * 📋 Additional metadata
     */
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerationQualityAssessment {
        private double narrativeClarity;
        private double technicalAccuracy;
        private double businessRelevance;
        private double completeness;
        private double readability;
        private double structuralCoherence;
        private String overallGrade;
        private List<String> strengths;
        private List<String> weaknesses;
        private List<String> missingElements;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NarrativeAdaptation {
        private String adaptationType;
        private String targetAudience;
        private String description;
        private List<String> requiredChanges;
        private double feasibility;
        private String expectedImpact;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentImprovement {
        private ImprovementType type;
        private String title;
        private String description;
        private String location;
        private double priority;
        private String implementationGuidance;
        private String expectedBenefit;
        
        public enum ImprovementType {
            CLARITY_ENHANCEMENT,
            TECHNICAL_DETAIL_ADDITION,
            BUSINESS_CONTEXT_IMPROVEMENT,
            EXAMPLE_ADDITION,
            VISUALIZATION_ENHANCEMENT,
            STRUCTURE_IMPROVEMENT,
            LANGUAGE_REFINEMENT,
            EVIDENCE_STRENGTHENING
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudienceEngagementMetrics {
        private double readabilityScore;
        private double technicalDepthMatch;
        private double businessValueClarity;
        private double actionabilityScore;
        private double comprehensiveness;
        private String audienceFitAssessment;
        private List<String> engagementFactors;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceAnalysisSummary {
        private int entitiesAnalyzed;
        private int codePathsTraced;
        private int businessRulesIdentified;
        private int technicalPatternsFound;
        private double sourceComplexity;
        private double analysisDepth;
        private List<String> dataQualityIssues;
        private List<String> analysisLimitations;
    }
    
    /**
     * 📖 Get the best narrative based on quality and audience fit
     */
    public FlowNarrative getBestNarrative() {
        if (primaryNarrative != null && primaryNarrative.hasHighQuality()) {
            return primaryNarrative;
        }
        
        return alternativeNarratives != null ? alternativeNarratives.stream()
            .filter(FlowNarrative::hasHighQuality)
            .findFirst()
            .orElse(primaryNarrative) : primaryNarrative;
    }
    
    /**
     * 💡 Get high-priority content improvements
     */
    public List<ContentImprovement> getHighPriorityImprovements() {
        return contentImprovements != null ? contentImprovements.stream()
            .filter(improvement -> improvement.getPriority() > 0.7)
            .toList() : List.of();
    }
    
    /**
     * 🎯 Get feasible adaptation suggestions
     */
    public List<NarrativeAdaptation> getFeasibleAdaptations() {
        return adaptationSuggestions != null ? adaptationSuggestions.stream()
            .filter(adaptation -> adaptation.getFeasibility() > 0.6)
            .toList() : List.of();
    }
    
    /**
     * 📊 Calculate overall generation success score
     */
    public double getOverallSuccessScore() {
        if (qualityAssessment == null) return 0.0;
        
        double clarity = qualityAssessment.getNarrativeClarity();
        double accuracy = qualityAssessment.getTechnicalAccuracy();
        double relevance = qualityAssessment.getBusinessRelevance();
        double completeness = qualityAssessment.getCompleteness();
        double readability = qualityAssessment.getReadability();
        
        return (clarity + accuracy + relevance + completeness + readability) / 5.0;
    }
    
    /**
     * 🎭 Get audience engagement summary
     */
    public String getAudienceEngagementSummary() {
        if (engagementMetrics == null) return "No engagement metrics available";
        
        return String.format("Readability: %.1f%%, Technical depth match: %.1f%%, Business value clarity: %.1f%%",
            engagementMetrics.getReadabilityScore() * 100,
            engagementMetrics.getTechnicalDepthMatch() * 100,
            engagementMetrics.getBusinessValueClarity() * 100);
    }
    
    /**
     * 📋 Get generation statistics
     */
    public Map<String, Object> getGenerationStatistics() {
        int totalNarratives = 1; // Primary narrative
        if (alternativeNarratives != null) {
            totalNarratives += alternativeNarratives.size();
        }
        
        return Map.of(
            "totalNarratives", totalNarratives,
            "contentImprovements", contentImprovements != null ? contentImprovements.size() : 0,
            "adaptationSuggestions", adaptationSuggestions != null ? adaptationSuggestions.size() : 0,
            "overallSuccessScore", getOverallSuccessScore(),
            "confidence", confidence,
            "sourceComplexity", sourceAnalysis != null ? sourceAnalysis.getSourceComplexity() : 0.0
        );
    }
    
    /**
     * 🔍 Get source analysis overview
     */
    public String getSourceAnalysisOverview() {
        if (sourceAnalysis == null) return "No source analysis available";
        
        return String.format("Analyzed %d entities, traced %d code paths, identified %d business rules",
            sourceAnalysis.getEntitiesAnalyzed(),
            sourceAnalysis.getCodePathsTraced(),
            sourceAnalysis.getBusinessRulesIdentified());
    }
    
    /**
     * 📊 Get quality assessment breakdown
     */
    public Map<String, Double> getQualityBreakdown() {
        if (qualityAssessment == null) return Map.of();
        
        return Map.of(
            "narrativeClarity", qualityAssessment.getNarrativeClarity(),
            "technicalAccuracy", qualityAssessment.getTechnicalAccuracy(),
            "businessRelevance", qualityAssessment.getBusinessRelevance(),
            "completeness", qualityAssessment.getCompleteness(),
            "readability", qualityAssessment.getReadability(),
            "structuralCoherence", qualityAssessment.getStructuralCoherence()
        );
    }
    
    /**
     * 💡 Get improvement recommendations by type
     */
    public Map<String, List<ContentImprovement>> getImprovementsByType() {
        if (contentImprovements == null) return Map.of();
        
        return contentImprovements.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                improvement -> improvement.getType().name()
            ));
    }
    
    /**
     * ❌ Create error result
     */
    public static NarrativeGenerationResult error(String message) {
        return NarrativeGenerationResult.builder()
            .primaryNarrative(null)
            .alternativeNarratives(List.of())
            .confidence(0.0)
            .generationSummary("Error: " + message)
            .qualityAssessment(GenerationQualityAssessment.builder()
                .narrativeClarity(0.0)
                .technicalAccuracy(0.0)
                .businessRelevance(0.0)
                .completeness(0.0)
                .readability(0.0)
                .structuralCoherence(0.0)
                .overallGrade("ERROR")
                .strengths(List.of())
                .weaknesses(List.of("Generation failed"))
                .missingElements(List.of("All elements"))
                .build())
            .contentImprovements(List.of())
            .adaptationSuggestions(List.of())
            .sourceAnalysis(SourceAnalysisSummary.builder()
                .entitiesAnalyzed(0)
                .codePathsTraced(0)
                .businessRulesIdentified(0)
                .technicalPatternsFound(0)
                .sourceComplexity(0.0)
                .analysisDepth(0.0)
                .dataQualityIssues(List.of())
                .analysisLimitations(List.of("Generation failed"))
                .build())
            .build();
    }
    
    /**
     * ✅ Check if generation was successful
     */
    public boolean isSuccessful() {
        return primaryNarrative != null && 
               confidence > 0.5 && 
               getOverallSuccessScore() > 0.6;
    }
}