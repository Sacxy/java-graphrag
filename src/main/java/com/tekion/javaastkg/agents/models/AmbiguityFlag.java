package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents ambiguities detected in user queries that may need clarification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmbiguityFlag {
    private AmbiguityType type;
    private String description;
    private String suggestedClarification;
    private double confidence;
    
    public enum AmbiguityType {
        MULTIPLE_ENTITIES,
        UNCLEAR_INTENT,
        MISSING_CONTEXT,
        CONFLICTING_TERMS,
        VAGUE_SCOPE
    }
}