package com.tekion.javaastkg.query.intelligence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates multi-level query expansion using all available expansion strategies.
 * Combines pattern-based, semantic, embedding-based, and graph-based expansions.
 */
@Service
@Slf4j
public class MultiLevelExpander {

    private final JavaNamingPatternExpander patternExpander;
    private final CompoundTermGenerator compoundGenerator;
    private final SemanticExpander semanticExpander;
    private final EmbeddingBasedExpander embeddingExpander;
    private final GraphRelationshipExpander graphExpander;
    
    @Value("${query_optimization.expansion.level1_weight:1.0}")
    private float level1Weight;
    
    @Value("${query_optimization.expansion.level2_weight:0.8}")
    private float level2Weight;
    
    @Value("${query_optimization.expansion.level3_weight:0.6}")
    private float level3Weight;
    
    @Value("${query_optimization.expansion.max_total_expansions:50}")
    private int maxTotalExpansions;
    
    @Value("${query_optimization.expansion.enable_parallel_expansion:true}")
    private boolean enableParallelExpansion;

    public MultiLevelExpander(JavaNamingPatternExpander patternExpander,
                             CompoundTermGenerator compoundGenerator,
                             SemanticExpander semanticExpander,
                             EmbeddingBasedExpander embeddingExpander,
                             GraphRelationshipExpander graphExpander) {
        this.patternExpander = patternExpander;
        this.compoundGenerator = compoundGenerator;
        this.semanticExpander = semanticExpander;
        this.embeddingExpander = embeddingExpander;
        this.graphExpander = graphExpander;
    }

    /**
     * Expands query using multi-level expansion strategy
     */
    public QueryExpansion expandQuery(String originalQuery, QueryIntentAnalyzer.QueryIntent intent) {
        log.info("Starting multi-level expansion for query: {}", originalQuery);
        
        // Extract base terms from query
        List<String> baseTerms = extractTerms(originalQuery);
        
        // Level 1: Direct pattern-based expansion
        ExpansionLevel level1 = performLevel1Expansion(baseTerms, intent);
        
        // Level 2: Semantic expansion
        ExpansionLevel level2 = performLevel2Expansion(baseTerms, level1.getAllTerms(), intent);
        
        // Level 3: Graph and embedding-based expansion
        ExpansionLevel level3 = performLevel3Expansion(baseTerms, level2.getAllTerms(), intent);
        
        // Combine and weight all expansions
        QueryExpansion finalExpansion = combineExpansions(
            originalQuery, intent, level1, level2, level3
        );
        
        log.info("Completed multi-level expansion: {} total terms", finalExpansion.getTotalTermCount());
//        log.info("Final Expansion: {}", finalExpansion);
        return finalExpansion;
    }
    
    /**
     * Level 1: Direct pattern-based expansion
     */
    private ExpansionLevel performLevel1Expansion(List<String> baseTerms, QueryIntentAnalyzer.QueryIntent intent) {
        log.debug("Performing Level 1 expansion (pattern-based)");
        
        Set<WeightedTerm> expansions = new LinkedHashSet<>();
        
        // Java naming pattern expansions
        for (String term : baseTerms) {
            List<String> patternExpansions = patternExpander.expandWithPatterns(term);
            for (String expansion : patternExpansions) {
                expansions.add(new WeightedTerm(expansion, level1Weight, "pattern"));
            }
        }
        
        // Compound term generation
        List<String> compounds = compoundGenerator.generateCompounds(baseTerms);
        for (String compound : compounds) {
            expansions.add(new WeightedTerm(compound, level1Weight * 0.9f, "compound"));
        }
        
        // Domain-specific patterns based on intent
        if (intent != null) {
            expansions.addAll(getIntentSpecificPatterns(baseTerms, intent));
        }
        
        return ExpansionLevel.builder()
            .level(1)
            .expansions(new ArrayList<>(expansions))
            .expansionType("Pattern-based")
            .build();
    }
    
