package com.tekion.javaastkg.query.intelligence;

import com.tekion.javaastkg.query.services.LLMService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Analyzes user queries to understand intent and contextual clues.
 * This enables intelligent query expansion and focused search strategies.
 */
@Service
@Slf4j
public class QueryIntentAnalyzer {

    private final LLMService llmService;
    
    @Value("${query_optimization.intent_analysis.llm_fallback_enabled:true}")
    private boolean llmFallbackEnabled;
    
    @Value("${query_optimization.intent_analysis.confidence_threshold:0.7}")
    private double confidenceThreshold;
    
    @Value("${query_optimization.intent_analysis.multi_intent_detection:true}")
    private boolean multiIntentDetection;

    // Intent pattern definitions
    private static final Map<IntentType, List<Pattern>> INTENT_PATTERNS = new HashMap<>();
    
    static {
        // Implementation intent patterns
        INTENT_PATTERNS.put(IntentType.IMPLEMENTATION, Arrays.asList(
            Pattern.compile("how\\s+does.*work", Pattern.CASE_INSENSITIVE),
            Pattern.compile("implementation\\s+of", Pattern.CASE_INSENSITIVE),
            Pattern.compile("algorithm\\s+for", Pattern.CASE_INSENSITIVE),
            Pattern.compile("logic\\s+behind", Pattern.CASE_INSENSITIVE),
            Pattern.compile("how\\s+is.*implemented", Pattern.CASE_INSENSITIVE),
            Pattern.compile("code\\s+for", Pattern.CASE_INSENSITIVE),
            Pattern.compile("source\\s+of", Pattern.CASE_INSENSITIVE)
        ));
        
        // Usage intent patterns
        INTENT_PATTERNS.put(IntentType.USAGE, Arrays.asList(
            Pattern.compile("where\\s+is.*used", Pattern.CASE_INSENSITIVE),
            Pattern.compile("what\\s+uses", Pattern.CASE_INSENSITIVE),
            Pattern.compile("called\\s+by", Pattern.CASE_INSENSITIVE),
            Pattern.compile("dependencies\\s+of", Pattern.CASE_INSENSITIVE),
            Pattern.compile("references\\s+to", Pattern.CASE_INSENSITIVE),
            Pattern.compile("who\\s+calls", Pattern.CASE_INSENSITIVE),
            Pattern.compile("consumers\\s+of", Pattern.CASE_INSENSITIVE)
        ));
        
        // Configuration intent patterns
        INTENT_PATTERNS.put(IntentType.CONFIGURATION, Arrays.asList(
            Pattern.compile("config(uration)?\\s+(for|of)?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("properties\\s+(for|of)?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("settings\\s+(for|of)?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("parameters\\s+(for|of)?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("environment\\s+variables", Pattern.CASE_INSENSITIVE),
            Pattern.compile("application\\.yml", Pattern.CASE_INSENSITIVE),
            Pattern.compile("@Value|@ConfigurationProperties", Pattern.CASE_INSENSITIVE)
        ));
        
        // Discovery intent patterns
        INTENT_PATTERNS.put(IntentType.DISCOVERY, Arrays.asList(
            Pattern.compile("what\\s+handles", Pattern.CASE_INSENSITIVE),
            Pattern.compile("responsible\\s+for", Pattern.CASE_INSENSITIVE),
            Pattern.compile("manages", Pattern.CASE_INSENSITIVE),
            Pattern.compile("controls", Pattern.CASE_INSENSITIVE),
            Pattern.compile("orchestrates", Pattern.CASE_INSENSITIVE),
            Pattern.compile("processes", Pattern.CASE_INSENSITIVE),
            Pattern.compile("service\\s+for", Pattern.CASE_INSENSITIVE)
        ));
        
        // Status intent patterns
        INTENT_PATTERNS.put(IntentType.STATUS, Arrays.asList(
            Pattern.compile("status(es)?\\s+(of|for)?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("state(s)?\\s+(of|for)?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("condition(s)?\\s+(of|for)?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("progress\\s+(of|for)?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("phase(s)?\\s+(of|for)?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("workflow\\s+state", Pattern.CASE_INSENSITIVE),
            Pattern.compile("execution\\s+status", Pattern.CASE_INSENSITIVE)
        ));
    }
    
    // Context pattern definitions
    private static final Map<ContextType, List<Pattern>> CONTEXT_PATTERNS = new HashMap<>();
    
    static {
        // Scope indicators
        CONTEXT_PATTERNS.put(ContextType.SCOPE, Arrays.asList(
            Pattern.compile("\\ball\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bmain\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bspecific\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\brelated\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bevery\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bonly\\b", Pattern.CASE_INSENSITIVE)
        ));
        
        // Relationship indicators
        CONTEXT_PATTERNS.put(ContextType.RELATIONSHIP, Arrays.asList(
            Pattern.compile("connected\\s+to", Pattern.CASE_INSENSITIVE),
            Pattern.compile("part\\s+of", Pattern.CASE_INSENSITIVE),
            Pattern.compile("depends\\s+on", Pattern.CASE_INSENSITIVE),
            Pattern.compile("extends", Pattern.CASE_INSENSITIVE),
            Pattern.compile("implements", Pattern.CASE_INSENSITIVE),
            Pattern.compile("uses", Pattern.CASE_INSENSITIVE),
            Pattern.compile("inherits\\s+from", Pattern.CASE_INSENSITIVE)
        ));
        
        // Temporal indicators
        CONTEXT_PATTERNS.put(ContextType.TEMPORAL, Arrays.asList(
            Pattern.compile("\\brecent(ly)?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\blatest\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bcurrent(ly)?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bdeprecated\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bnew(est)?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bold(est)?\\b", Pattern.CASE_INSENSITIVE)
        ));
        
        // Quality indicators
        CONTEXT_PATTERNS.put(ContextType.QUALITY, Arrays.asList(
            Pattern.compile("best\\s+practice", Pattern.CASE_INSENSITIVE),
            Pattern.compile("performance", Pattern.CASE_INSENSITIVE),
            Pattern.compile("security", Pattern.CASE_INSENSITIVE),
            Pattern.compile("efficient", Pattern.CASE_INSENSITIVE),
            Pattern.compile("optimal", Pattern.CASE_INSENSITIVE),
            Pattern.compile("clean\\s+code", Pattern.CASE_INSENSITIVE)
        ));
    }

    public QueryIntentAnalyzer(LLMService llmService) {
        this.llmService = llmService;
    }

    /**
     * Analyzes a query to determine user intent and extract contextual information
     */
    public QueryIntent analyzeIntent(String query) {
        log.info("Analyzing intent for query: {}", query);
        
        // 1. Pattern-based intent detection
        // TODO: Check What is it doing?
        Map<IntentType, Double> intentScores = detectIntentsWithPatterns(query);
        
        // 2. Context extraction
        // TODO: Check What is it doing?
        Map<ContextType, List<String>> contexts = extractContexts(query);
        
        // 3. LLM-based refinement for ambiguous cases
        // TODO: Check What is it doing?
        if (shouldUseLLMFallback(intentScores)) {
            intentScores = refineLLMIntent(query, intentScores);
        }
        
        // 4. Build QueryIntent result
        return buildQueryIntent(query, intentScores, contexts);
    }
    
    /**
     * Detects intents using regex patterns
     */
    private Map<IntentType, Double> detectIntentsWithPatterns(String query) {
        Map<IntentType, Double> scores = new HashMap<>();
        
        for (Map.Entry<IntentType, List<Pattern>> entry : INTENT_PATTERNS.entrySet()) {
            IntentType intent = entry.getKey();
            List<Pattern> patterns = entry.getValue();
            
            double maxScore = 0.0;
            for (Pattern pattern : patterns) {
                if (pattern.matcher(query).find()) {
                    // Calculate score based on pattern match strength
                    double score = calculatePatternScore(query, pattern);
                    maxScore = Math.max(maxScore, score);
                }
            }
            
            if (maxScore > 0) {
                scores.put(intent, maxScore);
            }
        }
        
        // Normalize scores
        if (!scores.isEmpty()) {
            double totalScore = scores.values().stream().mapToDouble(Double::doubleValue).sum();
            scores.replaceAll((k, v) -> v / totalScore);
        }
        
        return scores;
    }
    
    /**
     * Calculates pattern match score
     */
    private double calculatePatternScore(String query, Pattern pattern) {
        // Base score for match
        double score = 0.8;
        
        // Boost if pattern matches at the beginning of the query
        if (pattern.matcher(query).find() && query.toLowerCase().startsWith(pattern.pattern().toLowerCase())) {
            score += 0.2;
        }
        
        return score;
    }
    
    /**
     * Extracts contextual information from the query
     */
    private Map<ContextType, List<String>> extractContexts(String query) {
        Map<ContextType, List<String>> contexts = new HashMap<>();
        
        for (Map.Entry<ContextType, List<Pattern>> entry : CONTEXT_PATTERNS.entrySet()) {
            ContextType contextType = entry.getKey();
            List<Pattern> patterns = entry.getValue();
            
            List<String> matches = new ArrayList<>();
            for (Pattern pattern : patterns) {
                var matcher = pattern.matcher(query);
                while (matcher.find()) {
                    matches.add(matcher.group());
                }
            }
            
            if (!matches.isEmpty()) {
                contexts.put(contextType, matches);
            }
        }
        
        return contexts;
    }
    
    /**
     * Determines if LLM fallback should be used
     */
    private boolean shouldUseLLMFallback(Map<IntentType, Double> intentScores) {
        if (!llmFallbackEnabled) {
            return false;
        }
        
        // Use LLM if no clear intent detected
        if (intentScores.isEmpty()) {
            return true;
        }
        
        // Use LLM if highest confidence is below threshold
        double maxConfidence = intentScores.values().stream()
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(0.0);
            
        return maxConfidence < confidenceThreshold;
    }
    
    /**
     * Refines intent detection using LLM
     */
    private Map<IntentType, Double> refineLLMIntent(String query, Map<IntentType, Double> patternScores) {
        try {
            String prompt = buildLLMIntentPrompt(query);
            String response = llmService.analyzeText(prompt);
            
            // Parse LLM response and merge with pattern scores
            Map<IntentType, Double> llmScores = parseLLMIntentResponse(response);
            
            // Combine scores: 60% LLM, 40% patterns
            Map<IntentType, Double> combinedScores = new HashMap<>();
            Set<IntentType> allIntents = new HashSet<>();
            allIntents.addAll(patternScores.keySet());
            allIntents.addAll(llmScores.keySet());
            
            for (IntentType intent : allIntents) {
                double patternScore = patternScores.getOrDefault(intent, 0.0);
                double llmScore = llmScores.getOrDefault(intent, 0.0);
                combinedScores.put(intent, (llmScore * 0.6) + (patternScore * 0.4));
            }
            
            return combinedScores;
            
        } catch (Exception e) {
            log.warn("LLM intent refinement failed, falling back to pattern scores", e);
            return patternScores;
        }
    }
    
    /**
     * Builds prompt for LLM intent analysis
     */
    private String buildLLMIntentPrompt(String query) {
        return """
            Analyze the following code search query and identify the user's intent.
            
            Query: "%s"
            
            Classify the intent into one or more of these categories:
            - IMPLEMENTATION: User wants to understand how something works, see code implementation
            - USAGE: User wants to find where something is used, dependencies, references
            - CONFIGURATION: User is looking for configuration, properties, settings
            - DISCOVERY: User wants to discover what components handle certain functionality
            - STATUS: User is interested in states, statuses, conditions, workflow phases
            
            Provide confidence scores (0.0-1.0) for each applicable intent.
            Format: INTENT_TYPE:SCORE, one per line.
            """.formatted(query);
    }
    
    /**
     * Parses LLM response for intent scores
     */
    private Map<IntentType, Double> parseLLMIntentResponse(String response) {
        Map<IntentType, Double> scores = new HashMap<>();
        
        String[] lines = response.split("\n");
        for (String line : lines) {
            if (line.contains(":")) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    try {
                        IntentType intent = IntentType.valueOf(parts[0].trim());
                        double score = Double.parseDouble(parts[1].trim());
                        scores.put(intent, Math.min(1.0, Math.max(0.0, score)));
                    } catch (Exception e) {
                        // Skip invalid lines
                    }
                }
            }
        }
        
        return scores;
    }
    
    /**
     * Builds final QueryIntent object
     */
    private QueryIntent buildQueryIntent(String query, Map<IntentType, Double> intentScores, 
                                       Map<ContextType, List<String>> contexts) {
        
        // Determine primary intent
        IntentType primaryIntent = intentScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(IntentType.DISCOVERY); // Default fallback
        
        // Get secondary intents if multi-intent detection is enabled
        List<IntentType> secondaryIntents = new ArrayList<>();
        if (multiIntentDetection) {
            secondaryIntents = intentScores.entrySet().stream()
                .filter(e -> e.getKey() != primaryIntent)
                .filter(e -> e.getValue() >= confidenceThreshold * 0.7) // 70% of threshold
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        }
        
        return QueryIntent.builder()
            .originalQuery(query)
            .primaryIntent(primaryIntent)
            .secondaryIntents(secondaryIntents)
            .intentScores(intentScores)
            .contexts(contexts)
            .confidence(intentScores.getOrDefault(primaryIntent, 0.0))
            .build();
    }

    /**
     * Intent types
     */
    public enum IntentType {
        IMPLEMENTATION,
        USAGE,
        CONFIGURATION,
        DISCOVERY,
        STATUS
    }
    
    /**
     * Context types
     */
    public enum ContextType {
        SCOPE,
        RELATIONSHIP,
        TEMPORAL,
        QUALITY
    }

    /**
     * Query intent analysis result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryIntent {
        private String originalQuery;
        private IntentType primaryIntent;
        private List<IntentType> secondaryIntents;
        private Map<IntentType, Double> intentScores;
        private Map<ContextType, List<String>> contexts;
        private double confidence;
        
        /**
         * Gets focus areas based on intent
         */
        public SearchFocus getSearchFocus() {
            return switch (primaryIntent) {
                case IMPLEMENTATION -> new SearchFocus(true, true, false, false);
                case USAGE -> new SearchFocus(false, false, true, true);
                case CONFIGURATION -> new SearchFocus(false, false, false, true);
                case DISCOVERY -> new SearchFocus(false, true, true, false);
                case STATUS -> new SearchFocus(false, true, false, false);
            };
        }
    }
    
    /**
     * Search focus areas based on intent
     */
    @Data
    @AllArgsConstructor
    public static class SearchFocus {
        private boolean methodBodies;
        private boolean descriptions;
        private boolean relationships;
        private boolean configurations;
    }
}