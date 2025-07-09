package com.tekion.javaastkg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified data class for extracted entities from natural language queries.
 * Contains lists of identified classes, methods, packages, and terms.
 * Used across all entity extraction services for consistency.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedEntities {
    private List<String> classes;
    private List<String> methods;
    private List<String> packages;
    private List<String> terms;
    
    /**
     * Returns true if any entities were extracted
     */
    public boolean hasEntities() {
        return (classes != null && !classes.isEmpty()) || 
               (methods != null && !methods.isEmpty()) || 
               (packages != null && !packages.isEmpty()) || 
               (terms != null && !terms.isEmpty());
    }
    
    /**
     * Returns all entities as a single list
     */
    public List<String> getAllEntities() {
        List<String> all = new ArrayList<>();
        if (classes != null) all.addAll(classes);
        if (methods != null) all.addAll(methods);
        if (packages != null) all.addAll(packages);
        if (terms != null) all.addAll(terms);
        return all;
    }
    
    /**
     * Returns the total count of all entities
     */
    public int getTotalCount() {
        return getAllEntities().size();
    }
}
