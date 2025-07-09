package com.tekion.javaastkg.agents.entity;

import com.tekion.javaastkg.agents.entity.analysis.QueryAnalysisAgent;
import com.tekion.javaastkg.agents.entity.extraction.ExtractionAgent;
import com.tekion.javaastkg.agents.entity.extraction.FuzzyMatchingAgent;
import com.tekion.javaastkg.agents.entity.extraction.PatternMatchingAgent;
import com.tekion.javaastkg.agents.entity.extraction.SemanticMatchingAgent;
import com.tekion.javaastkg.agents.entity.models.EntityMatch;
import com.tekion.javaastkg.agents.entity.models.QueryContext;
import com.tekion.javaastkg.model.ExtractedEntities;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Intelligent Entity Extractor - Orchestrates multiple extraction agents.
 * Implements Factor 12 (Stateless Reducer) and Factor 8 (Own Your Control Flow).
 * 
 * Applied 12-Factor Principles:
 * - Factor 1: Structured outputs (ExtractedEntities)
 * - Factor 4: Tools as structured outputs
 * - Factor 8: Owns control flow (explicit agent orchestration)
 * - Factor 10: Composes small focused agents
 * - Factor 12: Stateless orchestrator (pure function)
 */
@Service
@Slf4j
public class IntelligentEntityExtractor {
    
    private final QueryAnalysisAgent queryAnalyzer;
    private final List<ExtractionAgent> extractionAgents;
    private final Executor agentExecutor;
    
    // Configuration
    private static final int MAX_TOTAL_RESULTS = 50;
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.3;
    private static final long MAX_EXTRACTION_TIME_MS = 5000; // 5 seconds max
    
    public IntelligentEntityExtractor(QueryAnalysisAgent queryAnalyzer,
                                    PatternMatchingAgent patternAgent,
                                    FuzzyMatchingAgent fuzzyAgent,
                                    SemanticMatchingAgent semanticAgent) {
        this.queryAnalyzer = queryAnalyzer;
        this.extractionAgents = List.of(patternAgent, fuzzyAgent, semanticAgent);
        this.agentExecutor = Executors.newFixedThreadPool(4); // Parallel agent execution
    }
    
