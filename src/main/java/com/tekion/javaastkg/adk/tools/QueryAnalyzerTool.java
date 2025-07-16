package com.tekion.javaastkg.adk.tools;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import com.tekion.javaastkg.adk.core.BaseAdkTool;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 🔍 Query Analyzer Tool - Refactored ADK FunctionTool
 * 
 * Breaks down user queries into structured components for analysis.
 * Extracts entities, identifies question patterns, and assesses query characteristics.
 * 
 * This tool extends BaseAdkTool for consistent patterns and provides:
 * - Entity extraction (classes, methods, packages)
 * - Question pattern recognition
 * - Query complexity analysis
 * - Contextual keyword detection
 * 
 * Used by QueryIntentClassifierAgent as the first step in the 3-step workflow.
 */
@Slf4j
public class QueryAnalyzerTool extends BaseAdkTool {
    
    // Tool configuration constants
    private static final String TOOL_NAME = "QueryAnalyzer";
    private static final String OPERATION_ANALYZE = "analyze_query";
    
    // Pattern recognition for query types
    private static final Pattern WHAT_PATTERN = Pattern.compile("\\b(what|which)\\b.*\\b(is|are|does|do)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOW_PATTERN = Pattern.compile("\\bhow\\b.*\\b(does|do|works?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHERE_PATTERN = Pattern.compile("\\bwhere\\b.*\\b(is|are)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHY_PATTERN = Pattern.compile("\\b(why|why does|why is)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHEN_PATTERN = Pattern.compile("\\b(when|during|at what)\\b", Pattern.CASE_INSENSITIVE);
    
    // Entity extraction patterns
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\b[A-Z][a-zA-Z0-9]*(?:Service|Controller|Repository|Manager|Handler|Component|Util|Exception|Entity|Model|Config|Factory|Builder|Helper|Processor|Provider|Resolver|Validator|Converter|Mapper|Adapter|Proxy|Decorator|Strategy|Command|Observer|Listener|Filter|Interceptor|Guard|Policy|Rule|Specification|Criteria|Query|Request|Response|DTO|VO|Bean|Interface|Abstract|Impl)\\b");
    private static final Pattern METHOD_PATTERN = Pattern.compile("\\b[a-z][a-zA-Z0-9]*\\s*\\(\\s*\\)");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("\\b[a-z]+(?:\\.[a-z]+)+\\b");
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\b[a-z][a-zA-Z0-9]*\\b");
    
    // Keyword categories for intent hints
    private static final Set<String> DEBUG_KEYWORDS = Set.of(
        "error", "exception", "bug", "fail", "crash", "broken", "issue", "problem", "wrong", "null", "undefined", "stack", "trace"
    );
    
    private static final Set<String> FLOW_KEYWORDS = Set.of(
        "flow", "sequence", "steps", "process", "execution", "call", "invoke", "trigger", "run", "execute", "lifecycle"
    );
    
    private static final Set<String> ARCHITECTURE_KEYWORDS = Set.of(
        "architecture", "design", "structure", "pattern", "relationship", "dependency", "coupling", "component", "module", "layer"
    );
    
    private static final Set<String> PERFORMANCE_KEYWORDS = Set.of(
        "performance", "slow", "fast", "optimize", "efficient", "memory", "cpu", "time", "latency", "throughput", "bottleneck"
    );
    
    /**
     * Analyze and break down user query into structured components
     * 
     * @param query User's natural language query
     * @param analysisContext Additional context for analysis
     * @param ctx Tool context from ADK framework
     * @return Structured analysis result
     */
    @Schema(description = "Analyze and break down user query into structured components")
    public static Map<String, Object> analyzeQuery(
        @Schema(description = "User's natural language query to analyze") String query,
        @Schema(description = "Additional context for analysis (optional)") Map<String, Object> analysisContext,
        @Schema(name = "toolContext") ToolContext ctx
    ) {
        long startTime = System.currentTimeMillis();
        logToolStart(TOOL_NAME, OPERATION_ANALYZE);
        
        try {
            // Input validation
            Optional<Map<String, Object>> validationError = validateRequired("query", query, OPERATION_ANALYZE);
            if (validationError.isPresent()) {
                return validationError.get();
            }
            
            // Extract tool context
            Map<String, Object> toolContext = extractToolContext(ctx);
            
            // Normalize query
            String normalizedQuery = query.trim().toLowerCase();
            
            // Perform analysis
            Map<String, Object> analysisResult = new HashMap<>();
            
            // 1. Extract entities
            Map<String, Object> entities = extractEntities(query);
            analysisResult.put("entities", entities);
            
            // 2. Identify question patterns
            Map<String, Object> questionPatterns = identifyQuestionPatterns(normalizedQuery);
            analysisResult.put("questionPatterns", questionPatterns);
            
            // 3. Analyze query complexity
            Map<String, Object> complexity = analyzeComplexity(query, entities);
            analysisResult.put("complexity", complexity);
            
            // 4. Extract keywords by category
            Map<String, Object> keywords = extractKeywords(normalizedQuery);
            analysisResult.put("keywords", keywords);
            
            // 5. Calculate analysis metrics
            Map<String, Object> metrics = calculateMetrics(query, entities, questionPatterns, complexity);
            analysisResult.put("metrics", metrics);
            
            // 6. Add context information
            if (analysisContext != null && !analysisContext.isEmpty()) {
                analysisResult.put("contextProvided", true);
                analysisResult.put("contextKeys", analysisContext.keySet());
            } else {
                analysisResult.put("contextProvided", false);
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            logToolComplete(TOOL_NAME, OPERATION_ANALYZE, executionTime);
            
            // Create successful response
            Map<String, Object> response = successResponse(OPERATION_ANALYZE, analysisResult);
            response.put("metadata", createMetadata(TOOL_NAME, executionTime));
            response.put("toolContext", toolContext);
            
            return response;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            String errorMessage = "Query analysis failed: " + e.getMessage();
            logToolError(TOOL_NAME, OPERATION_ANALYZE, errorMessage);
            
            return errorResponse(OPERATION_ANALYZE, errorMessage, Map.of(
                "exception", e.getClass().getSimpleName(),
                "executionTime", executionTime
            ));
        }
    }
    
    /**
     * Extract entities from the query
     */
    private static Map<String, Object> extractEntities(String query) {
        List<String> classes = extractMatches(query, CLASS_PATTERN);
        List<String> methods = extractMatches(query, METHOD_PATTERN);
        List<String> packages = extractMatches(query, PACKAGE_PATTERN);
        
        // Count entities
        Map<String, Integer> entityCounts = Map.of(
            "classes", classes.size(),
            "methods", methods.size(),
            "packages", packages.size(),
            "total", classes.size() + methods.size() + packages.size()
        );
        
        // Combine all entities
        Set<String> allEntities = new HashSet<>();
        allEntities.addAll(classes);
        allEntities.addAll(methods);
        allEntities.addAll(packages);
        
        return Map.of(
            "classes", classes,
            "methods", methods,
            "packages", packages,
            "allEntities", new ArrayList<>(allEntities),
            "entityCounts", entityCounts
        );
    }
    
    /**
     * Identify question patterns in the query
     */
    private static Map<String, Object> identifyQuestionPatterns(String normalizedQuery) {
        Map<String, Boolean> patterns = Map.of(
            "what", WHAT_PATTERN.matcher(normalizedQuery).find(),
            "how", HOW_PATTERN.matcher(normalizedQuery).find(),
            "where", WHERE_PATTERN.matcher(normalizedQuery).find(),
            "why", WHY_PATTERN.matcher(normalizedQuery).find(),
            "when", WHEN_PATTERN.matcher(normalizedQuery).find()
        );
        
        // Determine primary question type
        String primaryPattern = patterns.entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse("unknown");
        
        return Map.of(
            "patterns", patterns,
            "primaryPattern", primaryPattern,
            "hasQuestionPattern", patterns.containsValue(true)
        );
    }
    
    /**
     * Analyze query complexity
     */
    private static Map<String, Object> analyzeComplexity(String query, Map<String, Object> entities) {
        @SuppressWarnings("unchecked")
        Map<String, Integer> entityCounts = (Map<String, Integer>) entities.get("entityCounts");
        
        int wordCount = query.split("\\s+").length;
        int sentenceCount = query.split("[.!?]+").length;
        int entityCount = entityCounts.get("total");
        
        // Calculate complexity score (0.0 to 1.0)
        double complexityScore = Math.min(1.0, 
            (wordCount / 50.0) * 0.4 + 
            (sentenceCount / 5.0) * 0.3 + 
            (entityCount / 10.0) * 0.3
        );
        
        String complexityLevel = complexityScore < 0.3 ? "simple" : 
                                complexityScore < 0.7 ? "moderate" : "complex";
        
        return Map.of(
            "wordCount", wordCount,
            "sentenceCount", sentenceCount,
            "entityCount", entityCount,
            "complexityScore", complexityScore,
            "complexityLevel", complexityLevel
        );
    }
    
    /**
     * Extract keywords by category
     */
    private static Map<String, Object> extractKeywords(String normalizedQuery) {
        List<String> debugKeywords = DEBUG_KEYWORDS.stream()
            .filter(normalizedQuery::contains)
            .toList();
        
        List<String> flowKeywords = FLOW_KEYWORDS.stream()
            .filter(normalizedQuery::contains)
            .toList();
        
        List<String> architectureKeywords = ARCHITECTURE_KEYWORDS.stream()
            .filter(normalizedQuery::contains)
            .toList();
        
        List<String> performanceKeywords = PERFORMANCE_KEYWORDS.stream()
            .filter(normalizedQuery::contains)
            .toList();
        
        return Map.of(
            "debug", debugKeywords,
            "flow", flowKeywords,
            "architecture", architectureKeywords,
            "performance", performanceKeywords,
            "totalKeywords", debugKeywords.size() + flowKeywords.size() + 
                           architectureKeywords.size() + performanceKeywords.size()
        );
    }
    
    /**
     * Calculate analysis metrics
     */
    private static Map<String, Object> calculateMetrics(String query, Map<String, Object> entities, 
                                                       Map<String, Object> questionPatterns, 
                                                       Map<String, Object> complexity) {
        @SuppressWarnings("unchecked")
        Map<String, Integer> entityCounts = (Map<String, Integer>) entities.get("entityCounts");
        @SuppressWarnings("unchecked")
        Map<String, Boolean> patterns = (Map<String, Boolean>) questionPatterns.get("patterns");
        
        // Calculate analysis confidence based on various factors
        double confidence = 0.0;
        
        // Base confidence from query length and structure
        confidence += Math.min(0.3, query.length() / 200.0);
        
        // Confidence from entity detection
        confidence += Math.min(0.3, entityCounts.get("total") / 10.0);
        
        // Confidence from question pattern detection
        if ((Boolean) questionPatterns.get("hasQuestionPattern")) {
            confidence += 0.2;
        }
        
        // Confidence from keyword detection
        confidence += Math.min(0.2, entityCounts.get("total") > 0 ? 0.2 : 0.0);
        
        confidence = Math.min(1.0, confidence);
        
        return Map.of(
            "analysisConfidence", confidence,
            "entityDensity", entityCounts.get("total") / (double) query.split("\\s+").length,
            "questionPatternCount", (int) patterns.values().stream().mapToInt(b -> b ? 1 : 0).sum(),
            "analysisComplete", true
        );
    }
    
    /**
     * Extract matches from text using regex pattern
     */
    private static List<String> extractMatches(String text, Pattern pattern) {
        return pattern.matcher(text)
            .results()
            .map(match -> match.group().trim())
            .distinct()
            .toList();
    }
}