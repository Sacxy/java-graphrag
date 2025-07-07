package com.tekion.javaastkg.query.intelligence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Creates and manages search strategies based on query intent.
 * Determines how to weight different aspects of search based on what the user is trying to find.
 */
@Service
@Slf4j
public class IntentBasedSearchStrategy {

    /**
     * Creates a search strategy based on query intent and expansion
     */
    public SearchStrategy createStrategy(QueryIntentAnalyzer.QueryIntent intent, 
                                       MultiLevelExpander.QueryExpansion expansion) {
        if (intent == null) {
            log.info("No intent provided, using default search strategy");
            return createDefaultStrategy();
        }
        
        log.info("Creating search strategy for intent: {}", intent.getPrimaryIntent());
        
        SearchStrategy baseStrategy = switch (intent.getPrimaryIntent()) {
            case IMPLEMENTATION -> createImplementationStrategy(expansion);
            case USAGE -> createUsageStrategy(expansion);
            case CONFIGURATION -> createConfigurationStrategy(expansion);
            case DISCOVERY -> createDiscoveryStrategy(expansion);
            case STATUS -> createStatusStrategy(expansion);
        };
        
        // Apply secondary intent modifications
        if (intent.getSecondaryIntents() != null && !intent.getSecondaryIntents().isEmpty()) {
            applySecondaryIntentModifications(baseStrategy, intent.getSecondaryIntents());
        }
        
        // Apply context-based adjustments
        if (intent.getContexts() != null && !intent.getContexts().isEmpty()) {
            applyContextAdjustments(baseStrategy, intent.getContexts());
        }
        log.info("Base Strategy: {}", baseStrategy);
        return baseStrategy;
    }
    
    /**
     * Creates strategy for implementation-focused queries
     */
    private SearchStrategy createImplementationStrategy(MultiLevelExpander.QueryExpansion expansion) {
        return SearchStrategy.builder()
            .strategyName("Implementation Search")
            .searchDepth(SearchDepth.DEEP)
            .prioritizeMethodBodies(true)
            .includeDetailedDescriptions(true)
            .focusOnAlgorithmicContent(true)
            .includeInternalImplementations(true)
            
            // Weights
            .methodBodyWeight(0.8f)
            .descriptionWeight(0.7f)
            .signatureWeight(0.6f)
            .relationshipWeight(0.3f)
            .configurationWeight(0.2f)
            
            // Expansion usage
            .useHighConfidenceExpansions(true)
            .useMediumConfidenceExpansions(true)
            .useLowConfidenceExpansions(false)
            
            // Node type preferences
            .nodeTypeBoosts(Map.of(
                "Method", 0.8f,
                "Class", 0.6f,
                "Interface", 0.5f,
                "Description", 0.7f
            ))
            
            // Search configuration
            .maxSearchResults(100)
            .minScoreThreshold(0.1f)
            .expandGraphRelationships(true)
            .graphExpansionDepth(2)
            
            .build();
    }
    
    /**
     * Creates strategy for usage-focused queries
     */
    private SearchStrategy createUsageStrategy(MultiLevelExpander.QueryExpansion expansion) {
        return SearchStrategy.builder()
            .strategyName("Usage Search")
            .searchDepth(SearchDepth.WIDE)
            .prioritizeCallRelationships(true)
            .includeDependencyPatterns(true)
            .focusOnReferences(true)
            .includeGraphTraversal(true)
            
            // Weights
            .relationshipWeight(0.9f)
            .callPatternWeight(0.8f)
            .dependencyWeight(0.8f)
            .signatureWeight(0.5f)
            .methodBodyWeight(0.2f)
            
            // Expansion usage
            .useHighConfidenceExpansions(true)
            .useMediumConfidenceExpansions(true)
            .useLowConfidenceExpansions(true) // Cast wider net for usage
            
            // Node type preferences
            .nodeTypeBoosts(Map.of(
                "Method", 0.7f,
                "Class", 0.7f,
                "Interface", 0.8f, // Interfaces often define contracts
                "Call", 0.9f
            ))
            
            // Relationship preferences
            .relationshipBoosts(Map.of(
                "CALLS", 0.9f,
                "USES", 0.9f,
                "DEPENDS_ON", 0.8f,
                "EXTENDS", 0.7f,
                "IMPLEMENTS", 0.8f
            ))
            
            // Search configuration
            .maxSearchResults(150)
            .minScoreThreshold(0.05f)
            .expandGraphRelationships(true)
            .graphExpansionDepth(3)
            
            .build();
    }
    
