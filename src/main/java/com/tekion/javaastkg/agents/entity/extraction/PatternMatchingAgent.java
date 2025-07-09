package com.tekion.javaastkg.agents.entity.extraction;

import com.tekion.javaastkg.agents.entity.models.EntityMatch;
import com.tekion.javaastkg.agents.entity.models.QueryContext;
import com.tekion.javaastkg.agents.entity.registry.CodebaseEntityRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pattern Matching Agent - Fast exact and pattern-based entity matching.
 * Implements Factor 10 (Small Focused Agent) and Factor 12 (Stateless Reducer).
 * 
 * Applied 12-Factor Principles:
 * - Factor 1: Structured outputs (EntityMatch objects)
 * - Factor 4: Tools as structured outputs
 * - Factor 10: Small focused agent (3-6 steps: exact, prefix, suffix, compound, pattern)
 * - Factor 12: Stateless pure function
 */
@Component
@Slf4j
public class PatternMatchingAgent implements ExtractionAgent {
    
    private final CodebaseEntityRegistry registry;
    
    public PatternMatchingAgent(CodebaseEntityRegistry registry) {
        this.registry = registry;
    }
    
    /**
     * Factor 12: Pure function for pattern-based entity extraction
     * Factor 1: Converts QueryContext to structured EntityMatch decisions
     */
    @Override
    public List<EntityMatch> extract(QueryContext context) {
        log.debug("Starting pattern matching for query: {}", context.getOriginalQuery());
        
        List<EntityMatch> allMatches = new ArrayList<>();
        
        // Step 1: Direct exact matches
        allMatches.addAll(findExactMatches(context));
        
        // Step 2: Prefix-based matches
        allMatches.addAll(findPrefixMatches(context));
        
        // Step 3: Suffix-based matches
        allMatches.addAll(findSuffixMatches(context));
        
        // Step 4: Compound term matches
        allMatches.addAll(findCompoundMatches(context));
        
        // Step 5: Pattern-based matches
        allMatches.addAll(findPatternMatches(context));
        
        // Step 6: Remove duplicates and sort by confidence
        List<EntityMatch> uniqueMatches = deduplicateMatches(allMatches);
        
        log.debug("Pattern matching completed. Found {} unique matches", uniqueMatches.size());
        return uniqueMatches;
    }
    
    /**
     * Step 1: Find exact name matches
     */
    private List<EntityMatch> findExactMatches(QueryContext context) {
        List<EntityMatch> matches = new ArrayList<>();
        
        for (QueryContext.Token token : context.getIdentifierTokens()) {
            String term = token.getValue();
            
            // Direct exact matches from registry
            List<EntityMatch> exactMatches = registry.findExactMatches(term);
            
            // Boost confidence for exact matches and add source info
            for (EntityMatch match : exactMatches) {
                match.setSource("PatternMatchingAgent");
                match.setMatchReason("exact name match for token: " + term);
                matches.add(match);
            }
        }
        
        return matches;
    }
    
    /**
     * Step 2: Find prefix-based matches (get*, process*, etc.)
     */
    private List<EntityMatch> findPrefixMatches(QueryContext context) {
        List<EntityMatch> matches = new ArrayList<>();

        // Check for wildcard patterns
        for (QueryContext.CodePattern pattern : context.getPatterns()) {
            if (pattern.getType() == QueryContext.CodePattern.PatternType.WILDCARD_PATTERN) {
                String patternStr = pattern.getPattern();
                if (patternStr != null && patternStr.endsWith("*") && patternStr.length() > 1) {
                    String prefix = patternStr.substring(0, patternStr.length() - 1);
                    if (!prefix.trim().isEmpty()) {
                        List<EntityMatch> prefixMatches = registry.findByPrefix(prefix);

                        for (EntityMatch match : prefixMatches) {
                            match.setSource("PatternMatchingAgent");
                            match.setMatchReason("prefix pattern match: " + prefix + "*");
                            match.setConfidence(0.8); // High confidence for explicit patterns
                            matches.add(match);
                        }
                    }
                }
            }
        }
        
        // Check action words as potential method prefixes
        for (QueryContext.Token token : context.getTokens()) {
            if (token.getType() == QueryContext.Token.TokenType.ACTION_WORD &&
                token.getValue() != null && !token.getValue().trim().isEmpty()) {
                List<EntityMatch> actionMatches = registry.findByPrefix(token.getValue());

                for (EntityMatch match : actionMatches) {
                    if (match.getEntityType() == EntityMatch.EntityType.METHOD) {
                        match.setSource("PatternMatchingAgent");
                        match.setMatchReason("action word prefix: " + token.getValue());
                        match.setConfidence(0.7);
                        matches.add(match);
                    }
                }
            }
        }
        
        return matches;
    }
    
