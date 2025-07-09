package com.tekion.javaastkg.query.services;

import com.tekion.javaastkg.agents.entity.AgentBasedEntityExtractor;
import com.tekion.javaastkg.agents.entity.analysis.LLMEntityAnalyzer;
import com.tekion.javaastkg.model.ExtractedEntities;
import com.tekion.javaastkg.query.intelligence.ExpansionQualityFilter;
import com.tekion.javaastkg.query.intelligence.IntentBasedSearchStrategy;
import com.tekion.javaastkg.query.intelligence.MultiLevelExpander;
import com.tekion.javaastkg.query.intelligence.QueryIntentAnalyzer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Enhanced entity extraction service that integrates intelligent query expansion.
 * Combines basic entity extraction with multi-level expansion and quality filtering.
 */
@Service
@Slf4j
public class EnhancedEntityExtractor {

    private final AgentBasedEntityExtractor agentExtractor;
    private final LLMEntityAnalyzer llmEntityAnalyzer;
    private final QueryIntentAnalyzer intentAnalyzer;
    private final MultiLevelExpander multiLevelExpander;
    private final ExpansionQualityFilter qualityFilter;
    private final IntentBasedSearchStrategy strategyBuilder;

    @Value("${query_optimization.enabled:true}")
    private boolean queryOptimizationEnabled;

    @Value("${query_optimization.use_basic_extraction:true}")
    private boolean useBasicExtraction;

    @Value("${query_optimization.use_llm_analysis:true}")
    private boolean useLLMAnalysis;

    @Value("${query_optimization.log_expansion_details:false}")
    private boolean logExpansionDetails;

    public EnhancedEntityExtractor(AgentBasedEntityExtractor agentExtractor,
                                  LLMEntityAnalyzer llmEntityAnalyzer,
                                  QueryIntentAnalyzer intentAnalyzer,
                                  MultiLevelExpander multiLevelExpander,
                                  ExpansionQualityFilter qualityFilter,
                                  IntentBasedSearchStrategy strategyBuilder) {
        this.agentExtractor = agentExtractor;
        this.llmEntityAnalyzer = llmEntityAnalyzer;
        this.intentAnalyzer = intentAnalyzer;
        this.multiLevelExpander = multiLevelExpander;
        this.qualityFilter = qualityFilter;
        this.strategyBuilder = strategyBuilder;
    }

