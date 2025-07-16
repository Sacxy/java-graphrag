package com.tekion.javaastkg.adk.tools;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 📋 Strategy Recommender - ADK FunctionTool for QueryIntentClassifier Agent
 * 
 * Recommends optimal execution strategies and tool orchestration patterns
 * based on resolved intent and query characteristics.
 */
@Slf4j
public class StrategyRecommender {
    
    // Strategy definitions with tool sequences
    private static final Map<String, Map<String, Object>> STRATEGY_DEFINITIONS = Map.of(
        "SURGICAL", Map.of(
            "description", "Focused, precise analysis for specific problems",
            "tools", List.of("huntCode", "traceExecutionPath", "enrichContext"),
            "parallelism", false,
            "estimatedTime", "30-60 seconds"
        ),
        "FOCUSED", Map.of(
            "description", "Targeted analysis with limited scope expansion",
            "tools", List.of("huntCode", "exploreStructure", "enrichContext"),
            "parallelism", true,
            "estimatedTime", "45-90 seconds"
        ),
        "BALANCED", Map.of(
            "description", "Comprehensive analysis with moderate depth",
            "tools", List.of("huntCode", "exploreStructure", "traceExecutionPath", "enrichContext"),
            "parallelism", true,
            "estimatedTime", "60-120 seconds"
        ),
        "EXPLORATORY", Map.of(
            "description", "Deep exploration for complex architectural understanding",
            "tools", List.of("exploreStructure", "huntCode", "traceExecutionPath", "enrichContext", "generateNarrative"),
            "parallelism", true,
            "estimatedTime", "90-180 seconds"
        ),
        "COMPARATIVE", Map.of(
            "description", "Side-by-side analysis for entity comparison",
            "tools", List.of("huntCode", "exploreStructure", "enrichContext"),
            "parallelism", false,
            "estimatedTime", "75-150 seconds"
        )
    );
    
    @Schema(description = "Recommend execution strategy based on resolved intent and query characteristics")
    public static Map<String, Object> recommendStrategy(
        @Schema(description = "Resolved intent results from IntentResolver") Map<String, Object> intentResults,
        @Schema(description = "Original query analysis for strategy tuning") Map<String, Object> queryAnalysis,
        @Schema(description = "Strategy recommendation configuration") Map<String, Object> strategyConfig,
        @Schema(name = "toolContext") ToolContext ctx
    ) {
        
        log.debug("📋 Recommending execution strategy");
        
        try {
            // Validate input
            if (intentResults == null || !intentResults.containsKey("intent")) {
                return Map.of(
                    "status", "error",
                    "error", "Invalid intent results provided",
                    "strategy", Map.of()
                );
            }
            
            Map<String, Object> intent = (Map<String, Object>) intentResults.get("intent");
            Map<String, Object> primary = (Map<String, Object>) intent.get("primary");
            String primaryIntent = (String) primary.get("intent");
            double intentConfidence = (Double) primary.get("confidence");
            
            // Extract analysis characteristics
            Map<String, Object> analysis = (Map<String, Object>) queryAnalysis.get("analysis");
            Map<String, Object> characteristics = (Map<String, Object>) analysis.get("characteristics");
            Map<String, Object> entities = (Map<String, Object>) analysis.get("entities");
            
            // Determine base strategy from intent
            String baseStrategy = mapIntentToStrategy(primaryIntent, characteristics, entities);
            
            // Apply strategy modifiers
            String finalStrategy = applyStrategyModifiers(baseStrategy, characteristics, entities, intentConfidence);
            
            // Get strategy configuration
            Map<String, Object> strategyDetails = STRATEGY_DEFINITIONS.get(finalStrategy);
            
            // Generate tool execution plan
            Map<String, Object> executionPlan = generateExecutionPlan(finalStrategy, strategyDetails, primaryIntent, characteristics);
            
            // Calculate strategy confidence
            double strategyConfidence = calculateStrategyConfidence(finalStrategy, intentConfidence, characteristics);
            
            // Generate recommendations
            List<String> recommendations = generateRecommendations(finalStrategy, primaryIntent, characteristics);
            
            // Update context
            if (ctx != null && ctx.state() != null) {
                ctx.state().put("app:recommended_strategy", finalStrategy);
                ctx.state().put("app:strategy_confidence", strategyConfidence);
                ctx.state().put("app:tool_sequence", executionPlan.get("toolSequence"));
            }
            
            return Map.of(
                "status", "success",
                "strategy", Map.of(
                    "recommended", finalStrategy,
                    "confidence", strategyConfidence,
                    "description", strategyDetails.get("description"),
                    "rationale", generateStrategyRationale(finalStrategy, primaryIntent, characteristics)
                ),
                "executionPlan", executionPlan,
                "recommendations", recommendations,
                "strategyMetadata", Map.of(
                    "baseStrategy", baseStrategy,
                    "modifiersApplied", !baseStrategy.equals(finalStrategy),
                    "estimatedTime", strategyDetails.get("estimatedTime"),
                    "useParallelism", strategyDetails.get("parallelism"),
                    "toolCount", ((List<?>) strategyDetails.get("tools")).size()
                ),
                "nextActions", List.of("EXECUTE_STRATEGY")
            );
            
        } catch (Exception e) {
            log.error("❌ Strategy recommendation failed", e);
            return Map.of(
                "status", "error",
                "error", "Strategy recommendation failed: " + e.getMessage(),
                "strategy", Map.of()
            );
        }
    }
    
