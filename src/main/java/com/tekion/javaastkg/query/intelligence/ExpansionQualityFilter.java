package com.tekion.javaastkg.query.intelligence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Filters and ranks expanded terms based on relevance and quality metrics.
 * Ensures that query expansions are meaningful and don't introduce noise.
 */
@Service
@Slf4j
public class ExpansionQualityFilter {

    @Value("${query_optimization.quality_control.relevance_threshold:0.5}")
    private float relevanceThreshold;
    
    @Value("${query_optimization.quality_control.max_total_expansions:50}")
    private int maxTotalExpansions;
    
    @Value("${query_optimization.quality_control.expansion_ranking_enabled:true}")
    private boolean expansionRankingEnabled;
    
    @Value("${query_optimization.quality_control.min_string_similarity:0.3}")
    private float minStringSimilarity;
    
    @Value("${query_optimization.quality_control.max_edit_distance:5}")
    private int maxEditDistance;
    
    // Cache for string similarity calculations
    private final Map<String, Double> similarityCache = new ConcurrentHashMap<>();
    
    // Common code term patterns
    private static final Set<String> COMMON_CODE_TERMS = Set.of(
        "get", "set", "is", "has", "add", "remove", "delete", "update",
        "create", "build", "process", "handle", "execute", "run",
        "start", "stop", "init", "initialize", "validate", "check"
    );
    
    // Noise terms to filter out
    private static final Set<String> NOISE_TERMS = Set.of(
        "todo", "fixme", "hack", "temp", "temporary", "test", "testing",
        "debug", "log", "logger", "util", "utils", "helper", "common",
        "misc", "other", "unknown", "dummy", "sample", "example"
    );

    /**
     * Filters expanded terms by relevance to the original query
     */
    public List<String> filterByRelevance(List<String> expandedTerms, String originalQuery) {
        if (!expansionRankingEnabled || expandedTerms == null || expandedTerms.isEmpty()) {
            return expandedTerms;
        }
        
        log.debug("Filtering {} expanded terms for query: {}", expandedTerms.size(), originalQuery);
        
        // Calculate relevance scores for all terms
        List<ScoredTerm> scoredTerms = expandedTerms.stream()
            .distinct()
            .map(term -> new ScoredTerm(term, calculateRelevanceScore(term, originalQuery)))
            .filter(scored -> scored.getScore() >= relevanceThreshold)
            .sorted(Comparator.comparing(ScoredTerm::getScore).reversed())
            .limit(maxTotalExpansions)
            .collect(Collectors.toList());
        
        // Extract filtered terms
        List<String> filteredTerms = scoredTerms.stream()
            .map(ScoredTerm::getTerm)
            .collect(Collectors.toList());
        
        log.info("Filtered to {} relevant terms", filteredTerms.size());
        log.info("Relevant terms: {}", filteredTerms);
        return filteredTerms;
    }
    
    /**
     * Filters expanded terms with quality metrics
     */
    public QualityFilterResult filterWithQualityMetrics(List<MultiLevelExpander.WeightedTerm> weightedTerms,
                                                        String originalQuery,
                                                        QueryIntentAnalyzer.QueryIntent intent) {
        log.info("Applying quality filtering to {} weighted terms", weightedTerms.size());
        
        // Group terms by quality tier
        List<QualityTier> qualityTiers = new ArrayList<>();
        
        // Tier 1: High quality, directly relevant
        List<MultiLevelExpander.WeightedTerm> tier1Terms = weightedTerms.stream()
            .filter(term -> isHighQualityTerm(term, originalQuery, intent))
            .collect(Collectors.toList());
        qualityTiers.add(new QualityTier(1, "High Quality", tier1Terms));
        
        // Tier 2: Good quality, somewhat relevant
        List<MultiLevelExpander.WeightedTerm> tier2Terms = weightedTerms.stream()
            .filter(term -> !tier1Terms.contains(term))
            .filter(term -> isGoodQualityTerm(term, originalQuery, intent))
            .collect(Collectors.toList());
        qualityTiers.add(new QualityTier(2, "Good Quality", tier2Terms));
        
        // Tier 3: Acceptable quality
        List<MultiLevelExpander.WeightedTerm> tier3Terms = weightedTerms.stream()
            .filter(term -> !tier1Terms.contains(term) && !tier2Terms.contains(term))
            .filter(term -> isAcceptableQualityTerm(term, originalQuery))
            .collect(Collectors.toList());
        qualityTiers.add(new QualityTier(3, "Acceptable Quality", tier3Terms));
        
        // Filter out noise and combine tiers
        List<MultiLevelExpander.WeightedTerm> filteredTerms = new ArrayList<>();
        filteredTerms.addAll(tier1Terms);
        filteredTerms.addAll(tier2Terms);
        filteredTerms.addAll(tier3Terms.stream()
            .limit(Math.max(0, maxTotalExpansions - tier1Terms.size() - tier2Terms.size()))
            .collect(Collectors.toList()));
        
        // Remove noise terms
        filteredTerms = filteredTerms.stream()
            .filter(term -> !isNoiseTerm(term.getTerm()))
            .collect(Collectors.toList());

        log.info("Relevant terms: {}", filteredTerms);
        
        return QualityFilterResult.builder()
            .originalTermCount(weightedTerms.size())
            .filteredTermCount(filteredTerms.size())
            .filteredTerms(filteredTerms)
            .qualityTiers(qualityTiers)
            .averageQualityScore(calculateAverageQuality(filteredTerms))
            .build();
    }
    
