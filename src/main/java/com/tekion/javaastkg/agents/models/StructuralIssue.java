package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ⚠️ Structural Issue - Problems or concerns in code structure
 * 
 * Represents structural problems identified during analysis:
 * - Architecture violations and anti-patterns
 * - Coupling and cohesion issues
 * - Design principle violations
 * - Potential maintenance concerns
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuralIssue {
    
    /**
     * 🏷️ Severity level of the issue
     */
    private Severity severity;
    
    /**
     * 🎯 Type of structural issue
     */
    private IssueType issueType;
    
    /**
     * 📝 Human-readable description
     */
    private String description;
    
    /**
     * 📍 Location where issue was found
     */
    private String location;
    
    /**
     * 🎯 Affected entities
     */
    private List<String> affectedEntities;
    
    /**
     * 📊 Confidence in this issue detection (0.0 to 1.0)
     */
    private double confidence;
    
    /**
     * 💡 Suggested resolution or improvement
     */
    private String suggestedResolution;
    
    /**
     * 📏 Metrics related to this issue
     */
    private Map<String, Double> relatedMetrics;
    
    /**
     * 🕒 When this issue was detected
     */
    @Builder.Default
    private LocalDateTime detectedAt = LocalDateTime.now();
    
    /**
     * 🔗 References to related issues
     */
    private List<String> relatedIssues;
    
    public enum Severity {
        INFO("Information"),
        WARNING("Warning - potential concern"),
        ERROR("Error - should be addressed"),
        CRITICAL("Critical - requires immediate attention");
        
        private final String description;
        
        Severity(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum IssueType {
        // Coupling Issues
        HIGH_COUPLING("High coupling between components"),
        CIRCULAR_DEPENDENCY("Circular dependency detected"),
        
        // Cohesion Issues
        LOW_COHESION("Low cohesion within component"),
        SCATTERED_FUNCTIONALITY("Functionality scattered across multiple classes"),
        
        // Architecture Issues
        LAYERING_VIOLATION("Architecture layering violation"),
        DEPENDENCY_INVERSION_VIOLATION("Dependency inversion principle violation"),
        
        // Design Issues
        GOD_CLASS("God class - too many responsibilities"),
        DEAD_CODE("Dead code - unused components"),
        FEATURE_ENVY("Feature envy - excessive use of external class"),
        
        // Pattern Issues
        ANTI_PATTERN("Anti-pattern detected"),
        PATTERN_MISUSE("Design pattern misused"),
        
        // Complexity Issues
        HIGH_COMPLEXITY("High structural complexity"),
        DEEP_INHERITANCE("Inheritance hierarchy too deep"),
        
        // Maintenance Issues
        BRITTLE_STRUCTURE("Brittle structure - high change impact"),
        MISSING_ABSTRACTION("Missing abstraction layer"),
        
        // General Issues
        INCONSISTENT_NAMING("Inconsistent naming convention"),
        INCOMPLETE_IMPLEMENTATION("Incomplete implementation detected");
        
        private final String description;
        
        IssueType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * ⚠️ Create a warning issue
     */
    public static StructuralIssue warning(String description) {
        return StructuralIssue.builder()
            .severity(Severity.WARNING)
            .description(description)
            .confidence(0.8)
            .build();
    }
    
    /**
     * ❌ Create an error issue
     */
    public static StructuralIssue error(String description) {
        return StructuralIssue.builder()
            .severity(Severity.ERROR)
            .description(description)
            .confidence(0.9)
            .build();
    }
    
    /**
     * 🔥 Create a critical issue
     */
    public static StructuralIssue critical(String description) {
        return StructuralIssue.builder()
            .severity(Severity.CRITICAL)
            .description(description)
            .confidence(0.95)
            .build();
    }
    
    /**
     * ℹ️ Create an info issue
     */
    public static StructuralIssue info(String description) {
        return StructuralIssue.builder()
            .severity(Severity.INFO)
            .description(description)
            .confidence(0.7)
            .build();
    }
    
    /**
     * ⭐ Check if this is a high-severity issue
     */
    public boolean isHighSeverity() {
        return severity == Severity.ERROR || severity == Severity.CRITICAL;
    }
    
    /**
     * 🎯 Check if this issue has high confidence
     */
    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }
    
    /**
     * 📋 Get a formatted summary of this issue
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(severity.name()).append(": ").append(description);
        
        if (location != null) {
            summary.append(" (").append(location).append(")");
        }
        
        if (confidence > 0) {
            summary.append(String.format(" [%.1f%% confidence]", confidence * 100));
        }
        
        return summary.toString();
    }
    
    /**
     * 🎨 Get formatted issue report
     */
    public String getFormattedReport() {
        StringBuilder report = new StringBuilder();
        
        // Header
        String severityIcon = switch (severity) {
            case INFO -> "ℹ️";
            case WARNING -> "⚠️";
            case ERROR -> "❌";
            case CRITICAL -> "🔥";
        };
        
        report.append(severityIcon).append(" ").append(severity.getDescription()).append("\n");
        report.append("📝 ").append(description).append("\n");
        
        if (location != null) {
            report.append("📍 Location: ").append(location).append("\n");
        }
        
        if (affectedEntities != null && !affectedEntities.isEmpty()) {
            report.append("🎯 Affected: ").append(String.join(", ", affectedEntities)).append("\n");
        }
        
        if (suggestedResolution != null) {
            report.append("💡 Suggestion: ").append(suggestedResolution).append("\n");
        }
        
        return report.toString();
    }
}