    /**
     * Level 2: Semantic expansion
     */
    private ExpansionLevel performLevel2Expansion(List<String> baseTerms, List<String> level1Terms, 
                                                  QueryIntentAnalyzer.QueryIntent intent) {
        log.debug("Performing Level 2 expansion (semantic)");
        
        Set<WeightedTerm> expansions = new LinkedHashSet<>();
        
        // Semantic synonym expansion
        Set<String> termsToExpand = new HashSet<>(baseTerms);
        termsToExpand.addAll(level1Terms.stream().limit(10).collect(Collectors.toList())); // Limit to prevent explosion
        
        for (String term : termsToExpand) {
            List<String> semanticExpansions = semanticExpander.expandSemantics(term);
            for (String expansion : semanticExpansions) {
                if (!term.equals(expansion)) {
                    expansions.add(new WeightedTerm(expansion, level2Weight, "semantic"));
                }
            }
        }
        
        // Context-aware semantic expansion
        if (intent != null && intent.getContexts() != null) {
            List<String> contextTerms = intent.getContexts().values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
            
            for (String term : baseTerms) {
                List<String> contextExpansions = semanticExpander.expandWithContext(term, contextTerms);
                for (String expansion : contextExpansions) {
                    expansions.add(new WeightedTerm(expansion, level2Weight * 0.9f, "context_semantic"));
                }
            }
        }
        
        // Conceptual relationships
        for (String term : baseTerms) {
            List<String> conceptualTerms = semanticExpander.getConceptuallyRelatedTerms(term);
            for (String conceptual : conceptualTerms) {
                expansions.add(new WeightedTerm(conceptual, level2Weight * 0.8f, "conceptual"));
            }
        }
        
        return ExpansionLevel.builder()
            .level(2)
            .expansions(new ArrayList<>(expansions))
            .expansionType("Semantic")
            .build();
    }
    
    /**
     * Level 3: Graph and embedding-based expansion
     */
    private ExpansionLevel performLevel3Expansion(List<String> baseTerms, List<String> previousTerms,
                                                  QueryIntentAnalyzer.QueryIntent intent) {
        log.debug("Performing Level 3 expansion (graph & embedding-based)");
        
        Set<WeightedTerm> expansions = new LinkedHashSet<>();
        
        // Graph-based relationship expansion
        List<String> graphTerms = new ArrayList<>(baseTerms);
        graphTerms.addAll(previousTerms.stream().limit(5).collect(Collectors.toList())); // Top previous expansions
        
        GraphRelationshipExpander.GraphExpansionResult graphResult = 
            graphExpander.expandWithGraphAnalysis(graphTerms);
        
        // Add related terms from graph
        for (String relatedTerm : graphResult.getRelatedTerms()) {
            expansions.add(new WeightedTerm(relatedTerm, level3Weight, "graph_relationship"));
        }
        
        // Add co-occurring terms
        for (GraphRelationshipExpander.CoOccurringTerm coTerm : graphResult.getCoOccurringTerms()) {
            float weight = level3Weight * (0.5f + (coTerm.getCoOccurrenceCount() / 10.0f));
            expansions.add(new WeightedTerm(coTerm.getTerm(), Math.min(weight, level3Weight), "co_occurrence"));
        }
        
        // Embedding-based similarity expansion
        if (enableParallelExpansion) {
            Map<String, List<String>> embeddingExpansions = 
                embeddingExpander.findSimilarTermsForMultiple(baseTerms);
            
            for (Map.Entry<String, List<String>> entry : embeddingExpansions.entrySet()) {
                for (String similarTerm : entry.getValue()) {
                    expansions.add(new WeightedTerm(similarTerm, level3Weight * 0.9f, "embedding_similarity"));
                }
            }
        }
        
        // Add domain patterns from graph
        for (GraphRelationshipExpander.DomainPattern pattern : graphResult.getDomainPatterns()) {
            for (String component : pattern.getRelatedComponents()) {
                float weight = level3Weight * (pattern.getStrength() / 10.0f);
                expansions.add(new WeightedTerm(component, Math.min(weight, level3Weight), "domain_pattern"));
            }
        }
        
        return ExpansionLevel.builder()
            .level(3)
            .expansions(new ArrayList<>(expansions))
            .expansionType("Graph & Embedding")
            .build();
    }
    
