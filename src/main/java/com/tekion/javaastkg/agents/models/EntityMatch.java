package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Basic entity match from search operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityMatch {
    private String id;
    private String name;
    private EntityType entityType;
    private Map<String, Double> scores; // Scores from different search strategies
    private String sourceLocation;
    private String description;
    private String sourceCode;
    private String searchStrategy;
    private double compositeScore;
    private String explanation;
    private int relationshipCount;
    private Map<String, Object> metadata;
    
    public double getScore() {
        return compositeScore > 0 ? compositeScore : 
               scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }
}