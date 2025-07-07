package com.tekion.javaastkg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Graph entity models representing nodes and relationships in Neo4j
 */
public class GraphEntities {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MethodNode {
        private String id;
        private String signature;
        private String name;
        private String className;
        private List<String> businessTags;
        private float[] embedding;
        private String embeddingText;
        private String vectorizedAt;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassNode {
        private String id;
        private String fullName;
        private String name;
        private String packageName;
        private String type;
        private boolean isInterface;
        private boolean isAbstract;
        private float[] embedding;
        private String embeddingText;
        private String vectorizedAt;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DescriptionNode {
        private String id;
        private String content;
        private String type; // "llm_generated", "javadoc", "file_doc"
        private float[] embedding;
        private String createdAt;
        private String sourceFile;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileDocNode {
        private String id;
        private String fileName;
        private String content;
        private float[] embedding;
        private String packageName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphContext {
        private List<MethodNode> methods;
        private List<ClassNode> classes;
        private List<Relationship> relationships;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Relationship {
        private String fromId;
        private String toId;
        private String type;
        private Map<String, Object> properties;
    }
}
