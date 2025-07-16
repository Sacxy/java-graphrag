package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Detected architectural pattern in code
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchitecturalPattern {
    private PatternType type;
    private List<String> involvedNodes;
    private double confidence;
    private String description;
    private List<String> evidence;
    private String impact;
    
    public enum PatternType {
        SINGLETON, FACTORY, OBSERVER, STRATEGY, BUILDER,
        GOD_CLASS, CIRCULAR_DEPENDENCY, DEAD_CODE,
        LAYERED_ARCHITECTURE, MVC, REPOSITORY
    }
}