    /**
     * Calculates relevance score for a term
     */
    private float calculateRelevanceScore(String expandedTerm, String originalQuery) {
        float score = 0.0f;
        
        // 1. String similarity (40% weight)
        double stringSimilarity = calculateStringSimilarity(expandedTerm, originalQuery);
        score += stringSimilarity * 0.4f;
        
        // 2. Semantic coherence (30% weight)
        float semanticScore = calculateSemanticCoherence(expandedTerm, originalQuery);
        score += semanticScore * 0.3f;
        
        // 3. Java naming convention alignment (20% weight)
        float namingScore = calculateNamingConventionScore(expandedTerm);
        score += namingScore * 0.2f;
        
        // 4. Term quality (10% weight)
        float qualityScore = calculateTermQuality(expandedTerm);
        score += qualityScore * 0.1f;
        
        return score;
    }
    
    /**
     * Calculates string similarity using multiple metrics
     */
    @Cacheable("stringSimilarity")
    public double calculateStringSimilarity(String term1, String term2) {
        String cacheKey = term1 + "|" + term2;
        return similarityCache.computeIfAbsent(cacheKey, k -> {
            // Normalize terms
            String normalized1 = term1.toLowerCase();
            String normalized2 = term2.toLowerCase();
            
            // Exact match
            if (normalized1.equals(normalized2)) {
                return 1.0;
            }
            
            // Contains check
            if (normalized1.contains(normalized2) || normalized2.contains(normalized1)) {
                return 0.8;
            }
            
            // Calculate edit distance
            int editDistance = calculateEditDistance(normalized1, normalized2);
            if (editDistance <= maxEditDistance) {
                return 1.0 - (editDistance / (double) Math.max(normalized1.length(), normalized2.length()));
            }
            
            // Token-based similarity
            return calculateTokenSimilarity(normalized1, normalized2);
        });
    }
    
