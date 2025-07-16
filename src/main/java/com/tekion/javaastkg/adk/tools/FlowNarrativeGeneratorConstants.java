package com.tekion.javaastkg.adk.tools;

import java.util.List;
import java.util.Map;

/**
 * Flow Narrative Generator Constants
 * 
 * Contains all constants, thresholds, and configuration values used by the Flow Narrative Generator.
 * Designed to provide clear, configurable parameters for AI agent decision-making.
 */
public class FlowNarrativeGeneratorConstants {

    // ============================================================================
    // QUALITY THRESHOLDS
    // ============================================================================
    
    public static final double HIGH_QUALITY_THRESHOLD = 0.8;
    public static final double MODERATE_QUALITY_THRESHOLD = 0.6;
    public static final double LOW_QUALITY_THRESHOLD = 0.4;
    
    public static final double HIGH_CONFIDENCE_THRESHOLD = 0.85;
    public static final double MODERATE_CONFIDENCE_THRESHOLD = 0.7;
    public static final double LOW_CONFIDENCE_THRESHOLD = 0.5;
    
    // ============================================================================
    // VALIDATION SCORING WEIGHTS
    // ============================================================================
    
    public static final double COMPLETENESS_WEIGHT = 0.4;
    public static final double CLARITY_WEIGHT = 0.3;
    public static final double ACCURACY_WEIGHT = 0.3;
    
    // ============================================================================
    // READABILITY SCORING WEIGHTS
    // ============================================================================
    
    public static final double FLESCH_WEIGHT = 0.3;
    public static final double STRUCTURE_WEIGHT = 0.3;
    public static final double CLARITY_WEIGHT_READABILITY = 0.25;
    public static final double COMPLETENESS_WEIGHT_READABILITY = 0.15;
    
    // ============================================================================
    // PERSPECTIVE CONFIGURATIONS
    // ============================================================================
    
    public static final Map<String, Map<String, String>> PERSPECTIVE_CONFIGS = Map.of(
        "TECHNICAL", Map.of(
            "audienceLevel", "EXPERT",
            "technicalDetail", "HIGH",
            "businessContext", "LOW",
            "codeExamples", "EXTENSIVE",
            "terminology", "TECHNICAL"
        ),
        "BUSINESS", Map.of(
            "audienceLevel", "MANAGER",
            "technicalDetail", "LOW",
            "businessContext", "HIGH",
            "codeExamples", "MINIMAL",
            "terminology", "BUSINESS"
        ),
        "USER", Map.of(
            "audienceLevel", "END_USER",
            "technicalDetail", "MINIMAL",
            "businessContext", "MEDIUM",
            "codeExamples", "NONE",
            "terminology", "PLAIN_LANGUAGE"
        )
    );
    
    // ============================================================================
    // NARRATIVE STRUCTURE PATTERNS
    // ============================================================================
    
    public static final Map<String, List<String>> NARRATIVE_STRUCTURE_SECTIONS = Map.of(
        "PROCESS_EXPLANATION", List.of(
            "Process Overview",
            "Key Steps",
            "Implementation Details",
            "Flow Analysis",
            "Summary"
        ),
        "CAUSAL_ANALYSIS", List.of(
            "Root Causes",
            "Contributing Factors", 
            "Impact Analysis",
            "Relationships",
            "Conclusions"
        ),
        "DESCRIPTIVE_OVERVIEW", List.of(
            "System Overview",
            "Core Components",
            "Key Features",
            "Architecture",
            "Summary"
        ),
        "COMPARATIVE_ANALYSIS", List.of(
            "Comparison Overview",
            "Similarities",
            "Differences",
            "Trade-offs",
            "Recommendations"
        ),
        "EXPLORATORY_INVESTIGATION", List.of(
            "Investigation Overview",
            "Key Findings",
            "Detailed Analysis",
            "Implications",
            "Next Steps"
        )
    );
    
    // ============================================================================
    // COMPLEXITY ASSESSMENT THRESHOLDS
    // ============================================================================
    
    public static final int COMPLEX_INSIGHT_THRESHOLD = 20;
    public static final int MODERATE_INSIGHT_THRESHOLD = 10;
    public static final int SIMPLE_INSIGHT_THRESHOLD = 5;
    
    public static final int COMPLEX_ENTITY_THRESHOLD = 15;
    public static final int MODERATE_ENTITY_THRESHOLD = 8;
    