    /**
     * Factor 12: Pure function - same input always produces same output
     * Factor 8: Owns control flow explicitly
     */
    public ExtractedEntities extract(String query) {
        log.info("Starting intelligent entity extraction for query: {}", query);
        long startTime = System.currentTimeMillis();
        
        try {
            // Step 1: Analyze query (5-10ms)
            QueryContext context = queryAnalyzer.analyze(query);
            log.debug("Query analysis completed in {}ms", System.currentTimeMillis() - startTime);
            
            // Step 2: Select and execute agents in parallel (20-100ms)
            List<AgentResult> agentResults = executeAgentsInParallel(context);
            log.debug("Agent execution completed in {}ms", System.currentTimeMillis() - startTime);
            
            // Step 3: Combine and resolve results (5-10ms)
            List<EntityMatch> combinedMatches = combineAgentResults(agentResults);
            log.debug("Result combination completed in {}ms", System.currentTimeMillis() - startTime);
            
            // Step 4: Rank and filter final results (5-10ms)
            List<EntityMatch> finalMatches = rankAndFilterResults(combinedMatches, context);
            log.debug("Ranking and filtering completed in {}ms", System.currentTimeMillis() - startTime);
            
            // Step 5: Build final extracted entities
            ExtractedEntities result = buildExtractedEntities(finalMatches, context, agentResults);
            
            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Intelligent extraction completed in {}ms. Found {} entities", 
                    totalTime, result.getTotalCount());
            
            return result;
            
        } catch (Exception e) {
            log.error("Intelligent entity extraction failed for query: {}", query, e);
            return createEmptyResult(query);
        }
    }
    
    /**
     * Step 2: Execute all applicable agents in parallel
     * Factor 8: Explicit control flow with parallel execution
     */
    private List<AgentResult> executeAgentsInParallel(QueryContext context) {
        // Select agents that can handle this query
        List<ExtractionAgent> applicableAgents = selectApplicableAgents(context);
        
        // Execute agents in parallel
        List<CompletableFuture<AgentResult>> futures = applicableAgents.stream()
                .map(agent -> CompletableFuture.supplyAsync(() -> executeAgent(agent, context), agentExecutor))
                .collect(Collectors.toList());
        
        // Wait for all agents to complete
        List<AgentResult> results = new ArrayList<>();
        for (CompletableFuture<AgentResult> future : futures) {
            try {
                AgentResult result = future.get();
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception e) {
                log.warn("Agent execution failed", e);
            }
        }
        
        return results;
    }
    
    /**
     * Selects agents that can handle the query context
     */
    private List<ExtractionAgent> selectApplicableAgents(QueryContext context) {
        return extractionAgents.stream()
                .filter(agent -> agent.canHandle(context))
                .sorted((a, b) -> Double.compare(
                    b.getHandlingConfidence(context), 
                    a.getHandlingConfidence(context)
                ))
                .collect(Collectors.toList());
    }
    
    /**
     * Executes a single agent with error handling and timing
     */
    private AgentResult executeAgent(ExtractionAgent agent, QueryContext context) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("Executing agent: {}", agent.getAgentName());
            
            List<EntityMatch> matches = agent.extract(context);
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.debug("Agent {} completed in {}ms with {} matches", 
                    agent.getAgentName(), executionTime, matches.size());
            
            return AgentResult.builder()
                    .agentName(agent.getAgentName())
                    .matches(matches)
                    .executionTimeMs(executionTime)
                    .success(true)
                    .confidence(agent.getHandlingConfidence(context))
                    .build();
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.warn("Agent {} failed after {}ms", agent.getAgentName(), executionTime, e);
            
            return AgentResult.builder()
                    .agentName(agent.getAgentName())
                    .matches(Collections.emptyList())
                    .executionTimeMs(executionTime)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .confidence(0.0)
                    .build();
        }
    }
    
    /**
     * Step 3: Combine results from all agents
     * Factor 12: Pure function for result combination
     */
    private List<EntityMatch> combineAgentResults(List<AgentResult> agentResults) {
        Map<String, EntityMatch> uniqueMatches = new HashMap<>();
        
        for (AgentResult result : agentResults) {
            if (!result.isSuccess()) continue;
            
            for (EntityMatch match : result.getMatches()) {
                String key = match.getEntityId();
                EntityMatch existing = uniqueMatches.get(key);
                
                if (existing == null) {
                    // First time seeing this entity
                    uniqueMatches.put(key, match);
                } else {
                    // Merge matches - keep highest confidence
                    if (match.getConfidence() > existing.getConfidence()) {
                        // Update with higher confidence match but keep track of sources
                        match.setMatchReason(existing.getMatchReason() + "; " + match.getMatchReason());
                        uniqueMatches.put(key, match);
                    } else {
                        // Keep existing but update reason
                        existing.setMatchReason(existing.getMatchReason() + "; " + match.getMatchReason());
                    }
                }
            }
        }
        
        return new ArrayList<>(uniqueMatches.values());
    }
    
    /**
     * Step 4: Rank and filter final results
     * Factor 12: Pure function for ranking
     */
    private List<EntityMatch> rankAndFilterResults(List<EntityMatch> matches, QueryContext context) {
        return matches.stream()
                .filter(match -> match.getConfidence() >= MIN_CONFIDENCE_THRESHOLD)
                .map(match -> enhanceMatchScore(match, context))
                .sorted((a, b) -> {
                    // Primary sort: confidence
                    int confidenceCompare = Double.compare(b.getConfidence(), a.getConfidence());
                    if (confidenceCompare != 0) return confidenceCompare;
                    
                    // Secondary sort: match type priority
                    int typeCompare = getMatchTypePriority(a.getMatchType()) - getMatchTypePriority(b.getMatchType());
                    if (typeCompare != 0) return typeCompare;
                    
                    // Tertiary sort: alphabetical
                    return a.getEntityName().compareTo(b.getEntityName());
                })
                .limit(MAX_TOTAL_RESULTS)
                .collect(Collectors.toList());
    }
    
    /**
     * Enhances match score based on query context
     */
    private EntityMatch enhanceMatchScore(EntityMatch match, QueryContext context) {
        double enhancedConfidence = match.getConfidence();
        
        // Boost for entity type alignment with query intent
        if (context.getIntent() != null) {
            if ((context.getIntent() == QueryContext.QueryIntent.FIND_CLASS && 
                 match.getEntityType() == EntityMatch.EntityType.CLASS) ||
                (context.getIntent() == QueryContext.QueryIntent.FIND_METHOD && 
                 match.getEntityType() == EntityMatch.EntityType.METHOD)) {
                enhancedConfidence += 0.1;
            }
        }
        
        // Boost for constraint satisfaction
        if (context.getConstraints() != null && matchesSconstraints(match, context.getConstraints())) {
            enhancedConfidence += 0.05;
        }
        
        // Cap at 1.0
        match.setConfidence(Math.min(1.0, enhancedConfidence));
        
        return match;
    }
    
    /**
     * Step 5: Build final extracted entities result
     */
    private ExtractedEntities buildExtractedEntities(List<EntityMatch> matches, 
                                                   QueryContext context, 
                                                   List<AgentResult> agentResults) {
        
        // Categorize matches by entity type
        List<String> classes = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        List<String> packages = new ArrayList<>();
        List<String> terms = new ArrayList<>();
        
        for (EntityMatch match : matches) {
            switch (match.getEntityType()) {
                case CLASS:
                    classes.add(match.getEntityName());
                    break;
                case METHOD:
                    methods.add(match.getEntityName());
                    break;
                case PACKAGE:
                    packages.add(match.getEntityName());
                    break;
                default:
                    terms.add(match.getEntityName());
            }
        }
        
        // Build performance metrics
        Map<String, Long> agentPerformance = agentResults.stream()
                .collect(Collectors.toMap(
                    AgentResult::getAgentName,
                    AgentResult::getExecutionTimeMs
                ));
        
        return ExtractedEntities.builder()
                .classes(classes)
                .methods(methods)
                .packages(packages)
                .terms(terms)
                .build();
    }
    
    // ========== UTILITY METHODS ==========
    
    private boolean matchesSconstraints(EntityMatch match, QueryContext.Constraints constraints) {
        // Simplified constraint checking
        if (constraints.getRequiredTypes() != null) {
            String entityType = match.getEntityType().name().toLowerCase();
            if (!constraints.getRequiredTypes().contains(entityType)) {
                return false;
            }
        }
        
        return true;
    }
    
    private int getMatchTypePriority(EntityMatch.MatchType matchType) {
        switch (matchType) {
            case EXACT: return 1;
            case PREFIX: return 2;
            case SUFFIX: return 3;
            case PATTERN: return 4;
            case SEMANTIC: return 5;
            case FUZZY: return 6;
            case PHONETIC: return 7;
            case ABBREVIATION: return 8;
            default: return 9;
        }
    }
    
    private ExtractedEntities createEmptyResult(String query) {
        return ExtractedEntities.builder()
                .classes(Collections.emptyList())
                .methods(Collections.emptyList())
                .packages(Collections.emptyList())
                .terms(Collections.emptyList())
                .build();
    }
    
    // ========== DATA CLASSES ==========
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentResult {
        private String agentName;
        private List<EntityMatch> matches;
        private long executionTimeMs;
        private boolean success;
        private String errorMessage;
        private double confidence;
    }

}