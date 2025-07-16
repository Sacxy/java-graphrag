package com.tekion.javaastkg.adk.agents;

import com.google.adk.agents.BaseAgent;
import com.google.adk.tools.FunctionTool;
import com.tekion.javaastkg.adk.core.BaseAdkAgent;
import com.tekion.javaastkg.adk.core.AgentFactory;
import com.tekion.javaastkg.adk.tools.QueryAnalyzer;
import com.tekion.javaastkg.adk.tools.IntentResolver;
import com.tekion.javaastkg.adk.tools.StrategyRecommender;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 🎯 Query Intent Classifier Agent - Refactored ADK Implementation
 * 
 * Multi-agent system that analyzes user queries using specialized sub-tools.
 * This agent orchestrates query analysis, intent resolution, and strategy recommendation
 * using proper ADK patterns and clean architecture.
 * 
 * Architecture:
 * - Extends BaseAdkAgent for consistent ADK patterns
 * - Uses AgentFactory for standardized agent creation
 * - Leverages sub-tools: QueryAnalyzer, IntentResolver, StrategyRecommender
 * - Provides context-aware analysis with proper error handling
 * 
 * Intent Categories:
 * - DEBUG_ISSUE: Troubleshooting errors, exceptions, or problems
 * - UNDERSTAND_FLOW: Understanding how code executes or processes work
 * - LOCATE_ENTITY: Finding specific code entities or components
 * - COMPARE_ENTITIES: Comparing multiple code entities
 * - EXPLORE_ARCHITECTURE: Understanding system architecture or design
 * - ANALYZE_PERFORMANCE: Performance analysis or optimization
 * - GENERATE_DOCUMENTATION: Creating explanations or documentation
 * - UNDERSTAND_ENTITY: General understanding of code entities
 */
@Slf4j
public class QueryIntentClassifierAgent extends BaseAdkAgent {
    
    // Agent configuration constants
    private static final String AGENT_NAME = "query-intent-classifier";
    private static final String AGENT_DESCRIPTION = "Intelligent agent that analyzes user queries to determine intent and recommend strategies";
    
    // Agent instruction template
    private static final String AGENT_INSTRUCTION = """
        You are an expert code query analyst specializing in understanding developer intent.
        
        Your role is to analyze user queries about codebases and provide intelligent classification:
        
        **Workflow:**
        1. First call QueryAnalyzer to break down and understand query structure
        2. Then call IntentResolver with the analysis results to determine intent
        3. Finally call StrategyRecommender with intent and analysis to recommend strategy
        
        **Intent Categories:**
        - DEBUG_ISSUE: Troubleshooting errors, exceptions, or problems
        - UNDERSTAND_FLOW: Understanding how code executes or processes work
        - LOCATE_ENTITY: Finding specific code entities or components
        - COMPARE_ENTITIES: Comparing multiple code entities
        - EXPLORE_ARCHITECTURE: Understanding system architecture or design
        - ANALYZE_PERFORMANCE: Performance analysis or optimization
        - GENERATE_DOCUMENTATION: Creating explanations or documentation
        - UNDERSTAND_ENTITY: General understanding of code entities
        
        **Guidelines:**
        - Always follow the 3-step workflow for comprehensive analysis
        - Provide confident classifications with supporting evidence
        - Consider context from previous interactions when available
        - Recommend the most appropriate strategy for query resolution
        - Be thorough but concise in your analysis
        
        Provide comprehensive, accurate analysis that helps the orchestrator make informed decisions.
        """;
    
    // Singleton instance
    private static QueryIntentClassifierAgent instance;
    
    /**
     * Private constructor - use getInstance() for singleton pattern
     */
    private QueryIntentClassifierAgent() {
        super(AGENT_NAME, createClassifierAgent());
        log.info("🎯 QueryIntentClassifierAgent initialized successfully");
    }
    
    /**
     * Get singleton instance of QueryIntentClassifierAgent
     */
    public static QueryIntentClassifierAgent getInstance() {
        if (instance == null) {
            synchronized (QueryIntentClassifierAgent.class) {
                if (instance == null) {
                    instance = new QueryIntentClassifierAgent();
                }
            }
        }
        return instance;
    }
    
