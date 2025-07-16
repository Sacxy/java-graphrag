package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents a code entity extracted from user queries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeEntity {
    private String name;
    private EntityType type; // CLASS, METHOD, PACKAGE, FIELD, etc.
    private String context; // Where found in query
    private double confidence;
    private List<String> synonyms;
    private Map<String, String> attributes;
    
    public static CodeEntity method(String name, double confidence) {
        return CodeEntity.builder()
            .name(name)
            .type(EntityType.METHOD)
            .confidence(confidence)
            .build();
    }
    
    public static CodeEntity className(String name, double confidence) {
        return CodeEntity.builder()
            .name(name)
            .type(EntityType.CLASS)
            .confidence(confidence)
            .build();
    }
}