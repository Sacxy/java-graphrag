package com.tekion.javaastkg.agents.entity.analysis;

import com.tekion.javaastkg.agents.entity.models.QueryContext;
import com.tekion.javaastkg.agents.entity.models.QueryContext.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Query Analysis Agent - Converts natural language queries into structured QueryContext.
 * Implements Factor 1 (Natural Language to Tool Calls) and Factor 12 (Stateless Reducer).
 * 
 * Applied 12-Factor Principles:
 * - Factor 1: Converts natural language to structured decisions (QueryContext)
 * - Factor 2: Owns prompts and analysis logic completely
 * - Factor 10: Small focused agent (3-5 steps: tokenize, patterns, classify, constraints)
 * - Factor 12: Stateless pure function
 */
@Component
@Slf4j
public class QueryAnalysisAgent {
    
    // Predefined patterns for classification
    private static final Map<Pattern, QueryType> QUERY_TYPE_PATTERNS = Map.of(
        Pattern.compile(".*\\b(class|interface|enum)\\b.*", Pattern.CASE_INSENSITIVE), QueryType.SPECIFIC_ENTITY,
        Pattern.compile(".*\\b(method|function)\\b.*", Pattern.CASE_INSENSITIVE), QueryType.SPECIFIC_ENTITY,
        Pattern.compile(".*\\b(how|implement|logic|algorithm)\\b.*", Pattern.CASE_INSENSITIVE), QueryType.FUNCTIONALITY,
        Pattern.compile(".*\\b(what calls|who uses|depends|relationship)\\b.*", Pattern.CASE_INSENSITIVE), QueryType.RELATIONSHIP,
        Pattern.compile(".*\\b(all|list|show|find all)\\b.*", Pattern.CASE_INSENSITIVE), QueryType.PATTERN_SEARCH,
        Pattern.compile(".*\\b(why|error|bug|issue|problem)\\b.*", Pattern.CASE_INSENSITIVE), QueryType.DEBUGGING
    );
    
    private static final Map<Pattern, QueryIntent> INTENT_PATTERNS = Map.of(
        Pattern.compile(".*\\bclass\\b.*", Pattern.CASE_INSENSITIVE), QueryIntent.FIND_CLASS,
        Pattern.compile(".*\\b(method|function)\\b.*", Pattern.CASE_INSENSITIVE), QueryIntent.FIND_METHOD,
        Pattern.compile(".*\\bpackage\\b.*", Pattern.CASE_INSENSITIVE), QueryIntent.FIND_PACKAGE,
        Pattern.compile(".*\\b(implementation|how.*implement)\\b.*", Pattern.CASE_INSENSITIVE), QueryIntent.FIND_IMPLEMENTATION,
        Pattern.compile(".*\\b(usage|who.*use|what.*call)\\b.*", Pattern.CASE_INSENSITIVE), QueryIntent.FIND_USAGE,
        Pattern.compile(".*\\b(related|similar)\\b.*", Pattern.CASE_INSENSITIVE), QueryIntent.FIND_RELATED,
        Pattern.compile(".*\\b(flow|process|sequence)\\b.*", Pattern.CASE_INSENSITIVE), QueryIntent.UNDERSTAND_FLOW,
        Pattern.compile(".*\\b(debug|error|why|issue)\\b.*", Pattern.CASE_INSENSITIVE), QueryIntent.DEBUG_ISSUE
    );
    
    // Common stop words to ignore
    private static final Set<String> STOP_WORDS = Set.of(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", 
        "by", "is", "are", "was", "were", "be", "been", "have", "has", "had", "do", "does", 
        "did", "will", "would", "could", "should", "may", "might", "can", "i", "you", "we", 
        "they", "it", "this", "that", "these", "those", "what", "where", "when", "why", "how"
    );
    
    // Java keyword patterns
    private static final Set<String> JAVA_KEYWORDS = Set.of(
        "public", "private", "protected", "static", "final", "abstract", "synchronized",
        "volatile", "transient", "native", "strictfp", "class", "interface", "enum",
        "extends", "implements", "throws", "return", "void", "int", "long", "double",
        "float", "boolean", "char", "byte", "short", "String", "Object"
    );
    
