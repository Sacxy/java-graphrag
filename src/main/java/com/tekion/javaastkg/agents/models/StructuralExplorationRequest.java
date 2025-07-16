package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * 🏗️ Structural Exploration Request - Input for structural code analysis
 * 
 * Defines what structural analysis to perform:
 * - Starting points for exploration (seed nodes)
 * - Focus areas (hierarchy, dependencies, patterns)
 * - Exploration constraints and preferences
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuralExplorationRequest {
    
    /**
     * 🌱 Starting node IDs for exploration
     */
    private List<String> seedNodeIds;
    
    /**
     * 🎯 What aspect of structure to focus on
     */
    private ExplorationFocus focus;
    
    /**
     * 📏 Maximum depth for traversal
     */
    @Builder.Default
    private int maxDepth = 3;
    
    /**
     * 🔗 Types of relationships to follow
     */
    private Set<String> relationshipTypes;
    
    /**
     * 📊 Whether to include structural metrics
     */
    @Builder.Default
    private boolean includeMetrics = true;
    
    /**
     * 🎨 Whether to detect design patterns
     */
    @Builder.Default
    private boolean detectPatterns = true;
    
    /**
     * ⚠️ Whether to identify potential issues
     */
    @Builder.Default
    private boolean identifyIssues = true;
    
    /**
     * 📈 Maximum number of nodes to explore
     */
    @Builder.Default
    private int maxNodes = 100;
    
    /**
     * 🔍 Minimum relevance score for inclusion
     */
    @Builder.Default
    private double minRelevanceScore = 0.1;
    
    /**
     * 📝 Additional context from previous analysis
     */
    private String previousContext;
    
    public enum ExplorationFocus {
        HIERARCHY("Focus on class hierarchies and inheritance"),
        DEPENDENCIES("Focus on dependencies and coupling"),
        PATTERNS("Focus on architectural patterns"),
        COMPLETE("Complete structural analysis"),
        IMPACT("Focus on change impact analysis");
        
        private final String description;
        
        ExplorationFocus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * ✨ Create a hierarchy-focused exploration request
     */
    public static StructuralExplorationRequest hierarchy(List<String> seedNodes) {
        return StructuralExplorationRequest.builder()
            .seedNodeIds(seedNodes)
            .focus(ExplorationFocus.HIERARCHY)
            .relationshipTypes(Set.of("EXTENDS", "IMPLEMENTS", "COMPOSITION"))
            .maxDepth(4)
            .build();
    }
    
    /**
     * 🔗 Create a dependency-focused exploration request
     */
    public static StructuralExplorationRequest dependencies(List<String> seedNodes) {
        return StructuralExplorationRequest.builder()
            .seedNodeIds(seedNodes)
            .focus(ExplorationFocus.DEPENDENCIES)
            .relationshipTypes(Set.of("DEPENDS_ON", "CALLS", "USES"))
            .maxDepth(3)
            .build();
    }
    
    /**
     * 🎨 Create a pattern-focused exploration request
     */
    public static StructuralExplorationRequest patterns(List<String> seedNodes) {
        return StructuralExplorationRequest.builder()
            .seedNodeIds(seedNodes)
            .focus(ExplorationFocus.PATTERNS)
            .detectPatterns(true)
            .maxDepth(2)
            .maxNodes(50)
            .build();
    }
}