    /**
     * Step 3: Find suffix-based matches (*Service, *Controller, etc.)
     */
    private List<EntityMatch> findSuffixMatches(QueryContext context) {
        List<EntityMatch> matches = new ArrayList<>();
        
        // Check for wildcard patterns
        for (QueryContext.CodePattern pattern : context.getPatterns()) {
            if (pattern.getType() == QueryContext.CodePattern.PatternType.WILDCARD_PATTERN) {
                String patternStr = pattern.getPattern();
                if (patternStr != null && patternStr.startsWith("*") && patternStr.length() > 1) {
                    String suffix = patternStr.substring(1);
                    if (!suffix.trim().isEmpty()) {
                        List<EntityMatch> suffixMatches = registry.findBySuffix(suffix);

                        for (EntityMatch match : suffixMatches) {
                            match.setSource("PatternMatchingAgent");
                            match.setMatchReason("suffix pattern match: *" + suffix);
                            match.setConfidence(0.8);
                            matches.add(match);
                        }
                    }
                }
            }
        }
        
        // Infer potential suffixes from domain terms
        for (QueryContext.Token token : context.getDomainTerms()) {
            String term = token.getValue();
            if (term == null || term.trim().isEmpty()) {
                continue;
            }

            // Try common suffixes with domain terms
            String[] commonSuffixes = {"Service", "Controller", "Manager", "Handler", "Repository"};
            for (String suffix : commonSuffixes) {
                List<EntityMatch> suffixMatches = registry.findBySuffix(suffix);

                // Filter matches that are related to the domain term
                String termLower = term.toLowerCase();
                for (EntityMatch match : suffixMatches) {
                    String entityName = match.getEntityName();
                    if (entityName != null && entityName.toLowerCase().contains(termLower)) {
                        match.setSource("PatternMatchingAgent");
                        match.setMatchReason("domain term + suffix: " + term + " + " + suffix);
                        match.setConfidence(0.6);
                        matches.add(match);
                    }
                }
            }
        }
        
        return matches;
    }
    
    /**
     * Step 4: Find compound term matches (user service -> UserService)
     */
    private List<EntityMatch> findCompoundMatches(QueryContext context) {
        List<EntityMatch> matches = new ArrayList<>();
        
        // Check explicit compound patterns
        for (QueryContext.CodePattern pattern : context.getPatterns()) {
            if (pattern.getType() == QueryContext.CodePattern.PatternType.COMPOUND_TERM) {
                List<String> components = pattern.getComponents();
                if (components != null && !components.isEmpty() &&
                    components.stream().allMatch(c -> c != null && !c.trim().isEmpty())) {
                    List<EntityMatch> compoundMatches = registry.findByCompound(components.toArray(new String[0]));

                    for (EntityMatch match : compoundMatches) {
                        match.setSource("PatternMatchingAgent");
                        match.setMatchReason("compound term match: " + String.join(" + ", components));
                        match.setConfidence(0.75);
                        matches.add(match);
                    }
                }
            }
        }
        
        // Generate compound combinations from domain terms
        List<QueryContext.Token> domainTokens = context.getDomainTerms();
        if (domainTokens != null && domainTokens.size() >= 2) {
            for (int i = 0; i < domainTokens.size() - 1; i++) {
                for (int j = i + 1; j < domainTokens.size(); j++) {
                    QueryContext.Token token1 = domainTokens.get(i);
                    QueryContext.Token token2 = domainTokens.get(j);

                    if (token1 != null && token2 != null) {
                        String term1 = token1.getValue();
                        String term2 = token2.getValue();

                        if (term1 != null && term2 != null &&
                            !term1.trim().isEmpty() && !term2.trim().isEmpty()) {
                            List<EntityMatch> compoundMatches = registry.findByCompound(term1, term2);

                            for (EntityMatch match : compoundMatches) {
                                match.setSource("PatternMatchingAgent");
                                match.setMatchReason("inferred compound: " + term1 + " + " + term2);
                                match.setConfidence(0.6);
                                matches.add(match);
                            }
                        }
                    }
                }
            }
        }
        
        return matches;
    }
    
    /**
     * Step 5: Find matches based on CamelCase patterns
     */
    private List<EntityMatch> findPatternMatches(QueryContext context) {
        List<EntityMatch> matches = new ArrayList<>();
        
        // Check CamelCase split patterns
        for (QueryContext.CodePattern pattern : context.getPatterns()) {
            if (pattern.getType() == QueryContext.CodePattern.PatternType.CAMEL_CASE_SPLIT) {
                List<String> components = pattern.getComponents();
                String patternStr = pattern.getPattern();

                if (components != null && !components.isEmpty() &&
                    components.stream().allMatch(c -> c != null && !c.trim().isEmpty()) &&
                    patternStr != null) {
                    // Try to find entities that contain all components
                    matches.addAll(findEntitiesWithComponents(components, patternStr));
                }
            }
        }
        
        // Check method signature patterns
        for (QueryContext.CodePattern pattern : context.getPatterns()) {
            if (pattern.getType() == QueryContext.CodePattern.PatternType.METHOD_SIGNATURE) {
                List<String> components = pattern.getComponents();
                String patternStr = pattern.getPattern();

                if (components != null && !components.isEmpty() &&
                    components.get(0) != null && !components.get(0).trim().isEmpty() &&
                    patternStr != null) {
                    String methodName = components.get(0);
                    List<EntityMatch> methodMatches = registry.findExactMatches(methodName);

                    for (EntityMatch match : methodMatches) {
                        if (match.getEntityType() == EntityMatch.EntityType.METHOD) {
                            match.setSource("PatternMatchingAgent");
                            match.setMatchReason("method signature pattern: " + patternStr);
                            match.setConfidence(0.9);
                            matches.add(match);
                        }
                    }
                }
            }
        }
        
        return matches;
    }
    