    /**
     * Factor 12: Pure function - same input always produces same output
     * Factor 1: Converts natural language to structured decisions
     */
    public QueryContext analyze(String query) {
        log.debug("Analyzing query: {}", query);
        
        // Step 1: Preprocess and tokenize
        String normalizedQuery = normalizeQuery(query);
        List<Token> tokens = tokenizeQuery(normalizedQuery);
        
        // Step 2: Detect code patterns
        List<CodePattern> patterns = detectCodePatterns(tokens, normalizedQuery);
        
        // Step 3: Classify query type and intent
        QueryType queryType = classifyQueryType(normalizedQuery);
        QueryIntent intent = classifyQueryIntent(normalizedQuery);
        
        // Step 4: Extract constraints
        Constraints constraints = extractConstraints(tokens, normalizedQuery);
        
        // Step 5: Calculate confidence
        double confidence = calculateConfidence(tokens, patterns, queryType, intent);
        
        QueryContext context = QueryContext.builder()
                .originalQuery(query)
                .normalizedQuery(normalizedQuery)
                .tokens(tokens)
                .patterns(patterns)
                .queryType(queryType)
                .intent(intent)
                .constraints(constraints)
                .confidence(confidence)
                .build();
        
        log.debug("Analysis complete: type={}, intent={}, confidence={}", 
                queryType, intent, confidence);
        
        return context;
    }
    
    /**
     * Step 1: Normalizes query text
     */
    private String normalizeQuery(String query) {
        if (query == null) return "";

        return query.trim()
                .toLowerCase()
                .replaceAll("\\s+", " ") // Normalize whitespace
                .replaceAll("[^a-zA-Z0-9\\s\\.\\(\\)\\*_$-]", " "); // Keep alphanumeric, dots, parens, asterisks, underscores, dollar signs, hyphens
    }
    
    /**
     * Step 1: Tokenizes query into structured tokens
     */
    private List<Token> tokenizeQuery(String normalizedQuery) {
        List<Token> tokens = new ArrayList<>();
        String[] words = normalizedQuery.split("\\s+");
        
        for (int i = 0; i < words.length; i++) {
            String word = words[i].trim();
            if (word.isEmpty()) continue;
            
            Token token = Token.builder()
                    .value(word)
                    .normalizedValue(word.toLowerCase())
                    .type(classifyTokenType(word))
                    .position(i)
                    .confidence(calculateTokenConfidence(word))
                    .build();
            
            tokens.add(token);
        }
        
        return tokens;
    }
    