    /**
     * Creates strategy for configuration-focused queries
     */
    private SearchStrategy createConfigurationStrategy(MultiLevelExpander.QueryExpansion expansion) {
        return SearchStrategy.builder()
            .strategyName("Configuration Search")
            .searchDepth(SearchDepth.TARGETED)
            .prioritizeAnnotations(true)
            .includePropertyFiles(true)
            .focusOnConfigurationNodes(true)
            
            // Weights
            .annotationWeight(0.9f)
            .propertyWeight(0.8f)
            .configFileWeight(0.8f)
            .configurationWeight(0.9f)
            .methodBodyWeight(0.3f)
            
            // Expansion usage
            .useHighConfidenceExpansions(true)
            .useMediumConfidenceExpansions(false)
            .useLowConfidenceExpansions(false)
            
            // Node type preferences
            .nodeTypeBoosts(Map.of(
                "Configuration", 0.9f,
                "Property", 0.9f,
                "Annotation", 0.8f,
                "Field", 0.6f
            ))
            
            // Pattern preferences
            .patternBoosts(Map.of(
                "@Value", 0.9f,
                "@ConfigurationProperties", 0.95f,
                "application.yml", 0.9f,
                "application.properties", 0.9f,
                "config", 0.7f,
                "Config", 0.7f
            ))
            
            // Search configuration
            .maxSearchResults(50)
            .minScoreThreshold(0.2f)
            .expandGraphRelationships(false)
            .graphExpansionDepth(1)
            
            .build();
    }
    
    /**
     * Creates strategy for discovery-focused queries
     */
    private SearchStrategy createDiscoveryStrategy(MultiLevelExpander.QueryExpansion expansion) {
        return SearchStrategy.builder()
            .strategyName("Discovery Search")
            .searchDepth(SearchDepth.BALANCED)
            .prioritizeServiceComponents(true)
            .includeArchitecturalPatterns(true)
            .focusOnPublicAPIs(true)
            
            // Weights
            .classWeight(0.8f)
            .interfaceWeight(0.9f)
            .publicMethodWeight(0.7f)
            .descriptionWeight(0.6f)
            .relationshipWeight(0.5f)
            
            // Expansion usage
            .useHighConfidenceExpansions(true)
            .useMediumConfidenceExpansions(true)
            .useLowConfidenceExpansions(false)
            
            // Node type preferences
            .nodeTypeBoosts(Map.of(
                "Service", 0.9f,
                "Controller", 0.9f,
                "Manager", 0.8f,
                "Handler", 0.8f,
                "Interface", 0.8f
            ))
            
            // Suffix preferences for discovery
            .suffixBoosts(Map.of(
                "Service", 0.8f,
                "Controller", 0.8f,
                "Manager", 0.7f,
                "Handler", 0.7f,
                "Processor", 0.7f,
                "Factory", 0.6f
            ))
            
            // Search configuration
            .maxSearchResults(100)
            .minScoreThreshold(0.1f)
            .expandGraphRelationships(true)
            .graphExpansionDepth(2)
            
            .build();
    }
    
    /**
     * Creates strategy for status-focused queries
     */
    private SearchStrategy createStatusStrategy(MultiLevelExpander.QueryExpansion expansion) {
        return SearchStrategy.builder()
            .strategyName("Status Search")
            .searchDepth(SearchDepth.TARGETED)
            .prioritizeEnums(true)
            .includeStateMachines(true)
            .focusOnStatusFields(true)
            
            // Weights
            .enumWeight(0.9f)
            .fieldWeight(0.7f)
            .constantWeight(0.8f)
            .statePatternWeight(0.8f)
            .methodBodyWeight(0.4f)
            
            // Expansion usage
            .useHighConfidenceExpansions(true)
            .useMediumConfidenceExpansions(true)
            .useLowConfidenceExpansions(false)
            
            // Node type preferences
            .nodeTypeBoosts(Map.of(
                "Enum", 0.9f,
                "Field", 0.7f,
                "Constant", 0.8f,
                "StateMachine", 0.8f
            ))
            
            // Pattern preferences
            .patternBoosts(Map.of(
                "Status", 0.9f,
                "State", 0.9f,
                "Phase", 0.8f,
                "Stage", 0.8f,
                "Condition", 0.7f,
                "Result", 0.7f
            ))
            
            // Search configuration
            .maxSearchResults(75)
            .minScoreThreshold(0.15f)
            .expandGraphRelationships(true)
            .graphExpansionDepth(1)
            
            .build();
    }
    
