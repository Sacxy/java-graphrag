package com.tekion.javaastkg.adk.agents;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.agents.LlmAgent;
import com.google.genai.types.GenerateContentConfig;
import com.google.adk.tools.FunctionTool;
import com.tekion.javaastkg.adk.tools.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * ⚡ Parallel Analysis Workflow Agent - Concurrent Multi-Dimensional Code Analysis
 * 
 * This ADK ParallelAgent implements concurrent execution of independent analysis tools
 * to dramatically improve performance for queries where multiple analysis dimensions
 * can be explored simultaneously without dependencies.
 * 
 * Architecture:
 * - Uses Google ADK ParallelAgent for concurrent execution
 * - Coordinates independent analysis sub-agents running simultaneously
 * - Aggregates results from multiple parallel execution branches
 * - Optimizes performance for resource-intensive analysis operations
 * 
 * Key Features:
 * - Concurrent execution reduces total analysis time significantly
 * - Independent analysis dimensions explored simultaneously
 * - Result aggregation provides comprehensive multi-perspective insights
 * - Error isolation ensures partial results even if some branches fail
 * - Optimized for performance-critical scenarios and large-scale analysis
 * 
 * Workflow Patterns:
 * 1. Multi-Dimensional Discovery: Semantic + Structural + Execution analysis in parallel
 * 2. Comparative Analysis: Multiple entity analysis streams running concurrently
 * 3. Performance-Optimized Investigation: Independent tool execution for speed
 * 
 * Usage:
 * This agent is used by the Intelligent Orchestrator when query analysis indicates
 * that multiple independent analysis dimensions can be explored concurrently,
 * prioritizing speed and parallel insight generation.
 */
@Slf4j
public class ParallelAnalysisWorkflow {
    
    /**
     * Multi-Dimensional Discovery Workflow
     * 
     * Parallel execution: Semantic Search || Structural Analysis || Execution Tracing
     * Use case: When query requires multiple independent analysis perspectives
     */
    public static final BaseAgent MULTI_DIMENSIONAL_DISCOVERY = ParallelAgent.builder()
        .name("multi-dimensional-discovery")
        .description("Parallel workflow for concurrent multi-perspective analysis")
        .subAgents(Arrays.asList(
            createSemanticAnalysisAgent(),
            createStructuralAnalysisAgent(),
            createExecutionAnalysisAgent()
        ))
        .build();
    
    /**
     * Performance-Optimized Investigation Workflow
     * 
     * Parallel execution: Semantic + Context || Structural + Context || Execution + Context
     * Use case: When speed is critical and analysis dimensions are independent
     */
    public static final BaseAgent PERFORMANCE_OPTIMIZED_INVESTIGATION = ParallelAgent.builder()
        .name("performance-optimized-investigation")
        .description("Parallel workflow optimized for maximum analysis speed")
        .subAgents(Arrays.asList(
            createSemanticWithContextAgent(),
            createStructuralWithContextAgent(),
            createExecutionWithContextAgent()
        ))
        .build();
    
    /**
     * Comparative Entity Analysis Workflow
     * 
     * Parallel execution: Multiple entity analysis streams
     * Use case: When comparing multiple entities or analyzing large codebases
     */
    public static final BaseAgent COMPARATIVE_ENTITY_ANALYSIS = ParallelAgent.builder()
        .name("comparative-entity-analysis")
        .description("Parallel workflow for concurrent entity comparison and analysis")
        .subAgents(Arrays.asList(
            createEntityDiscoveryAgent("primary-entities"),
            createEntityDiscoveryAgent("related-entities"),
            createEntityDiscoveryAgent("dependency-entities")
        ))
        .build();
    
