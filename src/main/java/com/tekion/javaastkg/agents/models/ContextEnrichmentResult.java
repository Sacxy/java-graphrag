package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 📚 Context Enrichment Result - Complete result of context enrichment process
 * 
 * Contains enriched entities and comprehensive contextual analysis:
 * - Entities enhanced with business and technical context
 * - Contextual insights and discoveries
 * - Domain knowledge and relationships
 * - Recommendations for improvement
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextEnrichmentResult {
    
    /**
     * 📚 Entities enriched with contextual information
     */
    private List<EnrichedEntity> enrichedEntities;
    
    /**
     * 💡 Contextual insights discovered during enrichment
     */
    private List<ContextualInsight> insights;
    
    /**
     * 🏢 Domain and business context summary
     */
    private DomainContextSummary domainSummary;
    
    /**
     * 🔗 Cross-entity relationships and patterns
     */
    private List<EntityRelationshipPattern> relationshipPatterns;
    
    /**
     * 📊 Enrichment quality metrics
     */
    private EnrichmentQualityMetrics qualityMetrics;
    
    /**
     * 💡 Overall recommendations for improvement
     */
    private List<EnrichmentRecommendation> recommendations;
    
    /**
     * ⚠️ Identified gaps or concerns in context
     */
    private List<ContextGap> contextGaps;
    
    /**
     * 📈 Overall enrichment confidence
     */
    private double confidence;
    
    /**
     * 📝 Summary of enrichment process and findings
     */
    private String enrichmentSummary;
    
    /**
     * 🕒 When this enrichment was performed
     */
    @Builder.Default
    private LocalDateTime enrichedAt = LocalDateTime.now();
    
    /**
     * 📋 Additional metadata about the enrichment
     */
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DomainContextSummary {
        private String primaryDomain;
        private List<String> involvedDomains;
        private Map<String, String> domainTerminology;
        private List<String> businessProcesses;
        private List<String> keyStakeholders;
        private String architecturalStyle;
        private double domainCoverage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntityRelationshipPattern {
        private String patternName;
        private String description;
        private List<String> involvedEntities;
        private String relationshipType;
        private double strength;
        private String businessSignificance;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnrichmentQualityMetrics {
        private double completeness;
        private double accuracy;
        private double relevance;
        private double depth;
        private int entitiesEnriched;
        private int insightsGenerated;
        private int relationshipsDiscovered;
        private String overallQuality;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnrichmentRecommendation {
        private RecommendationType type;
        private String title;
        private String description;
        private List<String> targetEntities;
        private double priority;
        private String expectedBenefit;
        private String implementationApproach;
        
        public enum RecommendationType {
            DOCUMENTATION_IMPROVEMENT,
            BUSINESS_CONTEXT_ADDITION,
            USAGE_PATTERN_DOCUMENTATION,
            ARCHITECTURAL_CLARIFICATION,
            DOMAIN_KNOWLEDGE_CAPTURE,
            RELATIONSHIP_DOCUMENTATION,
            PERFORMANCE_CONTEXT_ADDITION,
            SECURITY_CONTEXT_ENHANCEMENT
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextGap {
        private GapType type;
        private String description;
        private List<String> affectedEntities;
        private double severity;
        private String recommendedAction;
        
        public enum GapType {
            MISSING_BUSINESS_CONTEXT,
            INCOMPLETE_USAGE_DOCUMENTATION,
            UNCLEAR_ARCHITECTURAL_ROLE,
            INSUFFICIENT_DOMAIN_KNOWLEDGE,
            MISSING_PERFORMANCE_CONTEXT,
            UNCLEAR_RELATIONSHIPS,
            INCOMPLETE_ERROR_HANDLING_CONTEXT
        }
    }
    
    /**
     * 🎯 Get high-impact enriched entities
     */
    public List<EnrichedEntity> getHighImpactEntities() {
        return enrichedEntities != null ? enrichedEntities.stream()
            .filter(EnrichedEntity::hasHighBusinessImpact)
            .toList() : List.of();
    }
    
    /**
     * 💡 Get high-importance insights
     */
    public List<ContextualInsight> getHighImportanceInsights() {
        return insights != null ? insights.stream()
            .filter(ContextualInsight::isHighImportance)
            .toList() : List.of();
    }
    
    /**
     * 🎯 Get actionable insights
     */
    public List<ContextualInsight> getActionableInsights() {
        return insights != null ? insights.stream()
            .filter(ContextualInsight::isActionable)
            .toList() : List.of();
    }
    
    /**
     * 🔗 Get strong relationship patterns
     */
    public List<EntityRelationshipPattern> getStrongRelationshipPatterns() {
        return relationshipPatterns != null ? relationshipPatterns.stream()
            .filter(pattern -> pattern.getStrength() > 0.7)
            .toList() : List.of();
    }
    
    /**
     * 💡 Get high-priority recommendations
     */
    public List<EnrichmentRecommendation> getHighPriorityRecommendations() {
        return recommendations != null ? recommendations.stream()
            .filter(rec -> rec.getPriority() > 0.7)
            .toList() : List.of();
    }
    
    /**
     * ⚠️ Get critical context gaps
     */
    public List<ContextGap> getCriticalContextGaps() {
        return contextGaps != null ? contextGaps.stream()
            .filter(gap -> gap.getSeverity() > 0.7)
            .toList() : List.of();
    }
    
    /**
     * 📊 Calculate overall enrichment score
     */
    public double getOverallEnrichmentScore() {
        if (qualityMetrics == null) return 0.0;
        
        double completeness = qualityMetrics.getCompleteness();
        double accuracy = qualityMetrics.getAccuracy();
        double relevance = qualityMetrics.getRelevance();
        double depth = qualityMetrics.getDepth();
        
        return (completeness + accuracy + relevance + depth) / 4.0;
    }
    
    /**
     * 🏢 Get domain coverage summary
     */
    public String getDomainCoverageSummary() {
        if (domainSummary == null) return "No domain information available";
        
        return String.format("Primary domain: %s, Coverage: %.1f%%, Involved domains: %d",
            domainSummary.getPrimaryDomain(),
            domainSummary.getDomainCoverage() * 100,
            domainSummary.getInvolvedDomains() != null ? domainSummary.getInvolvedDomains().size() : 0);
    }
    
    /**
     * 📈 Get enrichment statistics summary
     */
    public Map<String, Integer> getEnrichmentStatistics() {
        return Map.of(
            "entitiesEnriched", enrichedEntities != null ? enrichedEntities.size() : 0,
            "insightsGenerated", insights != null ? insights.size() : 0,
            "relationshipPatterns", relationshipPatterns != null ? relationshipPatterns.size() : 0,
            "recommendations", recommendations != null ? recommendations.size() : 0,
            "contextGaps", contextGaps != null ? contextGaps.size() : 0
        );
    }
    
    /**
     * 📋 Get enrichment summary by insight type
     */
    public Map<String, Long> getInsightsByType() {
        return insights != null ? insights.stream()
            .collect(Collectors.groupingBy(
                insight -> insight.getType().name(),
                Collectors.counting()
            )) : Map.of();
    }
    
    /**
     * ❌ Create error result
     */
    public static ContextEnrichmentResult error(String message) {
        return ContextEnrichmentResult.builder()
            .enrichedEntities(List.of())
            .insights(List.of())
            .confidence(0.0)
            .enrichmentSummary("Error: " + message)
            .qualityMetrics(EnrichmentQualityMetrics.builder()
                .completeness(0.0)
                .accuracy(0.0)
                .relevance(0.0)
                .depth(0.0)
                .entitiesEnriched(0)
                .insightsGenerated(0)
                .overallQuality("ERROR")
                .build())
            .recommendations(List.of())
            .contextGaps(List.of())
            .build();
    }
    
    /**
     * ✅ Check if enrichment was successful
     */
    public boolean isSuccessful() {
        return enrichedEntities != null && !enrichedEntities.isEmpty() && 
               confidence > 0.5 && getOverallEnrichmentScore() > 0.5;
    }
}