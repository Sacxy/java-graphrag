package com.tekion.javaastkg.agents.models;

import com.tekion.javaastkg.agents.tools.SemanticCodeHunter.SearchContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 🎯 Semantic Search Result - Output from semantic code hunting
 * 
 * Contains the results of a semantic search operation:
 * - Ranked list of code matches
 * - Search metadata and performance metrics
 * - Success/error information
 * - Context for understanding the search process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticSearchResult {
    
    /**
     * 📝 Original search query
     */
    private String query;
    
    /**
     * 🎯 List of code matches ranked by relevance
     */
    private List<CodeMatch> matches;
    
    /**
     * 🔢 Total number of matches found (before filtering/ranking)
     */
    private int totalMatches;
    
    /**
     * 🧠 Search context and analysis metadata
     */
    private SearchContext searchContext;
    
    /**
     * ⏱️ Time taken to execute the search in milliseconds
     */
    private long executionTimeMs;
    
    /**
     * ✅ Whether the search completed successfully
     */
    private boolean successful;
    
    /**
     * ❌ Error message if search failed
     */
    private String errorMessage;
    
    /**
     * 📊 Additional search metrics and statistics
     */
    private Map<String, Object> searchMetrics;
    
    /**
     * ⭐ Overall quality score of the results (0.0 to 1.0)
     */
    @Builder.Default
    private double qualityScore = 0.0;
    
    /**
     * 🕒 When this search was performed
     */
    private LocalDateTime timestamp;
    
    /**
     * 💡 Suggestions for improving the search
     */
    private List<String> searchSuggestions;
    
    /**
     * ⭐ Check if this search found high-quality results
     */
    public boolean hasHighQualityResults() {
        return successful && matches != null && !matches.isEmpty() && 
               matches.stream().anyMatch(CodeMatch::isHighQuality);
    }
    
    /**
     * 📊 Get the average semantic score of all matches
     */
    public double getAverageSemanticScore() {
        if (matches == null || matches.isEmpty()) {
            return 0.0;
        }
        
        return matches.stream()
            .mapToDouble(CodeMatch::getSemanticScore)
            .average()
            .orElse(0.0);
    }
    
    /**
     * 🎯 Get the top match (highest scoring)
     */
    public CodeMatch getTopMatch() {
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        
        return matches.get(0); // Assuming matches are already sorted by score
    }
    
    /**
     * 📋 Get a human-readable summary of the search results
     */
    public String getSummary() {
        if (!successful) {
            return String.format("Search failed: %s", errorMessage);
        }
        
        if (matches == null || matches.isEmpty()) {
            return "No matches found for the query";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Found %d matches", matches.size()));
        
        if (totalMatches > matches.size()) {
            summary.append(String.format(" (showing top %d of %d total)", matches.size(), totalMatches));
        }
        
        summary.append(String.format(" in %dms", executionTimeMs));
        
        double avgScore = getAverageSemanticScore();
        if (avgScore > 0) {
            summary.append(String.format(", avg score: %.1f%%", avgScore * 100));
        }
        
        return summary.toString();
    }
}