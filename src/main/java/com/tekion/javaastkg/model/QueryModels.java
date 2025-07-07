package com.tekion.javaastkg.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Models for query processing and results
 */
public class QueryModels {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryRequest {
        private String query;
        private Map<String, Object> parameters;
        private QueryOptions options;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryOptions {
        private int maxResults = 10;
        private boolean includeCode = true;
        private boolean includeRelationships = true;
        private int expansionDepth = 3;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryResult {
        private String query;
        private String summary;
        private List<RelevantComponent> components;
        private List<RelationshipInsight> relationships;
        private Map<String, Object> metadata;
        private double confidence;
        private long processingTimeMs;
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelevantComponent {
        private String type;
        private String signature;
        private String name;
        private String summary;
        private String codeSnippet;
        private double relevanceScore;
        private List<String> businessTags;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelationshipInsight {
        private String description;
        private String fromComponent;
        private String toComponent;
        private String relationshipType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievalResult {
        private List<String> topMethodIds;
        private GraphEntities.GraphContext graphContext;
        private Map<String, Double> scoreMap;
        private Map<String, Object> metadata;
    }
}