    /**
     * Extracts and intelligently expands entities from a query
     */
    public ExtractedEntities extractAndExpand(String query) {
        log.info("Enhanced entity extraction for query: {}", query);
        
        // If query optimization is disabled, fall back to agent extraction
        if (!queryOptimizationEnabled) {
            log.debug("Query optimization disabled, using agent extraction");
            return convertToEnhancedEntities(agentExtractor.extract(query), query, null, null);
        }
        
        try {
            // Step 1: Analyze query intent
            QueryIntentAnalyzer.QueryIntent intent = intentAnalyzer.analyzeIntent(query);
            log.info("Detected intent: {} with confidence: {}", intent.getPrimaryIntent(), intent.getConfidence());
            
            // Step 2: LLM-based entity analysis - NEW STEP REQUESTED BY USER
            ExtractedEntities llmEntities = null;
            if (useLLMAnalysis) {
                log.info("ENHANCED_ENTITY_EXTRACTOR: Starting LLM-based entity analysis for query: {}", query);
                llmEntities = llmEntityAnalyzer.analyzeEntities(query);
                log.info("ENHANCED_ENTITY_EXTRACTOR: LLM analysis found: {} classes, {} methods, {} packages, {} terms",
                    llmEntities.getClasses().size(),
                    llmEntities.getMethods().size(),
                    llmEntities.getPackages().size(),
                    llmEntities.getTerms().size());
            }
            
            // Step 3: Extract entities using agent system (optional)
            ExtractedEntities agentEntities = null;
            if (useBasicExtraction) {
                agentEntities = agentExtractor.extract(query);
                log.info("Agent extraction found: {} classes, {} methods, {} packages, {} terms",
                    agentEntities.getClasses().size(),
                    agentEntities.getMethods().size(),
                    agentEntities.getPackages().size(),
                    agentEntities.getTerms().size());
            }
            
            // Step 4: Perform multi-level expansion
            MultiLevelExpander.QueryExpansion expansion = multiLevelExpander.expandQuery(query, intent);
            log.info("Multi-level expansion generated {} total terms", expansion.getTotalTermCount());
            
            // Step 5: Apply quality filtering
            ExpansionQualityFilter.QualityFilterResult filterResult = 
                qualityFilter.filterWithQualityMetrics(expansion.getAllTerms(), query, intent);
            log.info("Quality filter reduced terms from {} to {}",
                expansion.getTotalTermCount(), filterResult.getFilteredTermCount());
            
            // Step 6: Create search strategy
            IntentBasedSearchStrategy.SearchStrategy searchStrategy = 
                strategyBuilder.createStrategy(intent, expansion);
            log.info("Created search strategy: {}", searchStrategy.getStrategyName());
            
            // Step 7: Build enhanced extracted entities
            ExtractedEntities enhancedEntities = buildEnhancedEntities(
                llmEntities, agentEntities, expansion, filterResult, intent, searchStrategy
            );
            
            log.info("ENHANCED_ENTITY_EXTRACTOR: Final combined entities - {} classes, {} methods, {} packages, {} terms",
                    enhancedEntities.getClasses().size(), enhancedEntities.getMethods().size(),
                    enhancedEntities.getPackages().size(), enhancedEntities.getTerms().size());
            
            log.info("ENHANCED_ENTITY_EXTRACTOR: Final CLASSES: {}", enhancedEntities.getClasses());
            log.info("ENHANCED_ENTITY_EXTRACTOR: Final METHODS: {}", enhancedEntities.getMethods());
            
            if (logExpansionDetails) {
                logExpansionDetails(enhancedEntities);
            }
            
            return enhancedEntities;
            
        } catch (Exception e) {
            log.error("Enhanced entity extraction failed, falling back to agent extraction", e);
            return convertToEnhancedEntities(agentExtractor.extract(query), query, null, null);
        }
    }
    
    /**
     * Builds enhanced entities from all components
     */
    private ExtractedEntities buildEnhancedEntities(ExtractedEntities llmEntities,
                                                   ExtractedEntities agentEntities,
                                                   MultiLevelExpander.QueryExpansion expansion,
                                                   ExpansionQualityFilter.QualityFilterResult filterResult,
                                                   QueryIntentAnalyzer.QueryIntent intent,
                                                   IntentBasedSearchStrategy.SearchStrategy strategy) {
        
        // Categorize filtered terms into appropriate entity types
        List<String> expandedClasses = new ArrayList<>();
        List<String> expandedMethods = new ArrayList<>();
        List<String> expandedPackages = new ArrayList<>();
        List<String> expandedTerms = new ArrayList<>();
        
        for (MultiLevelExpander.WeightedTerm weightedTerm : filterResult.getFilteredTerms()) {
            String term = weightedTerm.getTerm();
            String source = weightedTerm.getSource();
            
            // Categorize based on naming patterns and source
            if (isClassName(term, source)) {
                expandedClasses.add(term);
            } else if (isMethodName(term, source)) {
                expandedMethods.add(term);
            } else if (isPackageName(term, source)) {
                expandedPackages.add(term);
            } else {
                expandedTerms.add(term);
            }
        }
        
        // Merge with LLM entities first (highest priority)
        if (llmEntities != null) {
            expandedClasses.addAll(0, llmEntities.getClasses());
            expandedMethods.addAll(0, llmEntities.getMethods());
            expandedPackages.addAll(0, llmEntities.getPackages());
            expandedTerms.addAll(0, llmEntities.getTerms());
        }
        
        // Merge with agent entities if available
        if (agentEntities != null) {
            expandedClasses.addAll(agentEntities.getClasses());
            expandedMethods.addAll(agentEntities.getMethods());
            expandedPackages.addAll(agentEntities.getPackages());
            expandedTerms.addAll(agentEntities.getTerms());
        }
        
        // Remove duplicates while preserving order
        expandedClasses = removeDuplicatesPreservingOrder(expandedClasses);
        expandedMethods = removeDuplicatesPreservingOrder(expandedMethods);
        expandedPackages = removeDuplicatesPreservingOrder(expandedPackages);
        expandedTerms = removeDuplicatesPreservingOrder(expandedTerms);
        
        // Apply strategy-based limits
        if (strategy != null) {
            int maxPerCategory = strategy.getMaxSearchResults() / 4;
            expandedClasses = limitList(expandedClasses, maxPerCategory);
            expandedMethods = limitList(expandedMethods, maxPerCategory);
            expandedPackages = limitList(expandedPackages, maxPerCategory);
            expandedTerms = limitList(expandedTerms, maxPerCategory);
        }
        
        return ExtractedEntities.builder()
            .classes(expandedClasses)
            .methods(expandedMethods)
            .packages(expandedPackages)
            .terms(expandedTerms)
            .build();
    }
    
