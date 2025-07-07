package com.tekion.javaastkg.query.services;

import com.tekion.javaastkg.query.services.ParallelSearchService.SearchResult;
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
 * Service that combines and ranks results from parallel full-text and vector searches.
 * Implements weighted scoring and deduplication logic.
 */
@Service
@Slf4j
public class SearchResultCombiner {

    @Value("${query.retrieval.fulltext-weight:0.4}")
    private double fullTextWeight;

    @Value("${query.retrieval.vector-weight:0.6}")
    private double vectorWeight;

    @Value("${query.retrieval.score-threshold:0.1}")
    private double scoreThreshold;

    @Value("${query.retrieval.initial-limit:100}")
    private int initialLimit;

    /**
     * Combines and ranks results from parallel searches
     */
    public List<RankedResult> combine(List<SearchResult> fullTextResults,
                                    List<SearchResult> vectorResults) {
        log.debug("Combining {} full-text results and {} vector results", 
                 fullTextResults.size(), vectorResults.size());

        Map<String, RankedResult> combined = new HashMap<>();

        // Process full-text results
        for (SearchResult result : fullTextResults) {
            double weightedScore = result.getScore() * fullTextWeight;
            combined.put(result.getNodeId(), RankedResult.builder()
                    .nodeId(result.getNodeId())
                    .name(result.getName())
                    .signature(result.getSignature())
                    .className(result.getClassName())
                    .type(result.getType())
                    .fullTextScore(weightedScore)
                    .vectorScore(0.0)
                    .combinedScore(weightedScore)
                    .hasFullTextMatch(true)
                    .hasVectorMatch(false)
                    .build());
        }

        // Process vector results and merge with existing
        for (SearchResult result : vectorResults) {
            double weightedScore = result.getScore() * vectorWeight;
            
            combined.merge(result.getNodeId(),
                    RankedResult.builder()
                            .nodeId(result.getNodeId())
                            .name(result.getName())
                            .signature(result.getSignature())
                            .className(result.getClassName())
                            .type(result.getType())
                            .fullTextScore(0.0)
                            .vectorScore(weightedScore)
                            .combinedScore(weightedScore)
                            .hasFullTextMatch(false)
                            .hasVectorMatch(true)
                            .build(),
                    (existing, newResult) -> existing.toBuilder()
                            .vectorScore(weightedScore)
                            .combinedScore(existing.getFullTextScore() + weightedScore)
                            .hasVectorMatch(true)
                            .build());
        }

        // Sort by combined score and apply filtering
        List<RankedResult> rankedResults = combined.values().stream()
                .filter(result -> result.getCombinedScore() >= scoreThreshold)
                .sorted(Comparator.comparing(RankedResult::getCombinedScore).reversed())
                .limit(initialLimit)
                .collect(Collectors.toList());

        log.debug("Combined results: {} unique nodes, {} above threshold", 
                 combined.size(), rankedResults.size());

        // Log some statistics
        logCombinationStatistics(rankedResults);

        return rankedResults;
    }

    /**
     * Combines results with custom weights
     */
    public List<RankedResult> combineWithWeights(List<SearchResult> fullTextResults,
                                               List<SearchResult> vectorResults,
                                               double customFullTextWeight,
                                               double customVectorWeight) {
        double originalFullTextWeight = this.fullTextWeight;
        double originalVectorWeight = this.vectorWeight;

        try {
            this.fullTextWeight = customFullTextWeight;
            this.vectorWeight = customVectorWeight;
            return combine(fullTextResults, vectorResults);
        } finally {
            this.fullTextWeight = originalFullTextWeight;
            this.vectorWeight = originalVectorWeight;
        }
    }

    /**
     * Logs statistics about the combination process
     */
    private void logCombinationStatistics(List<RankedResult> results) {
        if (results.isEmpty()) {
            log.debug("No results after combination");
            return;
        }

        long bothMatches = results.stream()
                .mapToLong(r -> r.isHasFullTextMatch() && r.isHasVectorMatch() ? 1 : 0)
                .sum();
        
        long fullTextOnly = results.stream()
                .mapToLong(r -> r.isHasFullTextMatch() && !r.isHasVectorMatch() ? 1 : 0)
                .sum();
        
        long vectorOnly = results.stream()
                .mapToLong(r -> !r.isHasFullTextMatch() && r.isHasVectorMatch() ? 1 : 0)
                .sum();

        Map<String, Long> typeDistribution = results.stream()
                .collect(Collectors.groupingBy(RankedResult::getType, Collectors.counting()));

        log.debug("Result statistics - Both: {}, Full-text only: {}, Vector only: {}", 
                 bothMatches, fullTextOnly, vectorOnly);
        log.debug("Type distribution: {}", typeDistribution);
        
        if (!results.isEmpty()) {
            double avgScore = results.stream().mapToDouble(RankedResult::getCombinedScore).average().orElse(0.0);
            double maxScore = results.stream().mapToDouble(RankedResult::getCombinedScore).max().orElse(0.0);
            log.debug("Score statistics - Avg: {:.3f}, Max: {:.3f}", avgScore, maxScore);
        }
    }

    /**
     * Filters results by type
     */
    public List<RankedResult> filterByType(List<RankedResult> results, String type) {
        return results.stream()
                .filter(result -> type.equalsIgnoreCase(result.getType()))
                .collect(Collectors.toList());
    }

    /**
     * Gets the top N results
     */
    public List<RankedResult> getTopN(List<RankedResult> results, int n) {
        return results.stream()
                .limit(n)
                .collect(Collectors.toList());
    }

    /**
     * Data class for ranked search results
     */
    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankedResult {
        private String nodeId;
        private String name;
        private String signature;
        private String className;
        private String type;
        private double fullTextScore;
        private double vectorScore;
        private double combinedScore;
        private boolean hasFullTextMatch;
        private boolean hasVectorMatch;

        /**
         * Returns true if this result has both types of matches
         */
        public boolean hasBothMatches() {
            return hasFullTextMatch && hasVectorMatch;
        }

        /**
         * Returns the dominant search type
         */
        public String getDominantSearchType() {
            if (fullTextScore > vectorScore) {
                return "fulltext";
            } else if (vectorScore > fullTextScore) {
                return "semantic";
            } else {
                return "balanced";
            }
        }
    }
}