    /**
     * Map intent to base strategy
     */
    private static String mapIntentToStrategy(String intent, Map<String, Object> characteristics, Map<String, Object> entities) {
        int entityCount = getEntityCount(entities);
        String complexity = (String) characteristics.get("complexity");
        
        return switch (intent) {
            case "DEBUG_ISSUE" -> "SURGICAL";
            case "UNDERSTAND_FLOW" -> entityCount > 3 ? "EXPLORATORY" : "FOCUSED";
            case "LOCATE_ENTITY" -> "FOCUSED";
            case "COMPARE_ENTITIES" -> "COMPARATIVE";
            case "EXPLORE_ARCHITECTURE" -> "EXPLORATORY";
            case "ANALYZE_PERFORMANCE" -> "SURGICAL";
            case "GENERATE_DOCUMENTATION" -> "COMPLEX".equals(complexity) ? "EXPLORATORY" : "BALANCED";
            default -> "BALANCED";
        };
    }
    
    /**
     * Apply modifiers to base strategy based on characteristics
     */
    private static String applyStrategyModifiers(String baseStrategy, Map<String, Object> characteristics, 
                                               Map<String, Object> entities, double intentConfidence) {
        
        String complexity = (String) characteristics.get("complexity");
        int entityCount = getEntityCount(entities);
        int wordCount = (Integer) characteristics.get("wordCount");
        
        // Upgrade strategy for complex scenarios
        if ("COMPLEX".equals(complexity) && "FOCUSED".equals(baseStrategy)) {
            return "BALANCED";
        }
        
        if ("COMPLEX".equals(complexity) && "BALANCED".equals(baseStrategy)) {
            return "EXPLORATORY";
        }
        
        // Downgrade strategy for simple scenarios with low confidence
        if ("SIMPLE".equals(complexity) && intentConfidence < 0.6) {
            if ("EXPLORATORY".equals(baseStrategy)) return "BALANCED";
            if ("BALANCED".equals(baseStrategy)) return "FOCUSED";
        }
        
        // Adjust for entity count
        if (entityCount > 5 && !"EXPLORATORY".equals(baseStrategy)) {
            return "EXPLORATORY";
        }
        
        if (entityCount == 0 && wordCount < 5) {
            return "FOCUSED";
        }
        
        return baseStrategy;
    }
    
    /**
     * Generate detailed execution plan
     */
    private static Map<String, Object> generateExecutionPlan(String strategy, Map<String, Object> strategyDetails,
                                                            String intent, Map<String, Object> characteristics) {
        
        List<String> tools = (List<String>) strategyDetails.get("tools");
        boolean useParallelism = (Boolean) strategyDetails.get("parallelism");
        
        // Customize tool sequence based on intent
        List<String> customizedTools = customizeToolSequence(tools, intent, characteristics);
        
        // Generate execution phases
        List<Map<String, Object>> phases = generateExecutionPhases(customizedTools, useParallelism);
        
        // Calculate execution parameters
        Map<String, Object> parameters = generateExecutionParameters(strategy, characteristics);
        
        return Map.of(
            "toolSequence", customizedTools,
            "phases", phases,
            "useParallelism", useParallelism,
            "parameters", parameters,
            "estimatedDuration", strategyDetails.get("estimatedTime")
        );
    }
    
    /**
     * Customize tool sequence for specific intent
     */
    private static List<String> customizeToolSequence(List<String> baseTools, String intent, Map<String, Object> characteristics) {
        List<String> customized = new ArrayList<>(baseTools);
        
        // Add narrative generation for explanatory intents
        if (("UNDERSTAND_FLOW".equals(intent) || "GENERATE_DOCUMENTATION".equals(intent)) 
            && !customized.contains("generateNarrative")) {
            customized.add("generateNarrative");
        }
        
        // Prioritize execution tracing for debug scenarios
        if ("DEBUG_ISSUE".equals(intent) && customized.contains("traceExecutionPath")) {
            customized.remove("traceExecutionPath");
            customized.add(1, "traceExecutionPath"); // Move to second position
        }
        
        // Add structure exploration for architecture queries
        if ("EXPLORE_ARCHITECTURE".equals(intent) && !customized.contains("exploreStructure")) {
            customized.add(0, "exploreStructure"); // Move to first position
        }
        
        return customized;
    }
    
