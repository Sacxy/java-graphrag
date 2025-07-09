package com.tekion.javaastkg.agents.entity.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a match found by an entity extraction agent.
 * Contains the entity and metadata about the match quality.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityMatch {
    private String entityId;
    private String entityName;
    private EntityType entityType;
    private String source; // Which agent found this match
    private double confidence;
    private MatchType matchType;
    private String matchReason; // Why this entity was matched
    
    // Additional metadata
    private String className; // For methods, the containing class
    private String packageName;
    private String signature; // Full signature for methods
    
    public enum EntityType {
        CLASS,
        METHOD,
        PACKAGE,
        FIELD,
        ANNOTATION
    }
    
    public enum MatchType {
        EXACT,           // Exact name match
        PREFIX,          // Prefix match (getUser -> get*)
        SUFFIX,          // Suffix match (UserService -> *Service)
        PATTERN,         // Pattern match (compound words)
        FUZZY,           // Fuzzy/typo-tolerant match
        SEMANTIC,        // Semantic similarity match
        PHONETIC,        // Phonetic similarity match
        ABBREVIATION,    // Abbreviation expansion
        LLM_SUGGESTED    // Suggested by LLM
    }
    
    /**
     * Checks if this is a high-confidence match
     */
    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }
    
    /**
     * Checks if this is a medium-confidence match
     */
    public boolean isMediumConfidence() {
        return confidence >= 0.5 && confidence < 0.8;
    }
    
    /**
     * Checks if this is a low-confidence match
     */
    public boolean isLowConfidence() {
        return confidence < 0.5;
    }
    
    /**
     * Gets a display name for the entity
     */
    public String getDisplayName() {
        if (entityType == EntityType.METHOD && className != null) {
            return className + "." + entityName;
        }
        return entityName;
    }
}