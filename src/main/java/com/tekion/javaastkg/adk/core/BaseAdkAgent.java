package com.tekion.javaastkg.adk.core;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.agents.RunConfig.StreamingMode;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.runner.Runner;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🏗️ Base ADK Agent - Foundation for all ADK-based agents
 * 
 * Provides common functionality for ADK agent operations:
 * - Agent lifecycle management
 * - Context management per agent
 * - Consistent error handling
 * - Performance monitoring
 * - Reusable execution patterns
 * 
 * This base class follows ADK best practices and provides a clean foundation
 * for all domain-specific agents.
 */
@Slf4j
public abstract class BaseAdkAgent {
    
    // Agent configuration constants
    protected static final String DEFAULT_MODEL = "gemini-2.0-flash-001";
    protected static final String DEFAULT_APP_NAME = "JavaASTKG";
    
    // Agent context management - per agent instance
    private final Map<String, Object> agentContext = new ConcurrentHashMap<>();
    private final String agentName;
    private final BaseAgent agent;
    private final Runner runner;
    
    /**
     * Constructor for creating a new ADK agent
     * 
     * @param agentName Unique name for this agent
     * @param agent The configured ADK BaseAgent instance
     */
    protected BaseAdkAgent(String agentName, BaseAgent agent) {
        this.agentName = agentName;
        this.agent = agent;
        this.runner = new InMemoryRunner(agent, DEFAULT_APP_NAME);
        
        // Initialize agent context
        initializeAgentContext();
        
        log.debug("🏗️ Initialized ADK agent: {}", agentName);
    }
    
    /**
     * Initialize agent-specific context
     * Override this method to set up agent-specific context data
     */
    protected void initializeAgentContext() {
        agentContext.put("agentName", agentName);
        agentContext.put("createdAt", System.currentTimeMillis());
        agentContext.put("executionCount", 0);
    }
    
    /**
     * Execute agent with input and get structured response
     * 
     * @param input The input content for the agent
     * @param contextData Additional context data
     * @return Structured response from agent execution
     */
    protected Map<String, Object> executeAgent(String input, Map<String, Object> contextData) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Update execution count
            int count = (Integer) agentContext.get("executionCount");
            agentContext.put("executionCount", count + 1);
            
            // Prepare input content
            Content content = Content.fromParts(Part.fromText(input));
            
            // Execute with minimal session approach
            Map<String, Object> result = executeWithMinimalSession(content, contextData);
            
            // Add execution metadata
            result.put("executionTimeMs", System.currentTimeMillis() - startTime);
            result.put("agentName", agentName);
            result.put("executionCount", agentContext.get("executionCount"));
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ Agent execution failed for {}: {}", agentName, e.getMessage(), e);
            
            return Map.of(
                "status", "error",
                "error", "Agent execution failed: " + e.getMessage(),
                "agentName", agentName,
                "executionTimeMs", System.currentTimeMillis() - startTime
            );
        }
    }
    
    /**
     * Execute agent with minimal session for stateless operations
     */
    private Map<String, Object> executeWithMinimalSession(Content content, Map<String, Object> contextData) {
        String userId = "system";
        String sessionId = "temp-" + System.currentTimeMillis();
        
        try {
            // Create session
            var sessionService = runner.sessionService();
            var session = sessionService.createSession(DEFAULT_APP_NAME, userId).blockingGet();
            
            // Configure execution
            RunConfig runConfig = RunConfig.builder()
                .setStreamingMode(StreamingMode.NONE)
                .setMaxLlmCalls(50)
                .setSaveInputBlobsAsArtifacts(false)
                .build();
            
            // Execute agent
            Flowable<Event> eventStream = runner.runAsync(userId, session.id(), content, runConfig);
            List<Event> events = eventStream.toList().blockingGet();
            
            // Process events
            Map<String, Object> result = processEvents(events, contextData);
            
            // Clean up session
            sessionService.deleteSession(DEFAULT_APP_NAME, userId, session.id()).blockingAwait();
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ Minimal session execution failed: {}", e.getMessage(), e);
            throw new RuntimeException("Session execution failed", e);
        }
    }
    
    /**
     * Process ADK events and extract meaningful results
     * Override this method to customize event processing for specific agents
     */
    protected Map<String, Object> processEvents(List<Event> events, Map<String, Object> contextData) {
        if (events.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "error");
            result.put("error", "No events received from agent execution");
            return result;
        }
        
        // Extract final response
        Event lastEvent = events.get(events.size() - 1);
        
        // Look for turn complete events
        Optional<Event> turnCompleteEvent = events.stream()
            .filter(e -> e.turnComplete().orElse(false))
            .findFirst();
        
        if (turnCompleteEvent.isPresent()) {
            Event finalEvent = turnCompleteEvent.get();
            String response = "";
            
            // Extract content properly
            if (finalEvent.content() != null && finalEvent.content().isPresent()) {
                Content eventContent = finalEvent.content().get();
                response = eventContent.text();
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("response", response != null ? response : "No response content");
            result.put("eventsProcessed", events.size());
            result.put("agentUsed", true);
            return result;
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("status", "error");
        result.put("error", "No complete response found in events");
        result.put("eventsProcessed", events.size());
        return result;
    }
    
    /**
     * Get current agent context
     */
    protected Map<String, Object> getAgentContext() {
        return new HashMap<>(agentContext);
    }
    
    /**
     * Update agent context with new data
     */
    protected void updateAgentContext(String key, Object value) {
        agentContext.put(key, value);
    }
    
    /**
     * Get agent name
     */
    public String getAgentName() {
        return agentName;
    }
    
    /**
     * Get agent statistics
     */
    public Map<String, Object> getAgentStats() {
        return Map.of(
            "agentName", agentName,
            "executionCount", agentContext.get("executionCount"),
            "createdAt", agentContext.get("createdAt"),
            "uptime", System.currentTimeMillis() - (Long) agentContext.get("createdAt")
        );
    }
}