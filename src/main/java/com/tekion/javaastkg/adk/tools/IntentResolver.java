package com.tekion.javaastkg.adk.tools;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 🎯 Intent Resolver - ADK FunctionTool for QueryIntentClassifier Agent
 * 
 * Resolves user intent based on query analysis results.
 * Maps query patterns and characteristics to specific intent categories.
 */
@Slf4j
public class IntentResolver {
    
    // Intent scoring weights
    private static final Map<String, Double> INTENT_WEIGHTS = Map.of(
        "DEBUG_ISSUE", 0.9,
        "UNDERSTAND_FLOW", 0.8,
        "LOCATE_ENTITY", 0.8,
        "COMPARE_ENTITIES", 0.7,
        "EXPLORE_ARCHITECTURE", 0.7,
        "ANALYZE_PERFORMANCE", 0.8,
        "GENERATE_DOCUMENTATION", 0.6,
        "UNDERSTAND_ENTITY", 0.5
    );
    
    @Schema(description = "Resolve user intent from query analysis results")
    public static Map<String, Object> resolveIntent(
        @Schema(description = "Query analysis results from QueryAnalyzer") Map<String, Object> analysisResults,
        @Schema(description = "Intent resolution configuration") Map<String, Object> resolutionConfig,
        @Schema(name = "toolContext") ToolContext ctx
    ) {
        
        log.debug("🎯 Resolving intent from analysis results");
        
        try {
            // Validate input
            if (analysisResults == null || !analysisResults.containsKey("analysis")) {
                return Map.of(
                    "status", "error",
                    "error", "Invalid analysis results provided",
                    "intent", Map.of()
                );
            }
            
            Map<String, Object> analysis = (Map<String, Object>) analysisResults.get("analysis");
            
            // Extract key analysis components
            String originalQuery = (String) analysis.get("originalQuery");
            Map<String, Object> questionPatterns = (Map<String, Object>) analysis.get("questionPatterns");
            Map<String, Object> entities = (Map<String, Object>) analysis.get("entities");
            Map<String, Object> characteristics = (Map<String, Object>) analysis.get("characteristics");
            List<String> technicalKeywords = (List<String>) analysis.get("technicalKeywords");
            
            // Score all possible intents
            Map<String, Double> intentScores = scoreAllIntents(originalQuery, questionPatterns, entities, characteristics, technicalKeywords);
            
            // Find primary intent
            String primaryIntent = findPrimaryIntent(intentScores);
            double primaryConfidence = intentScores.get(primaryIntent);
            
            // Find secondary intents if applicable
            List<Map<String, Object>> secondaryIntents = findSecondaryIntents(intentScores, primaryIntent);
            
            // Generate intent explanation
            String explanation = generateIntentExplanation(primaryIntent, primaryConfidence, originalQuery, questionPatterns);
            
            // Calculate overall confidence
            double overallConfidence = calculateOverallConfidence(intentScores, analysis);
            
            // Update context
            if (ctx != null && ctx.state() != null) {
                ctx.state().put("app:resolved_intent", primaryIntent);
                ctx.state().put("app:intent_confidence", overallConfidence);
            }
            
            return Map.of(
                "status", "success",
                "intent", Map.of(
                    "primary", Map.of(
                        "intent", primaryIntent,
                        "confidence", primaryConfidence,
                        "explanation", explanation
                    ),
                    "secondary", secondaryIntents,
                    "allScores", intentScores,
                    "overallConfidence", overallConfidence
                ),
                "intentMetadata", Map.of(
                    "resolutionMethod", "PATTERN_BASED_SCORING",
                    "factorsConsidered", List.of("questionPatterns", "entities", "technicalKeywords", "characteristics"),
                    "topIntentScore", primaryConfidence,
                    "intentCount", intentScores.size()
                ),
                "nextActions", List.of("RECOMMEND_STRATEGY")
            );
            
        } catch (Exception e) {
            log.error("❌ Intent resolution failed", e);
            return Map.of(
                "status", "error",
                "error", "Intent resolution failed: " + e.getMessage(),
                "intent", Map.of()
            );
        }
    }
    
