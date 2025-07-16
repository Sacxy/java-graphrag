package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Insight derived from structural analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuralInsight {
    private InsightType type;
    private String title;
    private String description;
    private List<String> evidence;
    private double importance;
    private String impact;
    
    public enum InsightType {
        ARCHITECTURAL_LAYERING,
        HIGH_COUPLING,
        COHESION_ANALYSIS,
        CENTRAL_COMPONENTS,
        LEAF_COMPONENTS,
        CIRCULAR_DEPENDENCIES
    }
}