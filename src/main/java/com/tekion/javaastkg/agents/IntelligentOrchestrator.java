package com.tekion.javaastkg.agents;

import com.tekion.javaastkg.agents.models.*;
import com.tekion.javaastkg.agents.tools.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * 🧠 Intelligent Orchestrator Agent
 * 
 * @deprecated This Spring-based implementation is replaced by ADK-based agents.
 * Use com.tekion.javaastkg.adk.agents.IntelligentOrchestrator instead.
 * 
 * Central agent that coordinates all specialized tools to achieve complex goals:
 * - Analyzes user queries to determine optimal tool orchestration
 * - Coordinates multiple tools in parallel or sequential execution
 * - Synthesizes results from multiple tools into coherent insights
 * - Adapts strategy based on context and intermediate results
 * 
 * This agent embodies the "intelligence" of the system - making decisions about
 * which tools to use, when to use them, and how to combine their results.
 */
@Deprecated(since = "2024-01", forRemoval = true)
@Component
@Slf4j
@RequiredArgsConstructor
public class IntelligentOrchestrator {
    
    // Tool dependencies - the 6 specialized tools
    private final QueryIntentClassifierSimple queryIntentClassifier;
    private final SemanticCodeHunter semanticCodeHunter;
    private final StructuralCodeExplorer structuralCodeExplorer;
    private final ExecutionPathTracer executionPathTracer;
    private final CodeContextEnricher codeContextEnricher;
    private final FlowNarrativeGenerator flowNarrativeGenerator;
    
    /**
     * 🧠 Main orchestration method - coordinates all tools to achieve complex goals
     * 
     * @param request The high-level orchestration request
     * @return Comprehensive orchestration results
     */
    public OrchestrationResult orchestrate(OrchestrationRequest request) {
        
        log.info("🧠 Starting intelligent orchestration: goal={}, query='{}'", 
            request.getGoal(), request.getUserQuery());
        
        try {
            // Step 1: Initialize orchestration context
            OrchestrationContext context = initializeOrchestrationContext(request);
            
            // Step 2: Analyze user query and determine intent
            analyzeUserQueryIntent(context);
            
            // Step 3: Plan tool execution strategy
            planToolExecutionStrategy(context);
            
            // Step 4: Execute tools according to strategy
            executeToolStrategy(context);
            
            // Step 5: Synthesize results from all tools
            synthesizeToolResults(context);
            
            // Step 6: Generate final recommendations
            generateFinalRecommendations(context);
            
            // Step 7: Create comprehensive answer
            generateUserQueryAnswer(context);
            
            // Step 8: Calculate quality metrics
            calculateQualityMetrics(context);
            
            // Step 9: Build final orchestration result
            return buildOrchestrationResult(context);
            
        } catch (Exception e) {
            log.error("❌ Orchestration failed for query: {}", request.getUserQuery(), e);
            return OrchestrationResult.error("Orchestration failed: " + e.getMessage());
        }
    }
    
    /**
     * 🎯 Initialize orchestration context
     */
    private OrchestrationContext initializeOrchestrationContext(OrchestrationRequest request) {
        
        return OrchestrationContext.builder()
            .startTime(System.currentTimeMillis())
            .request(request)
            .executionSteps(new ArrayList<>())
            .toolResults(new HashMap<>())
            .synthesizedInsights(new ArrayList<>())
            .recommendations(new ArrayList<>())
            .qualityFactors(new HashMap<>())
            .executionPlan(new ArrayList<>())
            .build();
    }
    