    public static final int COMPLEX_RELATIONSHIP_THRESHOLD = 10;
    public static final int MODERATE_RELATIONSHIP_THRESHOLD = 5;
    
    public static final int COMPLEX_THEME_THRESHOLD = 3;
    
    // ============================================================================
    // INTERACTIVE ELEMENT TYPES
    // ============================================================================
    
    public static final String EXPANDABLE_CODE_TYPE = "EXPANDABLE_CODE";
    public static final String DRILLDOWN_SECTION_TYPE = "DRILLDOWN_SECTION";
    public static final String INTERACTIVE_DIAGRAM_TYPE = "INTERACTIVE_DIAGRAM";
    public static final String RELATED_ENTITY_TYPE = "RELATED_ENTITY_EXPLORATION";
    
    // ============================================================================
    // VALIDATION SEVERITY LEVELS
    // ============================================================================
    
    public static final String HIGH_SEVERITY = "HIGH";
    public static final String MEDIUM_SEVERITY = "MEDIUM";
    public static final String LOW_SEVERITY = "LOW";
    
    // ============================================================================
    // VALIDATION ISSUE TYPES
    // ============================================================================
    
    public static final String MISSING_INSIGHT_TYPE = "MISSING_INSIGHT";
    public static final String MISSING_INTRODUCTION_TYPE = "MISSING_INTRODUCTION";
    public static final String MISSING_CONCLUSION_TYPE = "MISSING_CONCLUSION";
    public static final String FACTUAL_INACCURACY_TYPE = "FACTUAL_INACCURACY";
    public static final String CLARITY_ISSUE_TYPE = "CLARITY_ISSUE";
    public static final String COMPLETENESS_ISSUE_TYPE = "COMPLETENESS_ISSUE";
    
    // ============================================================================
    // EVIDENCE TYPES
    // ============================================================================
    
    public static final String CODE_EVIDENCE_TYPE = "CODE";
    public static final String DOCUMENTATION_EVIDENCE_TYPE = "DOCUMENTATION";
    public static final String TEST_EVIDENCE_TYPE = "TEST";
    public static final String PATTERN_EVIDENCE_TYPE = "PATTERN";
    
    // ============================================================================
    // READABILITY LEVELS
    // ============================================================================
    
    public static final Map<String, String> READABILITY_LEVEL_DESCRIPTIONS = Map.of(
        "EXCELLENT", "Very easy to read and understand",
        "GOOD", "Easy to read with clear structure",
        "MODERATE", "Readable with some complexity",
        "CHALLENGING", "Requires careful reading",
        "DIFFICULT", "Complex and technical language"
    );
    
    public static final double EXCELLENT_READABILITY_THRESHOLD = 0.9;
    public static final double GOOD_READABILITY_THRESHOLD = 0.8;
    public static final double MODERATE_READABILITY_THRESHOLD = 0.6;
    public static final double CHALLENGING_READABILITY_THRESHOLD = 0.4;
    
    // ============================================================================
    // FLESCH READING EASE CONSTANTS
    // ============================================================================
    
    public static final double FLESCH_SENTENCE_WEIGHT = 1.015;
    public static final double FLESCH_SYLLABLE_WEIGHT = 84.6;
    public static final double FLESCH_BASE_SCORE = 206.835;
    
    // ============================================================================
    // DEFAULT CONFIGURATION VALUES
    // ============================================================================
    
    public static final String DEFAULT_INTERACTIVITY_LEVEL = "STATIC";
    public static final String DEFAULT_DETAIL_LEVEL = "MODERATE";
    public static final String DEFAULT_PERSPECTIVE = "TECHNICAL";
    public static final String DEFAULT_COMPLEXITY_LEVEL = "MODERATE";
    public static final String DEFAULT_TERMINOLOGY = "BALANCED";
    
    // ============================================================================
    // NARRATIVE CONTENT LIMITS
    // ============================================================================
    
    public static final int MAX_INTRODUCTION_LENGTH = 500;
    public static final int MAX_CONCLUSION_LENGTH = 400;
    public static final int MAX_SECTION_CONTENT_LENGTH = 1000;
    public static final int MAX_KEY_TAKEAWAYS = 5;
    public static final int MAX_SUPPORTING_EVIDENCE_ITEMS = 10;
    
    // ============================================================================
    // INSIGHT PROCESSING LIMITS
    // ============================================================================
    
