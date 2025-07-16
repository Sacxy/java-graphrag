package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Metadata about search execution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchMetadata {
    private int totalCandidates;
    private String searchStrategy;
    private long executionTimeMs;
    private List<String> strategiesUsed;
    private int luceneResults;
    private int vectorResults;
    private int graphResults;
    private boolean timeoutOccurred;
}