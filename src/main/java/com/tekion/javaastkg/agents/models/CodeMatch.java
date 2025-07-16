package com.tekion.javaastkg.agents.models;

import com.tekion.javaastkg.agents.tools.SemanticCodeHunter.MatchType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 🎯 Code Match - A piece of code found through semantic search
 * 
 * Represents a code element that matches a search query:
 * - Basic identification (name, type, location)
 * - Semantic relevance scoring
 * - Context and documentation
 * - Usage examples and relationships
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeMatch {
    
    /**
     * 🆔 Unique identifier for this entity
     */
    private String entityId;
    
    /**
     * 🏷️ Type of code entity (class, method, field, etc.)
     */
    private EntityType entityType;
    
    /**
     * 📝 Name of the code element
     */
    private String name;
    
    /**
     * ✍️ Signature or declaration of the element
     */
    private String signature;
    
    /**
     * 📁 File path where this element is defined
     */
    private String filePath;
    
    /**
     * 📍 Starting line number
     */
    private int startLine;
    
    /**
     * 📍 Ending line number
     */
    private int endLine;
    
    /**
     * 🔍 How this match was found
     */
    private MatchType matchType;
    
    /**
     * 📊 Semantic relevance score (0.0 to 1.0)
     */
    private double semanticScore;
    
    /**
     * 🆔 Unique identifier for deduplication
     */
    private String uniqueId;
    
    /**
     * 📚 Documentation or comments
     */
    private String documentation;
    
    /**
     * 💡 Usage examples
     */
    private List<String> usageExamples;
    
    /**
     * 🔗 Related code elements
     */
    private List<CodeMatch> relatedMatches;
    
    /**
     * 📊 Additional metadata about this match
     */
    private Map<String, Object> metadata;
    
    /**
     * 🎯 Reason why this was matched
     */
    private String matchReason;
    
    /**
     * ✨ Get a display-friendly representation
     */
    public String getDisplayName() {
        if (signature != null && !signature.trim().isEmpty()) {
            return signature;
        }
        return name;
    }
    
    /**
     * 📍 Get location string
     */
    public String getLocationString() {
        return String.format("%s:%d-%d", filePath, startLine, endLine);
    }
    
    /**
     * ⭐ Check if this is a high-quality match
     */
    public boolean isHighQuality() {
        return semanticScore >= 0.8;
    }
    
    /**
     * 📚 Check if documentation is available
     */
    public boolean hasDocumentation() {
        return documentation != null && !documentation.trim().isEmpty();
    }
    
    /**
     * 💡 Check if usage examples are available
     */
    public boolean hasUsageExamples() {
        return usageExamples != null && !usageExamples.isEmpty();
    }
    
    /**
     * 🔗 Check if related matches are available
     */
    public boolean hasRelatedMatches() {
        return relatedMatches != null && !relatedMatches.isEmpty();
    }
    
    /**
     * 📋 Get a summary of this match
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(getDisplayName());
        summary.append(" (").append(entityType).append(")");
        summary.append(" - ").append(String.format("%.1f%%", semanticScore * 100));
        summary.append(" match");
        
        if (hasDocumentation()) {
            summary.append(" [documented]");
        }
        
        if (hasUsageExamples()) {
            summary.append(" [").append(usageExamples.size()).append(" examples]");
        }
        
        return summary.toString();
    }
    
    /**
     * 🎨 Get formatted code snippet
     */
    public String getFormattedSnippet() {
        StringBuilder snippet = new StringBuilder();
        snippet.append("```java\n");
        snippet.append(getDisplayName()).append("\n");
        snippet.append("```\n");
        snippet.append("📍 ").append(getLocationString()).append("\n");
        
        if (hasDocumentation()) {
            snippet.append("📚 ").append(documentation.substring(0, Math.min(100, documentation.length())));
            if (documentation.length() > 100) {
                snippet.append("...");
            }
            snippet.append("\n");
        }
        
        return snippet.toString();
    }
}