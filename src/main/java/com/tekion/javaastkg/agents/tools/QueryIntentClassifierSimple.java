package com.tekion.javaastkg.agents.tools;

import com.tekion.javaastkg.agents.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 🎯 Simple Query Intent Classifier Tool - Working Version
 * 
 * @deprecated This Spring-based implementation is replaced by ADK FunctionTool.
 * Use com.tekion.javaastkg.adk.tools.QueryIntentClassifier instead.
 * 
 * Simplified version of the intent classifier that:
 * - Analyzes user queries to understand their intent
 * - Extracts basic code entities from queries  
 * - Provides basic execution strategy recommendations
 * 
 * Domain Focus: Understanding WHAT the user wants to know about their code
 */
@Deprecated(since = "2024-01", forRemoval = true)
@Component
@Slf4j
public class QueryIntentClassifierSimple {
    
    // Basic patterns for intent detection
    private static final Pattern WHAT_PATTERN = Pattern.compile("\\b(what|which)\\b.*\\b(is|are|does|do)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOW_PATTERN = Pattern.compile("\\bhow\\b.*\\b(does|do|works?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHERE_PATTERN = Pattern.compile("\\bwhere\\b.*\\b(is|are)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEBUG_PATTERN = Pattern.compile("\\b(error|exception|bug|issue|problem|fail)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPARE_PATTERN = Pattern.compile("\\b(compare|difference|versus|vs)\\b", Pattern.CASE_INSENSITIVE);
    
    // Basic entity patterns
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\b[A-Z][a-zA-Z0-9]*(?:Service|Controller|Repository|Manager|Handler)\\b");
    private static final Pattern METHOD_PATTERN = Pattern.compile("\\b[a-z][a-zA-Z0-9]*\\s*\\(\\s*\\)");
    
    /**
     * 🔍 Classify user query intent (simplified version)
     */
    public IntentClassificationResult classifyIntent(
            String query,
            String previousContext,
            Boolean allowMultiIntent
    ) {
        
        log.debug("🎯 Classifying intent for query: '{}'", query);
        
        try {
            // Step 1: Basic intent detection
            IntentType primaryIntent = detectPrimaryIntent(query);
            double confidence = calculateBasicConfidence(query, primaryIntent);
            
            // Step 2: Extract basic entities
            List<CodeEntity> entities = extractBasicEntities(query);
            
            // Step 3: Create basic strategy
            ExecutionStrategy strategy = createBasicStrategy(primaryIntent);
            
            // Step 4: Build result
            return IntentClassificationResult.builder()
                .intent(primaryIntent)
                .confidence(confidence)
                .entities(entities)
                .strategy(strategy)
                .explanation(buildBasicExplanation(primaryIntent, confidence, entities))
                .build();
                
        } catch (Exception e) {
            log.error("❌ Intent classification failed", e);
            
            return IntentClassificationResult.builder()
                .intent(IntentType.GENERAL_INQUIRY)
                .confidence(0.3)
                .entities(List.of())
                .strategy(ExecutionStrategy.defaultStrategy())
                .explanation("Failed to classify intent: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * 🎯 Detect primary intent using basic patterns
     */
    private IntentType detectPrimaryIntent(String query) {
        String lowerQuery = query.toLowerCase();
        
        // Check for debug patterns first (high priority)
        if (DEBUG_PATTERN.matcher(query).find()) {
            return IntentType.DEBUG_ISSUE;
        }
        
        // Check for comparison patterns
        if (COMPARE_PATTERN.matcher(query).find()) {
            return IntentType.COMPARE_IMPLEMENTATIONS;
        }
        
        // Check question types
        if (WHAT_PATTERN.matcher(query).find()) {
            return IntentType.UNDERSTAND_FUNCTIONALITY;
        }
        
        if (HOW_PATTERN.matcher(query).find()) {
            return IntentType.UNDERSTAND_FLOW;
        }
        
        if (WHERE_PATTERN.matcher(query).find()) {
            return IntentType.FIND_CODE_ENTITIES;
        }
        
        // Check for exploration keywords
        if (lowerQuery.contains("explore") || lowerQuery.contains("show") || lowerQuery.contains("find")) {
            return IntentType.EXPLORE_CODEBASE;
        }
        
        // Default to general inquiry
        return IntentType.GENERAL_INQUIRY;
    }
    
    /**
     * 📊 Calculate basic confidence score
     */
    private double calculateBasicConfidence(String query, IntentType intent) {
        double confidence = 0.5; // Base confidence
        
        // Boost for clear patterns
        if (DEBUG_PATTERN.matcher(query).find() && intent == IntentType.DEBUG_ISSUE) {
            confidence += 0.3;
        }
        
        if (WHAT_PATTERN.matcher(query).find() && intent == IntentType.UNDERSTAND_FUNCTIONALITY) {
            confidence += 0.2;
        }
        
        if (HOW_PATTERN.matcher(query).find() && intent == IntentType.UNDERSTAND_FLOW) {
            confidence += 0.2;
        }
        
        // Boost for specific code mentions
        if (CLASS_PATTERN.matcher(query).find() || METHOD_PATTERN.matcher(query).find()) {
            confidence += 0.1;
        }
        
        // Penalty for very short queries
        if (query.length() < 10) {
            confidence -= 0.2;
        }
        
        return Math.max(0.0, Math.min(1.0, confidence));
    }
    
    /**
     * 🔍 Extract basic code entities
     */
    private List<CodeEntity> extractBasicEntities(String query) {
        List<CodeEntity> entities = new ArrayList<>();
        
        // Extract class names
        var classMatcher = CLASS_PATTERN.matcher(query);
        while (classMatcher.find()) {
            entities.add(CodeEntity.builder()
                .name(classMatcher.group())
                .type(EntityType.CLASS)
                .context("Found in query")
                .confidence(0.8)
                .build());
        }
        
        // Extract method calls
        var methodMatcher = METHOD_PATTERN.matcher(query);
        while (methodMatcher.find()) {
            String methodCall = methodMatcher.group();
            String methodName = methodCall.replaceAll("\\s*\\(\\s*\\)", "");
            entities.add(CodeEntity.builder()
                .name(methodName)
                .type(EntityType.METHOD)
                .context("Method call in query")
                .confidence(0.9)
                .build());
        }
        
        return entities;
    }
    
    /**
     * 🎲 Create basic execution strategy
     */
    private ExecutionStrategy createBasicStrategy(IntentType intent) {
        return switch (intent) {
            case DEBUG_ISSUE, TRACE_ERROR -> ExecutionStrategy.surgical();
            case UNDERSTAND_FLOW, UNDERSTAND_ARCHITECTURE -> ExecutionStrategy.exploratory();
            case EXPLORE_CODEBASE -> ExecutionStrategy.exploratory();
            default -> ExecutionStrategy.defaultStrategy();
        };
    }
    
    /**
     * 📝 Build basic explanation
     */
    private String buildBasicExplanation(IntentType intent, double confidence, List<CodeEntity> entities) {
        StringBuilder explanation = new StringBuilder();
        explanation.append("Intent Classification Result:\n");
        explanation.append("• Primary Intent: ").append(intent.getDescription()).append("\n");
        explanation.append("• Confidence: ").append(String.format("%.1f%%", confidence * 100)).append("\n");
        
        if (!entities.isEmpty()) {
            explanation.append("• Code Entities Found: ").append(entities.size()).append("\n");
            entities.forEach(entity -> 
                explanation.append("  - ").append(entity.getType()).append(": ").append(entity.getName()).append("\n"));
        }
        
        return explanation.toString();
    }
}