    public static final int MAX_INSIGHTS_PER_SECTION = 10;
    public static final int MAX_CODE_EXAMPLES_PER_SECTION = 3;
    public static final int MAX_DIAGRAMS_PER_SECTION = 2;
    public static final int MAX_INTERACTIVE_ELEMENTS_PER_PERSPECTIVE = 15;
    
    // ============================================================================
    // PERFORMANCE TARGETS
    // ============================================================================
    
    public static final long MAX_SYNTHESIS_TIME_MS = 5000;
    public static final long MAX_GENERATION_TIME_MS = 3000;
    public static final long MAX_VALIDATION_TIME_MS = 1000;
    public static final long MAX_READABILITY_TIME_MS = 1000;
    
    // ============================================================================
    // ERROR MESSAGES
    // ============================================================================
    
    public static final String NULL_CONTEXT_ERROR = "Enriched context cannot be null or empty";
    public static final String NULL_QUERY_ERROR = "Original query cannot be null or empty";
    public static final String SYNTHESIS_FAILED_ERROR = "Information synthesis failed";
    public static final String GENERATION_FAILED_ERROR = "Narrative generation failed";
    public static final String VALIDATION_FAILED_ERROR = "Narrative validation failed";
    
    // ============================================================================
    // SUCCESS MESSAGES
    // ============================================================================
    
    public static final String SYNTHESIS_SUCCESS = "Information synthesis completed successfully";
    public static final String GENERATION_SUCCESS = "Narrative generation completed successfully";
    public static final String VALIDATION_SUCCESS = "Narrative validation completed successfully";
    
    // ============================================================================
    // AGENT CONTEXT STATE KEYS
    // ============================================================================
    
    public static final String NARRATIVE_GENERATED_KEY = "app:narrative_generated";
    public static final String ORIGINAL_QUERY_KEY = "app:original_query";
    public static final String NARRATIVE_PERSPECTIVES_KEY = "app:narrative_perspectives";
    public static final String NARRATIVE_CONFIDENCE_KEY = "app:narrative_confidence";
    public static final String VALIDATION_SCORE_KEY = "app:validation_score";
    public static final String NARRATIVE_HIGH_QUALITY_KEY = "app:narrative_high_quality";
    public static final String NARRATIVE_CRITICAL_ISSUES_KEY = "app:narrative_critical_issues";
    public static final String BEST_NARRATIVE_PERSPECTIVE_KEY = "app:best_narrative_perspective";
    
    // ============================================================================
    // UTILITY METHODS
    // ============================================================================
    
    /**
     * Determines readability level based on overall score
     */
    public static String determineReadabilityLevel(double overallScore) {
        if (overallScore >= EXCELLENT_READABILITY_THRESHOLD) {
            return "EXCELLENT";
        } else if (overallScore >= GOOD_READABILITY_THRESHOLD) {
            return "GOOD";
        } else if (overallScore >= MODERATE_READABILITY_THRESHOLD) {
            return "MODERATE";
        } else if (overallScore >= CHALLENGING_READABILITY_THRESHOLD) {
            return "CHALLENGING";
        } else {
            return "DIFFICULT";
        }
    }
    
    /**
     * Gets perspective configuration
     */
    public static Map<String, String> getPerspectiveConfig(String perspective) {
        return PERSPECTIVE_CONFIGS.getOrDefault(perspective.toUpperCase(), 
            PERSPECTIVE_CONFIGS.get("TECHNICAL"));
    }
    
    /**
     * Gets narrative structure sections
     */
    public static List<String> getNarrativeStructureSections(String structureType) {
        return NARRATIVE_STRUCTURE_SECTIONS.getOrDefault(structureType, 
            NARRATIVE_STRUCTURE_SECTIONS.get("EXPLORATORY_INVESTIGATION"));
    }
    
    /**
     * Validates if a confidence score meets the threshold for given quality level
     */
    public static boolean meetsQualityThreshold(double score, String qualityLevel) {
        return switch (qualityLevel.toUpperCase()) {
            case "HIGH" -> score >= HIGH_QUALITY_THRESHOLD;
            case "MODERATE" -> score >= MODERATE_QUALITY_THRESHOLD;
            case "LOW" -> score >= LOW_QUALITY_THRESHOLD;
            default -> score >= MODERATE_QUALITY_THRESHOLD;
        };
    }
}