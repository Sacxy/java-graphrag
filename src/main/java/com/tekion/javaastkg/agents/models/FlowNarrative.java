package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 📖 Flow Narrative - Human-readable narrative describing code flows
 * 
 * Represents a comprehensive narrative that explains:
 * - How code flows and executes
 * - Business context and purpose
 * - Technical implementation details
 * - Performance and quality characteristics
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FlowNarrative {
    
    /**
     * 📝 Narrative title
     */
    private String title;
    
    /**
     * 📋 Executive summary
     */
    private String executiveSummary;
    
    /**
     * 📖 Main narrative content organized by sections
     */
    private List<NarrativeSection> sections;
    
    /**
     * 🎯 Key insights and takeaways
     */
    private List<String> keyInsights;
    
    /**
     * 💡 Recommendations and next steps
     */
    private List<NarrativeRecommendation> recommendations;
    
    /**
     * 📊 Supporting metrics and data
     */
    private Map<String, Object> supportingMetrics;
    
    /**
     * 🎨 Visual elements and diagrams
     */
    private List<VisualizationElement> visualizations;
    
    /**
     * 📚 References and additional resources
     */
    private List<String> references;
    
    /**
     * 🏷️ Tags and categories
     */
    private List<String> tags;
    
    /**
     * 📊 Narrative quality metrics
     */
    private NarrativeQuality quality;
    
    /**
     * 🎭 Target audience information
     */
    private String targetAudience;
    
    /**
     * 🕒 When this narrative was generated
     */
    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();
    
    /**
     * 📋 Additional metadata
     */
    private Map<String, Object> metadata;
    
    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NarrativeSection {
        private String sectionTitle;
        private String content;
        private SectionType type;
        private int order;
        private List<String> codeExamples;
        private List<String> supportingEvidence;
        private Map<String, Object> sectionMetadata;
        
        public enum SectionType {
            OVERVIEW,
            BUSINESS_CONTEXT,
            TECHNICAL_DETAILS,
            FLOW_DESCRIPTION,
            PERFORMANCE_ANALYSIS,
            SECURITY_CONSIDERATIONS,
            ERROR_HANDLING,
            RECOMMENDATIONS,
            CONCLUSION
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NarrativeRecommendation {
        private String title;
        private String description;
        private String rationale;
        private double priority;
        private String implementationGuidance;
        private String expectedBenefit;
        private List<String> prerequisites;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VisualizationElement {
        private String title;
        private String description;
        private VisualizationType type;
        private String content;
        private Map<String, Object> renderingData;
        
        public enum VisualizationType {
            FLOWCHART,
            SEQUENCE_DIAGRAM,
            ARCHITECTURE_DIAGRAM,
            PERFORMANCE_CHART,
            RELATIONSHIP_GRAPH,
            TIMELINE,
            HEATMAP
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NarrativeQuality {
        private double clarity;
        private double completeness;
        private double accuracy;
        private double relevance;
        private double engagement;
        private String overallGrade;
        private List<String> strengths;
        private List<String> improvementAreas;
    }
    
    /**
     * 📝 Get narrative as formatted markdown
     */
    public String toMarkdown() {
        StringBuilder markdown = new StringBuilder();
        
        // Title
        markdown.append("# ").append(title).append("\n\n");
        
        // Executive Summary
        if (executiveSummary != null) {
            markdown.append("## Executive Summary\n\n");
            markdown.append(executiveSummary).append("\n\n");
        }
        
        // Sections
        if (sections != null && !sections.isEmpty()) {
            sections.stream()
                .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
                .forEach(section -> {
                    markdown.append("## ").append(section.getSectionTitle()).append("\n\n");
                    markdown.append(section.getContent()).append("\n\n");
                    
                    // Add code examples if present
                    if (section.getCodeExamples() != null && !section.getCodeExamples().isEmpty()) {
                        markdown.append("### Code Examples\n\n");
                        section.getCodeExamples().forEach(example -> {
                            markdown.append("```java\n").append(example).append("\n```\n\n");
                        });
                    }
                });
        }
        
        // Key Insights
        if (keyInsights != null && !keyInsights.isEmpty()) {
            markdown.append("## Key Insights\n\n");
            keyInsights.forEach(insight -> markdown.append("- ").append(insight).append("\n"));
            markdown.append("\n");
        }
        
        // Recommendations
        if (recommendations != null && !recommendations.isEmpty()) {
            markdown.append("## Recommendations\n\n");
            recommendations.forEach(rec -> {
                markdown.append("### ").append(rec.getTitle()).append("\n\n");
                markdown.append(rec.getDescription()).append("\n\n");
                if (rec.getRationale() != null) {
                    markdown.append("**Rationale:** ").append(rec.getRationale()).append("\n\n");
                }
            });
        }
        
        // Supporting Metrics
        if (supportingMetrics != null && !supportingMetrics.isEmpty()) {
            markdown.append("## Supporting Metrics\n\n");
            supportingMetrics.forEach((key, value) -> {
                markdown.append("- **").append(key).append("**: ").append(value).append("\n");
            });
            markdown.append("\n");
        }
        
        return markdown.toString();
    }
    
    /**
     * 📊 Get narrative statistics
     */
    public Map<String, Object> getStatistics() {
        int totalSections = sections != null ? sections.size() : 0;
        int totalRecommendations = recommendations != null ? recommendations.size() : 0;
        int totalInsights = keyInsights != null ? keyInsights.size() : 0;
        int totalVisualizations = visualizations != null ? visualizations.size() : 0;
        
        return Map.of(
            "totalSections", totalSections,
            "totalRecommendations", totalRecommendations,
            "totalInsights", totalInsights,
            "totalVisualizations", totalVisualizations,
            "overallQuality", quality != null ? quality.getOverallGrade() : "UNKNOWN"
        );
    }
    
    /**
     * 🎯 Get high-priority recommendations
     */
    public List<NarrativeRecommendation> getHighPriorityRecommendations() {
        return recommendations != null ? recommendations.stream()
            .filter(rec -> rec.getPriority() > 0.7)
            .toList() : List.of();
    }
    
    /**
     * 📝 Get sections by type
     */
    public List<NarrativeSection> getSectionsByType(NarrativeSection.SectionType type) {
        return sections != null ? sections.stream()
            .filter(section -> section.getType() == type)
            .toList() : List.of();
    }
    
    /**
     * 🎨 Get visualizations by type
     */
    public List<VisualizationElement> getVisualizationsByType(VisualizationElement.VisualizationType type) {
        return visualizations != null ? visualizations.stream()
            .filter(viz -> viz.getType() == type)
            .toList() : List.of();
    }
    
    /**
     * 📊 Check if narrative has high quality
     */
    public boolean hasHighQuality() {
        return quality != null && 
               quality.getClarity() > 0.8 && 
               quality.getCompleteness() > 0.8 && 
               quality.getAccuracy() > 0.8;
    }
    
    /**
     * 📋 Get narrative length estimate
     */
    public int getEstimatedLength() {
        int baseLength = executiveSummary != null ? executiveSummary.length() : 0;
        
        if (sections != null) {
            baseLength += sections.stream()
                .mapToInt(section -> section.getContent().length())
                .sum();
        }
        
        return baseLength;
    }
}