    /**
     * 🔍 Analyze user query to understand intent and requirements
     */
    private void analyzeUserQueryIntent(OrchestrationContext context) {
        
        log.debug("🔍 Analyzing user query intent");
        
        try {
            // Use QueryIntentClassifier to understand the user's intent
            IntentClassificationResult classificationResult = queryIntentClassifier.classifyIntent(
                context.getRequest().getUserQuery(),
                context.getRequest().getContext(),
                false
            );
            
            // Store classification result
            context.getToolResults().put("queryClassification", classificationResult);
            
            // Add execution step
            context.getExecutionSteps().add(OrchestrationResult.OrchestrationStep.builder()
                .stepNumber(1)
                .toolName("QueryIntentClassifier")
                .purpose("Analyze user query intent and requirements")
                .inputSummary("Query: " + context.getRequest().getUserQuery())
                .outputSummary("Intent: " + classificationResult.getIntent().name())
                .executionTimeMs(100) // Mock timing
                .successful(true)
                .build());
            
            log.debug("🔍 Query intent classified as: {}", classificationResult.getIntent());
            
        } catch (Exception e) {
            log.warn("⚠️ Query intent analysis failed: {}", e.getMessage());
            
            context.getExecutionSteps().add(OrchestrationResult.OrchestrationStep.builder()
                .stepNumber(1)
                .toolName("QueryIntentClassifier")
                .purpose("Analyze user query intent and requirements")
                .successful(false)
                .failureReason(e.getMessage())
                .build());
        }
    }
    
    /**
     * 📋 Plan tool execution strategy based on query intent and goals
     */
    private void planToolExecutionStrategy(OrchestrationContext context) {
        
        log.debug("📋 Planning tool execution strategy");
        
        OrchestrationRequest.OrchestrationGoal goal = context.getRequest().getGoal();
        
        // Create execution plan based on goal
        List<String> executionPlan = switch (goal) {
            case CODE_UNDERSTANDING -> List.of(
                "SemanticCodeHunter",
                "StructuralCodeExplorer", 
                "CodeContextEnricher",
                "FlowNarrativeGenerator"
            );
            case PERFORMANCE_ANALYSIS -> List.of(
                "SemanticCodeHunter",
                "ExecutionPathTracer",
                "StructuralCodeExplorer",
                "CodeContextEnricher"
            );
            case ARCHITECTURE_ANALYSIS -> List.of(
                "StructuralCodeExplorer",
                "SemanticCodeHunter",
                "CodeContextEnricher",
                "FlowNarrativeGenerator"
            );
            case DOCUMENTATION_GENERATION -> List.of(
                "SemanticCodeHunter",
                "CodeContextEnricher",
                "FlowNarrativeGenerator"
            );
            case QUALITY_ASSESSMENT -> List.of(
                "SemanticCodeHunter",
                "StructuralCodeExplorer",
                "ExecutionPathTracer",
                "CodeContextEnricher"
            );
            case IMPACT_ANALYSIS -> List.of(
                "SemanticCodeHunter",
                "StructuralCodeExplorer",
                "ExecutionPathTracer"
            );
            default -> List.of(
                "SemanticCodeHunter",
                "StructuralCodeExplorer",
                "CodeContextEnricher"
            );
        };
        
        context.setExecutionPlan(executionPlan);
        
        log.debug("📋 Planned execution of {} tools: {}", executionPlan.size(), executionPlan);
    }
    
    /**
     * 🚀 Execute tool strategy with parallel/sequential execution
     */
    private void executeToolStrategy(OrchestrationContext context) {
        
        log.debug("🚀 Executing tool strategy");
        
        OrchestrationRequest.OrchestrationStyle style = context.getRequest().getStyle();
        
        if (style == OrchestrationRequest.OrchestrationStyle.PARALLEL) {
            executeToolsInParallel(context);
        } else {
            executeToolsSequentially(context);
        }
    }
    
    /**
     * 🔄 Execute tools sequentially
     */
    private void executeToolsSequentially(OrchestrationContext context) {
        
        log.debug("🔄 Executing tools sequentially");
        
        int stepNumber = 2; // Start after query classification
        
        for (String toolName : context.getExecutionPlan()) {
            try {
                executeTool(toolName, context, stepNumber++);
            } catch (Exception e) {
                log.warn("⚠️ Tool execution failed: {}", toolName, e);
                
                context.getExecutionSteps().add(OrchestrationResult.OrchestrationStep.builder()
                    .stepNumber(stepNumber++)
                    .toolName(toolName)
                    .successful(false)
                    .failureReason(e.getMessage())
                    .build());
            }
        }
    }
    
    /**
     * ⚡ Execute tools in parallel (where possible)
     */
    private void executeToolsInParallel(OrchestrationContext context) {
        
        log.debug("⚡ Executing tools in parallel");
        
        // For now, execute sequentially - real implementation would use CompletableFuture
        // to run independent tools in parallel
        executeToolsSequentially(context);
    }
    
