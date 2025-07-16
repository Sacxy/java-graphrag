package com.tekion.javaastkg.agents.models;

import com.tekion.javaastkg.agents.tools.SemanticCodeHunter.SearchMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * 🔍 Semantic Search Request - Input for semantic code hunting
 * 
 * Represents a request to find code using semantic understanding:
 * - Natural language query describing what to find
 * - Search mode and filtering options
 * - Result limits and preferences
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticSearchRequest {
    
    /**
     * 📝 Natural language query describing what to find
     */
    private String query;
    
    /**
     * 🎯 Search mode (semantic, exact, fuzzy, hybrid)
     */
    private SearchMode searchMode;
    
    /**
     * 🔢 Maximum number of results to return
     */
    private Integer maxResults;
    
    /**
     * 🎯 Specific entity types to focus on
     */
    private Set<EntityType> targetEntityTypes;
    
    /**
     * 📁 File paths to restrict search to
     */
    private List<String> restrictToFiles;
    
    /**
     * 📁 File paths to exclude from search
     */
    private List<String> excludeFiles;
    
    /**
     * 🎚️ Minimum confidence score for results
     */
    @Builder.Default
    private double minConfidence = 0.3;
    
    /**
     * 📊 Include documentation in search
     */
    @Builder.Default
    private boolean includeDocumentation = true;
    
    /**
     * 🔗 Include related entities in results
     */
    @Builder.Default
    private boolean includeRelated = false;
    
    /**
     * 📝 Additional context from previous searches
     */
    private String previousContext;
    
    /**
     * ✨ Create a simple search request
     */
    public static SemanticSearchRequest simple(String query) {
        return SemanticSearchRequest.builder()
            .query(query)
            .searchMode(SearchMode.SEMANTIC)
            .maxResults(20)
            .build();
    }
    
    /**
     * 🎯 Create a precise search request
     */
    public static SemanticSearchRequest precise(String query, EntityType... entityTypes) {
        return SemanticSearchRequest.builder()
            .query(query)
            .searchMode(SearchMode.EXACT)
            .targetEntityTypes(Set.of(entityTypes))
            .maxResults(10)
            .minConfidence(0.8)
            .build();
    }
    
    /**
     * 🌊 Create an exploratory search request
     */
    public static SemanticSearchRequest exploratory(String query) {
        return SemanticSearchRequest.builder()
            .query(query)
            .searchMode(SearchMode.HYBRID)
            .maxResults(50)
            .includeRelated(true)
            .minConfidence(0.2)
            .build();
    }
}