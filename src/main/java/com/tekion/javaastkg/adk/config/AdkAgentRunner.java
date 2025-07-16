package com.tekion.javaastkg.adk.config;

import com.google.adk.agents.BaseAgent;
import com.google.adk.runner.InMemoryRunner;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.adk.sessions.Session;
import com.google.adk.events.Event;
import com.tekion.javaastkg.adk.context.Neo4jContextProvider;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

/**
 * ADK Agent Runner Component
 * 
 * Manages ADK agent execution using the correct ADK InMemoryRunner pattern.
 * Integrates with existing Neo4j infrastructure and follows ADK documentation.
 */
@Component
@Slf4j
public class AdkAgentRunner {
    
    private final Driver neo4jDriver;
    private final SessionConfig sessionConfig;
    
    // API key is now handled dynamically in the controller before each execution
    
    @Autowired
    public AdkAgentRunner(Driver neo4jDriver, SessionConfig sessionConfig) {
        this.neo4jDriver = neo4jDriver;
        this.sessionConfig = sessionConfig;
    }
    
    @PostConstruct
    public void init() {
        // Initialize Neo4j context provider
        Neo4jContextProvider.initialize(neo4jDriver, sessionConfig);
        log.info("Neo4j context provider initialized for ADK tools");
    }
    
    /**
     * Run an ADK agent with a text query using the correct ADK pattern
     */
    public String runAgent(BaseAgent agent, String query) {
        log.info("🚀 Starting agent execution: {} with query: {}", agent.name(), query);
        
        try {
            // API key is now configured directly in the agent via Gemini.builder().apiKey()
            log.info("✅ Using agent with pre-configured API key from application.yml");
            
            // Create ADK runner with the agent and consistent app name
            String appName = "JavaASTKG";
            InMemoryRunner runner = new InMemoryRunner(agent, appName);
            log.info("📝 Created InMemoryRunner with app name: {}", appName);
            
            // Create session with consistent user ID and app name
            String userId = "adk-user-" + System.currentTimeMillis();
            log.info("👤 Creating session for user: {} with app: {}", userId, appName);
            
            Session session = runner
                .sessionService()
                .createSession(appName, userId)
                .blockingGet();
            
            log.info("✅ Created ADK session: {} for user: {}", session.id(), userId);
            
            // Create user message content
            Content userMsg = Content.fromParts(Part.fromText(query));
            log.info("📨 Created user message content with query length: {}", query.length());
            
            // Execute agent and collect results - use same userId as session creation
            log.info("🔄 Starting agent execution with userId: {}, sessionId: {}", userId, session.id());
            Flowable<Event> events = runner.runAsync(userId, session.id(), userMsg);
            
            // Collect all events and build response
            List<String> allResponses = new ArrayList<>();
            List<String> agentReasoningOnly = new ArrayList<>();
            List<Event> eventList = new ArrayList<>();
            
            events
                .timeout(60, TimeUnit.SECONDS) // 60 second timeout
                .blockingForEach(event -> {
                    eventList.add(event);
                    String content = event.stringifyContent();
                    log.info("📥 Agent event received: type={}, content length={}", 
                        event.getClass().getSimpleName(), content != null ? content.length() : 0);
                    
                    // Log detailed event information for debugging
                    log.info("🔍 EVENT DETAILS: Class={}, ToString={}", 
                        event.getClass().getName(), 
                        event.toString().length() > 200 ? event.toString().substring(0, 200) + "..." : event.toString());
                    
                    if (content != null && !content.trim().isEmpty()) {
                        allResponses.add(content);
                        log.info("📄 EVENT CONTENT: {}", content.length() > 300 ? content.substring(0, 300) + "..." : content);
                        
                        // Separate agent reasoning from tool calls/responses
                        if (content.contains("Function Call:")) {
                            log.info("🛠️ TOOL CALL DETECTED in event");
                        } else if (content.contains("Function Response:")) {
                            log.info("🔄 TOOL RESPONSE DETECTED in event");
                        } else if (!content.contains("Function")) {
                            // This is pure agent reasoning/final response
                            agentReasoningOnly.add(content);
                            log.info("💭 AGENT REASONING: {}", content.length() > 200 ? content.substring(0, 200) + "..." : content);
                        }
                    }
                });
            
            log.info("📊 Event processing completed: {} total events, {} responses", eventList.size(), allResponses.size());
            
            // Use agent reasoning only for clean response, or fall back to all content
            String result;
            if (!agentReasoningOnly.isEmpty()) {
                result = String.join("\n\n", agentReasoningOnly);
                log.info("✨ Using clean agent reasoning response ({} reasoning segments)", agentReasoningOnly.size());
            } else {
                result = String.join("\n", allResponses);
                log.info("📋 Using full event stream response ({} total segments)", allResponses.size());
            }
            
            // Try cleanup
            try {
                log.info("🧹 Attempting session cleanup");
                runner.sessionService().deleteSession(appName, userId, session.id()).blockingAwait();
                log.info("✅ Session cleanup completed");
            } catch (Exception cleanupError) {
                log.warn("⚠️ Session cleanup failed: {}", cleanupError.getMessage());
            }
            
            log.info("🎉 Agent execution completed successfully, response length: {}", result.length());
            return result;
            
        } catch (Exception e) {
            log.error("❌ Agent execution failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to execute ADK agent: " + e.getMessage(), e);
        }
    }
}