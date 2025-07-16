package com.tekion.javaastkg.adk.agents;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.agents.LlmAgent;
import com.google.genai.types.GenerateContentConfig;
import com.google.adk.tools.FunctionTool;
import com.tekion.javaastkg.adk.tools.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * 🔄 Sequential Analysis Workflow Agent - Deterministic Multi-Step Code Analysis
 * 
 * This ADK SequentialAgent implements deterministic, step-by-step code analysis workflows
 * where each analysis tool builds upon the results of the previous tool. It provides
 * structured, progressive analysis for complex queries requiring cumulative insights.
 * 
 * Architecture:
 * - Uses Google ADK SequentialAgent for deterministic execution order
 * - Coordinates specialized analysis sub-agents in predetermined sequence
 * - Manages context state flow between analysis steps
 * - Provides progress tracking and intermediate result visibility
 * 
 * Key Features:
 * - Deterministic execution order ensures consistent, reproducible analysis
 * - Context state transfer enables each step to build on previous findings
 * - Progress tracking provides visibility into multi-step analysis workflows
 * - Error handling ensures graceful recovery from step-level failures
 * - Optimized for complex queries requiring comprehensive, layered analysis
 * 
 * Workflow Patterns:
 * 1. Deep Architecture Analysis: Intent → Hunt → Explore → Enrich → Narrative
 * 2. Debugging Investigation: Intent → Hunt → Trace → Enrich → Narrative  
 * 3. Comprehensive Assessment: Intent → Hunt → Explore → Trace → Enrich → Narrative
 * 
 * Usage:
 * This agent is used by the Intelligent Orchestrator for complex queries that benefit
 * from deterministic, step-by-step analysis where each tool's output directly informs
 * the next tool's execution strategy.
 */
@Slf4j
public class SequentialAnalysisWorkflow {
    
    /**
     * Deep Architecture Analysis Workflow
     * 
     * Sequential execution: Intent Classification → Semantic Search → Structural Analysis → Context Enrichment → Narrative Generation
     * Use case: Complex architecture understanding queries
     */
    public static final BaseAgent DEEP_ARCHITECTURE_WORKFLOW = SequentialAgent.builder()
        .name("deep-architecture-analysis")
        .description("Sequential workflow for comprehensive architectural understanding")
        .subAgents(Arrays.asList(
            createIntentAnalysisAgent(),
            createSemanticDiscoveryAgent(), 
            createStructuralAnalysisAgent(),
            createContextEnrichmentAgent(),
            createNarrativeGenerationAgent()
        ))
        .build();
    
    /**
     * Debugging Investigation Workflow
     * 
     * Sequential execution: Intent Classification → Semantic Search → Execution Tracing → Context Enrichment → Narrative Generation
     * Use case: Debugging and flow analysis queries
     */
    public static final BaseAgent DEBUGGING_INVESTIGATION_WORKFLOW = SequentialAgent.builder()
        .name("debugging-investigation")
        .description("Sequential workflow for systematic debugging and flow analysis")
        .subAgents(Arrays.asList(
            createIntentAnalysisAgent(),
            createSemanticDiscoveryAgent(),
            createExecutionTracingAgent(),
            createContextEnrichmentAgent(),
            createNarrativeGenerationAgent()
        ))
        .build();
    
    /**
     * Comprehensive Assessment Workflow
     * 
     * Sequential execution: Intent → Hunt → Explore → Trace → Enrich → Narrative
     * Use case: Most complex queries requiring full analysis depth
     */
    public static final BaseAgent COMPREHENSIVE_ASSESSMENT_WORKFLOW = SequentialAgent.builder()
        .name("comprehensive-assessment")
        .description("Sequential workflow for complete code analysis with all dimensions")
        .subAgents(Arrays.asList(
            createIntentAnalysisAgent(),
            createSemanticDiscoveryAgent(),
            createStructuralAnalysisAgent(),
            createExecutionTracingAgent(),
            createContextEnrichmentAgent(),
            createNarrativeGenerationAgent()
        ))
        .build();
    
