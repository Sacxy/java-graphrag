package com.tekion.javaastkg.agents.entity.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * Represents the analyzed context of a user query.
 * Contains structured information extracted from natural language.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryContext {
    private String originalQuery;
    private String normalizedQuery;
    private List<Token> tokens;
    private List<CodePattern> patterns;
    private QueryType queryType;
    private QueryIntent intent;
    private Constraints constraints;
    private double confidence;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Token {
        private String value;
        private String normalizedValue;
        private TokenType type;
        private int position;
        private double confidence;
        
        public enum TokenType {
            IDENTIFIER,      // Java identifier
            CAMEL_CASE,      // CamelCase word
            SNAKE_CASE,      // snake_case word
            PACKAGE_NAME,    // com.tekion.service
            CLASS_HINT,      // "class", "interface"
            METHOD_HINT,     // "method", "function"
            ACTION_WORD,     // "find", "get", "process"
            DOMAIN_TERM,     // Business domain word
            TECHNICAL_TERM,  // Technical/framework term
            MODIFIER,        // "public", "static"
            STOP_WORD        // Common words to ignore
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodePattern {
        private String pattern;
        private PatternType type;
        private List<String> components;
        private double confidence;
        
        public enum PatternType {
            CAMEL_CASE_SPLIT,    // getUserInfo -> [get, User, Info]
            COMPOUND_TERM,       // "user service" -> UserService
            METHOD_SIGNATURE,    // "getName()" -> getName
            PACKAGE_NOTATION,    // com.tekion.service
            WILDCARD_PATTERN,    // *Service, get*
            REGEX_PATTERN        // Custom regex
        }
    }
    
    public enum QueryType {
        SPECIFIC_ENTITY,     // "PaymentService class"
        FUNCTIONALITY,       // "payment validation logic"
        RELATIONSHIP,        // "what calls processPayment"
        PATTERN_SEARCH,      // "all services in payment package"
        EXPLORATORY,         // "show me payment related code"
        DEBUGGING,           // "why is payment failing"
        IMPLEMENTATION       // "how is payment implemented"
    }
    
    public enum QueryIntent {
        FIND_CLASS,
        FIND_METHOD,
        FIND_PACKAGE,
        FIND_IMPLEMENTATION,
        FIND_USAGE,
        FIND_RELATED,
        UNDERSTAND_FLOW,
        DEBUG_ISSUE,
        EXPLORE_DOMAIN
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Constraints {
        private Set<String> requiredTypes;     // "interface", "abstract class"
        private Set<String> requiredModifiers; // "public", "static"
        private Set<String> packageScopes;     // Limit to specific packages
        private Set<String> excludePatterns;   // Patterns to exclude
        private Integer maxResults;            // Limit number of results
        private boolean includeDeprecated;     // Include deprecated elements
        private boolean includePrivate;       // Include private elements
    }
    
    /**
     * Checks if the query is asking for a specific entity type
     */
    public boolean isLookingForClasses() {
        return queryType == QueryType.SPECIFIC_ENTITY && 
               (intent == QueryIntent.FIND_CLASS || 
                tokens.stream().anyMatch(t -> t.type == Token.TokenType.CLASS_HINT));
    }
    
    /**
     * Checks if the query is asking for methods
     */
    public boolean isLookingForMethods() {
        return queryType == QueryType.SPECIFIC_ENTITY && 
               (intent == QueryIntent.FIND_METHOD || 
                tokens.stream().anyMatch(t -> t.type == Token.TokenType.METHOD_HINT));
    }
    
    /**
     * Gets all identifier tokens from the query
     */
    public List<Token> getIdentifierTokens() {
        return tokens.stream()
                .filter(t -> t.type == Token.TokenType.IDENTIFIER || 
                           t.type == Token.TokenType.CAMEL_CASE ||
                           t.type == Token.TokenType.SNAKE_CASE)
                .toList();
    }
    
    /**
     * Gets all domain terms from the query
     */
    public List<Token> getDomainTerms() {
        return tokens.stream()
                .filter(t -> t.type == Token.TokenType.DOMAIN_TERM ||
                           t.type == Token.TokenType.TECHNICAL_TERM)
                .toList();
    }
}