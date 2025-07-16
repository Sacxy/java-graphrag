package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Enhanced entity match with detailed explanations and context
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedEntityMatch {
    private EntityMatch entityMatch;
    private String explanation;
    private double confidence;
    private String contextSnippet;
    private List<String> highlights;
    private List<String> suggestions;
    private List<RelevanceFactor> relevanceFactors;
    private Map<String, Object> metadata;
    
    public String getDisplayName() {
        return entityMatch.getEntityType().name() + ": " + entityMatch.getName();
    }
    
    public String getShortExplanation() {
        return explanation != null && explanation.length() > 100 ? 
               explanation.substring(0, 97) + "..." : explanation;
    }
}