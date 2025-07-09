package com.tekion.javaastkg.agents.entity.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Represents a class entity in the codebase with all its metadata.
 * Used for intelligent entity extraction and matching.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassEntity {
    private String id;
    private String name;
    private String fullName;
    private String packageName;
    private String filePath;
    private String description; // LLM-generated description from DESCRIPTION node
    
    // Type information
    private ClassType type; // CLASS, INTERFACE, ENUM, ANNOTATION
    private Set<String> modifiers; // public, abstract, final, etc.
    
    // Relationships
    private String superClass;
    private Set<String> interfaces;
    private Set<String> annotations;
    
    // Methods in this class
    private List<String> methodIds;
    private List<String> methodNames;
    
    // Usage statistics
    private int usageCount;
    private LocalDateTime lastAccessed;
    private LocalDateTime lastModified;
    
    // Search optimization
    private List<String> nameTokens; // Tokenized name for fuzzy search
    private String namePattern; // Extracted pattern (e.g., *Service, *Controller)
    
    // Embeddings
    private float[] embedding; // Semantic embedding for similarity search
    
    public enum ClassType {
        CLASS,
        INTERFACE,
        ENUM,
        ANNOTATION,
        RECORD
    }
    
    /**
     * Checks if this class matches a given pattern
     */
    public boolean matchesPattern(String pattern) {
        if (pattern.startsWith("*")) {
            return name.endsWith(pattern.substring(1));
        } else if (pattern.endsWith("*")) {
            return name.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return name.equals(pattern);
    }
    
    /**
     * Gets the suffix of the class name (e.g., Service, Controller)
     */
    public String getSuffix() {
        // Extract common suffixes
        String[] commonSuffixes = {
            "Service", "Controller", "Manager", "Handler", "Engine",
            "Factory", "Builder", "Repository", "DAO", "Processor",
            "Provider", "Consumer", "Listener", "Observer", "Adapter",
            "Impl", "Helper", "Utils", "Config", "Exception"
        };
        
        for (String suffix : commonSuffixes) {
            if (name.endsWith(suffix)) {
                return suffix;
            }
        }
        return null;
    }
    
    /**
     * Gets the prefix of the class name
     */
    public String getPrefix() {
        String suffix = getSuffix();
        if (suffix != null && name.length() > suffix.length()) {
            return name.substring(0, name.length() - suffix.length());
        }
        return name;
    }
}