    /**
     * Calculates Levenshtein edit distance
     */
    private int calculateEditDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j], Math.min(dp[i][j - 1], dp[i - 1][j - 1]));
                }
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    /**
     * Calculates token-based similarity
     */
    private double calculateTokenSimilarity(String s1, String s2) {
        Set<String> tokens1 = tokenize(s1);
        Set<String> tokens2 = tokenize(s2);
        
        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return 0.0;
        }
        
        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);
        
        Set<String> union = new HashSet<>(tokens1);
        union.addAll(tokens2);
        
        return intersection.size() / (double) union.size();
    }
    
    /**
     * Tokenizes a string for similarity calculation
     */
    private Set<String> tokenize(String str) {
        // Split on camelCase, snake_case, and common delimiters
        return Arrays.stream(str.split("(?=[A-Z])|_|-|\\s+"))
            .filter(token -> token.length() > 1)
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
    }
    
    /**
     * Calculates semantic coherence score
     */
    private float calculateSemanticCoherence(String expandedTerm, String originalQuery) {
        // Check if term shares semantic roots with query
        Set<String> queryTokens = tokenize(originalQuery);
        Set<String> termTokens = tokenize(expandedTerm);
        
        // Check for shared tokens
        long sharedTokens = termTokens.stream()
            .filter(queryTokens::contains)
            .count();
        
        if (sharedTokens > 0) {
            return Math.min(1.0f, sharedTokens / (float) Math.min(queryTokens.size(), termTokens.size()));
        }
        
        // Check for semantic relationships
        for (String queryToken : queryTokens) {
            for (String termToken : termTokens) {
                if (areSemanticallySimilar(queryToken, termToken)) {
                    return 0.7f;
                }
            }
        }
        
        return 0.0f;
    }
    
    /**
     * Checks if two tokens are semantically similar
     */
    private boolean areSemanticallySimilar(String token1, String token2) {
        // Simple semantic similarity check
        Map<String, Set<String>> semanticGroups = Map.of(
            "process", Set.of("handle", "execute", "run", "perform"),
            "create", Set.of("make", "build", "generate", "construct"),
            "get", Set.of("retrieve", "fetch", "obtain", "find"),
            "data", Set.of("info", "information", "content", "payload")
        );
        
        for (Set<String> group : semanticGroups.values()) {
            if (group.contains(token1) && group.contains(token2)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Calculates naming convention score
     */
    private float calculateNamingConventionScore(String term) {
        float score = 0.5f; // Base score
        
        // Check for proper casing
        if (Character.isUpperCase(term.charAt(0))) {
            score += 0.2f; // Class-like naming
        }
        
        // Check for camelCase
        if (term.matches(".*[a-z][A-Z].*")) {
            score += 0.2f;
        }
        
        // Check for common patterns
        if (term.matches(".*Service$|.*Controller$|.*Manager$|.*Handler$")) {
            score += 0.1f;
        }
        
        return Math.min(1.0f, score);
    }
    
    /**
     * Calculates term quality score
     */
    private float calculateTermQuality(String term) {
        float score = 1.0f;
        
        // Penalize very short terms
        if (term.length() < 3) {
            score -= 0.3f;
        }
        
        // Penalize very long terms
        if (term.length() > 50) {
            score -= 0.2f;
        }
        
        // Penalize noise terms
        if (NOISE_TERMS.contains(term.toLowerCase())) {
            score -= 0.5f;
        }
        
        // Boost common code terms
        if (COMMON_CODE_TERMS.contains(term.toLowerCase())) {
            score += 0.1f;
        }
        
        return Math.max(0.0f, Math.min(1.0f, score));
    }
    
    /**
     * Checks if a term is high quality
     */
    private boolean isHighQualityTerm(MultiLevelExpander.WeightedTerm term, String originalQuery,
                                     QueryIntentAnalyzer.QueryIntent intent) {
        // High weight from expansion
        if (term.getWeight() >= 0.8f) {
            return true;
        }
        
        // Direct match or substring
        if (originalQuery.toLowerCase().contains(term.getTerm().toLowerCase())) {
            return true;
        }
        
        // Intent-specific high quality
        if (intent != null && isIntentSpecificHighQuality(term, intent)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if a term is good quality
     */
    private boolean isGoodQualityTerm(MultiLevelExpander.WeightedTerm term, String originalQuery,
                                     QueryIntentAnalyzer.QueryIntent intent) {
        // Good weight from expansion
        if (term.getWeight() >= 0.6f) {
            return true;
        }
        
        // Good string similarity
        double similarity = calculateStringSimilarity(term.getTerm(), originalQuery);
        if (similarity >= 0.5) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if a term is acceptable quality
     */
    private boolean isAcceptableQualityTerm(MultiLevelExpander.WeightedTerm term, String originalQuery) {
        // Minimum weight threshold
        if (term.getWeight() < 0.3f) {
            return false;
        }
        
        // Not a noise term
        return !isNoiseTerm(term.getTerm());
    }
    
    /**
     * Checks if a term is noise
     */
    private boolean isNoiseTerm(String term) {
        String lowerTerm = term.toLowerCase();
        
        // Check against noise terms
        if (NOISE_TERMS.contains(lowerTerm)) {
            return true;
        }
        
        // Check for test-related terms
        if (lowerTerm.contains("test") || lowerTerm.contains("mock") || lowerTerm.contains("stub")) {
            return true;
        }
        
        // Check for single characters or numbers
        if (term.length() <= 1 || term.matches("\\d+")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if a term is high quality for specific intent
     */
    private boolean isIntentSpecificHighQuality(MultiLevelExpander.WeightedTerm term,
                                               QueryIntentAnalyzer.QueryIntent intent) {
        String termLower = term.getTerm().toLowerCase();
        
        switch (intent.getPrimaryIntent()) {
            case IMPLEMENTATION:
                return termLower.contains("impl") || termLower.contains("execute") || 
                       termLower.contains("process");
                
            case CONFIGURATION:
                return termLower.contains("config") || termLower.contains("property") || 
                       termLower.contains("setting");
                
            case DISCOVERY:
                return termLower.endsWith("service") || termLower.endsWith("manager") || 
                       termLower.endsWith("controller");
                
            case STATUS:
                return termLower.contains("status") || termLower.contains("state") || 
                       termLower.contains("phase");
                
            default:
                return false;
        }
    }
    
    /**
     * Calculates average quality score
     */
    private float calculateAverageQuality(List<MultiLevelExpander.WeightedTerm> terms) {
        if (terms.isEmpty()) {
            return 0.0f;
        }
        
        double totalWeight = terms.stream()
            .mapToDouble(MultiLevelExpander.WeightedTerm::getWeight)
            .sum();
            
        return (float) (totalWeight / terms.size());
    }

    /**
     * Scored term representation
     */
    @Data
    @AllArgsConstructor
    private static class ScoredTerm {
        private String term;
        private float score;
    }
    
    /**
     * Quality tier representation
     */
    @Data
    @AllArgsConstructor
    public static class QualityTier {
        private int tier;
        private String description;
        private List<MultiLevelExpander.WeightedTerm> terms;
    }
    
    /**
     * Quality filter result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityFilterResult {
        private int originalTermCount;
        private int filteredTermCount;
        private List<MultiLevelExpander.WeightedTerm> filteredTerms;
        private List<QualityTier> qualityTiers;
        private float averageQualityScore;
        
        /**
         * Gets filtered terms as simple string list
         */
        public List<String> getFilteredTermStrings() {
            return filteredTerms.stream()
                .map(MultiLevelExpander.WeightedTerm::getTerm)
                .collect(Collectors.toList());
        }
    }
}