    /**
     * Score all possible intents based on analysis results
     */
    private static Map<String, Double> scoreAllIntents(String query, Map<String, Object> questionPatterns,
                                                      Map<String, Object> entities, Map<String, Object> characteristics,
                                                      List<String> technicalKeywords) {
        
        Map<String, Double> scores = new HashMap<>();
        String lowerQuery = query.toLowerCase();
        String primaryQuestionType = (String) questionPatterns.get("primaryQuestionType");
        String sentiment = (String) characteristics.get("sentiment");
        
        // DEBUG_ISSUE Intent
        double debugScore = 0.0;
        if ("PROBLEM_FOCUSED".equals(sentiment)) debugScore += 0.4;
        if ("WHY".equals(primaryQuestionType)) debugScore += 0.3;
        if (containsDebugKeywords(technicalKeywords)) debugScore += 0.3;
        if (containsErrorTerms(lowerQuery)) debugScore += 0.4;
        scores.put("DEBUG_ISSUE", debugScore);
        
        // UNDERSTAND_FLOW Intent
        double flowScore = 0.0;
        if ("HOW".equals(primaryQuestionType)) flowScore += 0.4;
        if (lowerQuery.contains("flow") || lowerQuery.contains("process") || lowerQuery.contains("works")) flowScore += 0.3;
        if (getEntityCount(entities) > 1) flowScore += 0.2;
        if (containsFlowKeywords(lowerQuery)) flowScore += 0.2;
        scores.put("UNDERSTAND_FLOW", flowScore);
        
        // LOCATE_ENTITY Intent
        double locateScore = 0.0;
        if ("WHERE".equals(primaryQuestionType)) locateScore += 0.5;
        if (lowerQuery.contains("find") || lowerQuery.contains("locate") || lowerQuery.contains("search")) locateScore += 0.3;
        if (getEntityCount(entities) == 1) locateScore += 0.2;
        scores.put("LOCATE_ENTITY", locateScore);
        
        // COMPARE_ENTITIES Intent
        double compareScore = 0.0;
        if (lowerQuery.contains("compare") || lowerQuery.contains("difference") || lowerQuery.contains("vs")) compareScore += 0.5;
        if (getEntityCount(entities) >= 2) compareScore += 0.3;
        if (lowerQuery.contains(" and ") && getEntityCount(entities) > 1) compareScore += 0.2;
        scores.put("COMPARE_ENTITIES", compareScore);
        
        // EXPLORE_ARCHITECTURE Intent
        double archScore = 0.0;
        if (containsArchitectureKeywords(technicalKeywords)) archScore += 0.4;
        if (lowerQuery.contains("architecture") || lowerQuery.contains("structure") || lowerQuery.contains("design")) archScore += 0.3;
        if ("COMPLEX".equals(characteristics.get("complexity"))) archScore += 0.2;
        scores.put("EXPLORE_ARCHITECTURE", archScore);
        
        // ANALYZE_PERFORMANCE Intent
        double perfScore = 0.0;
        if (containsPerformanceKeywords(technicalKeywords)) perfScore += 0.4;
        if (lowerQuery.contains("performance") || lowerQuery.contains("slow") || lowerQuery.contains("optimize")) perfScore += 0.3;
        if (lowerQuery.contains("bottleneck") || lowerQuery.contains("fast")) perfScore += 0.2;
        scores.put("ANALYZE_PERFORMANCE", perfScore);
        
        // GENERATE_DOCUMENTATION Intent
        double docScore = 0.0;
        if (lowerQuery.contains("document") || lowerQuery.contains("explain") || lowerQuery.contains("describe")) docScore += 0.3;
        if ("LEARNING_FOCUSED".equals(sentiment)) docScore += 0.2;
        if (lowerQuery.contains("summary") || lowerQuery.contains("overview")) docScore += 0.2;
        scores.put("GENERATE_DOCUMENTATION", docScore);
        
        // UNDERSTAND_ENTITY Intent (default)
        double entityScore = 0.3; // Base score
        if ("WHAT".equals(primaryQuestionType)) entityScore += 0.3;
        if (getEntityCount(entities) == 1) entityScore += 0.2;
        if ("LEARNING_FOCUSED".equals(sentiment)) entityScore += 0.1;
        scores.put("UNDERSTAND_ENTITY", entityScore);
        
        return scores;
    }
    
