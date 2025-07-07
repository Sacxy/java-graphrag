package com.tekion.javaastkg.query.services;

import com.tekion.javaastkg.query.intelligence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced entity extraction service that integrates intelligent query expansion.
 * Combines basic entity extraction with multi-level expansion and quality filtering.
 */
@Service
@Slf4j
public class EnhancedEntityExtractor {

    private final EntityExtractor basicExtractor;
    private final QueryIntentAnalyzer intentAnalyzer;
    private final MultiLevelExpander multiLevelExpander;
    private final ExpansionQualityFilter qualityFilter;
    private final IntentBasedSearchStrategy strategyBuilder;
    
    @Value("${query_optimization.enabled:true}")
    private boolean queryOptimizationEnabled;
    
    @Value("${query_optimization.use_basic_extraction:true}")
    private boolean useBasicExtraction;
    
    @Value("${query_optimization.log_expansion_details:false}")
    private boolean logExpansionDetails;

    public EnhancedEntityExtractor(EntityExtractor basicExtractor,
                                  QueryIntentAnalyzer intentAnalyzer,
                                  MultiLevelExpander multiLevelExpander,
                                  ExpansionQualityFilter qualityFilter,
                                  IntentBasedSearchStrategy strategyBuilder) {
        this.basicExtractor = basicExtractor;
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
        
        // If query optimization is disabled, fall back to basic extraction
        if (!queryOptimizationEnabled) {
            log.debug("Query optimization disabled, using basic extraction");
            return convertToEnhancedEntities(basicExtractor.extract(query), query, null, null);
        }
        
        try {
            // Step 1: Analyze query intent
            QueryIntentAnalyzer.QueryIntent intent = intentAnalyzer.analyzeIntent(query);
            log.info("Detected intent: {} with confidence: {}", intent.getPrimaryIntent(), intent.getConfidence());
            
            // Step 2: Extract basic entities (optional)
            EntityExtractor.ExtractedEntities basicEntities = null;
            if (useBasicExtraction) {
                basicEntities = basicExtractor.extract(query);
                log.info("Basic extraction found: {} classes, {} methods, {} packages, {} terms",
                    basicEntities.getClasses().size(),
                    basicEntities.getMethods().size(),
                    basicEntities.getPackages().size(),
                    basicEntities.getTerms().size());
            }
            
            // Step 3: Perform multi-level expansion
            MultiLevelExpander.QueryExpansion expansion = multiLevelExpander.expandQuery(query, intent);
            log.info("Multi-level expansion generated {} total terms", expansion.getTotalTermCount());
            
            // Step 4: Apply quality filtering
            ExpansionQualityFilter.QualityFilterResult filterResult = 
                qualityFilter.filterWithQualityMetrics(expansion.getAllTerms(), query, intent);
            log.info("Quality filter reduced terms from {} to {}",
                expansion.getTotalTermCount(), filterResult.getFilteredTermCount());
            
            // Step 5: Create search strategy
            IntentBasedSearchStrategy.SearchStrategy searchStrategy = 
                strategyBuilder.createStrategy(intent, expansion);
            log.info("Created search strategy: {}", searchStrategy.getStrategyName());
            
            // Step 6: Build enhanced extracted entities
            ExtractedEntities enhancedEntities = buildEnhancedEntities(
                basicEntities, expansion, filterResult, intent, searchStrategy
            );
            
            if (logExpansionDetails) {
                logExpansionDetails(enhancedEntities);
            }
            
            return enhancedEntities;
            
        } catch (Exception e) {
            log.error("Enhanced entity extraction failed, falling back to basic extraction", e);
            return convertToEnhancedEntities(basicExtractor.extract(query), query, null, null);
        }
    }
    
    /**
     * Builds enhanced entities from all components
     */
    private ExtractedEntities buildEnhancedEntities(EntityExtractor.ExtractedEntities basicEntities,
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
        
        // Merge with basic entities if available
        if (basicEntities != null) {
            expandedClasses.addAll(0, basicEntities.getClasses());
            expandedMethods.addAll(0, basicEntities.getMethods());
            expandedPackages.addAll(0, basicEntities.getPackages());
            expandedTerms.addAll(0, basicEntities.getTerms());
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
            .intent(intent)
            .searchStrategy(strategy)
            .expansion(expansion)
            .highConfidenceTerms(expansion.getHighConfidenceTerms())
            .mediumConfidenceTerms(expansion.getMediumConfidenceTerms())
            .lowConfidenceTerms(expansion.getLowConfidenceTerms())
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
    private ExtractedEntities convertToEnhancedEntities(EntityExtractor.ExtractedEntities basic,
                                                       String query,
                                                       QueryIntentAnalyzer.QueryIntent intent,
                                                       IntentBasedSearchStrategy.SearchStrategy strategy) {
        return ExtractedEntities.builder()
            .classes(basic.getClasses())
            .methods(basic.getMethods())
            .packages(basic.getPackages())
            .terms(basic.getTerms())
            .intent(intent)
            .searchStrategy(strategy)
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
        log.info("Intent: {}", entities.getIntent() != null ? entities.getIntent().getPrimaryIntent() : "None");
        log.info("High confidence terms: {}", entities.getHighConfidenceTerms());
        log.info("========================");
    }

    /**
     * Enhanced extracted entities with expansion information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedEntities {
        private List<String> classes;
        private List<String> methods;
        private List<String> packages;
        private List<String> terms;
        
        // Enhanced fields
        private QueryIntentAnalyzer.QueryIntent intent;
        private IntentBasedSearchStrategy.SearchStrategy searchStrategy;
        private MultiLevelExpander.QueryExpansion expansion;
        private List<String> highConfidenceTerms;
        private List<String> mediumConfidenceTerms;
        private List<String> lowConfidenceTerms;
        
        /**
         * Returns true if any entities were extracted
         */
        public boolean hasEntities() {
            return !classes.isEmpty() || !methods.isEmpty() || 
                   !packages.isEmpty() || !terms.isEmpty();
        }
        
        /**
         * Returns all entities as a single list
         */
        public List<String> getAllEntities() {
            List<String> all = new ArrayList<>();
            all.addAll(classes);
            all.addAll(methods);
            all.addAll(packages);
            all.addAll(terms);
            return all;
        }
        
        /**
         * Gets entities filtered by confidence level
         */
        public List<String> getEntitiesByConfidence(ConfidenceLevel level) {
            switch (level) {
                case HIGH:
                    return highConfidenceTerms != null ? highConfidenceTerms : new ArrayList<>();
                case MEDIUM:
                    return mediumConfidenceTerms != null ? mediumConfidenceTerms : new ArrayList<>();
                case LOW:
                    return lowConfidenceTerms != null ? lowConfidenceTerms : new ArrayList<>();
                default:
                    return getAllEntities();
            }
        }
        
        /**
         * Checks if expansion was successful
         */
        public boolean isExpanded() {
            return expansion != null && expansion.getTotalTermCount() > 0;
        }
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