package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 💡 Contextual Insight - Insight derived from context enrichment
 * 
 * Represents meaningful insights about code entities discovered through
 * context enrichment process:
 * - Business and domain insights
 * - Usage pattern insights
 * - Quality and performance insights
 * - Architectural and design insights
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextualInsight {
    
    /**
     * 🏷️ Type of contextual insight
     */
    private InsightType type;
    
    /**
     * 📝 Insight title
     */
    private String title;
    
    /**
     * 📋 Detailed description
     */
    private String description;
    
    /**
     * 🎯 Target entity this insight applies to
     */
    private String targetEntity;
    
    /**
     * 🏢 Business or domain category
     */
    private String category;
    
    /**
     * 📊 Importance level (0.0 to 1.0)
     */
    private double importance;
    
    /**
     * 🎭 Context that led to this insight
     */
    private String context;
    
    /**
     * 🔍 Evidence supporting this insight
     */
    private List<String> evidence;
    
    /**
     * 💡 Actionable recommendations
     */
    private List<String> recommendations;
    
    /**
     * 📈 Impact assessment
     */
    private ImpactAssessment impact;
    
    /**
     * 🔗 Related entities or concepts
     */
    private List<String> relatedEntities;
    
    /**
     * 🕒 When this insight was generated
     */
    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();
    
    /**
     * 📊 Confidence in this insight (0.0 to 1.0)
     */
    private double confidence;
    
    /**
     * 📋 Additional metadata
     */
    private Map<String, Object> metadata;
    
    public enum InsightType {
        /**
         * 🏢 Business and domain insights
         */
        BUSINESS_SIGNIFICANCE("Business significance and domain importance"),
        
        /**
         * 📖 Usage pattern insights
         */
        USAGE_PATTERN("Common usage patterns and practices"),
        
        /**
         * 🎨 Architectural insights
         */
        ARCHITECTURAL_ROLE("Architectural role and design patterns"),
        
        /**
         * 📊 Performance insights
         */
        PERFORMANCE_CHARACTERISTIC("Performance characteristics and bottlenecks"),
        
        /**
         * 🔒 Security insights
         */
        SECURITY_CONSIDERATION("Security considerations and vulnerabilities"),
        
        /**
         * 🔧 Quality insights
         */
        QUALITY_INDICATOR("Code quality and maintainability indicators"),
        
        /**
         * 🔗 Dependency insights
         */
        DEPENDENCY_ANALYSIS("Dependency relationships and coupling"),
        
        /**
         * 📈 Evolution insights
         */
        EVOLUTION_PATTERN("Code evolution and change patterns"),
        
        /**
         * 🎯 Improvement insights
         */
        IMPROVEMENT_OPPORTUNITY("Opportunities for improvement"),
        
        /**
         * ⚠️ Risk insights
         */
        RISK_ASSESSMENT("Risk factors and potential issues");
        
        private final String description;
        
        InsightType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImpactAssessment {
        private ImpactLevel businessImpact;
        private ImpactLevel technicalImpact;
        private ImpactLevel userImpact;
        private String impactDescription;
        private List<String> affectedAreas;
        
        public enum ImpactLevel {
            LOW, MEDIUM, HIGH, CRITICAL
        }
    }
    
    /**
     * 💡 Create business significance insight
     */
    public static ContextualInsight businessSignificance(String entity, String description, double importance) {
        return ContextualInsight.builder()
            .type(InsightType.BUSINESS_SIGNIFICANCE)
            .title("Business Significance")
            .description(description)
            .targetEntity(entity)
            .importance(importance)
            .confidence(0.8)
            .build();
    }
    
    /**
     * 📖 Create usage pattern insight
     */
    public static ContextualInsight usagePattern(String entity, String pattern, List<String> evidence) {
        return ContextualInsight.builder()
            .type(InsightType.USAGE_PATTERN)
            .title("Common Usage Pattern")
            .description(pattern)
            .targetEntity(entity)
            .evidence(evidence)
            .importance(0.7)
            .confidence(0.9)
            .build();
    }
    
    /**
     * 🎨 Create architectural role insight
     */
    public static ContextualInsight architecturalRole(String entity, String role, String context) {
        return ContextualInsight.builder()
            .type(InsightType.ARCHITECTURAL_ROLE)
            .title("Architectural Role")
            .description(role)
            .targetEntity(entity)
            .context(context)
            .importance(0.8)
            .confidence(0.8)
            .build();
    }
    
    /**
     * 📊 Create performance insight
     */
    public static ContextualInsight performanceCharacteristic(String entity, String characteristic, double importance) {
        return ContextualInsight.builder()
            .type(InsightType.PERFORMANCE_CHARACTERISTIC)
            .title("Performance Characteristic")
            .description(characteristic)
            .targetEntity(entity)
            .importance(importance)
            .confidence(0.7)
            .build();
    }
    
    /**
     * 🎯 Create improvement opportunity insight
     */
    public static ContextualInsight improvementOpportunity(String entity, String opportunity, List<String> recommendations) {
        return ContextualInsight.builder()
            .type(InsightType.IMPROVEMENT_OPPORTUNITY)
            .title("Improvement Opportunity")
            .description(opportunity)
            .targetEntity(entity)
            .recommendations(recommendations)
            .importance(0.6)
            .confidence(0.8)
            .build();
    }
    
    /**
     * ⚠️ Create risk assessment insight
     */
    public static ContextualInsight riskAssessment(String entity, String risk, double importance) {
        return ContextualInsight.builder()
            .type(InsightType.RISK_ASSESSMENT)
            .title("Risk Assessment")
            .description(risk)
            .targetEntity(entity)
            .importance(importance)
            .confidence(0.7)
            .build();
    }
    
    /**
     * ⭐ Check if this is a high-importance insight
     */
    public boolean isHighImportance() {
        return importance >= 0.8;
    }
    
    /**
     * 🎯 Check if this insight is actionable
     */
    public boolean isActionable() {
        return recommendations != null && !recommendations.isEmpty();
    }
    
    /**
     * 📊 Check if this insight has strong evidence
     */
    public boolean hasStrongEvidence() {
        return evidence != null && evidence.size() >= 2 && confidence >= 0.8;
    }
    
    /**
     * 📋 Get formatted insight summary
     */
    public String getFormattedSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("🔍 ").append(type.name()).append(": ").append(title).append("\n");
        summary.append("📝 ").append(description).append("\n");
        
        if (context != null) {
            summary.append("🎭 Context: ").append(context).append("\n");
        }
        
        if (importance > 0) {
            summary.append("📊 Importance: ").append(String.format("%.1f", importance * 100)).append("%\n");
        }
        
        if (recommendations != null && !recommendations.isEmpty()) {
            summary.append("💡 Recommendations:\n");
            recommendations.forEach(rec -> summary.append("  • ").append(rec).append("\n"));
        }
        
        return summary.toString();
    }
}