    /**
     * Checks if a term is likely a class name
     */
    private boolean isClassName(String term, String source) {
        // Check if it starts with uppercase (Java convention)
        if (!Character.isUpperCase(term.charAt(0))) {
            return false;
        }
        
        // Check for common class suffixes
        String[] classSuffixes = {
            "Service", "Controller", "Manager", "Handler", "Engine",
            "Factory", "Builder", "Repository", "DAO", "Processor",
            "Provider", "Consumer", "Listener", "Observer", "Adapter"
        };
        
        for (String suffix : classSuffixes) {
            if (term.endsWith(suffix)) {
                return true;
            }
        }
        
        // Check source hints
        if (source != null && (source.contains("pattern") || source.contains("compound"))) {
            return Character.isUpperCase(term.charAt(0));
        }
        
        return false;
    }
    
    /**
     * Checks if a term is likely a method name
     */
    private boolean isMethodName(String term, String source) {
        // Check if it starts with lowercase (Java convention)
        if (!Character.isLowerCase(term.charAt(0))) {
            return false;
        }
        
        // Check for common method prefixes
        String[] methodPrefixes = {
            "get", "set", "is", "has", "can", "should",
            "create", "build", "process", "handle", "execute",
            "validate", "check", "find", "search", "save"
        };
        
        for (String prefix : methodPrefixes) {
            if (term.startsWith(prefix)) {
                return true;
            }
        }
        
        // Check if it ends with parentheses
        if (term.endsWith("()")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if a term is likely a package name
     */
    private boolean isPackageName(String term, String source) {
        // Check for dot notation
        if (term.contains(".")) {
            return true;
        }
        
        // Check if all lowercase (package convention)
        if (term.equals(term.toLowerCase()) && term.length() > 2) {
            // Common package names
            String[] commonPackages = {
                "service", "controller", "repository", "model", "entity",
                "dto", "util", "utils", "helper", "config", "security"
            };
            
            return Arrays.asList(commonPackages).contains(term);
        }
        
        return false;
    }
    
    /**
     * Removes duplicates while preserving order
     */
    private List<String> removeDuplicatesPreservingOrder(List<String> list) {
        LinkedHashSet<String> set = new LinkedHashSet<>(list);
        return new ArrayList<>(set);
    }
    
    /**
     * Limits list size
     */
    private List<String> limitList(List<String> list, int limit) {
        if (list.size() <= limit) {
            return list;
        }
        return list.subList(0, limit);
    }
    
    /**
     * Converts basic entities to enhanced format
     */
    private ExtractedEntities convertToEnhancedEntities(ExtractedEntities basic,
                                                       String query,
                                                       QueryIntentAnalyzer.QueryIntent intent,
                                                       IntentBasedSearchStrategy.SearchStrategy strategy) {
        return ExtractedEntities.builder()
            .classes(basic.getClasses())
            .methods(basic.getMethods())
            .packages(basic.getPackages())
            .terms(basic.getTerms())
            .build();
    }
    
    /**
     * Logs expansion details for debugging
     */
    private void logExpansionDetails(ExtractedEntities entities) {
        log.info("=== Expansion Details ===");
        log.info("Classes: {}", entities.getClasses());
        log.info("Methods: {}", entities.getMethods());
        log.info("Packages: {}", entities.getPackages());
        log.info("Terms: {}", entities.getTerms());
        log.info("Total entities: {}", entities.getTotalCount());
        log.info("========================");
    }

    
    /**
     * Confidence level enumeration
     */
    public enum ConfidenceLevel {
        HIGH,
        MEDIUM,
        LOW,
        ALL
    }
}