    /**
     * Generate execution phases for parallelism
     */
    private static List<Map<String, Object>> generateExecutionPhases(List<String> tools, boolean useParallelism) {
        if (!useParallelism) {
            return tools.stream()
                .map(tool -> Map.<String, Object>of("tools", List.of(tool), "type", "SEQUENTIAL"))
                .toList();
        }
        
        // Group tools into parallel-safe phases
        List<Map<String, Object>> phases = new ArrayList<>();
        
        // Phase 1: Code hunting (can run alone)
        if (tools.contains("huntCode")) {
            phases.add(Map.of("tools", List.of("huntCode"), "type", "SEQUENTIAL"));
        }
        
        // Phase 2: Structure and trace analysis (can run in parallel)
        List<String> parallelTools = new ArrayList<>();
        if (tools.contains("exploreStructure")) parallelTools.add("exploreStructure");
        if (tools.contains("traceExecutionPath")) parallelTools.add("traceExecutionPath");
        
        if (!parallelTools.isEmpty()) {
            phases.add(Map.of("tools", parallelTools, "type", "PARALLEL"));
        }
        
        // Phase 3: Context enrichment (depends on previous phases)
        if (tools.contains("enrichContext")) {
            phases.add(Map.of("tools", List.of("enrichContext"), "type", "SEQUENTIAL"));
        }
        
        // Phase 4: Narrative generation (final step)
        if (tools.contains("generateNarrative")) {
            phases.add(Map.of("tools", List.of("generateNarrative"), "type", "SEQUENTIAL"));
        }
        
        return phases;
    }
    
    /**
     * Generate execution parameters for strategy
     */
    private static Map<String, Object> generateExecutionParameters(String strategy, Map<String, Object> characteristics) {
        String complexity = (String) characteristics.get("complexity");
        
        return Map.of(
            "maxDepth", switch (strategy) {
                case "SURGICAL" -> 2;
                case "FOCUSED" -> 3;
                case "BALANCED" -> 4;
                case "EXPLORATORY" -> 5;
                case "COMPARATIVE" -> 3;
                default -> 3;
            },
            "maxResults", switch (complexity) {
                case "SIMPLE" -> 10;
                case "MODERATE" -> 20;
                case "COMPLEX" -> 50;
                default -> 20;
            },
            "timeout", switch (strategy) {
                case "SURGICAL" -> 60;
                case "FOCUSED" -> 90;
                case "BALANCED" -> 120;
                case "EXPLORATORY" -> 180;
                case "COMPARATIVE" -> 150;
                default -> 120;
            }
        );
    }
    
    /**
     * Calculate confidence in strategy recommendation
     */
    private static double calculateStrategyConfidence(String strategy, double intentConfidence, Map<String, Object> characteristics) {
        double base = intentConfidence * 0.7; // Base on intent confidence
        
        // Boost confidence for well-defined strategies
        if (List.of("SURGICAL", "FOCUSED").contains(strategy)) {
            base += 0.2;
        }
        
        // Reduce confidence for complex scenarios
        if ("COMPLEX".equals(characteristics.get("complexity"))) {
            base -= 0.1;
        }
        
        return Math.min(1.0, Math.max(0.3, base));
    }
    
    /**
     * Generate strategy rationale
     */
    private static String generateStrategyRationale(String strategy, String intent, Map<String, Object> characteristics) {
        StringBuilder rationale = new StringBuilder();
        
        rationale.append(String.format("Selected '%s' strategy for '%s' intent. ", 
            strategy.toLowerCase(), intent.replace("_", " ").toLowerCase()));
        
        String complexity = (String) characteristics.get("complexity");
        rationale.append(String.format("Query complexity is %s. ", complexity.toLowerCase()));
        
        switch (strategy) {
            case "SURGICAL" -> rationale.append("Using precise, focused analysis for specific problem resolution.");
            case "FOCUSED" -> rationale.append("Using targeted analysis with controlled scope expansion.");
            case "BALANCED" -> rationale.append("Using comprehensive analysis with moderate depth.");
            case "EXPLORATORY" -> rationale.append("Using deep exploration for complex architectural understanding.");
            case "COMPARATIVE" -> rationale.append("Using side-by-side analysis for entity comparison.");
        }
        
        return rationale.toString();
    }
    
    /**
     * Generate execution recommendations
     */
    private static List<String> generateRecommendations(String strategy, String intent, Map<String, Object> characteristics) {
        List<String> recommendations = new ArrayList<>();
        
        if ("COMPLEX".equals(characteristics.get("complexity"))) {
            recommendations.add("Consider breaking down complex query into smaller parts");
        }
        
        if ("DEBUG_ISSUE".equals(intent)) {
            recommendations.add("Focus on error context and execution path analysis");
        }
        
        if ("EXPLORATORY".equals(strategy)) {
            recommendations.add("Allow sufficient time for comprehensive analysis");
        }
        
        if ((Boolean) characteristics.get("hasConjunctions")) {
            recommendations.add("Query contains multiple concerns - results may address each separately");
        }
        
        return recommendations;
    }
    
    private static int getEntityCount(Map<String, Object> entities) {
        Map<String, Object> counts = (Map<String, Object>) entities.get("entityCounts");
        return (Integer) counts.get("total");
    }
}