    /**
     * Classifies the type of a token
     */
    private Token.TokenType classifyTokenType(String word) {
        String lower = word.toLowerCase();
        
        // Check stop words first
        if (STOP_WORDS.contains(lower)) {
            return Token.TokenType.STOP_WORD;
        }
        
        // Check Java keywords
        if (JAVA_KEYWORDS.contains(lower)) {
            return Token.TokenType.MODIFIER;
        }
        
        // Check for specific hints - exact word matching
        if (isExactWordMatch(lower, "class", "interface", "enum", "annotation")) {
            return Token.TokenType.CLASS_HINT;
        }
        if (isExactWordMatch(lower, "method", "function")) {
            return Token.TokenType.METHOD_HINT;
        }

        // Check for package notation - more flexible pattern
        if (word.contains(".") && word.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*(\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*$")) {
            return Token.TokenType.PACKAGE_NAME;
        }
        
        // Check naming conventions
        if (isCamelCase(word)) {
            return Token.TokenType.CAMEL_CASE;
        }
        if (isSnakeCase(word)) {
            return Token.TokenType.SNAKE_CASE;
        }
        
        // Check for action words
        if (isActionWord(lower)) {
            return Token.TokenType.ACTION_WORD;
        }
        
        // Check for domain terms
        if (isDomainTerm(lower)) {
            return Token.TokenType.DOMAIN_TERM;
        }
        
        // Check for technical terms
        if (isTechnicalTerm(lower)) {
            return Token.TokenType.TECHNICAL_TERM;
        }
        
        // Default to identifier if it looks like a valid identifier
        if (word.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*$")) {
            return Token.TokenType.IDENTIFIER;
        }
        
        return Token.TokenType.STOP_WORD;
    }
    
    /**
     * Step 2: Detects code patterns in the query
     */
    private List<CodePattern> detectCodePatterns(List<Token> tokens, String normalizedQuery) {
        List<CodePattern> patterns = new ArrayList<>();
        
        // Detect CamelCase splits
        for (Token token : tokens) {
            if (token.getType() == Token.TokenType.CAMEL_CASE) {
                List<String> components = splitCamelCase(token.getValue());
                if (components.size() > 1) {
                    patterns.add(CodePattern.builder()
                            .pattern(token.getValue())
                            .type(CodePattern.PatternType.CAMEL_CASE_SPLIT)
                            .components(components)
                            .confidence(0.9)
                            .build());
                }
            }
        }
        
        // Detect compound terms
        patterns.addAll(detectCompoundTerms(tokens));
        
        // Detect method signatures
        patterns.addAll(detectMethodSignatures(normalizedQuery));
        
        // Detect wildcard patterns
        patterns.addAll(detectWildcardPatterns(normalizedQuery));
        
        return patterns;
    }
    
    /**
     * Detects compound terms like "user service" -> UserService
     */
    private List<CodePattern> detectCompoundTerms(List<Token> tokens) {
        List<CodePattern> patterns = new ArrayList<>();
        
        for (int i = 0; i < tokens.size() - 1; i++) {
            Token token1 = tokens.get(i);
            Token token2 = tokens.get(i + 1);
            
            if (isValidCompoundComponent(token1) && isValidCompoundComponent(token2)) {
                String compound = token1.getValue() + " " + token2.getValue();
                patterns.add(CodePattern.builder()
                        .pattern(compound)
                        .type(CodePattern.PatternType.COMPOUND_TERM)
                        .components(List.of(token1.getValue(), token2.getValue()))
                        .confidence(0.8)
                        .build());
            }
        }
        
        return patterns;
    }
    
    /**
     * Detects method signatures like "getName()"
     */
    private List<CodePattern> detectMethodSignatures(String query) {
        List<CodePattern> patterns = new ArrayList<>();
        Pattern methodPattern = Pattern.compile("\\b([a-zA-Z_$][a-zA-Z0-9_$]*)\\(\\)");
        var matcher = methodPattern.matcher(query);
        
        while (matcher.find()) {
            String methodName = matcher.group(1);
            patterns.add(CodePattern.builder()
                    .pattern(matcher.group(0))
                    .type(CodePattern.PatternType.METHOD_SIGNATURE)
                    .components(List.of(methodName))
                    .confidence(0.95)
                    .build());
        }
        
        return patterns;
    }
    
    /**
     * Detects wildcard patterns like "*Service" or "get*"
     */
    private List<CodePattern> detectWildcardPatterns(String query) {
        List<CodePattern> patterns = new ArrayList<>();
        Pattern wildcardPattern = Pattern.compile("\\b(\\*[a-zA-Z]+|[a-zA-Z]+\\*)\\b");
        var matcher = wildcardPattern.matcher(query);
        
        while (matcher.find()) {
            String pattern = matcher.group(1);
            patterns.add(CodePattern.builder()
                    .pattern(pattern)
                    .type(CodePattern.PatternType.WILDCARD_PATTERN)
                    .components(List.of(pattern.replace("*", "")))
                    .confidence(0.9)
                    .build());
        }
        
        return patterns;
    }
    
    /**
     * Step 3: Classifies the overall query type
     */
    private QueryType classifyQueryType(String query) {
        for (Map.Entry<Pattern, QueryType> entry : QUERY_TYPE_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(query).matches()) {
                return entry.getValue();
            }
        }
        
        // Default classification based on heuristics
        if (query.contains("*") || query.contains("all") || query.contains("list")) {
            return QueryType.PATTERN_SEARCH;
        }
        if (query.contains("?") || query.contains("what") || query.contains("how")) {
            return QueryType.EXPLORATORY;
        }
        
        return QueryType.SPECIFIC_ENTITY;
    }
    
    /**
     * Step 3: Classifies the query intent
     */
    private QueryIntent classifyQueryIntent(String query) {
        for (Map.Entry<Pattern, QueryIntent> entry : INTENT_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(query).matches()) {
                return entry.getValue();
            }
        }
        
        // Default classification
        if (query.contains("explore") || query.contains("understand")) {
            return QueryIntent.EXPLORE_DOMAIN;
        }
        
