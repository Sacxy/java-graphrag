package com.tekion.javaastkg.agents.entity.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Represents a method entity in the codebase with all its metadata.
 * Used for intelligent entity extraction and matching.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodEntity {
    private String id;
    private String name;
    private String signature;
    private String className;
    private String packageName;
    private String filePath;
    private String description; // LLM-generated description from DESCRIPTION node
    
    // Method details
    private String returnType;
    private List<String> parameterTypes;
    private List<String> parameterNames;
    private Set<String> modifiers; // public, static, final, etc.
    private Set<String> annotations;
    
    // Method characteristics
    private boolean isConstructor;
    private boolean isGetter;
    private boolean isSetter;
    private boolean isOverridden;
    
    // Usage statistics
    private int usageCount;
    private int callCount; // How many times this method is called
    private LocalDateTime lastAccessed;
    private LocalDateTime lastModified;
    
    // Search optimization
    private List<String> nameTokens; // Tokenized name for fuzzy search
    private String namePattern; // Extracted pattern (e.g., get*, set*, process*)
    
    // Embeddings
    private float[] embedding; // Semantic embedding for similarity search
    
    /**
     * Checks if this method matches a given pattern
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
     * Gets the prefix of the method name (e.g., get, set, process)
     */
    public String getPrefix() {
        String[] commonPrefixes = {
            "get", "set", "is", "has", "can", "should", "will",
            "create", "build", "make", "generate", "construct",
            "process", "handle", "execute", "run", "perform",
            "validate", "check", "verify", "confirm", "ensure",
            "find", "search", "query", "fetch", "retrieve",
            "save", "store", "persist", "update", "insert",
            "delete", "remove", "clear", "clean", "purge",
            "load", "reload", "refresh", "sync", "initialize"
        };
        
        String lowerName = name.toLowerCase();
        for (String prefix : commonPrefixes) {
            if (lowerName.startsWith(prefix)) {
                // Check if it's followed by uppercase or end of string
                if (name.length() == prefix.length() || 
                    Character.isUpperCase(name.charAt(prefix.length()))) {
                    return prefix;
                }
            }
        }
        return null;
    }
    
    /**
     * Gets the action part of the method name (without prefix)
     */
    public String getAction() {
        String prefix = getPrefix();
        if (prefix != null && name.length() > prefix.length()) {
            return name.substring(prefix.length());
        }
        return name;
    }
    
    /**
     * Determines the method type based on naming patterns
     */
    public MethodType getMethodType() {
        if (isConstructor) return MethodType.CONSTRUCTOR;
        if (isGetter) return MethodType.GETTER;
        if (isSetter) return MethodType.SETTER;
        
        String prefix = getPrefix();
        if (prefix != null) {
            switch (prefix) {
                case "get", "is", "has", "can", "should", "will":
                    return MethodType.GETTER;
                case "set":
                    return MethodType.SETTER;
                case "create", "build", "make", "generate", "construct":
                    return MethodType.CREATOR;
                case "process", "handle", "execute", "run", "perform":
                    return MethodType.PROCESSOR;
                case "validate", "check", "verify", "confirm", "ensure":
                    return MethodType.VALIDATOR;
                case "find", "search", "query", "fetch", "retrieve":
                    return MethodType.FINDER;
                case "save", "store", "persist", "update", "insert":
                    return MethodType.PERSISTER;
                case "delete", "remove", "clear", "clean", "purge":
                    return MethodType.DELETER;
                default:
                    return MethodType.BUSINESS_LOGIC;
            }
        }
        
        return MethodType.BUSINESS_LOGIC;
    }
    
    public enum MethodType {
        CONSTRUCTOR,
        GETTER,
        SETTER,
        CREATOR,
        PROCESSOR,
        VALIDATOR,
        FINDER,
        PERSISTER,
        DELETER,
        BUSINESS_LOGIC
    }
}