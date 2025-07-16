package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 📚 Enriched Entity - Code entity enhanced with contextual information
 * 
 * Represents a code entity that has been enriched with:
 * - Semantic meaning and business purpose
 * - Usage patterns and examples
 * - Relationships and dependencies
 * - Performance and quality characteristics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrichedEntity {
    
    /**
     * 🆔 Original entity identifier
     */
    private String entityId;
    
    /**
     * 🏷️ Entity type (class, method, field, etc.)
     */
    private EntityType entityType;
    
    /**
     * 📝 Entity name and basic information
     */
    private String entityName;
    
    /**
     * 📍 Location information
     */
    private String location;
    
    /**
     * 🎯 Business purpose and semantic meaning
     */
    private String businessPurpose;
    
    /**
     * 🔧 Technical implementation details
     */
    private String technicalDescription;
    
    /**
     * 🏢 Domain and business context
     */
    private DomainContext domainContext;
    
    /**
     * 📖 Usage patterns and examples
     */
    private List<UsagePattern> usagePatterns;
    
    /**
     * 🔗 Related entities and dependencies
     */
    private List<EntityRelationship> relationships;
    
    /**
     * 📊 Quality and performance characteristics
     */
    private QualityMetrics qualityMetrics;
    
    /**
     * 💡 Contextual insights about this entity
     */
    private List<ContextualInsight> insights;
    
    /**
     * 🎨 Architectural patterns and design context
     */
    private List<String> architecturalPatterns;
    
    /**
     * ⚠️ Potential issues or concerns
     */
    private List<String> concerns;
    
    /**
     * 💡 Recommendations for improvement
     */
    private List<String> recommendations;
    
    /**
     * 🕒 When this enrichment was performed
     */
    @Builder.Default
    private LocalDateTime enrichedAt = LocalDateTime.now();
    
    /**
     * 📊 Confidence in the enrichment accuracy
     */
    private double enrichmentConfidence;
    
    /**
     * 📋 Additional metadata
     */
    private Map<String, Object> metadata;
    
    public enum EntityType {
        CLASS,
        METHOD,
        FIELD,
        INTERFACE,
        PACKAGE,
        MODULE,
        ANNOTATION,
        ENUM,
        CONSTRUCTOR
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DomainContext {
        private String domainArea;
        private String businessFunction;
        private List<String> stakeholders;
        private String criticalityLevel;
        private List<String> businessRules;
        private Map<String, String> domainTerms;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsagePattern {
        private String patternName;
        private String description;
        private String commonScenario;
        private List<String> codeExamples;
        private double frequency;
        private String context;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntityRelationship {
        private String relatedEntityId;
        private String relationshipType;
        private String description;
        private double strength;
        private boolean isCritical;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityMetrics {
        private double complexity;
        private double maintainability;
        private double testability;
        private double performance;
        private double security;
        private double documentation;
        private String overallGrade;
    }
    
    /**
     * 🎯 Check if entity has high business impact
     */
    public boolean hasHighBusinessImpact() {
        return domainContext != null && 
               ("HIGH".equals(domainContext.getCriticalityLevel()) || 
                "CRITICAL".equals(domainContext.getCriticalityLevel()));
    }
    
    /**
     * 📊 Check if entity has quality concerns
     */
    public boolean hasQualityConcerns() {
        return concerns != null && !concerns.isEmpty() ||
               (qualityMetrics != null && qualityMetrics.getComplexity() > 0.8);
    }
    
    /**
     * 💡 Check if entity has improvement opportunities
     */
    public boolean hasImprovementOpportunities() {
        return recommendations != null && !recommendations.isEmpty();
    }
    
    /**
     * 🔗 Get critical relationships
     */
    public List<EntityRelationship> getCriticalRelationships() {
        return relationships != null ? relationships.stream()
            .filter(EntityRelationship::isCritical)
            .toList() : List.of();
    }
    
    /**
     * 📖 Get most common usage patterns
     */
    public List<UsagePattern> getCommonUsagePatterns() {
        return usagePatterns != null ? usagePatterns.stream()
            .filter(pattern -> pattern.getFrequency() > 0.5)
            .toList() : List.of();
    }
    
    /**
     * 📋 Get enrichment summary
     */
    public String getEnrichmentSummary() {
        StringBuilder summary = new StringBuilder();
        
        if (businessPurpose != null) {
            summary.append("Purpose: ").append(businessPurpose).append("; ");
        }
        
        if (domainContext != null && domainContext.getDomainArea() != null) {
            summary.append("Domain: ").append(domainContext.getDomainArea()).append("; ");
        }
        
        if (usagePatterns != null && !usagePatterns.isEmpty()) {
            summary.append("Usage patterns: ").append(usagePatterns.size()).append("; ");
        }
        
        if (relationships != null && !relationships.isEmpty()) {
            summary.append("Relationships: ").append(relationships.size());
        }
        
        return summary.toString();
    }
}