    /**
     * Create Intent Analysis sub-agent optimized for sequential workflow
     */
    private static BaseAgent createIntentAnalysisAgent() {
        return LlmAgent.builder()
            .name("intent-analyzer")
            .description("Analyzes user queries to determine analysis scope and target entities")
            .model("gemini-2.0-flash-001")
            .instruction("""
                You are the Intent Analysis specialist in a sequential code analysis workflow.
                
                Your role is to:
                1. Analyze the user query to understand their intent and goals
                2. Identify target entities, scope, and analysis requirements
                3. Determine the confidence level and analysis strategy
                4. Set context state for subsequent analysis agents
                
                Use the classifyIntent tool to perform comprehensive intent analysis.
                Store results in context state with key 'sequential_intent_result' for next agent.
                
                Focus on providing clear, actionable guidance for the semantic discovery phase.
                """)
            .tools(Arrays.asList(
                FunctionTool.create(QueryIntentClassifier.class, "classifyIntent")
            ))
            .generateContentConfig(GenerateContentConfig.builder()
                .temperature(0.1F)
                .maxOutputTokens(1500)
                .build())
            .build();
    }
    
    /**
     * Create Semantic Discovery sub-agent optimized for sequential workflow
     */
    private static BaseAgent createSemanticDiscoveryAgent() {
        return LlmAgent.builder()
            .name("semantic-discoverer")
            .description("Discovers relevant code entities using semantic search based on intent analysis")
            .model("gemini-2.0-flash-001")
            .instruction("""
                You are the Semantic Discovery specialist in a sequential code analysis workflow.
                
                Your role is to:
                1. Read intent analysis results from previous agent: {sequential_intent_result}
                2. Use semantic search to find relevant code entities and patterns
                3. Filter and rank results based on intent analysis findings
                4. Prepare entity list for structural/execution analysis
                
                Use the huntCode tool with search criteria derived from intent analysis.
                Store results in context state with key 'sequential_hunt_results' for next agent.
                
                Focus on finding the most relevant entities for the identified intent and scope.
                """)
            .tools(Arrays.asList(
                FunctionTool.create(SemanticCodeHunter.class, "huntCode")
            ))
            .generateContentConfig(GenerateContentConfig.builder()
                .temperature(0.2F)
                .maxOutputTokens(1500)
                .build())
            .build();
    }
    
    /**
     * Create Structural Analysis sub-agent optimized for sequential workflow
     */
    private static BaseAgent createStructuralAnalysisAgent() {
        return LlmAgent.builder()
            .name("structural-analyzer")
            .description("Analyzes architectural patterns and relationships for discovered entities")
            .model("gemini-2.0-flash-001")
            .instruction("""
                You are the Structural Analysis specialist in a sequential code analysis workflow.
                
                Your role is to:
                1. Read semantic discovery results from previous agent: {sequential_hunt_results}
                2. Explore structural relationships and architectural patterns
                3. Build comprehensive understanding of code organization
                4. Identify key architectural insights and patterns
                
                Use the exploreStructure tool with entities from semantic discovery.
                Store results in context state with key 'sequential_structure_data' for next agent.
                
                Focus on understanding how discovered entities relate and organize architecturally.
                """)
            .tools(Arrays.asList(
                FunctionTool.create(StructuralCodeExplorer.class, "exploreStructure")
            ))
            .generateContentConfig(GenerateContentConfig.builder()
                .temperature(0.1F)
                .maxOutputTokens(1500)
                .build())
            .build();
    }
    
    /**
     * Create Execution Tracing sub-agent optimized for sequential workflow
     */
    private static BaseAgent createExecutionTracingAgent() {
        return LlmAgent.builder()
            .name("execution-tracer")
            .description("Traces execution paths and analyzes dynamic behavior for discovered entities")
            .model("gemini-2.0-flash-001")
            .instruction("""
                You are the Execution Tracing specialist in a sequential code analysis workflow.
                
                Your role is to:
                1. Read semantic discovery results from previous agent: {sequential_hunt_results}
                2. Trace execution paths and analyze dynamic behavior
                3. Understand flow relationships and performance characteristics
                4. Identify execution insights and potential issues
                
                Use the tracePath tool with entities from semantic discovery.
                Store results in context state with key 'sequential_execution_paths' for next agent.
                
                Focus on understanding how discovered entities behave at runtime and interact dynamically.
                """)
            .tools(Arrays.asList(
                FunctionTool.create(ExecutionPathTracer.class, "tracePath")
            ))
            .generateContentConfig(GenerateContentConfig.builder()
                .temperature(0.1F)
                .maxOutputTokens(1500)
                .build())
            .build();
    }
    