    /**
     * Create the classifier agent with proper configuration
     */
    private static BaseAgent createClassifierAgent() {
        List<FunctionTool> tools = Arrays.asList(
            FunctionTool.create(QueryAnalyzer.class, "analyzeQuery"),
            FunctionTool.create(IntentResolver.class, "resolveIntent"),
            FunctionTool.create(StrategyRecommender.class, "recommendStrategy")
        );
        
        return AgentFactory.createQueryAnalysisAgent(
            AGENT_NAME,
            AGENT_INSTRUCTION,
            tools
        );
    }
    
    /**
     * Classify user query intent using intelligent multi-agent analysis
     * 
     * @param query User's natural language query about the codebase
     * @param previousContext Previous context from session (optional)
     * @param allowMultiIntent Allow multiple intents in response
     * @return Structured classification result
     */
    public Map<String, Object> classifyIntent(String query, String previousContext, Boolean allowMultiIntent) {
        log.debug("🎯 Starting intent classification for query: '{}'", query);
        
        // Input validation
        if (query == null || query.trim().isEmpty()) {
            return createErrorResponse("Query cannot be empty");
        }
        
        // Prepare context data
        Map<String, Object> contextData = new HashMap<>();
        contextData.put("previousContext", previousContext);
        contextData.put("allowMultiIntent", allowMultiIntent != null ? allowMultiIntent : false);
        contextData.put("timestamp", System.currentTimeMillis());
        
        // Prepare input for agent
        String agentInput = buildAgentInput(query, previousContext, allowMultiIntent);
        
        // Execute agent
        Map<String, Object> result = executeAgent(agentInput, contextData);
        
        // Enhance result with classification-specific metadata
        enhanceClassificationResult(result, query, contextData);
        
        log.debug("✅ Intent classification completed for query: '{}'", query);
        return result;
    }
    
    /**
     * Build structured input for the agent
     */
    private String buildAgentInput(String query, String previousContext, Boolean allowMultiIntent) {
        StringBuilder input = new StringBuilder();
        
        input.append("Analyze the following user query about a codebase:\n\n");
        input.append("Query: ").append(query).append("\n\n");
        
        if (previousContext != null && !previousContext.trim().isEmpty()) {
            input.append("Previous Context: ").append(previousContext).append("\n\n");
        }
        
        if (allowMultiIntent != null && allowMultiIntent) {
            input.append("Note: Multiple intents are allowed in the response.\n\n");
        }
        
        input.append("Please follow the 3-step workflow to provide comprehensive analysis.");
        
        return input.toString();
    }
    
    /**
     * Enhance classification result with additional metadata
     */
    private void enhanceClassificationResult(Map<String, Object> result, String query, Map<String, Object> contextData) {
        // Add classification method
        result.put("classificationMethod", "INTELLIGENT_AGENT");
        
        // Add query information
        result.put("queryLength", query.length());
        result.put("queryWordCount", query.split("\\s+").length);
        
        // Add context information
        result.put("hasContext", contextData.get("previousContext") != null);
        result.put("allowMultiIntent", contextData.get("allowMultiIntent"));
        
        // Add agent statistics
        result.put("agentStats", getAgentStats());
    }
    
    /**
     * Create standardized error response
     */
    private Map<String, Object> createErrorResponse(String error) {
        return Map.of(
            "status", "error",
            "error", error,
            "classificationMethod", "INTELLIGENT_AGENT",
            "confidence", 0.0,
            "timestamp", System.currentTimeMillis()
        );
    }
    
    /**
     * Get agent configuration for monitoring
     */
    public Map<String, Object> getAgentConfiguration() {
        return Map.of(
            "agentName", AGENT_NAME,
            "agentDescription", AGENT_DESCRIPTION,
            "toolCount", 3,
            "toolNames", Arrays.asList("QueryAnalyzer", "IntentResolver", "StrategyRecommender"),
            "intentCategories", Arrays.asList(
                "DEBUG_ISSUE", "UNDERSTAND_FLOW", "LOCATE_ENTITY", "COMPARE_ENTITIES",
                "EXPLORE_ARCHITECTURE", "ANALYZE_PERFORMANCE", "GENERATE_DOCUMENTATION", "UNDERSTAND_ENTITY"
            )
        );
    }
}