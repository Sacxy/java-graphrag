package com.tekion.javaastkg.adk.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Flow Narrative Generator Data Models
 * 
 * Comprehensive data structures designed specifically for AI agents to process
 * and generate intelligent narratives from complex code analysis results.
 * 
 * Each model is designed with:
 * - Clear semantic structure for AI reasoning
 * - Conversion methods for tool output compatibility
 * - Builder patterns for flexible construction
 * - Validation and quality assessment capabilities
 */
public class FlowNarrativeGeneratorDataModels {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SynthesizedContent {
        private KeyInformation keyInformation;
        private ConsolidatedInformation consolidatedInformation;
        private OrganizedFlow organizedFlow;
        private List<NarrativeTheme> narrativeThemes;
        private EvidenceStructure evidenceStructure;
        private String originalQuery;
        private String complexityLevel;
        private long synthesisTimestamp;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "keyInformation", keyInformation != null ? keyInformation.toMap() : Map.of(),
                "consolidatedInformation", consolidatedInformation != null ? consolidatedInformation.toMap() : Map.of(),
                "organizedFlow", organizedFlow != null ? organizedFlow.toMap() : Map.of(),
                "narrativeThemes", narrativeThemes != null ? narrativeThemes.stream()
                    .map(NarrativeTheme::toMap).collect(Collectors.toList()) : List.of(),
                "evidenceStructure", evidenceStructure != null ? evidenceStructure.toMap() : Map.of(),
                "originalQuery", originalQuery != null ? originalQuery : "",
                "complexityLevel", complexityLevel != null ? complexityLevel : "MODERATE",
                "synthesisTimestamp", synthesisTimestamp
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NarrativeConfiguration {
        @Builder.Default
        private String interactivityLevel = "STATIC";
        @Builder.Default
        private String detailLevel = "MODERATE";
        @Builder.Default
        private List<String> validationRules = new ArrayList<>();
        @Builder.Default
        private Map<String, Double> qualityThresholds = new HashMap<>();
        @Builder.Default
        private List<String> focusAreas = new ArrayList<>();
        
        public static NarrativeConfiguration fromMap(Map<String, Object> configMap) {
            if (configMap == null || configMap.isEmpty()) {
                return NarrativeConfiguration.builder().build();
            }
            
            return NarrativeConfiguration.builder()
                .interactivityLevel((String) configMap.getOrDefault("interactivityLevel", "STATIC"))
                .detailLevel((String) configMap.getOrDefault("detailLevel", "MODERATE"))
                .validationRules((List<String>) configMap.getOrDefault("validationRules", new ArrayList<>()))
                .qualityThresholds((Map<String, Double>) configMap.getOrDefault("qualityThresholds", new HashMap<>()))
                .focusAreas((List<String>) configMap.getOrDefault("focusAreas", new ArrayList<>()))
                .build();
        }
        
        public Map<String, Object> toMap() {
            return Map.of(
                "interactivityLevel", interactivityLevel,
                "detailLevel", detailLevel,
                "validationRules", validationRules,
                "qualityThresholds", qualityThresholds,
                "focusAreas", focusAreas
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyInformation {
        @Builder.Default
        private List<StructuralInsight> structuralInsights = new ArrayList<>();
        @Builder.Default
        private List<ExecutionInsight> executionInsights = new ArrayList<>();
        @Builder.Default
        private List<ContextInsight> contextInsights = new ArrayList<>();
        @Builder.Default
        private List<KeyEntity> keyEntities = new ArrayList<>();
        @Builder.Default
        private List<KeyRelationship> keyRelationships = new ArrayList<>();
        private long extractionTimestamp;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "structuralInsights", structuralInsights.stream()
                    .map(StructuralInsight::toMap).collect(Collectors.toList()),
                "executionInsights", executionInsights.stream()
                    .map(ExecutionInsight::toMap).collect(Collectors.toList()),
                "contextInsights", contextInsights.stream()
                    .map(ContextInsight::toMap).collect(Collectors.toList()),
                "keyEntities", keyEntities.stream()
                    .map(KeyEntity::toMap).collect(Collectors.toList()),
                "keyRelationships", keyRelationships.stream()
                    .map(KeyRelationship::toMap).collect(Collectors.toList()),
                "extractionTimestamp", extractionTimestamp
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StructuralInsight {
        private String id;
        private String title;
        private String description;
        private String insightType;
        private double confidence;
        private String complexity;
        private Map<String, Object> details;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "id", id != null ? id : "",
                "title", title != null ? title : "",
                "description", description != null ? description : "",
                "insightType", insightType != null ? insightType : "",
                "confidence", confidence,
                "complexity", complexity != null ? complexity : "MODERATE",
                "details", details != null ? details : Map.of()
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionInsight {
        private String id;
        private String title;
        private String description;
        private String pathType;
        private List<String> executionSteps;
        private double confidence;
        private Map<String, Object> details;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "id", id != null ? id : "",
                "title", title != null ? title : "",
                "description", description != null ? description : "",
                "pathType", pathType != null ? pathType : "",
                "executionSteps", executionSteps != null ? executionSteps : List.of(),
                "confidence", confidence,
                "details", details != null ? details : Map.of()
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextInsight {
        private String id;
        private String title;
        private String description;
        private String contextType;
        private double relevance;
        private Map<String, Object> enrichment;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "id", id != null ? id : "",
                "title", title != null ? title : "",
                "description", description != null ? description : "",
                "contextType", contextType != null ? contextType : "",
                "relevance", relevance,
                "enrichment", enrichment != null ? enrichment : Map.of()
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyEntity {
        private String id;
        private String name;
        private String type;
        private String description;
        private double importance;
        private Map<String, Object> attributes;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "id", id != null ? id : "",
                "name", name != null ? name : "",
                "type", type != null ? type : "",
                "description", description != null ? description : "",
                "importance", importance,
                "attributes", attributes != null ? attributes : Map.of()
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyRelationship {
        private String id;
        private String sourceId;
        private String targetId;
        private String relationshipType;
        private String description;
        private double strength;
        private Map<String, Object> properties;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "id", id != null ? id : "",
                "sourceId", sourceId != null ? sourceId : "",
                "targetId", targetId != null ? targetId : "",
                "relationshipType", relationshipType != null ? relationshipType : "",
                "description", description != null ? description : "",
                "strength", strength,
                "properties", properties != null ? properties : Map.of()
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsolidatedInformation {
        @Builder.Default
        private List<Insight> uniqueInsights = new ArrayList<>();
        @Builder.Default
        private List<KeyEntity> mergedEntities = new ArrayList<>();
        @Builder.Default
        private List<KeyRelationship> consolidatedRelationships = new ArrayList<>();
        private Map<String, Object> consolidationMetadata;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "uniqueInsights", uniqueInsights.stream()
                    .map(Insight::toMap).collect(Collectors.toList()),
                "mergedEntities", mergedEntities.stream()
                    .map(KeyEntity::toMap).collect(Collectors.toList()),
                "consolidatedRelationships", consolidatedRelationships.stream()
                    .map(KeyRelationship::toMap).collect(Collectors.toList()),
                "consolidationMetadata", consolidationMetadata != null ? consolidationMetadata : Map.of()
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Insight {
        private String id;
        private String title;
        private String description;
        private String type;
        private String complexity;
        private double confidence;
        private double importance;
        private String source;
        private Map<String, Object> details;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "id", id != null ? id : "",
                "title", title != null ? title : "",
                "description", description != null ? description : "",
                "type", type != null ? type : "",
                "complexity", complexity != null ? complexity : "MODERATE",
                "confidence", confidence,
                "importance", importance,
                "source", source != null ? source : "",
                "details", details != null ? details : Map.of()
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrganizedFlow {
        private NarrativeStructure narrativeStructure;
        @Builder.Default
        private List<Insight> orderedInsights = new ArrayList<>();
        @Builder.Default
        private List<NarrativeSection> narrativeSections = new ArrayList<>();
        private Map<String, Object> flowMetadata;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "narrativeStructure", narrativeStructure != null ? narrativeStructure.name() : "EXPLORATORY_INVESTIGATION",
                "orderedInsights", orderedInsights.stream()
                    .map(Insight::toMap).collect(Collectors.toList()),
                "narrativeSections", narrativeSections.stream()
                    .map(NarrativeSection::toMap).collect(Collectors.toList()),
                "flowMetadata", flowMetadata != null ? flowMetadata : Map.of()
            );
        }
    }

    public enum NarrativeStructure {
        PROCESS_EXPLANATION,
        CAUSAL_ANALYSIS,
        DESCRIPTIVE_OVERVIEW,
        COMPARATIVE_ANALYSIS,
        EXPLORATORY_INVESTIGATION
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NarrativeSection {
        private String id;
        private String title;
        private String content;
        @Builder.Default
        private List<String> supportingDetails = new ArrayList<>();
        @Builder.Default
        private List<CodeExample> codeExamples = new ArrayList<>();
        @Builder.Default
        private List<Diagram> diagrams = new ArrayList<>();
        @Builder.Default
        private List<Insight> insights = new ArrayList<>();
        
        public Map<String, Object> toMap() {
            return Map.of(
                "id", id != null ? id : "",
                "title", title != null ? title : "",
                "content", content != null ? content : "",
                "supportingDetails", supportingDetails,
                "codeExamples", codeExamples.stream()
                    .map(CodeExample::toMap).collect(Collectors.toList()),
                "diagrams", diagrams.stream()
                    .map(Diagram::toMap).collect(Collectors.toList()),
                "insights", insights.stream()
                    .map(Insight::toMap).collect(Collectors.toList())
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeExample {
        private String id;
        private String methodName;
        private String signature;
        private String fullCode;
        private String language;
        private int lineCount;
        private String description;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "id", id != null ? id : "",
                "methodName", methodName != null ? methodName : "",
                "signature", signature != null ? signature : "",
                "fullCode", fullCode != null ? fullCode : "",
                "language", language != null ? language : "java",
                "lineCount", lineCount,
                "description", description != null ? description : ""
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Diagram {
        private String id;
        private String title;
        private String type;
        private String description;
        private Map<String, Object> diagramData;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "id", id != null ? id : "",
                "title", title != null ? title : "",
                "type", type != null ? type : "",
                "description", description != null ? description : "",
                "diagramData", diagramData != null ? diagramData : Map.of()
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NarrativeTheme {
        private String id;
        private String name;
        private String description;
        private double relevance;
        @Builder.Default
        private List<String> supportingInsights = new ArrayList<>();
        
        public Map<String, Object> toMap() {
            return Map.of(
                "id", id != null ? id : "",
                "name", name != null ? name : "",
                "description", description != null ? description : "",
                "relevance", relevance,
                "supportingInsights", supportingInsights
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceStructure {
        @Builder.Default
        private List<Evidence> structuralEvidence = new ArrayList<>();
        @Builder.Default
        private List<Evidence> executionEvidence = new ArrayList<>();
        @Builder.Default
        private List<Evidence> contextEvidence = new ArrayList<>();
        private Map<String, Object> evidenceMetadata;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "structuralEvidence", structuralEvidence.stream()
                    .map(Evidence::toMap).collect(Collectors.toList()),
                "executionEvidence", executionEvidence.stream()
                    .map(Evidence::toMap).collect(Collectors.toList()),
                "contextEvidence", contextEvidence.stream()
                    .map(Evidence::toMap).collect(Collectors.toList()),
                "evidenceMetadata", evidenceMetadata != null ? evidenceMetadata : Map.of()
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Evidence {
        private String type;
        private String description;
        private String source;
        private double relevance;
        private Map<String, Object> details;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "type", type != null ? type : "",
                "description", description != null ? description : "",
                "source", source != null ? source : "",
                "relevance", relevance,
                "details", details != null ? details : Map.of()
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NarrativeContent {
        private String perspective;
        private String introduction;
        @Builder.Default
        private List<NarrativeSection> bodySections = new ArrayList<>();
        private String conclusion;
        @Builder.Default
        private List<String> keyTakeaways = new ArrayList<>();
        @Builder.Default
        private List<Evidence> supportingEvidence = new ArrayList<>();
        private long generationTimestamp;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "perspective", perspective != null ? perspective : "",
                "introduction", introduction != null ? introduction : "",
                "bodySections", bodySections.stream()
                    .map(NarrativeSection::toMap).collect(Collectors.toList()),
                "conclusion", conclusion != null ? conclusion : "",
                "keyTakeaways", keyTakeaways,
                "supportingEvidence", supportingEvidence.stream()
                    .map(Evidence::toMap).collect(Collectors.toList()),
                "generationTimestamp", generationTimestamp
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractiveElement {
        private String type;
        private String id;
        private String title;
        private String shortVersion;
        private String expandedVersion;
        private String interactionType;
        private Map<String, Object> metadata;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "type", type != null ? type : "",
                "id", id != null ? id : "",
                "title", title != null ? title : "",
                "shortVersion", shortVersion != null ? shortVersion : "",
                "expandedVersion", expandedVersion != null ? expandedVersion : "",
                "interactionType", interactionType != null ? interactionType : "",
                "metadata", metadata != null ? metadata : Map.of()
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResults {
        private Map<String, ValidationScore> narrativeScores;
        @Builder.Default
        private List<ValidationIssue> validationIssues = new ArrayList<>();
        private double overallScore;
        @Builder.Default
        private List<String> improvementSuggestions = new ArrayList<>();
        private long validationTimestamp;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "narrativeScores", narrativeScores != null ? 
                    narrativeScores.entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().toMap()
                    )) : Map.of(),
                "validationIssues", validationIssues.stream()
                    .map(ValidationIssue::toMap).collect(Collectors.toList()),
                "overallScore", overallScore,
                "improvementSuggestions", improvementSuggestions,
                "validationTimestamp", validationTimestamp
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationScore {
        private double completenessScore;
        private double clarityScore;
        private double accuracyScore;
        private double overallScore;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "completenessScore", completenessScore,
                "clarityScore", clarityScore,
                "accuracyScore", accuracyScore,
                "overallScore", overallScore
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationIssue {
        private String type;
        private String severity;
        private String description;
        private String suggestion;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "type", type != null ? type : "",
                "severity", severity != null ? severity : "MEDIUM",
                "description", description != null ? description : "",
                "suggestion", suggestion != null ? suggestion : ""
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadabilityScore {
        private double fleschReadingEase;
        private double structureScore;
        private double clarityScore;
        private double completenessScore;
        private double overallScore;
        private String readingLevel;
        @Builder.Default
        private List<String> improvements = new ArrayList<>();
        
        public Map<String, Object> toMap() {
            return Map.of(
                "fleschReadingEase", fleschReadingEase,
                "structureScore", structureScore,
                "clarityScore", clarityScore,
                "completenessScore", completenessScore,
                "overallScore", overallScore,
                "readingLevel", readingLevel != null ? readingLevel : "MODERATE",
                "improvements", improvements
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerspectiveSettings {
        private String perspective;
        private String audienceLevel;
        private String technicalDetail;
        private String businessContext;
        private String codeExamples;
        private String terminology;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "perspective", perspective != null ? perspective : "",
                "audienceLevel", audienceLevel != null ? audienceLevel : "MIXED",
                "technicalDetail", technicalDetail != null ? technicalDetail : "MEDIUM",
                "businessContext", businessContext != null ? businessContext : "MEDIUM",
                "codeExamples", codeExamples != null ? codeExamples : "MODERATE",
                "terminology", terminology != null ? terminology : "BALANCED"
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompletenessValidation {
        private double score;
        @Builder.Default
        private List<ValidationIssue> issues = new ArrayList<>();
        private int coveredInsights;
        private int totalInsights;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "score", score,
                "issues", issues.stream().map(ValidationIssue::toMap).collect(Collectors.toList()),
                "coveredInsights", coveredInsights,
                "totalInsights", totalInsights
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClarityValidation {
        private double score;
        @Builder.Default
        private List<ValidationIssue> issues = new ArrayList<>();
        private double readabilityScore;
        private double structureScore;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "score", score,
                "issues", issues.stream().map(ValidationIssue::toMap).collect(Collectors.toList()),
                "readabilityScore", readabilityScore,
                "structureScore", structureScore
            );
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccuracyValidation {
        private double score;
        @Builder.Default
        private List<ValidationIssue> issues = new ArrayList<>();
        private int verifiedClaims;
        private int totalClaims;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "score", score,
                "issues", issues.stream().map(ValidationIssue::toMap).collect(Collectors.toList()),
                "verifiedClaims", verifiedClaims,
                "totalClaims", totalClaims
            );
        }
    }
}