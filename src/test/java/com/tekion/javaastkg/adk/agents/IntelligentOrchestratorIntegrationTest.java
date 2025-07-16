package com.tekion.javaastkg.adk.agents;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.genai.types.GenerateContentConfig;
import com.google.adk.tools.FunctionTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration Test Suite for Intelligent Orchestrator Agent
 * 
 * Verifies that the orchestrator agent is properly configured with:
 * - All 6 specialized tools integrated via FunctionTool
 * - Proper ADK LlmAgent configuration
 * - Comprehensive instruction for intelligent decision-making
 * - Correct model and generation parameters
 * 
 * This test ensures the orchestrator is ready for end-to-end code analysis workflows.
 */
class IntelligentOrchestratorIntegrationTest {

    private BaseAgent orchestratorAgent;

    @BeforeEach
    void setUp() {
        orchestratorAgent = IntelligentOrchestrator.getAgent();
    }

    @Test
    void shouldInitializeOrchestratorAgent() {
        // Verify agent is properly initialized
        assertThat(orchestratorAgent).isNotNull();
        assertThat(orchestratorAgent).isInstanceOf(LlmAgent.class);
        
        LlmAgent llmAgent = (LlmAgent) orchestratorAgent;
        
        // Verify basic agent configuration
        assertThat(llmAgent.name()).isEqualTo("intelligent-orchestrator");
        assertThat(llmAgent.description()).contains("Central coordinator for intelligent code query processing");
        // Note: model() method returns Model object with Optional fields
        assertThat(llmAgent.model()).isNotNull();
        assertThat(llmAgent.model().toString()).contains("gemini-2.0-flash-001");
    }

    @Test
    void shouldHaveAllSixSpecializedToolsIntegrated() throws Exception {
        LlmAgent llmAgent = (LlmAgent) orchestratorAgent;
        
        // Use reflection to access tools list (since it might be private)
        Field toolsField = LlmAgent.class.getDeclaredField("tools");
        toolsField.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<FunctionTool> tools = (List<FunctionTool>) toolsField.get(llmAgent);
        
        // Verify we have exactly 6 tools
        assertThat(tools).hasSize(6);
        
        // Verify each expected tool is present by checking method names
        // Note: We can't directly check class types due to FunctionTool encapsulation,
        // but we can verify the tools are properly configured
        assertThat(tools).isNotEmpty();
        
        // Verify tools are properly created FunctionTool instances
        for (FunctionTool tool : tools) {
            assertThat(tool).isNotNull();
            assertThat(tool.name()).isNotEmpty();
        }
    }

    @Test
    void shouldHaveProperGenerateContentConfiguration() {
        LlmAgent llmAgent = (LlmAgent) orchestratorAgent;
        
        // Verify generation configuration
        assertThat(llmAgent.generateContentConfig()).isPresent();
        GenerateContentConfig config = llmAgent.generateContentConfig().get();
        
        // Verify low temperature for consistent decision-making
        assertThat(config.temperature()).isPresent();
        assertThat(config.temperature().get()).isEqualTo(0.1F);
        
        // Verify sufficient token limit for reasoning
        assertThat(config.maxOutputTokens()).isPresent();
        assertThat(config.maxOutputTokens().get()).isEqualTo(3000);
    }

    @Test
    void shouldHaveComprehensiveInstructionSet() {
        LlmAgent llmAgent = (LlmAgent) orchestratorAgent;
        
        assertThat(llmAgent.instruction()).isPresent();
        String instruction = llmAgent.instruction().get();
        assertThat(instruction).isNotNull();
        assertThat(instruction).isNotEmpty();
        
        // Verify key instruction components
        assertThat(instruction).contains("intelligent orchestrator");
        assertThat(instruction).contains("classifyIntent");
        assertThat(instruction).contains("huntCode");
        assertThat(instruction).contains("exploreStructure");
        assertThat(instruction).contains("tracePath");
        assertThat(instruction).contains("analyzeContextQuality");
        assertThat(instruction).contains("generateNarrative");
        
        // Verify workflow patterns are defined
        assertThat(instruction).contains("SIMPLE");
        assertThat(instruction).contains("STRUCTURAL");
        assertThat(instruction).contains("EXECUTION");
        assertThat(instruction).contains("COMPREHENSIVE");
        
        // Verify decision rules are included
        assertThat(instruction).contains("confidence");
        assertThat(instruction).contains("context.state");
        assertThat(instruction).contains("ERROR HANDLING");
    }