    /**
     * Creates default strategy when no intent is detected
     */
    private SearchStrategy createDefaultStrategy() {
        return SearchStrategy.builder()
            .strategyName("Default Search")
            .searchDepth(SearchDepth.BALANCED)
            
            // Balanced weights
            .methodBodyWeight(0.5f)
            .descriptionWeight(0.5f)
            .signatureWeight(0.5f)
            .relationshipWeight(0.5f)
            .configurationWeight(0.3f)
            
            // Use all expansions conservatively
            .useHighConfidenceExpansions(true)
            .useMediumConfidenceExpansions(true)
            .useLowConfidenceExpansions(false)
            
            // No specific boosts
            .nodeTypeBoosts(new HashMap<>())
            .relationshipBoosts(new HashMap<>())
            
            // Standard configuration
            .maxSearchResults(100)
            .minScoreThreshold(0.1f)
            .expandGraphRelationships(true)
            .graphExpansionDepth(2)
            
            .build();
    }
    
    /**
     * Applies modifications based on secondary intents
     */
    private void applySecondaryIntentModifications(SearchStrategy strategy, 
                                                  List<QueryIntentAnalyzer.IntentType> secondaryIntents) {
        for (QueryIntentAnalyzer.IntentType secondaryIntent : secondaryIntents) {
            switch (secondaryIntent) {
                case IMPLEMENTATION:
                    strategy.setMethodBodyWeight(Math.min(1.0f, strategy.getMethodBodyWeight() + 0.1f));
                    strategy.setIncludeDetailedDescriptions(true);
                    break;
                    
                case USAGE:
                    strategy.setRelationshipWeight(Math.min(1.0f, strategy.getRelationshipWeight() + 0.1f));
                    strategy.setGraphExpansionDepth(Math.max(strategy.getGraphExpansionDepth(), 2));
                    break;
                    
                case CONFIGURATION:
                    strategy.setConfigurationWeight(Math.min(1.0f, strategy.getConfigurationWeight() + 0.15f));
                    strategy.setPrioritizeAnnotations(true);
                    break;
                    
                case DISCOVERY:
                    strategy.setMaxSearchResults(Math.max(strategy.getMaxSearchResults(), 150));
                    strategy.setUseLowConfidenceExpansions(true);
                    break;
                    
                case STATUS:
                    strategy.setPrioritizeEnums(true);
                    strategy.getPatternBoosts().put("State", 0.8f);
                    break;
            }
        }
    }
    
    /**
     * Applies context-based adjustments to the strategy
     */
    private void applyContextAdjustments(SearchStrategy strategy, 
                                       Map<QueryIntentAnalyzer.ContextType, List<String>> contexts) {
        
        // Scope adjustments
        if (contexts.containsKey(QueryIntentAnalyzer.ContextType.SCOPE)) {
            List<String> scopeIndicators = contexts.get(QueryIntentAnalyzer.ContextType.SCOPE);
            
            if (scopeIndicators.stream().anyMatch(s -> s.equalsIgnoreCase("all"))) {
                strategy.setMaxSearchResults(strategy.getMaxSearchResults() * 2);
                strategy.setUseLowConfidenceExpansions(true);
            }
            
            if (scopeIndicators.stream().anyMatch(s -> s.equalsIgnoreCase("specific"))) {
                strategy.setMinScoreThreshold(strategy.getMinScoreThreshold() * 1.5f);
                strategy.setUseLowConfidenceExpansions(false);
            }
        }
        
        // Temporal adjustments
        if (contexts.containsKey(QueryIntentAnalyzer.ContextType.TEMPORAL)) {
            List<String> temporalIndicators = contexts.get(QueryIntentAnalyzer.ContextType.TEMPORAL);
            
            if (temporalIndicators.stream().anyMatch(t -> 
                    t.equalsIgnoreCase("recent") || t.equalsIgnoreCase("latest"))) {
                strategy.setPrioritizeRecentChanges(true);
                strategy.setRecencyBoost(0.2f);
            }
            
            if (temporalIndicators.stream().anyMatch(t -> t.equalsIgnoreCase("deprecated"))) {
                strategy.getPatternBoosts().put("@Deprecated", 0.9f);
                strategy.getPatternBoosts().put("deprecated", 0.8f);
            }
        }
        
        // Quality adjustments
        if (contexts.containsKey(QueryIntentAnalyzer.ContextType.QUALITY)) {
            List<String> qualityIndicators = contexts.get(QueryIntentAnalyzer.ContextType.QUALITY);
            
            if (qualityIndicators.stream().anyMatch(q -> q.contains("performance"))) {
                strategy.getPatternBoosts().put("Performance", 0.7f);
                strategy.getPatternBoosts().put("Optimized", 0.7f);
                strategy.getPatternBoosts().put("Fast", 0.6f);
            }
            
            if (qualityIndicators.stream().anyMatch(q -> q.contains("security"))) {
                strategy.getPatternBoosts().put("Security", 0.8f);
                strategy.getPatternBoosts().put("Secure", 0.7f);
                strategy.getPatternBoosts().put("Auth", 0.7f);
            }
        }
    }

