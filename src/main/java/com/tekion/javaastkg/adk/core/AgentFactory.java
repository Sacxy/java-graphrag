package com.tekion.javaastkg.adk.core;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.FunctionTool;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 🏭 Agent Factory - Centralized ADK Agent Creation
 * 
 * Provides consistent agent creation patterns:
 * - Standardized agent configuration
 * - Reusable agent templates
 * - Environment-specific settings
 * - Best practice defaults
 * 
 * This factory ensures all agents follow consistent patterns and reduces
 * code duplication across agent implementations.
 */
@Slf4j
public class AgentFactory {
    
    // Default configuration constants
    private static final String DEFAULT_MODEL = "gemini-2.0-flash-001";
    private static final double DEFAULT_TEMPERATURE = 0.1;
    private static final int DEFAULT_MAX_TOKENS = 2048;
    
    /**
     * Agent configuration builder
     */
    public static class AgentConfig {
        private String name;
        private String model = DEFAULT_MODEL;
        private String description;
        private String instruction;
        private List<FunctionTool> tools = new ArrayList<>();
        private double temperature = DEFAULT_TEMPERATURE;
        private int maxTokens = DEFAULT_MAX_TOKENS;
        private Map<String, Object> metadata = new HashMap<>();
        
        public AgentConfig name(String name) {
            this.name = name;
            return this;
        }
        
        public AgentConfig model(String model) {
            this.model = model;
            return this;
        }
        
        public AgentConfig description(String description) {
            this.description = description;
            return this;
        }
        
        public AgentConfig instruction(String instruction) {
            this.instruction = instruction;
            return this;
        }
        
        public AgentConfig addTool(FunctionTool tool) {
            this.tools.add(tool);
            return this;
        }
        
        public AgentConfig tools(List<FunctionTool> tools) {
            this.tools = new ArrayList<>(tools);
            return this;
        }
        
        public AgentConfig temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }
        
        public AgentConfig maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }
        
        public AgentConfig metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }
        
        public BaseAgent build() {
            return AgentFactory.createAgent(this);
        }
    }
    
    /**
     * Create a new agent configuration builder
     */
    public static AgentConfig newAgent() {
        return new AgentConfig();
    }
    
    /**
     * Create agent from configuration
     */
    private static BaseAgent createAgent(AgentConfig config) {
        // Validate required fields
        if (config.name == null || config.name.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent name is required");
        }
        if (config.instruction == null || config.instruction.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent instruction is required");
        }
        
        log.debug("🏭 Creating ADK agent: {} with {} tools", config.name, config.tools.size());
        
        // Build the agent
        LlmAgent.Builder builder = LlmAgent.builder()
            .name(config.name)
            .model(config.model)
            .instruction(config.instruction);
        
        // Add optional fields
        if (config.description != null && !config.description.trim().isEmpty()) {
            builder.description(config.description);
        }
        
        if (!config.tools.isEmpty()) {
            builder.tools(config.tools);
        }
        
        // TODO: Add generation config when available in ADK
        // builder.generateContentConfig(GenerateContentConfig.builder()
        //     .temperature(config.temperature)
        //     .maxOutputTokens(config.maxTokens)
        //     .build());
        
        BaseAgent agent = builder.build();
        
        log.debug("✅ Agent created successfully: {}", config.name);
        return agent;
    }
    
    /**
     * Create a query analysis agent with standard configuration
     */
    public static BaseAgent createQueryAnalysisAgent(String name, String instruction, List<FunctionTool> tools) {
        return newAgent()
            .name(name)
            .description("Specialized agent for query analysis and processing")
            .instruction(instruction)
            .tools(tools)
            .temperature(0.1) // Low temperature for consistent analysis
            .metadata("agentType", "query-analysis")
            .metadata("domain", "code-understanding")
            .build();
    }
    
    /**
     * Create a code exploration agent with standard configuration
     */
    public static BaseAgent createCodeExplorationAgent(String name, String instruction, List<FunctionTool> tools) {
        return newAgent()
            .name(name)
            .description("Specialized agent for code exploration and discovery")
            .instruction(instruction)
            .tools(tools)
            .temperature(0.2) // Slightly higher for creative exploration
            .metadata("agentType", "code-exploration")
            .metadata("domain", "code-understanding")
            .build();
    }
    
    /**
     * Create a narrative generation agent with standard configuration
     */
    public static BaseAgent createNarrativeAgent(String name, String instruction, List<FunctionTool> tools) {
        return newAgent()
            .name(name)
            .description("Specialized agent for generating explanations and narratives")
            .instruction(instruction)
            .tools(tools)
            .temperature(0.3) // Higher temperature for creative explanations
            .metadata("agentType", "narrative-generation")
            .metadata("domain", "documentation")
            .build();
    }
    
    /**
     * Create a debugging agent with standard configuration
     */
    public static BaseAgent createDebuggingAgent(String name, String instruction, List<FunctionTool> tools) {
        return newAgent()
            .name(name)
            .description("Specialized agent for debugging and error analysis")
            .instruction(instruction)
            .tools(tools)
            .temperature(0.1) // Low temperature for precise debugging
            .metadata("agentType", "debugging")
            .metadata("domain", "error-analysis")
            .build();
    }
    
    /**
     * Get agent metadata for monitoring and analytics
     */
    public static Map<String, Object> getAgentMetadata(BaseAgent agent) {
        Map<String, Object> metadata = new HashMap<>();
        
        // Extract basic agent information
        metadata.put("agentName", agent.name());
        if (agent.description() != null) {
            metadata.put("agentDescription", agent.description());
        }
        metadata.put("createdAt", System.currentTimeMillis());
        
        // Note: BaseAgent doesn't expose model or tools information
        // This would need to be tracked separately if needed
        
        return metadata;
    }
}