        return QueryIntent.FIND_CLASS; // Default
    }
    
    /**
     * Step 4: Extracts constraints from the query
     */
    private Constraints extractConstraints(List<Token> tokens, String query) {
        Set<String> requiredTypes = new HashSet<>();
        Set<String> requiredModifiers = new HashSet<>();
        Set<String> packageScopes = new HashSet<>();
        Set<String> excludePatterns = new HashSet<>();
        
        // Extract type constraints
        if (query.contains("interface")) requiredTypes.add("interface");
        if (query.contains("abstract")) requiredTypes.add("abstract");
        if (query.contains("enum")) requiredTypes.add("enum");
        
        // Extract modifier constraints
        if (query.contains("public")) requiredModifiers.add("public");
        if (query.contains("private")) requiredModifiers.add("private");
        if (query.contains("static")) requiredModifiers.add("static");
        
        // Extract package constraints
        for (Token token : tokens) {
            if (token.getType() == Token.TokenType.PACKAGE_NAME) {
                packageScopes.add(token.getValue());
            }
        }
        
        // Extract exclude patterns with context-aware detection
        excludePatterns.addAll(extractExclusionPatterns(query, tokens));
        
        return Constraints.builder()
                .requiredTypes(requiredTypes.isEmpty() ? null : requiredTypes)
                .requiredModifiers(requiredModifiers.isEmpty() ? null : requiredModifiers)
                .packageScopes(packageScopes.isEmpty() ? null : packageScopes)
                .excludePatterns(excludePatterns.isEmpty() ? null : excludePatterns)
                .includeDeprecated(!query.contains("not deprecated"))
                .includePrivate(query.contains("private") || query.contains("all"))
                .build();
    }
    
    /**
     * Step 5: Calculates overall confidence in the analysis
     */
    private double calculateConfidence(List<Token> tokens, List<CodePattern> patterns,
                                     QueryType queryType, QueryIntent intent) {
        double confidence = 0.3; // Lower base confidence

        // Calculate token quality score
        double tokenQuality = calculateTokenQuality(tokens);
        confidence += tokenQuality * 0.4; // Up to 40% boost for high-quality tokens

        // Calculate pattern quality score
        double patternQuality = calculatePatternQuality(patterns);
        confidence += patternQuality * 0.2; // Up to 20% boost for high-quality patterns

        // Boost for clear intent signals with coherence check
        if (queryType != QueryType.EXPLORATORY && isIntentCoherent(queryType, intent)) {
            confidence += 0.1;
        }

        // Penalty for conflicting signals
        if (hasConflictingSignals(tokens, queryType, intent)) {
            confidence -= 0.15;
        }

        return Math.max(0.1, Math.min(1.0, confidence));
    }

    /**
     * Calculates the quality of tokens based on their types and confidence
     */
    private double calculateTokenQuality(List<Token> tokens) {
        if (tokens.isEmpty()) return 0.0;

        double totalWeight = 0.0;
        double weightedScore = 0.0;

        for (Token token : tokens) {
            double weight = getTokenWeight(token.getType());
            totalWeight += weight;
            weightedScore += weight * token.getConfidence();
        }

        return totalWeight > 0 ? weightedScore / totalWeight : 0.0;
    }

    /**
     * Calculates the quality of detected patterns
     */
    private double calculatePatternQuality(List<CodePattern> patterns) {
        if (patterns.isEmpty()) return 0.0;

        return patterns.stream()
                .mapToDouble(CodePattern::getConfidence)
                .average()
                .orElse(0.0);
    }

    /**
     * Checks if query type and intent are coherent
     */
    private boolean isIntentCoherent(QueryType queryType, QueryIntent intent) {
        return switch (queryType) {
            case SPECIFIC_ENTITY -> intent == QueryIntent.FIND_CLASS ||
                                  intent == QueryIntent.FIND_METHOD ||
                                  intent == QueryIntent.FIND_PACKAGE;
            case FUNCTIONALITY -> intent == QueryIntent.FIND_IMPLEMENTATION ||
                                intent == QueryIntent.UNDERSTAND_FLOW;
            case RELATIONSHIP -> intent == QueryIntent.FIND_USAGE ||
                               intent == QueryIntent.FIND_RELATED;
            case DEBUGGING -> intent == QueryIntent.DEBUG_ISSUE;
            default -> true; // Other combinations are acceptable
        };
    }

    /**
     * Detects conflicting signals in the analysis
     */
    private boolean hasConflictingSignals(List<Token> tokens, QueryType queryType, QueryIntent intent) {
        // Check for conflicting token types
        boolean hasClassHints = tokens.stream().anyMatch(t -> t.getType() == Token.TokenType.CLASS_HINT);
        boolean hasMethodHints = tokens.stream().anyMatch(t -> t.getType() == Token.TokenType.METHOD_HINT);

        // Conflicting if both class and method hints present but intent is specific to one
        if (hasClassHints && hasMethodHints) {
            return intent == QueryIntent.FIND_CLASS || intent == QueryIntent.FIND_METHOD;
        }

        return false;
    }

    /**
     * Gets the weight of a token type for confidence calculation
     */
    private double getTokenWeight(Token.TokenType tokenType) {
        return switch (tokenType) {
            case IDENTIFIER, CAMEL_CASE, SNAKE_CASE -> 1.0;
            case PACKAGE_NAME, CLASS_HINT, METHOD_HINT -> 0.9;
            case DOMAIN_TERM, TECHNICAL_TERM -> 0.8;
            case ACTION_WORD -> 0.7;
            case MODIFIER -> 0.6;
            case STOP_WORD -> 0.1;
        };
    }
    
    // ========== UTILITY METHODS ==========

    /**
     * Checks if a word exactly matches any of the given target words
     */
    private boolean isExactWordMatch(String word, String... targets) {
        for (String target : targets) {
            if (word.equals(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts exclusion patterns with context awareness
     */
    private Set<String> extractExclusionPatterns(String query, List<Token> tokens) {
        Set<String> patterns = new HashSet<>();

        // Look for negation patterns with context
        String[] negationWords = {"not", "exclude", "except", "without", "ignore"};
        String[] words = query.split("\\s+");

        for (int i = 0; i < words.length; i++) {
            String word = words[i].toLowerCase();

            // Check if current word is a negation word
            for (String negation : negationWords) {
                if (word.equals(negation) && i + 1 < words.length) {
                    String nextWord = words[i + 1].toLowerCase();

                    // Add specific exclusion patterns based on context
                    switch (nextWord) {
                        case "test", "tests" -> patterns.add("*Test*");
                        case "deprecated" -> patterns.add("*Deprecated*");
                        case "private" -> patterns.add("*private*");
                        case "abstract" -> patterns.add("*Abstract*");
                        case "interface", "interfaces" -> patterns.add("*interface*");
                        default -> {
                            // For other words, create a general exclusion pattern
                            if (nextWord.length() > 2) {
                                patterns.add("*" + capitalize(nextWord) + "*");
                            }
                        }
                    }
                    break;
                }
            }
        }

        return patterns;
    }

    private double calculateTokenConfidence(String word) {
        if (STOP_WORDS.contains(word.toLowerCase())) return 0.1;
        if (isCamelCase(word) || isSnakeCase(word)) return 0.9;
        if (isActionWord(word.toLowerCase())) return 0.8;
        if (isDomainTerm(word.toLowerCase())) return 0.7;
        return 0.6;
    }
    
    private boolean isCamelCase(String word) {
        // Handle both camelCase and PascalCase
        // Must have at least one uppercase letter and not be all uppercase
        if (word.length() < 2 || word.equals(word.toLowerCase()) || word.equals(word.toUpperCase())) {
            return false;
        }

        // Check for valid Java identifier with mixed case
        return word.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*$") &&
               word.chars().anyMatch(Character::isUpperCase) &&
               word.chars().anyMatch(Character::isLowerCase);
    }

    private boolean isSnakeCase(String word) {
        return word.matches("^[a-z][a-z0-9]*(_[a-z0-9]+)+$") && word.contains("_");
    }
    
    private boolean isActionWord(String word) {
        Set<String> actionWords = Set.of(
            "get", "set", "create", "delete", "update", "find", "search", "process",
            "handle", "execute", "run", "start", "stop", "build", "make", "generate",
            "validate", "check", "verify", "save", "load", "fetch", "retrieve"
        );
        return actionWords.contains(word);
    }
    
    private boolean isDomainTerm(String word) {
        Set<String> domainTerms = Set.of(
            "user", "customer", "account", "payment", "transaction", "order", "product",
            "service", "manager", "controller", "repository", "entity", "model",
            "config", "security", "auth", "validation", "notification", "email"
        );
        return domainTerms.contains(word);
    }
    
    private boolean isTechnicalTerm(String word) {
        Set<String> techTerms = Set.of(
            "api", "rest", "http", "json", "xml", "database", "sql", "cache",
            "queue", "thread", "async", "sync", "batch", "stream", "lambda",
            "annotation", "reflection", "proxy", "factory", "singleton"
        );
        return techTerms.contains(word);
    }
    
    private List<String> splitCamelCase(String camelCase) {
        List<String> components = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        
        for (char c : camelCase.toCharArray()) {
            if (Character.isUpperCase(c) && current.length() > 0) {
                components.add(current.toString());
                current = new StringBuilder();
            }
            current.append(Character.toLowerCase(c));
        }
        
        if (current.length() > 0) {
            components.add(current.toString());
        }
        
        return components;
    }
    
    private boolean isValidCompoundComponent(Token token) {
        return token.getType() == Token.TokenType.IDENTIFIER ||
               token.getType() == Token.TokenType.DOMAIN_TERM ||
               token.getType() == Token.TokenType.TECHNICAL_TERM ||
               token.getType() == Token.TokenType.ACTION_WORD;
    }

    /**
     * Capitalizes the first letter of a string
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1).toLowerCase();
    }
}