    /**
     * Find the primary intent with highest score
     */
    private static String findPrimaryIntent(Map<String, Double> scores) {
        return scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("UNDERSTAND_ENTITY");
    }
    
    /**
     * Find secondary intents (scores within 0.2 of primary)
     */
    private static List<Map<String, Object>> findSecondaryIntents(Map<String, Double> scores, String primaryIntent) {
        double primaryScore = scores.get(primaryIntent);
        double threshold = primaryScore - 0.2;
        
        return scores.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(primaryIntent))
            .filter(entry -> entry.getValue() >= threshold && entry.getValue() > 0.3)
            .map(entry -> Map.<String, Object>of(
                "intent", entry.getKey(),
                "confidence", entry.getValue(),
                "scoreDifference", primaryScore - entry.getValue()
            ))
            .sorted((a, b) -> Double.compare((Double) b.get("confidence"), (Double) a.get("confidence")))
            .limit(2)
            .toList();
    }
    
    /**
     * Generate explanation for the resolved intent
     */
    private static String generateIntentExplanation(String intent, double confidence, String query, Map<String, Object> questionPatterns) {
        String primaryQuestionType = (String) questionPatterns.get("primaryQuestionType");
        
        StringBuilder explanation = new StringBuilder();
        explanation.append(String.format("Resolved intent '%s' with %.1f%% confidence. ", 
            intent.replace("_", " ").toLowerCase(), confidence * 100));
        
        switch (intent) {
            case "DEBUG_ISSUE" -> explanation.append("Query indicates troubleshooting or error investigation.");
            case "UNDERSTAND_FLOW" -> explanation.append("Query seeks to understand process or execution flow.");
            case "LOCATE_ENTITY" -> explanation.append("Query aims to find or locate specific code entities.");
            case "COMPARE_ENTITIES" -> explanation.append("Query requests comparison between multiple entities.");
            case "EXPLORE_ARCHITECTURE" -> explanation.append("Query focuses on architectural understanding.");
            case "ANALYZE_PERFORMANCE" -> explanation.append("Query relates to performance analysis or optimization.");
            case "GENERATE_DOCUMENTATION" -> explanation.append("Query requests documentation or explanation generation.");
            default -> explanation.append("Query seeks general understanding of code entities.");
        }
        
        if (!"UNKNOWN".equals(primaryQuestionType)) {
            explanation.append(String.format(" Primary question type: %s.", primaryQuestionType));
        }
        
        return explanation.toString();
    }
    
    /**
     * Calculate overall confidence based on intent scores and analysis quality
     */
    private static double calculateOverallConfidence(Map<String, Double> scores, Map<String, Object> analysis) {
        double maxScore = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        
        Map<String, Object> metrics = (Map<String, Object>) analysis.get("metrics");
        Object confidenceObj = metrics.get("analysisConfidence");
        double analysisConfidence = confidenceObj instanceof Number ? ((Number) confidenceObj).doubleValue() : 0.0;
        
        // Combine intent score with analysis confidence
        return (maxScore * 0.7) + (analysisConfidence * 0.3);
    }
    
    // Helper methods for keyword detection
    private static boolean containsDebugKeywords(List<String> keywords) {
        return keywords.stream().anyMatch(k -> k.startsWith("debugging:"));
    }
    
    private static boolean containsErrorTerms(String query) {
        return query.contains("error") || query.contains("exception") || query.contains("fail") || 
               query.contains("bug") || query.contains("crash") || query.contains("nullpointer");
    }
    
    private static boolean containsFlowKeywords(String query) {
        return query.contains("sequence") || query.contains("step") || query.contains("execution") ||
               query.contains("call") || query.contains("invoke");
    }
    
    private static boolean containsArchitectureKeywords(List<String> keywords) {
        return keywords.stream().anyMatch(k -> k.startsWith("architecture:"));
    }
    
    private static boolean containsPerformanceKeywords(List<String> keywords) {
        return keywords.stream().anyMatch(k -> k.startsWith("performance:"));
    }
    
    private static int getEntityCount(Map<String, Object> entities) {
        Map<String, Object> counts = (Map<String, Object>) entities.get("entityCounts");
        return (Integer) counts.get("total");
    }
}