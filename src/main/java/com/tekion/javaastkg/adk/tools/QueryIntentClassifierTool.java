package com.tekion.javaastkg.adk.tools;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import com.tekion.javaastkg.adk.agents.QueryIntentClassifierAgent;
import com.tekion.javaastkg.adk.core.BaseAdkTool;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 🎯 Query Intent Classifier Tool - FunctionTool Wrapper
 * 
 * Provides a FunctionTool interface for the QueryIntentClassifierAgent.
 * This wrapper allows the agent to be used as a tool in other agents while
 * maintaining clean separation of concerns.
 * 
 * This tool:
 * - Wraps the QueryIntentClassifierAgent for use as a FunctionTool
 * - Provides consistent interface with other tools
 * - Maintains backward compatibility
 * - Handles context properly
 * 
 * Used by higher-level orchestrator agents that need intent classification.
 */
@Slf4j
public class QueryIntentClassifierTool extends BaseAdkTool {
    
    // Tool configuration constants
    private static final String TOOL_NAME = "QueryIntentClassifier";
    private static final String OPERATION_CLASSIFY = "classify_intent";
    
    /**
     * Classify user query intent using intelligent multi-agent analysis
     * 
     * @param query User's natural language query about the codebase
     * @param previousContext Previous context from session (optional)
     * @param allowMultiIntent Allow multiple intents in response
     * @param ctx Tool context from ADK framework
     * @return Structured classification result
     */
    @Schema(description = "Classify user query intent using intelligent multi-agent analysis")
    public static Map<String, Object> classifyIntent(
        @Schema(description = "User's natural language query about the codebase") String query,
        @Schema(description = "Previous context from session (optional)") String previousContext,
        @Schema(description = "Allow multiple intents in response") Boolean allowMultiIntent,
        @Schema(name = "toolContext") ToolContext ctx
    ) {
        long startTime = System.currentTimeMillis();
        logToolStart(TOOL_NAME, OPERATION_CLASSIFY);
        
        try {
            // Input validation
            Optional<Map<String, Object>> validationError = validateRequired("query", query, OPERATION_CLASSIFY);
            if (validationError.isPresent()) {
                return validationError.get();
            }
            
            // Extract tool context
            Map<String, Object> toolContext = extractToolContext(ctx);
            
            // Get agent instance and execute classification
            QueryIntentClassifierAgent agent = QueryIntentClassifierAgent.getInstance();
            Map<String, Object> result = agent.classifyIntent(query, previousContext, allowMultiIntent);
            
            long executionTime = System.currentTimeMillis() - startTime;
            logToolComplete(TOOL_NAME, OPERATION_CLASSIFY, executionTime);
            
            // Enhance result with tool metadata
            result.put("toolName", TOOL_NAME);
            result.put("toolContext", toolContext);
            
            // Update execution time if not already set
            if (!result.containsKey("executionTimeMs")) {
                result.put("executionTimeMs", executionTime);
            }
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            String errorMessage = "Intent classification failed: " + e.getMessage();
            logToolError(TOOL_NAME, OPERATION_CLASSIFY, errorMessage);
            
            return errorResponse(OPERATION_CLASSIFY, errorMessage, Map.of(
                "exception", e.getClass().getSimpleName(),
                "executionTime", executionTime
            ));
        }
    }
    
    /**
     * Simple classification method for testing without ToolContext
     * 
     * @param query User's natural language query about the codebase
     * @param previousContext Previous context from session (optional)
     * @param allowMultiIntent Allow multiple intents in response
     * @return Structured classification result
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
    
    /**
     * Get agent configuration for monitoring and debugging
     * 
     * @param ctx Tool context from ADK framework
     * @return Agent configuration details
     */
    @Schema(description = "Get agent configuration for monitoring and debugging")
    public static Map<String, Object> getAgentConfiguration(
        @Schema(name = "toolContext") ToolContext ctx
    ) {
        long startTime = System.currentTimeMillis();
        logToolStart(TOOL_NAME, "get_configuration");
        
        try {
            QueryIntentClassifierAgent agent = QueryIntentClassifierAgent.getInstance();
            Map<String, Object> config = agent.getAgentConfiguration();
            
            long executionTime = System.currentTimeMillis() - startTime;
            logToolComplete(TOOL_NAME, "get_configuration", executionTime);
            
            return successResponse("get_configuration", config);
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            String errorMessage = "Failed to get agent configuration: " + e.getMessage();
            logToolError(TOOL_NAME, "get_configuration", errorMessage);
            
            return errorResponse("get_configuration", errorMessage, Map.of(
                "exception", e.getClass().getSimpleName(),
                "executionTime", executionTime
            ));
        }
    }
    
    /**
     * Get agent statistics for monitoring
     * 
     * @param ctx Tool context from ADK framework
     * @return Agent statistics
     */
    @Schema(description = "Get agent statistics for monitoring")
    public static Map<String, Object> getAgentStats(
        @Schema(name = "toolContext") ToolContext ctx
    ) {
        long startTime = System.currentTimeMillis();
        logToolStart(TOOL_NAME, "get_stats");
        
        try {
            QueryIntentClassifierAgent agent = QueryIntentClassifierAgent.getInstance();
            Map<String, Object> stats = agent.getAgentStats();
            
            long executionTime = System.currentTimeMillis() - startTime;
            logToolComplete(TOOL_NAME, "get_stats", executionTime);
            
            return successResponse("get_stats", stats);
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            String errorMessage = "Failed to get agent statistics: " + e.getMessage();
            logToolError(TOOL_NAME, "get_stats", errorMessage);
            
            return errorResponse("get_stats", errorMessage, Map.of(
                "exception", e.getClass().getSimpleName(),
                "executionTime", executionTime
            ));
        }
    }
}