    @Test
    void shouldHaveIntelligentDecisionMakingGuidance() {
        LlmAgent llmAgent = (LlmAgent) orchestratorAgent;
        
        assertThat(llmAgent.instruction()).isPresent();
        String instruction = llmAgent.instruction().get();
        
        // Verify adaptive strategy guidance
        assertThat(instruction).contains("Adaptive Strategy Selection");
        assertThat(instruction).contains("DECISION RULES");
        assertThat(instruction).contains("Context Management Rules");
        
        // Verify confidence threshold logic
        assertThat(instruction).contains("High Confidence");
        assertThat(instruction).contains("Medium Confidence");
        assertThat(instruction).contains("Low Confidence");
        
        // Verify error recovery patterns
        assertThat(instruction).contains("Tool Failure Recovery");
        assertThat(instruction).contains("Query Ambiguity Handling");
        assertThat(instruction).contains("Performance Optimization");
    }

    @Test
    void shouldHaveContextStateManagementGuidance() {
        LlmAgent llmAgent = (LlmAgent) orchestratorAgent;
        
        assertThat(llmAgent.instruction()).isPresent();
        String instruction = llmAgent.instruction().get();
        
        // Verify context state key definitions
        assertThat(instruction).contains("app:user_query");
        assertThat(instruction).contains("app:intent_result");
        assertThat(instruction).contains("app:hunt_results");
        assertThat(instruction).contains("app:structure_data");
        assertThat(instruction).contains("app:execution_paths");
        assertThat(instruction).contains("app:enriched_context");
        assertThat(instruction).contains("app:synthesis_config");
        
        // Verify context management strategies
        assertThat(instruction).contains("State Preservation");
        assertThat(instruction).contains("Information Flow");
        assertThat(instruction).contains("Compression Strategy");
    }

    @Test
    void shouldProvideWorkflowExamplesForCommonScenarios() {
        LlmAgent llmAgent = (LlmAgent) orchestratorAgent;
        
        assertThat(llmAgent.instruction()).isPresent();
        String instruction = llmAgent.instruction().get();
        
        // Verify workflow examples are provided
        assertThat(instruction).contains("What does UserService do?");
        assertThat(instruction).contains("How is the payment system structured?");
        assertThat(instruction).contains("How does user authentication work?");
        assertThat(instruction).contains("Explain the entire order processing system");
        
        // Verify each workflow pattern has clear guidance
        assertThat(instruction).contains("intent → hunt → enrich → narrative");
        assertThat(instruction).contains("intent → hunt → explore → enrich → narrative");
        assertThat(instruction).contains("intent → hunt → trace → enrich → narrative");
    }

    @Test
    void shouldHaveClearSuccessCriteriaAndCommunicationGuidance() {
        LlmAgent llmAgent = (LlmAgent) orchestratorAgent;
        
        assertThat(llmAgent.instruction()).isPresent();
        String instruction = llmAgent.instruction().get();
        
        // Verify success criteria are defined
        assertThat(instruction).contains("SUCCESS CRITERIA");
        assertThat(instruction).contains("Accuracy");
        assertThat(instruction).contains("Efficiency");
        assertThat(instruction).contains("Comprehensiveness");
        assertThat(instruction).contains("Clarity");
        assertThat(instruction).contains("Adaptability");
        
        // Verify communication style guidance
        assertThat(instruction).contains("COMMUNICATION STYLE");
        assertThat(instruction).contains("Be Transparent");
        assertThat(instruction).contains("Be Adaptive");
        assertThat(instruction).contains("Be Helpful");
        assertThat(instruction).contains("Be Comprehensive");
        assertThat(instruction).contains("Be Confident");
    }

    @Test
    void shouldBeReadyForProductionUsage() {
        // Verify the orchestrator can be retrieved multiple times consistently
        BaseAgent agent1 = IntelligentOrchestrator.getAgent();
        BaseAgent agent2 = IntelligentOrchestrator.getAgent();
        
        // Both should reference the same static ROOT_AGENT instance
        assertThat(agent1).isSameAs(agent2);
        
        // Verify static configuration is stable
        assertThat(agent1.name()).isEqualTo(agent2.name());
        assertThat(agent1.description()).isEqualTo(agent2.description());
        // Note: BaseAgent doesn't expose model() method directly
    }
}