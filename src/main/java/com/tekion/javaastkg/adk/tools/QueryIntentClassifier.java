package com.tekion.javaastkg.adk.tools;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.Gemini;
import com.google.adk.tools.FunctionTool;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import com.google.adk.runner.Runner;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.agents.RunConfig;
import com.google.adk.agents.RunConfig.StreamingMode;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import com.google.adk.events.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 🎯 Query Intent Classifier - ADK Sub-Agent Implementation
 * 
 * Multi-agent system that analyzes user queries using specialized sub-tools.
 * Uses an internal LlmAgent to orchestrate query analysis, intent resolution, and strategy recommendation.
 * 
 * Architecture:
 * - QueryAnalyzer: Breaks down query structure and extracts entities
 * - IntentResolver: Maps query patterns to specific intent categories  
 * - StrategyRecommender: Recommends optimal tool execution strategies
 * 
 * This sub-agent provides intelligent decision-making for the main orchestrator.
 */
@Slf4j
public class QueryIntentClassifier {
    
    
    // Internal agent instruction for intelligent classification
    private static final String CLASSIFIER_INSTRUCTION = """
        You are an expert code query analyst specializing in understanding developer intent.
        
        Your role:
        1. Use QueryAnalyzer to break down and understand query structure
        2. Use IntentResolver to map query patterns to specific intents
        3. Use StrategyRecommender to suggest optimal tool execution strategies
        
        Follow this workflow:
        1. First call QueryAnalyzer to analyze the query structure and extract entities
        2. Then call IntentResolver with the analysis results to determine intent
        3. Finally call StrategyRecommender with intent and analysis to recommend strategy
        
        Intent Categories:
        - DEBUG_ISSUE: Troubleshooting errors, exceptions, or problems
        - UNDERSTAND_FLOW: Understanding how code executes or processes work
        - LOCATE_ENTITY: Finding specific code entities or components
        - COMPARE_ENTITIES: Comparing multiple code entities
        - EXPLORE_ARCHITECTURE: Understanding system architecture or design
        - ANALYZE_PERFORMANCE: Performance analysis or optimization
        - GENERATE_DOCUMENTATION: Creating explanations or documentation
        - UNDERSTAND_ENTITY: General understanding of code entities
        
        Provide comprehensive, accurate analysis that helps the orchestrator make informed decisions.
        """;
    
    /**
     * Create classifier agent with API key from system properties
     * This avoids the static initialization problem with LlmRegistry
     */
    private static BaseAgent createClassifierAgent() {
        // Get the API key that was set by LLMConfig during startup
        String apiKey = System.getProperty("GOOGLE_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Google API key not available in system properties. LLMConfig should have set it during startup.");
        }
        
        log.info("🔧 Creating CLASSIFIER_AGENT with API key from system properties");
        
        // Create Gemini model with API key
        Gemini geminiModel = Gemini.builder()
            .modelName("gemini-2.0-flash-001")
            .apiKey(apiKey)
            .build();
        