    /**
     * Create Context Enrichment sub-agent optimized for sequential workflow
     */
    private static BaseAgent createContextEnrichmentAgent() {
        return LlmAgent.builder()
            .name("context-enricher")
            .description("Enriches analysis with comprehensive contextual information")
            .model("gemini-2.0-flash-001")
            .instruction("""
                You are the Context Enrichment specialist in a sequential code analysis workflow.
                
                Your role is to:
                1. Read discovery results from previous agent: {sequential_hunt_results}
                2. Read structural insights: {sequential_structure_data} (if available)
                3. Read execution insights: {sequential_execution_paths} (if available)
                4. Enrich context with comprehensive documentation, usage, and quality assessment
                
                Use the analyzeContextQuality tool with all discovered entities.
                Store results in context state with key 'sequential_enriched_context' for final synthesis.
                
                Focus on providing comprehensive context that enables high-quality narrative generation.
                """)
            .tools(Arrays.asList(
                FunctionTool.create(CodeContextEnricherRedesigned.class, "analyzeContextQuality")
            ))
            .generateContentConfig(GenerateContentConfig.builder()
                .temperature(0.1F)
                .maxOutputTokens(1500)
                .build())
            .build();
    }
    
    /**
     * Create Narrative Generation sub-agent optimized for sequential workflow
     */
    private static BaseAgent createNarrativeGenerationAgent() {
        return LlmAgent.builder()
            .name("narrative-generator")
            .description("Synthesizes all analysis results into comprehensive narrative")
            .model("gemini-2.0-flash-001")
            .instruction("""
                You are the Narrative Generation specialist completing a sequential code analysis workflow.
                
                Your role is to:
                1. Read original intent: {sequential_intent_result}
                2. Read discovery results: {sequential_hunt_results}
                3. Read structural analysis: {sequential_structure_data} (if available)
                4. Read execution analysis: {sequential_execution_paths} (if available)
                5. Read enriched context: {sequential_enriched_context}
                6. Synthesize comprehensive narrative addressing the original query
                
                Use the generateNarrative tool with all accumulated analysis results.
                Provide final response with multi-perspective insights and recommendations.
                
                Focus on creating a comprehensive, user-friendly response that leverages all analysis steps.
                """)
            .tools(Arrays.asList(
                FunctionTool.create(FlowNarrativeGenerator.class, "generateNarrative")
            ))
            .generateContentConfig(GenerateContentConfig.builder()
                .temperature(0.3F) // Slightly higher for creative narrative generation
                .maxOutputTokens(2000)
                .build())
            .build();
    }
    
    /**
     * Get deep architecture analysis workflow for complex architectural queries
     */
    public static BaseAgent getDeepArchitectureWorkflow() {
        log.info("🔄 Initializing Deep Architecture Analysis Sequential Workflow");
        return DEEP_ARCHITECTURE_WORKFLOW;
    }
    
    /**
     * Get debugging investigation workflow for flow analysis and debugging queries
     */
    public static BaseAgent getDebuggingInvestigationWorkflow() {
        log.info("🔄 Initializing Debugging Investigation Sequential Workflow");
        return DEBUGGING_INVESTIGATION_WORKFLOW;
    }
    
    /**
     * Get comprehensive assessment workflow for most complex analysis queries
     */
    public static BaseAgent getComprehensiveAssessmentWorkflow() {
        log.info("🔄 Initializing Comprehensive Assessment Sequential Workflow");
        return COMPREHENSIVE_ASSESSMENT_WORKFLOW;
    }
}