    /**
     * Combines expansions from all levels with appropriate weighting
     */
    private QueryExpansion combineExpansions(String originalQuery, QueryIntentAnalyzer.QueryIntent intent,
                                           ExpansionLevel level1, ExpansionLevel level2, ExpansionLevel level3) {
        
        // Aggregate all weighted terms
        Map<String, WeightedTerm> termMap = new HashMap<>();
        
        // Add terms from each level, keeping highest weight for duplicates
        for (ExpansionLevel level : Arrays.asList(level1, level2, level3)) {
            for (WeightedTerm term : level.getExpansions()) {
                termMap.merge(term.getTerm(), term, (existing, newTerm) -> 
                    existing.getWeight() >= newTerm.getWeight() ? existing : newTerm
                );
            }
        }
        
        // Apply intent-based weighting adjustments
        if (intent != null) {
            applyIntentWeighting(termMap, intent);
        }
        
        // Sort by weight and limit total expansions
        List<WeightedTerm> finalTerms = termMap.values().stream()
            .sorted(Comparator.comparing(WeightedTerm::getWeight).reversed())
            .limit(maxTotalExpansions)
            .collect(Collectors.toList());
        
        // Group by expansion type for analysis
        Map<String, List<WeightedTerm>> termsByType = finalTerms.stream()
            .collect(Collectors.groupingBy(WeightedTerm::getSource));
        
        // Extract term lists by weight threshold
        List<String> highConfidenceTerms = finalTerms.stream()
            .filter(t -> t.getWeight() >= 0.8)
            .map(WeightedTerm::getTerm)
            .collect(Collectors.toList());
            
        List<String> mediumConfidenceTerms = finalTerms.stream()
            .filter(t -> t.getWeight() >= 0.5 && t.getWeight() < 0.8)
            .map(WeightedTerm::getTerm)
            .collect(Collectors.toList());
            
        List<String> lowConfidenceTerms = finalTerms.stream()
            .filter(t -> t.getWeight() < 0.5)
            .map(WeightedTerm::getTerm)
            .collect(Collectors.toList());
        
        return QueryExpansion.builder()
            .originalQuery(originalQuery)
            .intent(intent)
            .allTerms(finalTerms)
            .highConfidenceTerms(highConfidenceTerms)
            .mediumConfidenceTerms(mediumConfidenceTerms)
            .lowConfidenceTerms(lowConfidenceTerms)
            .termsByType(termsByType)
            .level1Expansions(level1)
            .level2Expansions(level2)
            .level3Expansions(level3)
            .totalTermCount(finalTerms.size())
            .build();
    }
    
    /**
     * Applies intent-based weighting adjustments
     */
    private void applyIntentWeighting(Map<String, WeightedTerm> termMap, QueryIntentAnalyzer.QueryIntent intent) {
        switch (intent.getPrimaryIntent()) {
            case IMPLEMENTATION:
                // Boost method-related terms
                boostTermsContaining(termMap, Arrays.asList("impl", "execute", "process", "handle"), 0.1f);
                break;
                
            case USAGE:
                // Boost relationship terms
                boostTermsContaining(termMap, Arrays.asList("use", "call", "invoke", "reference"), 0.1f);
                break;
                
            case CONFIGURATION:
                // Boost config-related terms
                boostTermsContaining(termMap, Arrays.asList("config", "property", "setting", "option"), 0.15f);
                break;
                
            case DISCOVERY:
                // Boost service/component terms
                boostTermsContaining(termMap, Arrays.asList("service", "manager", "controller", "handler"), 0.1f);
                break;
                
            case STATUS:
                // Boost state-related terms
                boostTermsContaining(termMap, Arrays.asList("status", "state", "phase", "condition"), 0.15f);
                break;
        }
    }
    
    /**
     * Boosts weight of terms containing specific substrings
     */
    private void boostTermsContaining(Map<String, WeightedTerm> termMap, List<String> substrings, float boost) {
        for (WeightedTerm term : termMap.values()) {
            String lowerTerm = term.getTerm().toLowerCase();
            if (substrings.stream().anyMatch(lowerTerm::contains)) {
                term.setWeight(Math.min(1.0f, term.getWeight() + boost));
            }
        }
    }
    
