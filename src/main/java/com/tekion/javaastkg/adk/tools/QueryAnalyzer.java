package com.tekion.javaastkg.adk.tools;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 🔍 Query Analyzer - ADK FunctionTool for QueryIntentClassifier Agent
 * 
 * Breaks down user queries into structured components for analysis.
 * Extracts entities, identifies question patterns, and assesses query characteristics.
 */
@Slf4j
public class QueryAnalyzer {
    
    // Pattern recognition for query types
    private static final Pattern WHAT_PATTERN = Pattern.compile("\\b(what|which)\\b.*\\b(is|are|does|do)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOW_PATTERN = Pattern.compile("\\bhow\\b.*\\b(does|do|works?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHERE_PATTERN = Pattern.compile("\\bwhere\\b.*\\b(is|are)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHY_PATTERN = Pattern.compile("\\b(why|why does|why is)\\b", Pattern.CASE_INSENSITIVE);
    
    // Entity extraction patterns
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\b[A-Z][a-zA-Z0-9]*(?:Service|Controller|Repository|Manager|Handler|Component|Util|Exception)\\b");
    private static final Pattern METHOD_PATTERN = Pattern.compile("\\b[a-z][a-zA-Z0-9]*\\s*\\(\\s*\\)");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("\\b[a-z]+(?:\\.[a-z]+)+\\b");
    
    @Schema(description = "Analyze and break down user query into structured components")
    public static Map<String, Object> analyzeQuery(
        @Schema(description = "User's natural language query") String query,
        @Schema(description = "Additional context for analysis") Map<String, Object> analysisContext,
        @Schema(name = "toolContext") ToolContext ctx
    ) {
        
        log.debug("🔍 Analyzing query structure: '{}'", query);
        
        try {
            // Input validation
            if (query == null || query.trim().isEmpty()) {
                return Map.of(
                    "status", "error",
                    "error", "Query cannot be empty",
                    "analysis", Map.of()
                );
            }
            
            String normalizedQuery = query.trim();
            
            // Extract question patterns
            Map<String, Object> questionPatterns = analyzeQuestionPatterns(normalizedQuery);
            
            // Extract entities
            Map<String, Object> entities = extractAllEntities(normalizedQuery);
            
            // Analyze query characteristics
            Map<String, Object> characteristics = analyzeQueryCharacteristics(normalizedQuery);
            
            // Identify technical keywords
            List<String> technicalKeywords = identifyTechnicalKeywords(normalizedQuery);
            
            // Calculate analysis metrics
            Map<String, Object> metrics = calculateAnalysisMetrics(normalizedQuery, entities, questionPatterns);
            
            // Update context
            if (ctx != null && ctx.state() != null) {
                ctx.state().put("app:query_analyzed", true);
                ctx.state().put("app:entity_count", ((List<?>) entities.get("allEntities")).size());
            }
            
            return Map.of(
                "status", "success",
                "analysis", Map.of(
                    "originalQuery", normalizedQuery,
                    "questionPatterns", questionPatterns,
                    "entities", entities,
                    "characteristics", characteristics,
                    "technicalKeywords", technicalKeywords,
                    "metrics", metrics
                ),
                "nextActions", List.of("RESOLVE_INTENT", "RECOMMEND_STRATEGY")
            );
            
        } catch (Exception e) {
            log.error("❌ Query analysis failed", e);
            return Map.of(
                "status", "error",
                "error", "Query analysis failed: " + e.getMessage(),
                "analysis", Map.of()
            );
        }
    }
    
    /**
     * Analyze question patterns in the query
     */
    private static Map<String, Object> analyzeQuestionPatterns(String query) {
        Map<String, Boolean> patterns = new HashMap<>();
        
        patterns.put("isWhatQuestion", WHAT_PATTERN.matcher(query).find());
        patterns.put("isHowQuestion", HOW_PATTERN.matcher(query).find());
        patterns.put("isWhereQuestion", WHERE_PATTERN.matcher(query).find());
        patterns.put("isWhyQuestion", WHY_PATTERN.matcher(query).find());
        
        // Determine primary question type
        String primaryType = "UNKNOWN";
        if (patterns.get("isWhatQuestion")) primaryType = "WHAT";
        else if (patterns.get("isHowQuestion")) primaryType = "HOW";
        else if (patterns.get("isWhereQuestion")) primaryType = "WHERE";
        else if (patterns.get("isWhyQuestion")) primaryType = "WHY";
        
        return Map.of(
            "patterns", patterns,
            "primaryQuestionType", primaryType,
            "hasQuestionWord", patterns.values().stream().anyMatch(Boolean::booleanValue)
        );
    }
    
    /**
     * Extract all types of entities from the query
     */
    private static Map<String, Object> extractAllEntities(String query) {
        List<String> classes = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        List<String> packages = new ArrayList<>();
        List<String> quoted = new ArrayList<>();
        
        // Extract class names
        var classMatcher = CLASS_PATTERN.matcher(query);
        while (classMatcher.find()) {
            classes.add(classMatcher.group());
        }
        
        // Extract method patterns
        var methodMatcher = METHOD_PATTERN.matcher(query);
        while (methodMatcher.find()) {
            methods.add(methodMatcher.group().trim());
        }
        
        // Extract package patterns
        var packageMatcher = PACKAGE_PATTERN.matcher(query);
        while (packageMatcher.find()) {
            packages.add(packageMatcher.group());
        }
        
        // Extract quoted strings
        Pattern quotedPattern = Pattern.compile("\"([^\"]+)\"|'([^']+)'");
        var quotedMatcher = quotedPattern.matcher(query);
        while (quotedMatcher.find()) {
            String quotedText = quotedMatcher.group(1) != null ? quotedMatcher.group(1) : quotedMatcher.group(2);
            quoted.add(quotedText);
        }
        
        // Combine all entities
        List<String> allEntities = new ArrayList<>();
        allEntities.addAll(classes);
        allEntities.addAll(methods);
        allEntities.addAll(packages);
        allEntities.addAll(quoted);
        
        return Map.of(
            "classes", classes,
            "methods", methods,
            "packages", packages,
            "quoted", quoted,
            "allEntities", allEntities,
            "entityCounts", Map.of(
                "classes", classes.size(),
                "methods", methods.size(),
                "packages", packages.size(),
                "quoted", quoted.size(),
                "total", allEntities.size()
            )
        );
    }
    
    /**
     * Analyze query characteristics
     */
    private static Map<String, Object> analyzeQueryCharacteristics(String query) {
        String lowerQuery = query.toLowerCase();
        
        return Map.of(
            "length", query.length(),
            "wordCount", query.split("\\s+").length,
            "hasConjunctions", lowerQuery.contains(" and ") || lowerQuery.contains(" or "),
            "hasNegation", lowerQuery.contains("not ") || lowerQuery.contains("don't ") || lowerQuery.contains("doesn't "),
            "isImperative", lowerQuery.startsWith("show ") || lowerQuery.startsWith("find ") || lowerQuery.startsWith("get "),
            "complexity", determineComplexity(query),
            "sentiment", analyzeSentiment(lowerQuery)
        );
    }
    
    /**
     * Identify technical keywords
     */
    private static List<String> identifyTechnicalKeywords(String query) {
        String lowerQuery = query.toLowerCase();
        List<String> keywords = new ArrayList<>();
        
        // Technical domain keywords
        Map<String, List<String>> domains = Map.of(
            "architecture", List.of("architecture", "design", "pattern", "structure"),
            "performance", List.of("performance", "optimize", "slow", "fast", "bottleneck"),
            "debugging", List.of("error", "exception", "bug", "issue", "problem", "fail", "crash", "nullpointer"),
            "testing", List.of("test", "junit", "mock", "assert"),
            "security", List.of("security", "auth", "permission", "token", "ssl"),
            "database", List.of("database", "query", "sql", "transaction", "hibernate"),
            "integration", List.of("api", "rest", "service", "endpoint", "integration")
        );
        
        for (Map.Entry<String, List<String>> domain : domains.entrySet()) {
            for (String keyword : domain.getValue()) {
                if (lowerQuery.contains(keyword)) {
                    keywords.add(domain.getKey() + ":" + keyword);
                }
            }
        }
        
        return keywords;
    }
    
    /**
     * Calculate analysis metrics
     */
    private static Map<String, Object> calculateAnalysisMetrics(String query, Map<String, Object> entities, Map<String, Object> patterns) {
        int entityCount = ((List<?>) entities.get("allEntities")).size();
        boolean hasQuestionWord = (Boolean) ((Map<?, ?>) patterns.get("patterns")).values().stream().anyMatch(v -> (Boolean) v);
        
        double specificity = calculateSpecificity(query, entityCount);
        double clarity = calculateClarity(query, hasQuestionWord);
        
        return Map.of(
            "specificity", specificity,
            "clarity", clarity,
            "overallScore", (specificity + clarity) / 2.0,
            "analysisConfidence", calculateAnalysisConfidence(query, entityCount, hasQuestionWord)
        );
    }
    
    private static String determineComplexity(String query) {
        int factors = 0;
        String lowerQuery = query.toLowerCase();
        
        if (query.length() > 100) factors++;
        if (query.split("\\s+").length > 15) factors++;
        if (lowerQuery.contains(" and ") || lowerQuery.contains(" or ")) factors++;
        if (lowerQuery.matches(".*\\b(architecture|performance|integration|dependency)\\b.*")) factors++;
        
        return switch (factors) {
            case 0, 1 -> "SIMPLE";
            case 2, 3 -> "MODERATE";
            default -> "COMPLEX";
        };
    }
    
    private static String analyzeSentiment(String lowerQuery) {
        if (lowerQuery.contains("error") || lowerQuery.contains("problem") || lowerQuery.contains("issue")) {
            return "PROBLEM_FOCUSED";
        } else if (lowerQuery.contains("understand") || lowerQuery.contains("explain") || lowerQuery.contains("show")) {
            return "LEARNING_FOCUSED";
        } else {
            return "NEUTRAL";
        }
    }
    
    private static double calculateSpecificity(String query, int entityCount) {
        double base = 0.5;
        if (entityCount > 0) base += 0.3;
        if (entityCount > 2) base += 0.2;
        if (query.contains("\"") || query.contains("'")) base += 0.2;
        return Math.min(1.0, base);
    }
    
    private static double calculateClarity(String query, boolean hasQuestionWord) {
        double base = hasQuestionWord ? 0.7 : 0.4;
        if (query.length() >= 10 && query.length() <= 150) base += 0.2;
        if (query.endsWith("?")) base += 0.1;
        return Math.min(1.0, base);
    }
    
    private static double calculateAnalysisConfidence(String query, int entityCount, boolean hasQuestionWord) {
        double confidence = 0.6;
        if (hasQuestionWord) confidence += 0.2;
        if (entityCount > 0) confidence += 0.1;
        if (query.length() >= 20) confidence += 0.1;
        return Math.min(1.0, confidence);
    }
}