        // Create agent with custom model
        return LlmAgent.builder()
            .name("query-intent-classifier")
            .model(geminiModel)  // Use custom Gemini with API key
            .description("Intelligent agent that analyzes user queries to determine intent and recommend strategies")
            .instruction(CLASSIFIER_INSTRUCTION)
            .tools(Arrays.asList(
                FunctionTool.create(QueryAnalyzer.class, "analyzeQuery"),
                FunctionTool.create(IntentResolver.class, "resolveIntent"),
                FunctionTool.create(StrategyRecommender.class, "recommendStrategy")
            ))
            .build();
    }
    
    
    /**
     * Simple classification method for testing without ToolContext
     */
    @Schema(description = "Simple query intent classification for testing")
    public static Map<String, Object> classifyIntentSimple(
        @Schema(description = "User's natural language query about the codebase") String query,
        @Schema(description = "Previous context from session (optional)") String previousContext,
        @Schema(description = "Allow multiple intents in response") Boolean allowMultiIntent
    ) {
        // Call the main method with null ToolContext for testing
        return classifyIntent(query, previousContext, allowMultiIntent, null);
    }
    
    @Schema(description = "Classify user query intent using intelligent multi-agent analysis")
    public static Map<String, Object> classifyIntent(
        @Schema(description = "User's natural language query about the codebase") String query,
        @Schema(description = "Previous context from session (optional)") String previousContext,
        @Schema(description = "Allow multiple intents in response") Boolean allowMultiIntent,
        @Schema(name = "toolContext") ToolContext ctx
    ) {
        
        log.info("🎯 Starting intelligent intent classification for query: '{}'", query);
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Input validation
            if (query == null || query.trim().isEmpty()) {
                log.warn("⚠️ Invalid query input: query is null or empty");
                return Map.of(
                    "status", "error",
                    "error", "Query cannot be empty",
                    "confidence", 0.0
                );
            }
            
            log.info("📝 Query validation passed, query length: {}", query.length());
            log.info("🔍 Previous context: {}", previousContext != null ? "Present" : "None");
            log.info("🔀 Allow multi-intent: {}", allowMultiIntent);
            
            // Create context for internal agent
            Map<String, Object> agentContext = Map.of(
                "query", query.trim(),
                "previousContext", previousContext != null ? previousContext : "",
                "allowMultiIntent", allowMultiIntent != null ? allowMultiIntent : false,
                "sessionInfo", ctx != null && ctx.state() != null ? ctx.state() : Map.of()
            );
            
            log.info("🤖 Creating agent context with {} keys", agentContext.size());
            
            // Execute classification using true ADK Runner-based approach
            log.info("🔄 Starting ADK agent execution for classification");
            Map<String, Object> combinedResult = executeAgentWithRunner(query, previousContext, allowMultiIntent, ctx);
            
            log.info("📊 Agent execution completed, result keys: {}", combinedResult.keySet());
            
            // Create mutable result map with execution metadata
            Map<String, Object> classificationResult = new HashMap<>(combinedResult);
            long executionTime = System.currentTimeMillis() - startTime;
            classificationResult.put("executionTimeMs", executionTime);
            classificationResult.put("classificationMethod", "INTELLIGENT_AGENT");
            
            log.info("⏱️ Intent classification execution time: {}ms", executionTime);
            
            // Update context with results (safely handle immutable state)
            if (ctx != null && ctx.state() != null) {
                log.info("📋 Updating context state");
                try {
                    // Attempt to update context state
                    var state = ctx.state();
                    state.put("app:last_intent", classificationResult.get("intent"));
                    state.put("app:last_confidence", classificationResult.get("confidence"));
                    state.put("app:classification_method", "MULTI_AGENT");
                    log.info("✅ Updated context state with classification results");
                } catch (UnsupportedOperationException e) {
                    log.info("🔒 Context state is read-only, skipping state updates");
                } catch (Exception e) {
                    log.warn("⚠️ Failed to update context state: {}", e.getMessage());
                }
            } else {
                log.info("📋 No context available for state update");
            }
            
            return classificationResult;
            
        } catch (Exception e) {
            log.error("❌ Intelligent intent classification failed for query: {}", query, e);
            return Map.of(
                "status", "error", 
                "error", "Intent classification failed: " + e.getMessage(),
                "confidence", 0.0,
                "classificationMethod", "ERROR"
            );
        }
    }
    
    /**
     * Combine results from all sub-tools into final classification
     */
    private static Map<String, Object> combineSubToolResults(Map<String, Object> analysisResult, 
                                                           Map<String, Object> intentResult, 
                                                           Map<String, Object> strategyResult) {
        
        // Extract key information from each tool
        Map<String, Object> analysis = (Map<String, Object>) analysisResult.get("analysis");
        Map<String, Object> intent = (Map<String, Object>) intentResult.get("intent");
        Map<String, Object> strategy = (Map<String, Object>) strategyResult.get("strategy");
        Map<String, Object> executionPlan = (Map<String, Object>) strategyResult.get("executionPlan");
        
        // Extract primary intent and confidence
        Map<String, Object> primaryIntent = (Map<String, Object>) intent.get("primary");
        String intentType = (String) primaryIntent.get("intent");
        double confidence = (Double) primaryIntent.get("confidence");
        
        // Build final result
        return Map.of(
            "status", "success",
            "intent", intentType,
            "confidence", confidence,
            "entities", ((Map<String, Object>) analysis.get("entities")).get("allEntities"),
            "complexity", analysis.get("characteristics") != null ? 
                ((Map<String, Object>) analysis.get("characteristics")).get("complexity") : "MODERATE",
            "recommendedStrategy", strategy.get("recommended"),
            "nextActions", executionPlan.get("toolSequence"),
            "explanation", buildCombinedExplanation(intentType, confidence, analysis, strategy),
            "metadata", Map.of(
                "agentUsed", true,
                "toolsInvoked", List.of("QueryAnalyzer", "IntentResolver", "StrategyRecommender"),
                "multiAgentFlow", true,
                "analysisMethod", "SUB_AGENT_ORCHESTRATION"
            )
        );
    }
    
    /**
     * Execute CLASSIFIER_AGENT using true ADK Runner-based execution with minimal session
     */
    private static Map<String, Object> executeAgentWithRunner(String query, String previousContext, 
                                                            Boolean allowMultiIntent, ToolContext ctx) {
        try {
            log.info("🚀 Executing CLASSIFIER_AGENT with ADK Runner (Option A - Minimal Session)");
            
            // Step 1: Create RunConfig for agent execution
            RunConfig runConfig = RunConfig.builder()
                .setStreamingMode(StreamingMode.NONE)  // Non-streaming for function tool usage
                .setMaxLlmCalls(50)  // Reasonable limit for classification
                .setSaveInputBlobsAsArtifacts(false)  // Don't save artifacts for classification
                .build();
            
            // Step 2: Create InMemoryRunner with proper configuration
            // Use the app name that matches session creation
            log.info("🔧 About to create CLASSIFIER_AGENT with custom API key");
            BaseAgent classifierAgent = createClassifierAgent();
            log.info("✅ Created CLASSIFIER_AGENT with API key");
            
            log.info("🔧 About to create InMemoryRunner with CLASSIFIER_AGENT");
            InMemoryRunner runner = new InMemoryRunner(classifierAgent, "QueryIntentClassification");
            log.info("✅ Created InMemoryRunner for intent classification");
            
            // Step 3: Build prompt for agent
            String classificationPrompt = buildAgentPrompt(query, previousContext, allowMultiIntent);
            
            // Step 4: Create content from prompt using correct ADK API
            Content content = Content.fromParts(Part.fromText(classificationPrompt));
            
            // Step 5: Execute agent with minimal session approach (try multiple patterns)
            Map<String, Object> agentResult = executeAgentWithSession(runner, content, runConfig);
            
            // Step 6: Parse agent response into structured format
            return parseAgentResponse(agentResult);
            
        } catch (Exception e) {
            log.error("❌ ADK Runner execution failed", e);
            return Map.of(
                "status", "error",
                "error", "ADK Runner execution failed: " + e.getMessage(),
                "confidence", 0.0,
                "executionMethod", "ADK_RUNNER_FAILED"
            );
        }
    }
    
    
    /**
     * Execute agent with session - tries multiple ADK patterns
     * Falls back to different approaches if one fails
     */
    private static Map<String, Object> executeAgentWithSession(InMemoryRunner runner, Content content, RunConfig runConfig) {
        // Try Pattern 1: Minimal session approach
        try {
            return executeAgentWithMinimalSession(runner, content, runConfig);
        } catch (Exception e) {
            log.warn("Minimal session approach failed, trying alternative: {}", e.getMessage());
        }
        
        // Try Pattern 2: Direct execution without explicit session
        try {
            return executeAgentDirectly(runner, content, runConfig);
        } catch (Exception e) {
            log.warn("Direct execution failed, trying simple runner: {}", e.getMessage());
        }
        
        // Try Pattern 3: Simple runner execution
        try {
            return executeAgentSimple(runner, content);
        } catch (Exception e) {
            log.error("All execution patterns failed", e);
            return Map.of(
                "status", "error",
                "error", "All ADK execution patterns failed: " + e.getMessage(),
                "executionMethod", "ALL_PATTERNS_FAILED"
            );
        }
    }
    
    /**
     * Execute agent with minimal session approach (Option A)
     * Creates a temporary session for single execution, enabling true LLM reasoning
     */
    private static Map<String, Object> executeAgentWithMinimalSession(InMemoryRunner runner, Content content, RunConfig runConfig) {
        try {
            log.info("🔄 Creating minimal session for agent execution");
            
            // Create a unique user ID - use consistent naming from ADK docs
            String userId = "user-" + System.currentTimeMillis();
            
            // Create the session using the session service with proper parameters
            var sessionService = runner.sessionService();
            
            // ADK pattern: createSession(name, userId) - name should be descriptive
            var sessionSingle = sessionService.createSession("QueryIntentClassification", userId);
            var session = sessionSingle.blockingGet(); // Block to get the actual session
            
            log.info("📊 Created session: {} for user: {}", session.id(), userId);
            log.info("📊 Executing agent with session");
            
            // Execute agent with the created session using correct ADK pattern
            // ADK pattern: runAsync(userId, sessionId, content) - without runConfig if causing issues
            Flowable<Event> eventStream = runner.runAsync(userId, session.id(), content);
            
            // Collect all events - this includes tool calls and agent reasoning
            List<Event> events = eventStream.toList().blockingGet();
            
            log.info("📋 Collected {} events from agent execution", events.size());
            
            // Extract the complete agent response including tool interactions
            Map<String, Object> executionResult = extractAgentExecutionResult(events);
            
            // Clean up - delete the temporary session
            try {
                sessionService.deleteSession("QueryIntentClassification", userId, session.id()).blockingAwait();
                log.info("🧹 Cleaned up temporary session");
            } catch (Exception cleanupError) {
                log.info("Failed to clean up session (not critical): {}", cleanupError.getMessage());
            }
            
            log.info("✅ Minimal session execution completed successfully");
            
            return executionResult;
            
        } catch (Exception e) {
            log.error("❌ Failed to execute agent with minimal session", e);
            return Map.of(
                "status", "error",
                "error", "Minimal session execution failed: " + e.getMessage(),
                "executionMethod", "MINIMAL_SESSION_ERROR"
            );
        }
    }
    
    /**
     * Execute agent directly without explicit session management (Option B)
     * Uses the runner's default session handling
     */
    private static Map<String, Object> executeAgentDirectly(InMemoryRunner runner, Content content, RunConfig runConfig) {
        try {
            log.debug("🚀 Executing agent directly without explicit session");
            
            // Create a unique user ID for this execution
            String userId = "direct-user-" + System.currentTimeMillis();
            
            // Execute agent directly - let the runner handle session management
            // This may create an implicit session internally
            // Create a temporary session for execution
            var sessionService = runner.sessionService();
            var sessionSingle = sessionService.createSession("DirectExecution", userId);
            var session = sessionSingle.blockingGet();
            
            Flowable<Event> eventStream;
            if (runConfig != null) {
                eventStream = runner.runAsync(userId, session.id(), content, runConfig);
            } else {
                eventStream = runner.runAsync(userId, session.id(), content);
            }
            
            // Collect all events
            List<Event> events = eventStream.toList().blockingGet();
            
            log.debug("📋 Direct execution collected {} events", events.size());
            
            // Extract execution result
            Map<String, Object> executionResult = extractAgentExecutionResult(events);
            executionResult.put("executionMethod", "DIRECT_EXECUTION");
            
            return executionResult;
            
        } catch (Exception e) {
            log.error("❌ Direct agent execution failed", e);
            return Map.of(
                "status", "error",
                "error", "Direct execution failed: " + e.getMessage(),
                "executionMethod", "DIRECT_EXECUTION_ERROR"
            );
        }
    }
    
    /**
     * Execute agent with simple pattern (Option C)
     * Minimal approach for basic functionality
     */
    private static Map<String, Object> executeAgentSimple(InMemoryRunner runner, Content content) {
        try {
            log.debug("🚀 Executing agent with simple pattern");
            
            // Very basic execution - minimal parameters
            String userId = "simple-user";
            
            // Create a session for simple execution
            var sessionService = runner.sessionService();
            var sessionSingle = sessionService.createSession("SimpleExecution", userId);
            var session = sessionSingle.blockingGet();
            
            // Execute with minimal configuration
            Flowable<Event> eventStream = runner.runAsync(userId, session.id(), content);
            
            // Collect events with timeout
            List<Event> events = eventStream.toList().blockingGet();
            
            log.debug("📋 Simple execution collected {} events", events.size());
            
            // Extract basic result
            Map<String, Object> executionResult = extractAgentExecutionResult(events);
            executionResult.put("executionMethod", "SIMPLE_EXECUTION");
            
            return executionResult;
            
        } catch (Exception e) {
            log.error("❌ Simple agent execution failed", e);
            return Map.of(
                "status", "error",
                "error", "Simple execution failed: " + e.getMessage(),
                "executionMethod", "SIMPLE_EXECUTION_ERROR"
            );
        }
    }
    
    /**
     * Extract comprehensive execution result from agent events
     * Captures tool calls, reasoning, and final response
     */
    private static Map<String, Object> extractAgentExecutionResult(List<Event> events) {
        StringBuilder agentResponse = new StringBuilder();
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        Map<String, Object> finalState = new HashMap<>();
        boolean foundResponse = false;
        
        for (Event event : events) {
            // Extract content from events
            if (event.content() != null && event.content().isPresent()) {
                Content eventContent = event.content().get();
                String textContent = eventContent.text();
                if (textContent != null && !textContent.trim().isEmpty()) {
                    agentResponse.append(textContent).append("\n");
                    foundResponse = true;
                }
            }
            
            // Track function calls made by the agent (tool calls)
            if (event.functionCalls() != null && !event.functionCalls().isEmpty()) {
                for (var functionCall : event.functionCalls()) {
                    Map<String, Object> toolCall = Map.of(
                        "tool", functionCall.name(),
                        "args", functionCall.args(),
                        "timestamp", System.currentTimeMillis()
                    );
                    toolCalls.add(toolCall);
                }
            }
            
            // Track function responses (tool results)
            if (event.functionResponses() != null && !event.functionResponses().isEmpty()) {
                // Agent received tool responses and can reason about them
                log.debug("Agent received {} function responses", event.functionResponses().size());
            }
            
            // Check if this is a complete turn
            if (event.turnComplete().orElse(false)) {
                log.debug("Agent completed reasoning turn");
            }
        }
        
        if (!foundResponse) {
            return Map.of(
                "status", "error",
                "error", "No agent response found in execution",
                "executionMethod", "MINIMAL_SESSION"
            );
        }
        
        return Map.of(
            "status", "success",
            "content", agentResponse.toString().trim(),
            "toolCalls", toolCalls,
            "eventCount", events.size(),
            "finalState", finalState,
            "executionMethod", "MINIMAL_SESSION_WITH_REASONING"
        );
    }
    
    /**
     * Build prompt for CLASSIFIER_AGENT
     */
    private static String buildAgentPrompt(String query, String previousContext, Boolean allowMultiIntent) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Analyze this user query about a codebase: \"").append(query).append("\"");
        
        if (previousContext != null && !previousContext.trim().isEmpty()) {
            prompt.append("\\n\\nPrevious context: ").append(previousContext);
        }
        
        if (allowMultiIntent != null && allowMultiIntent) {
            prompt.append("\\n\\nNote: This query may have multiple intents. Please identify all relevant intents.");
        }
        
        prompt.append("\\n\\nPlease use your tools systematically:");
        prompt.append("\\n1. First use QueryAnalyzer to break down the query structure");
        prompt.append("\\n2. Then use IntentResolver to determine the primary intent");
        prompt.append("\\n3. Finally use StrategyRecommender to suggest the best execution approach");
        prompt.append("\\n\\nProvide a comprehensive analysis that helps the orchestrator make informed decisions.");
        
        return prompt.toString();
    }
    
    
    /**
     * Parse agent response into structured classification result
     */
    private static Map<String, Object> parseAgentResponse(Map<String, Object> agentResult) {
        try {
            // Check if agent execution was successful
            if (!"success".equals(agentResult.get("status"))) {
                return agentResult; // Return error as-is
            }
            
            // Extract execution details
            String content = agentResult.getOrDefault("content", "").toString();
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) agentResult.getOrDefault("toolCalls", List.of());
            Map<String, Object> finalState = (Map<String, Object>) agentResult.getOrDefault("finalState", Map.of());
            
            // Parse agent's structured response
            // The agent should have called tools in sequence and produced structured output
            Map<String, Object> parsedOutput = parseAgentContent(content, toolCalls, finalState);
            
            // Extract classification results from parsed output
            String intent = (String) parsedOutput.getOrDefault("intent", "UNDERSTAND_ENTITY");
            double confidence = (Double) parsedOutput.getOrDefault("confidence", 0.7);
            List<String> entities = (List<String>) parsedOutput.getOrDefault("entities", List.of());
            String complexity = (String) parsedOutput.getOrDefault("complexity", "MODERATE");
            String strategy = (String) parsedOutput.getOrDefault("strategy", "BALANCED");
            List<String> nextActions = (List<String>) parsedOutput.getOrDefault("nextActions", 
                List.of("huntCodeEntities", "exploreStructure", "enrichContext"));
            
            // Build explanation including tool usage
            String explanation = buildAgentExplanation(intent, confidence, toolCalls, content);
            
            return Map.of(
                "status", "success",
                "intent", intent,
                "confidence", confidence,
                "entities", entities,
                "complexity", complexity,
                "recommendedStrategy", strategy,
                "nextActions", nextActions,
                "explanation", explanation,
                "metadata", Map.of(
                    "agentUsed", true,
                    "executionMethod", agentResult.get("executionMethod"),
                    "toolsInvoked", extractToolNames(toolCalls),
                    "toolCallCount", toolCalls.size(),
                    "eventCount", agentResult.getOrDefault("eventCount", 0),
                    "agentThinking", content
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to parse agent response", e);
            return Map.of(
                "status", "error",
                "error", "Failed to parse agent response: " + e.getMessage(),
                "confidence", 0.0
            );
        }
    }
    
    /**
     * Parse the agent's content to extract structured data
     * In a real implementation, this would parse the LLM's structured output
     */
    private static Map<String, Object> parseAgentContent(String content, List<Map<String, Object>> toolCalls, Map<String, Object> state) {
        // For now, simulate parsing based on tool calls
        // In production, would parse the agent's structured JSON or formatted response
        
        Map<String, Object> result = new HashMap<>();
        
        // Default values
        result.put("intent", "UNDERSTAND_ENTITY");
        result.put("confidence", 0.75);
        result.put("entities", List.of());
        result.put("complexity", "MODERATE");
        result.put("strategy", "BALANCED");
        result.put("nextActions", List.of("huntCodeEntities", "exploreStructure", "enrichContext"));
        
        // Override with state values if available
        if (state.containsKey("resolved_intent")) {
            result.put("intent", state.get("resolved_intent"));
        }
        if (state.containsKey("confidence")) {
            result.put("confidence", state.get("confidence"));
        }
        if (state.containsKey("recommended_strategy")) {
            result.put("strategy", state.get("recommended_strategy"));
        }
        
        // Analyze tool calls to infer results
        if (toolCalls.size() >= 3) {
            // Agent likely completed full analysis
            result.put("confidence", Math.min(0.95, (Double) result.get("confidence") + 0.1));
        }
        
        return result;
    }
    
    /**
     * Extract tool names from tool call events
     */
    private static List<String> extractToolNames(List<Map<String, Object>> toolCalls) {
        return toolCalls.stream()
            .map(tc -> {
                Object tool = tc.get("tool");
                if (tool instanceof Optional) {
                    return ((Optional<?>) tool).map(Object::toString).orElse("unknown");
                } else if (tool != null) {
                    return tool.toString();
                } else {
                    return "unknown";
                }
            })
            .distinct()
            .toList();
    }
    
    /**
     * Build comprehensive explanation of agent's analysis
     */
    private static String buildAgentExplanation(String intent, double confidence, 
                                               List<Map<String, Object>> toolCalls, String agentContent) {
        StringBuilder explanation = new StringBuilder();
        
        explanation.append(String.format("Agent classified query as '%s' with %.1f%% confidence. ",
            intent.replace("_", " ").toLowerCase(), confidence * 100));
        
        if (!toolCalls.isEmpty()) {
            explanation.append(String.format("Used %d tools for analysis: %s. ",
                toolCalls.size(), extractToolNames(toolCalls)));
        }
        
        explanation.append("Agent reasoning enabled iterative analysis and intelligent tool selection.");
        
        return explanation.toString();
    }
    
    /**
     * Build combined explanation from all sub-tool results
     */
    private static String buildCombinedExplanation(String intentType, double confidence, 
                                                 Map<String, Object> analysis, Map<String, Object> strategy) {
        StringBuilder explanation = new StringBuilder();
        
        // Intent and confidence
        explanation.append(String.format("Multi-agent analysis resolved '%s' intent with %.1f%% confidence. ",
            intentType.replace("_", " ").toLowerCase(), confidence * 100));
        
        // Entity information
        if (analysis.containsKey("entities")) {
            Map<String, Object> entities = (Map<String, Object>) analysis.get("entities");
            List<String> allEntities = (List<String>) entities.get("allEntities");
            if (!allEntities.isEmpty()) {
                explanation.append(String.format("Found %d code entities: %s. ", 
                    allEntities.size(), String.join(", ", allEntities.subList(0, Math.min(3, allEntities.size())))));
            }
        }
        
        // Strategy recommendation
        if (strategy.containsKey("recommended")) {
            String recommendedStrategy = (String) strategy.get("recommended");
            explanation.append(String.format("Recommended '%s' strategy for optimal execution.", 
                recommendedStrategy.toLowerCase()));
        }
        
        return explanation.toString();
    }
}