    /**
     * Search depth enumeration
     */
    public enum SearchDepth {
        SHALLOW,    // Quick, surface-level search
        BALANCED,   // Standard depth search
        DEEP,       // Thorough, detailed search
        WIDE,       // Broad search across many relationships
        TARGETED    // Focused search on specific node types
    }

    /**
     * Comprehensive search strategy configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchStrategy {
        private String strategyName;
        private SearchDepth searchDepth;
        
        // Search focus flags
        private boolean prioritizeMethodBodies;
        private boolean includeDetailedDescriptions;
        private boolean focusOnAlgorithmicContent;
        private boolean includeInternalImplementations;
        private boolean prioritizeCallRelationships;
        private boolean includeDependencyPatterns;
        private boolean focusOnReferences;
        private boolean includeGraphTraversal;
        private boolean prioritizeAnnotations;
        private boolean includePropertyFiles;
        private boolean focusOnConfigurationNodes;
        private boolean prioritizeServiceComponents;
        private boolean includeArchitecturalPatterns;
        private boolean focusOnPublicAPIs;
        private boolean prioritizeEnums;
        private boolean includeStateMachines;
        private boolean focusOnStatusFields;
        private boolean prioritizeRecentChanges;
        
        // Weight configurations
        private float methodBodyWeight;
        private float descriptionWeight;
        private float signatureWeight;
        private float relationshipWeight;
        private float configurationWeight;
        private float annotationWeight;
        private float propertyWeight;
        private float configFileWeight;
        private float callPatternWeight;
        private float dependencyWeight;
        private float classWeight;
        private float interfaceWeight;
        private float publicMethodWeight;
        private float enumWeight;
        private float fieldWeight;
        private float constantWeight;
        private float statePatternWeight;
        private float recencyBoost;
        
        // Expansion usage
        private boolean useHighConfidenceExpansions;
        private boolean useMediumConfidenceExpansions;
        private boolean useLowConfidenceExpansions;
        
        // Boost maps
        @Builder.Default
        private Map<String, Float> nodeTypeBoosts = new HashMap<>();
        
        @Builder.Default
        private Map<String, Float> relationshipBoosts = new HashMap<>();
        
        @Builder.Default
        private Map<String, Float> patternBoosts = new HashMap<>();
        
        @Builder.Default
        private Map<String, Float> suffixBoosts = new HashMap<>();
        
        // Search configuration
        private int maxSearchResults;
        private float minScoreThreshold;
        private boolean expandGraphRelationships;
        private int graphExpansionDepth;
        
        /**
         * Gets the appropriate weight for a given aspect
         */
        public float getWeightForAspect(SearchAspect aspect) {
            return switch (aspect) {
                case METHOD_BODY -> methodBodyWeight;
                case DESCRIPTION -> descriptionWeight;
                case SIGNATURE -> signatureWeight;
                case RELATIONSHIP -> relationshipWeight;
                case CONFIGURATION -> configurationWeight;
                case ANNOTATION -> annotationWeight;
                case PROPERTY -> propertyWeight;
                case CLASS -> classWeight;
                case INTERFACE -> interfaceWeight;
                case ENUM -> enumWeight;
                case FIELD -> fieldWeight;
            };
        }
    }
    
    /**
     * Search aspects that can be weighted
     */
    public enum SearchAspect {
        METHOD_BODY,
        DESCRIPTION,
        SIGNATURE,
        RELATIONSHIP,
        CONFIGURATION,
        ANNOTATION,
        PROPERTY,
        CLASS,
        INTERFACE,
        ENUM,
        FIELD
    }
}