    /**
     * 🔧 Execute individual tool
     */
    private void executeTool(String toolName, OrchestrationContext context, int stepNumber) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            switch (toolName) {
                case "SemanticCodeHunter" -> executeSemanticCodeHunter(context, stepNumber);
                case "StructuralCodeExplorer" -> executeStructuralCodeExplorer(context, stepNumber);
                case "ExecutionPathTracer" -> executeExecutionPathTracer(context, stepNumber);
                case "CodeContextEnricher" -> executeCodeContextEnricher(context, stepNumber);
                case "FlowNarrativeGenerator" -> executeFlowNarrativeGenerator(context, stepNumber);
                default -> log.warn("⚠️ Unknown tool: {}", toolName);
            }
            
        } catch (Exception e) {
            log.error("❌ Tool execution failed: {}", toolName, e);
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            log.debug("⏱️ Tool {} executed in {}ms", toolName, executionTime);
        }
    }
    
    /**
     * 🔍 Execute Semantic Code Hunter
     */
    private void executeSemanticCodeHunter(OrchestrationContext context, int stepNumber) {
        
        log.debug("🔍 Executing Semantic Code Hunter");
        
        SemanticSearchRequest searchRequest = SemanticSearchRequest.builder()
            .query(context.getRequest().getUserQuery())
            .searchMode(com.tekion.javaastkg.agents.tools.SemanticCodeHunter.SearchMode.SEMANTIC)
            .maxResults(20)
            .build();
        
        SemanticSearchResult result = semanticCodeHunter.huntCode(searchRequest);
        
        context.getToolResults().put("semanticSearch", result);
        
        context.getExecutionSteps().add(OrchestrationResult.OrchestrationStep.builder()
            .stepNumber(stepNumber)
            .toolName("SemanticCodeHunter")
            .purpose("Find relevant code entities using semantic search")
            .inputSummary("Query: " + context.getRequest().getUserQuery())
            .outputSummary("Found " + result.getMatches().size() + " relevant entities")
            .executionTimeMs(150)
            .successful(true)
            .build());
    }
    
    /**
     * 🏗️ Execute Structural Code Explorer
     */
    private void executeStructuralCodeExplorer(OrchestrationContext context, int stepNumber) {
        
        log.debug("🏗️ Executing Structural Code Explorer");
        
        List<String> seedNodes = getRelevantEntities(context);
        
        StructuralExplorationRequest explorationRequest = StructuralExplorationRequest.builder()
            .seedNodeIds(seedNodes)
            .focus(StructuralExplorationRequest.ExplorationFocus.COMPLETE)
            .maxDepth(5)
            .build();
        
        StructuralAnalysisResult result = structuralCodeExplorer.exploreStructure(explorationRequest);
        
        context.getToolResults().put("structuralAnalysis", result);
        
        context.getExecutionSteps().add(OrchestrationResult.OrchestrationStep.builder()
            .stepNumber(stepNumber)
            .toolName("StructuralCodeExplorer")
            .purpose("Analyze structural relationships and patterns")
            .inputSummary("Seed nodes: " + seedNodes.size())
            .outputSummary("Analyzed structure with " + result.getPatterns().size() + " patterns")
            .executionTimeMs(200)
            .successful(true)
            .build());
    }
    
    /**
     * 🔍 Execute Execution Path Tracer
     */
    private void executeExecutionPathTracer(OrchestrationContext context, int stepNumber) {
        
        log.debug("🔍 Executing Execution Path Tracer");
        
        List<String> relevantEntities = getRelevantEntities(context);
        String startingMethod = relevantEntities.isEmpty() ? "main" : relevantEntities.get(0);
        
        ExecutionPathRequest pathRequest = ExecutionPathRequest.builder()
            .startingMethod(startingMethod)
            .traceType(ExecutionPathRequest.PathTraceType.METHOD_CALLS)
            .maxDepth(8)
            .build();
        
        ExecutionPathResult result = executionPathTracer.traceExecutionPath(pathRequest);
        
        context.getToolResults().put("executionPath", result);
        
        context.getExecutionSteps().add(OrchestrationResult.OrchestrationStep.builder()
            .stepNumber(stepNumber)
            .toolName("ExecutionPathTracer")
            .purpose("Trace execution paths and flow")
            .inputSummary("Starting method: " + startingMethod)
            .outputSummary("Traced " + result.getExecutionPath().size() + " steps")
            .executionTimeMs(180)
            .successful(true)
            .build());
    }
    
    /**
     * 📚 Execute Code Context Enricher
     */
    private void executeCodeContextEnricher(OrchestrationContext context, int stepNumber) {
        
        log.debug("📚 Executing Code Context Enricher");
        
        List<String> targetEntities = getRelevantEntities(context);
        
        ContextEnrichmentRequest enrichmentRequest = ContextEnrichmentRequest.builder()
            .targetEntities(targetEntities)
            .enrichmentType(ContextEnrichmentRequest.EnrichmentType.COMPREHENSIVE)
            .scope(ContextEnrichmentRequest.EnrichmentScope.MODERATE)
            .includeUsagePatterns(true)
            .includeRecommendations(true)
            .build();
        
        ContextEnrichmentResult result = codeContextEnricher.enrichContext(enrichmentRequest);
        
        context.getToolResults().put("contextEnrichment", result);
        
        context.getExecutionSteps().add(OrchestrationResult.OrchestrationStep.builder()
            .stepNumber(stepNumber)
            .toolName("CodeContextEnricher")
            .purpose("Enrich entities with contextual information")
            .inputSummary("Target entities: " + targetEntities.size())
            .outputSummary("Enriched " + result.getEnrichedEntities().size() + " entities")
            .executionTimeMs(160)
            .successful(true)
            .build());
    }
    
    /**
     * 📖 Execute Flow Narrative Generator
     */
    private void executeFlowNarrativeGenerator(OrchestrationContext context, int stepNumber) {
        
        log.debug("📖 Executing Flow Narrative Generator");
        
        List<String> targetEntities = getRelevantEntities(context);
        
        NarrativeGenerationRequest narrativeRequest = NarrativeGenerationRequest.builder()
            .targetEntities(targetEntities)
            .narrativeType(NarrativeGenerationRequest.NarrativeType.COMPREHENSIVE_DOCUMENTATION)
            .audience(NarrativeGenerationRequest.AudienceType.DEVELOPERS)
            .detailLevel(NarrativeGenerationRequest.DetailLevel.MODERATE)
            .includeContext(true)
            .includeRecommendations(true)
            .build();
        
        NarrativeGenerationResult result = flowNarrativeGenerator.generateNarrative(narrativeRequest);
        
        context.getToolResults().put("narrativeGeneration", result);
        
        context.getExecutionSteps().add(OrchestrationResult.OrchestrationStep.builder()
            .stepNumber(stepNumber)
            .toolName("FlowNarrativeGenerator")
            .purpose("Generate human-readable documentation")
            .inputSummary("Target entities: " + targetEntities.size())
            .outputSummary("Generated narrative with " + (result.getPrimaryNarrative() != null ? 
                result.getPrimaryNarrative().getSections().size() : 0) + " sections")
            .executionTimeMs(220)
            .successful(true)
            .build());
    }
    
    /**
     * 🔄 Synthesize results from all tools into coherent insights
     */
    private void synthesizeToolResults(OrchestrationContext context) {
        
        log.debug("🔄 Synthesizing tool results");
        
        List<OrchestrationResult.SynthesizedInsight> insights = new ArrayList<>();
        
        // Synthesize insights from semantic search
        SemanticSearchResult semanticResult = (SemanticSearchResult) context.getToolResults().get("semanticSearch");
        if (semanticResult != null) {
            insights.add(OrchestrationResult.SynthesizedInsight.builder()
                .title("Code Entity Discovery")
                .description("Found " + semanticResult.getMatches().size() + " relevant code entities")
                .type(OrchestrationResult.SynthesizedInsight.InsightType.ARCHITECTURAL_FINDING)
                .confidence(semanticResult.getQualityScore())
                .sourcingTools(List.of("SemanticCodeHunter"))
                .build());
        }
        
        // Synthesize insights from structural analysis
        StructuralAnalysisResult structuralResult = (StructuralAnalysisResult) context.getToolResults().get("structuralAnalysis");
        if (structuralResult != null) {
            insights.add(OrchestrationResult.SynthesizedInsight.builder()
                .title("Structural Patterns")
                .description("Identified " + structuralResult.getPatterns().size() + " architectural patterns")
                .type(OrchestrationResult.SynthesizedInsight.InsightType.ARCHITECTURAL_FINDING)
                .confidence(structuralResult.getConfidence())
                .sourcingTools(List.of("StructuralCodeExplorer"))
                .build());
        }
        
        // Synthesize insights from execution path tracing
        ExecutionPathResult pathResult = (ExecutionPathResult) context.getToolResults().get("executionPath");
        if (pathResult != null) {
            insights.add(OrchestrationResult.SynthesizedInsight.builder()
                .title("Execution Flow Analysis")
                .description("Traced execution path with " + pathResult.getExecutionPath().size() + " steps")
                .type(OrchestrationResult.SynthesizedInsight.InsightType.PERFORMANCE_INSIGHT)
                .confidence(pathResult.getConfidence())
                .sourcingTools(List.of("ExecutionPathTracer"))
                .build());
        }
        
        // Synthesize insights from context enrichment
        ContextEnrichmentResult contextResult = (ContextEnrichmentResult) context.getToolResults().get("contextEnrichment");
        if (contextResult != null) {
            insights.add(OrchestrationResult.SynthesizedInsight.builder()
                .title("Business Context")
                .description("Enriched " + contextResult.getEnrichedEntities().size() + " entities with business context")
                .type(OrchestrationResult.SynthesizedInsight.InsightType.BUSINESS_IMPACT)
                .confidence(contextResult.getConfidence())
                .sourcingTools(List.of("CodeContextEnricher"))
                .build());
        }
        
        context.setSynthesizedInsights(insights);
        
        log.debug("🔄 Synthesized {} insights from tool results", insights.size());
    }
    
    /**
     * 💡 Generate final recommendations based on all analysis
     */
    private void generateFinalRecommendations(OrchestrationContext context) {
        
        log.debug("💡 Generating final recommendations");
        
        List<OrchestrationResult.OrchestrationRecommendation> recommendations = new ArrayList<>();
        
        // Generate recommendations based on goal
        OrchestrationRequest.OrchestrationGoal goal = context.getRequest().getGoal();
        
        switch (goal) {
            case CODE_UNDERSTANDING -> {
                recommendations.add(OrchestrationResult.OrchestrationRecommendation.builder()
                    .title("Review Generated Documentation")
                    .description("Review the generated documentation to better understand the code structure")
                    .type(OrchestrationResult.OrchestrationRecommendation.RecommendationType.DOCUMENTATION_UPDATE)
                    .priority(0.8)
                    .expectedBenefit("Improved code understanding and maintainability")
                    .build());
            }
            case PERFORMANCE_ANALYSIS -> {
                recommendations.add(OrchestrationResult.OrchestrationRecommendation.builder()
                    .title("Optimize Critical Path")
                    .description("Focus optimization efforts on the identified critical execution paths")
                    .type(OrchestrationResult.OrchestrationRecommendation.RecommendationType.PERFORMANCE_OPTIMIZATION)
                    .priority(0.9)
                    .expectedBenefit("Improved system performance and response times")
                    .build());
            }
            case ARCHITECTURE_ANALYSIS -> {
                recommendations.add(OrchestrationResult.OrchestrationRecommendation.builder()
                    .title("Refactor Architecture")
                    .description("Consider architectural improvements based on identified patterns")
                    .type(OrchestrationResult.OrchestrationRecommendation.RecommendationType.ARCHITECTURE_IMPROVEMENT)
                    .priority(0.7)
                    .expectedBenefit("Better code organization and maintainability")
                    .build());
            }
            default -> {
                recommendations.add(OrchestrationResult.OrchestrationRecommendation.builder()
                    .title("Regular Code Review")
                    .description("Implement regular code review processes based on insights")
                    .type(OrchestrationResult.OrchestrationRecommendation.RecommendationType.TEAM_COLLABORATION)
                    .priority(0.6)
                    .expectedBenefit("Improved code quality and team collaboration")
                    .build());
            }
        }
        
        context.setRecommendations(recommendations);
        
        log.debug("💡 Generated {} recommendations", recommendations.size());
    }
    
    /**
     * 🎯 Generate comprehensive answer to user's query
     */
    private void generateUserQueryAnswer(OrchestrationContext context) {
        
        log.debug("🎯 Generating user query answer");
        
        StringBuilder answer = new StringBuilder();
        
        // Add executive summary
        answer.append("Based on comprehensive analysis of your request, here are the key findings:\n\n");
        
        // Add insights
        if (!context.getSynthesizedInsights().isEmpty()) {
            answer.append("**Key Insights:**\n");
            for (OrchestrationResult.SynthesizedInsight insight : context.getSynthesizedInsights()) {
                answer.append("- ").append(insight.getTitle()).append(": ").append(insight.getDescription()).append("\n");
            }
            answer.append("\n");
        }
        
        // Add recommendations
        if (!context.getRecommendations().isEmpty()) {
            answer.append("**Recommendations:**\n");
            for (OrchestrationResult.OrchestrationRecommendation rec : context.getRecommendations()) {
                answer.append("- ").append(rec.getTitle()).append(": ").append(rec.getDescription()).append("\n");
            }
            answer.append("\n");
        }
        
        // Add execution summary
        answer.append("**Analysis Summary:**\n");
        answer.append("Executed ").append(context.getExecutionSteps().size()).append(" analysis steps ");
        answer.append("to provide comprehensive insights into your code.\n");
        
        context.setUserQueryAnswer(answer.toString());
        
        log.debug("🎯 Generated user query answer ({} characters)", answer.length());
    }
    
    /**
     * 📊 Calculate quality metrics for the orchestration
     */
    private void calculateQualityMetrics(OrchestrationContext context) {
        
        log.debug("📊 Calculating quality metrics");
        
        // Calculate success rate
        long successfulSteps = context.getExecutionSteps().stream()
            .filter(OrchestrationResult.OrchestrationStep::isSuccessful)
            .count();
        
        double successRate = (double) successfulSteps / context.getExecutionSteps().size();
        
        // Calculate quality factors
        context.getQualityFactors().put("completeness", successRate);
        context.getQualityFactors().put("accuracy", 0.85); // Mock value
        context.getQualityFactors().put("coherence", 0.80); // Mock value
        context.getQualityFactors().put("relevance", 0.90); // Mock value
        context.getQualityFactors().put("actionability", 0.75); // Mock value
        
        log.debug("📊 Quality metrics calculated: success rate = {:.2f}", successRate);
    }
    
    /**
     * 🏗️ Build final orchestration result
     */
    private OrchestrationResult buildOrchestrationResult(OrchestrationContext context) {
        
        log.debug("🏗️ Building final orchestration result");
        
        long totalExecutionTime = System.currentTimeMillis() - context.getStartTime();
        
        // Build tool results
        OrchestrationResult.ToolExecutionResults toolResults = OrchestrationResult.ToolExecutionResults.builder()
            .queryClassification((IntentClassificationResult) context.getToolResults().get("queryClassification"))
            .semanticSearch((SemanticSearchResult) context.getToolResults().get("semanticSearch"))
            .structuralAnalysis((StructuralAnalysisResult) context.getToolResults().get("structuralAnalysis"))
            .executionPath((ExecutionPathResult) context.getToolResults().get("executionPath"))
            .contextEnrichment((ContextEnrichmentResult) context.getToolResults().get("contextEnrichment"))
            .narrativeGeneration((NarrativeGenerationResult) context.getToolResults().get("narrativeGeneration"))
            .executedTools(context.getExecutionPlan())
            .skippedTools(List.of())
            .build();
        
        // Build quality metrics
        OrchestrationResult.OrchestrationQualityMetrics qualityMetrics = OrchestrationResult.OrchestrationQualityMetrics.builder()
            .completeness(context.getQualityFactors().getOrDefault("completeness", 0.0))
            .accuracy(context.getQualityFactors().getOrDefault("accuracy", 0.0))
            .coherence(context.getQualityFactors().getOrDefault("coherence", 0.0))
            .relevance(context.getQualityFactors().getOrDefault("relevance", 0.0))
            .actionability(context.getQualityFactors().getOrDefault("actionability", 0.0))
            .toolSynergy(0.8) // Mock value
            .overallGrade("B+")
            .qualityFactors(List.of("Comprehensive analysis", "Good tool coordination"))
            .limitationFactors(List.of("Limited business context"))
            .build();
        
        // Build performance metrics
        OrchestrationResult.OrchestrationPerformanceMetrics performanceMetrics = OrchestrationResult.OrchestrationPerformanceMetrics.builder()
            .totalExecutionTimeMs(totalExecutionTime)
            .averageToolExecutionTimeMs(totalExecutionTime / context.getExecutionSteps().size())
            .parallelizationEfficiency(0.6) // Mock value
            .totalToolsExecuted(context.getExecutionSteps().size())
            .successfulToolExecutions((int) context.getExecutionSteps().stream()
                .filter(OrchestrationResult.OrchestrationStep::isSuccessful).count())
            .failedToolExecutions((int) context.getExecutionSteps().stream()
                .filter(step -> !step.isSuccessful()).count())
            .performanceGrade("B")
            .build();
        
        // Calculate overall confidence
        double confidence = context.getQualityFactors().values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.5);
        
        return OrchestrationResult.builder()
            .executionSummary(generateExecutionSummary(context))
            .keyInsights(context.getSynthesizedInsights())
            .toolResults(toolResults)
            .executionFlow(context.getExecutionSteps())
            .recommendations(context.getRecommendations())
            .qualityMetrics(qualityMetrics)
            .performanceMetrics(performanceMetrics)
            .userQueryAnswer(context.getUserQueryAnswer())
            .confidence(confidence)
            .nextActions(List.of()) // TODO: Generate next actions
            .supportingEvidence(Map.of("totalToolsExecuted", context.getExecutionSteps().size()))
            .metadata(Map.of(
                "orchestrationTimeMs", totalExecutionTime,
                "successful", true,
                "timestamp", LocalDateTime.now().toString(),
                "goal", context.getRequest().getGoal().name()
            ))
            .build();
    }
    
    // Helper methods
    
    private List<String> extractSearchTerms(String query) {
        // Simple keyword extraction - in real implementation, use NLP
        return Arrays.asList(query.toLowerCase().split("\\s+"));
    }
    
    private List<String> getRelevantEntities(OrchestrationContext context) {
        // Extract entities from previous tool results
        List<String> entities = new ArrayList<>();
        
        if (context.getRequest().getFocusEntities() != null) {
            entities.addAll(context.getRequest().getFocusEntities());
        }
        
        // Extract from semantic search results
        SemanticSearchResult semanticResult = (SemanticSearchResult) context.getToolResults().get("semanticSearch");
        if (semanticResult != null && !semanticResult.getMatches().isEmpty()) {
            entities.addAll(semanticResult.getMatches().stream()
                .map(match -> match.getEntityId())
                .limit(10)
                .toList());
        }
        
        // Return default entities if none found
        return entities.isEmpty() ? List.of("com.example.DefaultEntity") : entities;
    }
    
    private String generateExecutionSummary(OrchestrationContext context) {
        long successfulSteps = context.getExecutionSteps().stream()
            .filter(OrchestrationResult.OrchestrationStep::isSuccessful)
            .count();
        
        return String.format("Orchestration completed with %d/%d successful steps. " +
            "Analyzed code for %s goal and generated %d insights with %d recommendations.",
            successfulSteps, context.getExecutionSteps().size(),
            context.getRequest().getGoal().name(),
            context.getSynthesizedInsights().size(),
            context.getRecommendations().size());
    }
    
    // Supporting data class
    
    @lombok.Data
    @lombok.Builder
    public static class OrchestrationContext {
        private long startTime;
        private OrchestrationRequest request;
        private List<OrchestrationResult.OrchestrationStep> executionSteps;
        private Map<String, Object> toolResults;
        private List<OrchestrationResult.SynthesizedInsight> synthesizedInsights;
        private List<OrchestrationResult.OrchestrationRecommendation> recommendations;
        private Map<String, Double> qualityFactors;
        private List<String> executionPlan;
        private String userQueryAnswer;
    }
}