    /**
     * Create Semantic Analysis sub-agent for parallel execution
     */
    private static BaseAgent createSemanticAnalysisAgent() {
        return LlmAgent.builder()
            .name("parallel-semantic-analyzer")
            .description("Concurrent semantic search and pattern discovery")
            .model("gemini-2.0-flash-001")
            .instruction("""
                You are the Semantic Analysis specialist in a parallel code analysis workflow.
                
                Your role is to:
                1. Perform comprehensive semantic search based on the user query
                2. Discover relevant code entities, patterns, and relationships
                3. Analyze semantic relationships and conceptual connections
                4. Provide semantic insights independent of other analysis dimensions
                
                Use the huntCode tool to perform thorough semantic discovery.
                Store results in context state with key 'parallel_semantic_results'.
                
                Focus on semantic depth and pattern recognition while running concurrently with other analyses.
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
     * Create Structural Analysis sub-agent for parallel execution
     */
    private static BaseAgent createStructuralAnalysisAgent() {
        return LlmAgent.builder()
            .name("parallel-structural-analyzer")
            .description("Concurrent architectural pattern and relationship analysis")
            .model("gemini-2.0-flash-001")
            .instruction("""
                You are the Structural Analysis specialist in a parallel code analysis workflow.
                
                Your role is to:
                1. Explore architectural patterns and structural relationships
                2. Analyze code organization and dependency structures
                3. Identify architectural insights and design patterns
                4. Provide structural understanding independent of other analysis dimensions
                
                Use the exploreStructure tool to perform comprehensive architectural analysis.
                Store results in context state with key 'parallel_structural_results'.
                
                Focus on architectural depth and pattern identification while running concurrently.
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
     * Create Execution Analysis sub-agent for parallel execution
     */
    private static BaseAgent createExecutionAnalysisAgent() {
        return LlmAgent.builder()
            .name("parallel-execution-analyzer")
            .description("Concurrent execution flow and dynamic behavior analysis")
            .model("gemini-2.0-flash-001")
            .instruction("""
                You are the Execution Analysis specialist in a parallel code analysis workflow.
                
                Your role is to:
                1. Trace execution paths and analyze dynamic behavior
                2. Understand flow relationships and performance characteristics
                3. Identify execution insights and runtime patterns
                4. Provide dynamic understanding independent of other analysis dimensions
                
                Use the tracePath tool to perform comprehensive execution analysis.
                Store results in context state with key 'parallel_execution_results'.
                
                Focus on execution depth and flow understanding while running concurrently.
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
     * Create Semantic + Context Analysis sub-agent for performance optimization
     */
    private static BaseAgent createSemanticWithContextAgent() {
        return LlmAgent.builder()
            .name("semantic-context-analyzer")
            .description("Combined semantic search and context enrichment for performance")
            .model("gemini-2.0-flash-001")
            .instruction("""
                You are a combined Semantic-Context specialist in a performance-optimized parallel workflow.
                
                Your role is to:
                1. Perform semantic search to discover relevant entities
                2. Immediately enrich discovered entities with contextual information
                3. Provide combined semantic and contextual insights
                4. Optimize for speed by combining two related operations
                
                Use huntCode followed by analyzeContextQuality for discovered entities.
                Store results in context state with key 'parallel_semantic_context_results'.
                
                Focus on efficiency and combined insights while running concurrently.
                """)
            .tools(Arrays.asList(
                FunctionTool.create(SemanticCodeHunter.class, "huntCode"),
                FunctionTool.create(CodeContextEnricherRedesigned.class, "analyzeContextQuality")
            ))
            .generateContentConfig(GenerateContentConfig.builder()
                .temperature(0.2F)
                .maxOutputTokens(2000)
                .build())
            .build();
    }
    
    /**
     * Create Structural + Context Analysis sub-agent for performance optimization
     */
    private static BaseAgent createStructuralWithContextAgent() {
        return LlmAgent.builder()
            .name("structural-context-analyzer")
            .description("Combined structural analysis and context enrichment for performance")
            .model("gemini-2.0-flash-001")
            .instruction("""
                You are a combined Structural-Context specialist in a performance-optimized parallel workflow.
                
                Your role is to:
                1. Explore structural relationships and architectural patterns
                2. Enrich structural findings with comprehensive contextual information
                3. Provide combined structural and contextual insights
                4. Optimize for speed by combining two related operations
                
                Use exploreStructure followed by analyzeContextQuality for key entities.
                Store results in context state with key 'parallel_structural_context_results'.
                
                Focus on architectural efficiency and combined insights while running concurrently.
                """)
            .tools(Arrays.asList(
                FunctionTool.create(StructuralCodeExplorer.class, "exploreStructure"),
                FunctionTool.create(CodeContextEnricherRedesigned.class, "analyzeContextQuality")
            ))
            .generateContentConfig(GenerateContentConfig.builder()
                .temperature(0.1F)
                .maxOutputTokens(2000)
                .build())
            .build();
    }
    
    /**
     * Create Execution + Context Analysis sub-agent for performance optimization
     */
    private static BaseAgent createExecutionWithContextAgent() {
        return LlmAgent.builder()
            .name("execution-context-analyzer")
            .description("Combined execution tracing and context enrichment for performance")
            .model("gemini-2.0-flash-001")
            .instruction("""
                You are a combined Execution-Context specialist in a performance-optimized parallel workflow.
                
                Your role is to:
                1. Trace execution paths and analyze dynamic behavior
                2. Enrich execution findings with comprehensive contextual information
                3. Provide combined execution and contextual insights
                4. Optimize for speed by combining two related operations
                
                Use tracePath followed by analyzeContextQuality for key entities.
                Store results in context state with key 'parallel_execution_context_results'.
                
                Focus on execution efficiency and combined insights while running concurrently.
                """)
            .tools(Arrays.asList(
                FunctionTool.create(ExecutionPathTracer.class, "tracePath"),
                FunctionTool.create(CodeContextEnricherRedesigned.class, "analyzeContextQuality")
            ))
            .generateContentConfig(GenerateContentConfig.builder()
                .temperature(0.1F)
                .maxOutputTokens(2000)
                .build())
            .build();
    }
    
    /**
     * Create Entity Discovery sub-agent for comparative analysis
     */
    private static BaseAgent createEntityDiscoveryAgent(String entityScope) {
        return LlmAgent.builder()
            .name("entity-discovery-" + entityScope)
            .description("Discovers and analyzes entities in scope: " + entityScope)
            .model("gemini-2.0-flash-001")
            .instruction(String.format("""
                You are an Entity Discovery specialist focusing on: %s
                
                Your role is to:
                1. Search for entities within your specific scope (%s)
                2. Analyze discovered entities for patterns and relationships
                3. Provide insights specific to your entity scope
                4. Enable comparative analysis across different entity groups
                
                Use huntCode with scope-specific search criteria.
                Store results in context state with key 'parallel_%s_entities'.
                
                Focus on thorough discovery within your scope while other agents explore different scopes.
                """, entityScope, entityScope, entityScope.replace("-", "_")))
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
     * Get multi-dimensional discovery workflow for comprehensive parallel analysis
     */
    public static BaseAgent getMultiDimensionalDiscoveryWorkflow() {
        log.info("⚡ Initializing Multi-Dimensional Discovery Parallel Workflow");
        return MULTI_DIMENSIONAL_DISCOVERY;
    }
    
    /**
     * Get performance-optimized investigation workflow for speed-critical scenarios
     */
    public static BaseAgent getPerformanceOptimizedInvestigationWorkflow() {
        log.info("⚡ Initializing Performance-Optimized Investigation Parallel Workflow");
        return PERFORMANCE_OPTIMIZED_INVESTIGATION;
    }
    
    /**
     * Get comparative entity analysis workflow for large-scale entity comparison
     */
    public static BaseAgent getComparativeEntityAnalysisWorkflow() {
        log.info("⚡ Initializing Comparative Entity Analysis Parallel Workflow");
        return COMPARATIVE_ENTITY_ANALYSIS;
    }
}