    /**
     * Gets intent-specific pattern expansions
     */
    private List<WeightedTerm> getIntentSpecificPatterns(List<String> terms, QueryIntentAnalyzer.QueryIntent intent) {
        List<WeightedTerm> patterns = new ArrayList<>();
        
        for (String term : terms) {
            List<String> intentPatterns = patternExpander.getDomainSpecificPatterns(term);
            
            // Add patterns relevant to the intent
            switch (intent.getPrimaryIntent()) {
                case IMPLEMENTATION:
                    patterns.addAll(createWeightedTerms(
                        filterPatterns(intentPatterns, "Impl", "Engine", "Processor"),
                        level1Weight * 0.95f, "intent_pattern"
                    ));
                    break;
                    
                case CONFIGURATION:
                    patterns.addAll(createWeightedTerms(
                        filterPatterns(intentPatterns, "Config", "Properties", "Settings"),
                        level1Weight * 0.95f, "intent_pattern"
                    ));
                    break;
                    
                case DISCOVERY:
                    patterns.addAll(createWeightedTerms(
                        filterPatterns(intentPatterns, "Service", "Manager", "Handler"),
                        level1Weight * 0.95f, "intent_pattern"
                    ));
                    break;
            }
        }
        
        return patterns;
    }
    
    /**
     * Filters patterns containing specific suffixes
     */
    private List<String> filterPatterns(List<String> patterns, String... suffixes) {
        return patterns.stream()
            .filter(pattern -> Arrays.stream(suffixes).anyMatch(pattern::endsWith))
            .collect(Collectors.toList());
    }
    
    /**
     * Creates weighted terms from a list of terms
     */
    private List<WeightedTerm> createWeightedTerms(List<String> terms, float weight, String source) {
        return terms.stream()
            .map(term -> new WeightedTerm(term, weight, source))
            .collect(Collectors.toList());
    }
    
    /**
     * Extracts terms from the original query
     */
    private List<String> extractTerms(String query) {
        // Remove common stop words and split by common delimiters
        Set<String> stopWords = Set.of(
            "the", "is", "at", "which", "on", "and", "a", "an",
            "as", "are", "been", "have", "has", "had", "do", "does",
            "did", "will", "would", "should", "could", "may", "might"
        );
        
        return Arrays.stream(query.split("[\\s,;.!?]+"))
            .map(String::toLowerCase)
            .filter(term -> term.length() > 2)
            .filter(term -> !stopWords.contains(term))
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * Weighted term representation
     */
    @Data
    @AllArgsConstructor
    public static class WeightedTerm {
        private String term;
        private float weight;
        private String source;
    }
    
    /**
     * Expansion level representation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpansionLevel {
        private int level;
        private List<WeightedTerm> expansions;
        private String expansionType;
        
        public List<String> getAllTerms() {
            return expansions.stream()
                .map(WeightedTerm::getTerm)
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Complete query expansion result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryExpansion {
        private String originalQuery;
        private QueryIntentAnalyzer.QueryIntent intent;
        private List<WeightedTerm> allTerms;
        private List<String> highConfidenceTerms;
        private List<String> mediumConfidenceTerms;
        private List<String> lowConfidenceTerms;
        private Map<String, List<WeightedTerm>> termsByType;
        private ExpansionLevel level1Expansions;
        private ExpansionLevel level2Expansions;
        private ExpansionLevel level3Expansions;
        private int totalTermCount;
        
        /**
         * Gets all expanded terms as a simple list
         */
        public List<String> getAllExpandedTerms() {
            return allTerms.stream()
                .map(WeightedTerm::getTerm)
                .collect(Collectors.toList());
        }
        
        /**
         * Gets terms above a specific weight threshold
         */
        public List<String> getTermsAboveThreshold(float threshold) {
            return allTerms.stream()
                .filter(t -> t.getWeight() >= threshold)
                .map(WeightedTerm::getTerm)
                .collect(Collectors.toList());
        }
    }
}