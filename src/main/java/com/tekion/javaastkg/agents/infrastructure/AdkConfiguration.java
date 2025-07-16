package com.tekion.javaastkg.agents.infrastructure;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.genai.types.GenerateContentConfig;
// TODO: Import actual tool classes when implemented
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Configuration for Google ADK (Agent Development Kit) integration
 */
@Configuration
@Slf4j
public class AdkConfiguration {

    @Value("${adk.agents.orchestrator.max-tool-calls:10}")
    private int maxToolCalls;

    @Value("${adk.agents.orchestrator.timeout-ms:180000}")
    private long timeoutMs;

    @Value("${adk.agents.orchestrator.context-window-max:180000}")
    private int contextWindowMax;

    @Value("${llm.openai.api-key}")
    private String openAiApiKey;

    /**
     * Configure the main Intelligent Orchestrator Agent
     */
    @Bean
    @Primary
    public BaseAgent intelligentOrchestratorAgent() {
        log.info("Configuring Intelligent Orchestrator Agent with ADK");
        
        return LlmAgent.builder()
            .name("intelligent-orchestrator")
            .description("Central orchestrator for code query processing using specialized tools")
            .model("gemini-2.0-flash-001")
            .instruction(ORCHESTRATOR_INSTRUCTION)
            .generateContentConfig(GenerateContentConfig.builder()
                .temperature(0.1F) // Low temperature for deterministic decisions
                .maxOutputTokens(2000)
                .build())
            .outputKey("queryResult")
            .build();
    }

    /**
     * Executor service for parallel tool execution
     */
    @Bean("agentExecutorService")
    public ExecutorService agentExecutorService() {
        return Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "agent-executor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Agent runner for executing ADK agents
     */
    @Bean
    public AgentRunner agentRunner(BaseAgent intelligentOrchestratorAgent,
                                   ExecutorService agentExecutorService) {
        return new AgentRunner(intelligentOrchestratorAgent, agentExecutorService);
    }

    /**
     * Context window manager for managing token usage
     */
    @Bean
    public ContextWindowManager contextWindowManager() {
        return new ContextWindowManager(contextWindowMax);
    }

    /**
     * Metrics collector for agent performance monitoring
     */
    @Bean
    public MetricsCollector metricsCollector() {
        return new MetricsCollector();
    }

    /**
     * Circuit breaker for preventing infinite loops and runaway execution
     */
    @Bean
    public AgentCircuitBreaker agentCircuitBreaker() {
        return new AgentCircuitBreaker(maxToolCalls, Duration.ofMillis(timeoutMs));
    }

    private static final String ORCHESTRATOR_INSTRUCTION = """
        You are an intelligent orchestrator for code query processing. Your role is to:
        
        1. ANALYZE the query using classifyIntent to understand user intent
        2. DECIDE which tools to use based on intent analysis
        3. EXECUTE tools in optimal sequence (parallel when possible)
        4. MANAGE context window by compressing/summarizing results
        5. ITERATE until you have sufficient information
        6. SYNTHESIZE final response using generateNarrative
        
        EXECUTION PATTERNS:
        - EXPLORATORY: intent -> semantic -> structural -> context -> narrative
        - SURGICAL: intent -> semantic -> path -> context -> narrative  
        - COMPARATIVE: intent -> semantic -> parallel(structural+context) -> narrative
        - IMPACT_ANALYSIS: intent -> structural -> path -> narrative
        
        DECISION RULES:
        - If confidence < 0.7, request clarification
        - If too many results (>20), add filters
        - If too few results (<3), expand search
        - Use parallel execution for independent operations
        - Compress results when approaching token limits
        
        Always explain your reasoning and provide confidence scores.
        
        Available tools:
        - classifyIntent: Analyze query intent and recommend strategy
        - huntCodeEntities: Search for relevant code entities
        - exploreStructure: Analyze code structure and patterns
        - traceExecution: Trace execution paths and behavior
        - enrichContext: Gather comprehensive context
        - generateNarrative: Create final response narrative
        """;
}