    /**
     * Finds entities that contain all the given components
     */
    private List<EntityMatch> findEntitiesWithComponents(List<String> components, String originalPattern) {
        List<EntityMatch> matches = new ArrayList<>();

        if (components == null || components.isEmpty()) {
            return matches;
        }

        // Create potential entity names from components
        List<String> potentialNames = generatePotentialNames(components);

        for (String potentialName : potentialNames) {
            if (potentialName != null && !potentialName.trim().isEmpty()) {
                List<EntityMatch> entityMatches = registry.findExactMatches(potentialName);

                for (EntityMatch match : entityMatches) {
                    match.setSource("PatternMatchingAgent");
                    match.setMatchReason("component pattern match from: " + originalPattern);
                    match.setConfidence(0.7);
                    matches.add(match);
                }
            }
        }

        return matches;
    }
    
    /**
     * Generates potential entity names from components
     */
    private List<String> generatePotentialNames(List<String> components) {
        List<String> potentialNames = new ArrayList<>();

        if (components == null || components.isEmpty()) {
            return potentialNames;
        }

        // Filter out null or empty components
        List<String> validComponents = components.stream()
                .filter(c -> c != null && !c.trim().isEmpty())
                .map(String::trim)
                .toList();

        if (validComponents.isEmpty()) {
            return potentialNames;
        }

        try {
            // CamelCase combination
            String camelCase = validComponents.get(0).toLowerCase();
            for (int i = 1; i < validComponents.size(); i++) {
                camelCase += capitalize(validComponents.get(i));
            }
            potentialNames.add(camelCase);

            // PascalCase combination
            StringBuilder pascalCase = new StringBuilder();
            for (String component : validComponents) {
                pascalCase.append(capitalize(component));
            }
            String pascalCaseStr = pascalCase.toString();
            potentialNames.add(pascalCaseStr);

            // Add common suffixes
            String[] commonSuffixes = {"Service", "Controller", "Manager", "Handler", "Repository", "Impl"};
            for (String suffix : commonSuffixes) {
                potentialNames.add(pascalCaseStr + suffix);
            }
        } catch (Exception e) {
            log.warn("Error generating potential names from components: {}", validComponents, e);
        }

        return potentialNames;
    }
    
    /**
     * Step 6: Remove duplicate matches and sort by confidence
     */
    private List<EntityMatch> deduplicateMatches(List<EntityMatch> matches) {
        if (matches == null || matches.isEmpty()) {
            return new ArrayList<>();
        }

        // Group by entity ID and keep the highest confidence match
        Map<String, EntityMatch> uniqueMatches = new HashMap<>();

        for (EntityMatch match : matches) {
            if (match != null && match.getEntityId() != null) {
                String key = match.getEntityId();
                EntityMatch existing = uniqueMatches.get(key);

                if (existing == null || match.getConfidence() > existing.getConfidence()) {
                    uniqueMatches.put(key, match);
                }
            }
        }

        // Sort by confidence (highest first)
        return uniqueMatches.values().stream()
                .sorted((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()))
                .collect(Collectors.toList());
    }
    
    // ========== UTILITY METHODS ==========
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1).toLowerCase();
    }
    
    /**
     * Checks if the agent can handle the given query context
     */
    @Override
    public boolean canHandle(QueryContext context) {
        if (context == null) {
            return false;
        }

        // Can handle if there are identifiers, patterns, or domain terms
        return (context.getIdentifierTokens() != null && !context.getIdentifierTokens().isEmpty()) ||
               (context.getPatterns() != null && !context.getPatterns().isEmpty()) ||
               (context.getDomainTerms() != null && !context.getDomainTerms().isEmpty());
    }
    
    /**
     * Gets the confidence score for this agent handling the query
     */
    @Override
    public double getHandlingConfidence(QueryContext context) {
        if (context == null) {
            return 0.0;
        }

        double confidence = 0.5; // Base confidence

        // Boost for exact patterns
        if (context.getPatterns() != null) {
            long exactPatterns = context.getPatterns().stream()
                    .filter(p -> p != null && (p.getType() == QueryContext.CodePattern.PatternType.WILDCARD_PATTERN ||
                               p.getType() == QueryContext.CodePattern.PatternType.METHOD_SIGNATURE))
                    .count();
            confidence += exactPatterns * 0.2;
        }

        // Boost for identifiers
        if (context.getIdentifierTokens() != null) {
            confidence += Math.min(0.3, context.getIdentifierTokens().size() * 0.1);
        }

        return Math.min(1.0, confidence);
    }
}