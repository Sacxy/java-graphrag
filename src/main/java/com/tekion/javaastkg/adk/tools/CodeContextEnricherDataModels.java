package com.tekion.javaastkg.adk.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Data models for Code Context Enricher
 * 
 * This file contains all the supporting data classes used by the Code Context Enricher tool
 * for structured context analysis, quality assessment, and recommendation generation.
 */
public class CodeContextEnricherDataModels {

    /**
     * Complete context analysis for a single entity
     */
    @Data
    @Builder
    public static class EntityContextAnalysis {
        private String entityId;
        private DocumentationContext documentation;
        private UsageContext usage;
        private TestContext tests;
        private BusinessContext business;
        private EntityQualityScores qualityScores;
        private long analysisTimestamp;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "entityId", entityId,
                "documentation", documentation != null ? documentation.toMap() : Map.of(),
                "usage", usage != null ? usage.toMap() : Map.of(),
                "tests", tests != null ? tests.toMap() : Map.of(),
                "business", business != null ? business.toMap() : Map.of(),
                "qualityScores", qualityScores != null ? qualityScores.toMap() : Map.of(),
                "analysisTimestamp", analysisTimestamp
            );
        }
    }
    
    /**
     * Documentation context including all forms of documentation
     */
    @Data
    @Builder
    public static class DocumentationContext {
        private List<String> javadocContent;
        private List<String> inlineComments;
        private List<String> readmeContent;
        private double completenessScore;
        private double qualityScore;
        private boolean hasDocumentation;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "javadocContent", javadocContent != null ? javadocContent : List.of(),
                "inlineComments", inlineComments != null ? inlineComments : List.of(),
                "readmeContent", readmeContent != null ? readmeContent : List.of(),
                "completenessScore", completenessScore,
                "qualityScore", qualityScore,
                "hasDocumentation", hasDocumentation
            );
        }
    }
    
    /**
     * Usage context from method calls and relationships
     */
    @Data
    @Builder
    public static class UsageContext {
        private List<UsageExample> usageExamples;
        private int callerCount;
        private int calleeCount;
        private int fieldUsageCount;
        private double usageRichness;
        private double usageDiversity;
        private boolean hasUsageExamples;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "usageExamples", usageExamples != null ? 
                    usageExamples.stream().map(UsageExample::toMap).collect(Collectors.toList()) : List.of(),
                "callerCount", callerCount,
                "calleeCount", calleeCount,
                "fieldUsageCount", fieldUsageCount,
                "usageRichness", usageRichness,
                "usageDiversity", usageDiversity,
                "hasUsageExamples", hasUsageExamples
            );
        }
    }
    
    /**
     * Individual usage example with context
     */
    @Data
    @Builder
    public static class UsageExample {
        private String source;          // method_call, calls_method, field_access
        private String example;         // Actual usage example
        private String context;         // Context where it's used
        private String packageContext;  // Package information
        
        public Map<String, Object> toMap() {
            return Map.of(
                "source", source != null ? source : "",
                "example", example != null ? example : "",
                "context", context != null ? context : "",
                "packageContext", packageContext != null ? packageContext : ""
            );
        }
    }
    
    /**
     * Test context including coverage and quality information
     */
    @Data
    @Builder
    public static class TestContext {
        private List<TestReference> testReferences;
        private int testClassCount;
        private int testMethodCount;
        private double estimatedCoverage;
        private double testQuality;
        private boolean hasTests;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "testReferences", testReferences != null ?
                    testReferences.stream().map(TestReference::toMap).collect(Collectors.toList()) : List.of(),
                "testClassCount", testClassCount,
                "testMethodCount", testMethodCount,
                "estimatedCoverage", estimatedCoverage,
                "testQuality", testQuality,
                "hasTests", hasTests
            );
        }
    }
    
    /**
     * Individual test reference
     */
    @Data
    @Builder
    public static class TestReference {
        private String testType;        // class, method
        private String testName;        // Name of test class or method
        private String testClass;       // Test class name (for methods)
        private String testCategory;    // unit, integration, end-to-end
        private String packageName;     // Package information
        
        public Map<String, Object> toMap() {
            return Map.of(
                "testType", testType != null ? testType : "",
                "testName", testName != null ? testName : "",
                "testClass", testClass != null ? testClass : "",
                "testCategory", testCategory != null ? testCategory : "",
                "packageName", packageName != null ? packageName : ""
            );
        }
    }
    
    /**
     * Business context including domain knowledge and rules
     */
    @Data
    @Builder
    public static class BusinessContext {
        private List<String> businessDescriptions;
        private List<String> domainConcepts;
        private List<String> businessRules;
        private double businessContextRichness;
        private boolean hasBusinessContext;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "businessDescriptions", businessDescriptions != null ? businessDescriptions : List.of(),
                "domainConcepts", domainConcepts != null ? domainConcepts : List.of(),
                "businessRules", businessRules != null ? businessRules : List.of(),
                "businessContextRichness", businessContextRichness,
                "hasBusinessContext", hasBusinessContext
            );
        }
    }
    
    /**
     * Quality scores for different context dimensions
     */
    @Data
    @Builder
    public static class EntityQualityScores {
        private double documentationScore;
        private double usageScore;
        private double testScore;
        private double businessScore;
        private double overallScore;
        private String overallGrade;        // A, B, C, D, F
        
        public Map<String, Object> toMap() {
            return Map.of(
                "documentationScore", documentationScore,
                "usageScore", usageScore,
                "testScore", testScore,
                "businessScore", businessScore,
                "overallScore", overallScore,
                "overallGrade", overallGrade != null ? overallGrade : "F"
            );
        }
    }
    
    /**
     * Overall quality assessment across all entities
     */
    @Data
    @Builder
    public static class OverallQualityAssessment {
        private double overallScore;
        private String overallGrade;
        private Map<String, Double> categoryScores;    // Average scores by category
        private Map<String, Integer> gradeDistribution; // Count of entities by grade
        private List<String> qualityStrengths;
        private List<String> qualityWeaknesses;
        private List<String> improvementPriorities;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "overallScore", overallScore,
                "overallGrade", overallGrade != null ? overallGrade : "F",
                "categoryScores", categoryScores != null ? categoryScores : Map.of(),
                "gradeDistribution", gradeDistribution != null ? gradeDistribution : Map.of(),
                "qualityStrengths", qualityStrengths != null ? qualityStrengths : List.of(),
                "qualityWeaknesses", qualityWeaknesses != null ? qualityWeaknesses : List.of(),
                "improvementPriorities", improvementPriorities != null ? improvementPriorities : List.of()
            );
        }
    }
    
    /**
     * Identified context gap with severity and impact
     */
    @Data
    @Builder
    public static class ContextGap {
        private String gapType;             // MISSING_DOCUMENTATION, INSUFFICIENT_TESTS, etc.
        private String gapDescription;      // Human-readable description
        private String severity;            // HIGH, MEDIUM, LOW
        private List<String> affectedEntities;
        private String impact;              // Developer impact description
        private List<String> rootCauses;    // Potential causes of the gap
        private double priorityScore;       // Calculated priority for fixing
        
        public Map<String, Object> toMap() {
            return Map.of(
                "gapType", gapType != null ? gapType : "",
                "gapDescription", gapDescription != null ? gapDescription : "",
                "severity", severity != null ? severity : "LOW",
                "affectedEntities", affectedEntities != null ? affectedEntities : List.of(),
                "impact", impact != null ? impact : "",
                "rootCauses", rootCauses != null ? rootCauses : List.of(),
                "priorityScore", priorityScore
            );
        }
    }
    
    /**
     * Actionable recommendation for improvement
     */
    @Data
    @Builder
    public static class ContextRecommendation {
        private String recommendationType;      // DOCUMENTATION_IMPROVEMENT, TEST_ENHANCEMENT, etc.
        private String title;                   // Short recommendation title
        private String description;             // Detailed recommendation
        private String priority;                // HIGH, MEDIUM, LOW
        private String effortEstimate;          // LOW, MEDIUM, HIGH
        private List<String> actionItems;       // Specific steps to take
        private List<String> affectedEntities;  // Entities this helps
        private String expectedImpact;          // Expected improvement description
        private Map<String, Object> implementation; // Implementation guidance
        
        public Map<String, Object> toMap() {
            return Map.of(
                "recommendationType", recommendationType != null ? recommendationType : "",
                "title", title != null ? title : "",
                "description", description != null ? description : "",
                "priority", priority != null ? priority : "LOW",
                "effortEstimate", effortEstimate != null ? effortEstimate : "MEDIUM",
                "actionItems", actionItems != null ? actionItems : List.of(),
                "affectedEntities", affectedEntities != null ? affectedEntities : List.of(),
                "expectedImpact", expectedImpact != null ? expectedImpact : "",
                "implementation", implementation != null ? implementation : Map.of()
            );
        }
    }
}