package com.tekion.javaastkg.adk.tools;

import com.google.adk.tools.ToolContext;
import com.tekion.javaastkg.adk.tools.FlowNarrativeGeneratorDataModels.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Flow Narrative Generator Test Suite
 * 
 * Comprehensive tests for the Flow Narrative Generator tool to ensure:
 * - Correct narrative synthesis from complex analysis results
 * - Multi-perspective narrative generation (TECHNICAL, BUSINESS, USER)
 * - Quality validation and readability assessment
 * - Interactive element generation
 * - AI agent-friendly output format and decision support
 */
class FlowNarrativeGeneratorTest {


    private Map<String, Object> mockEnrichedContext;
    private String testQuery;
    private List<String> testPerspectives;
    private Map<String, Object> testNarrativeConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Create comprehensive mock enriched context
        mockEnrichedContext = createMockEnrichedContext();
        
        // Setup test parameters
        testQuery = "How does the payment processing system work?";
        testPerspectives = List.of("TECHNICAL", "BUSINESS");
        testNarrativeConfig = Map.of(
            "interactivityLevel", "STATIC",
            "detailLevel", "MODERATE",
            "validationRules", List.of("NO_SPECULATION"),
            "qualityThresholds", Map.of("completeness", 0.8, "clarity", 0.7),
            "focusAreas", List.of("ARCHITECTURE", "FLOW")
        );
    }

    @Test
    void testBasicNarrativeGeneration() {
        // Act
        Map<String, Object> result = FlowNarrativeGenerator.generateNarrative(
            mockEnrichedContext,
            testQuery,
            testPerspectives,
            testNarrativeConfig,
            null
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.get("status")).isEqualTo("success");
        assertThat(result).containsKey("narrativeVariants");
        assertThat(result).containsKey("primaryNarrative");
        assertThat(result).containsKey("validationResults");
        assertThat(result).containsKey("confidence");
        
        // Verify perspectives were generated
        @SuppressWarnings("unchecked")
        Map<String, Object> narrativeVariants = (Map<String, Object>) result.get("narrativeVariants");
        assertThat(narrativeVariants).containsKeys("TECHNICAL", "BUSINESS");
        
        // Verify confidence is reasonable
        Double confidence = (Double) result.get("confidence");
        assertThat(confidence).isBetween(0.0, 1.0);
    }

    @Test
    void testTechnicalPerspectiveNarrative() {
        // Arrange
        List<String> technicalPerspective = List.of("TECHNICAL");

        // Act
        Map<String, Object> result = FlowNarrativeGenerator.generateNarrative(
            mockEnrichedContext,
            "Explain the authentication architecture",
            technicalPerspective,
            testNarrativeConfig,
            null
        );

        // Assert
        assertThat(result.get("status")).isEqualTo("success");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> narrativeVariants = (Map<String, Object>) result.get("narrativeVariants");
        assertThat(narrativeVariants).containsKey("TECHNICAL");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> technicalNarrative = (Map<String, Object>) narrativeVariants.get("TECHNICAL");
        assertThat(technicalNarrative.get("perspective")).isEqualTo("TECHNICAL");
        assertThat(technicalNarrative.get("introduction")).isNotNull();
        assertThat(technicalNarrative.get("conclusion")).isNotNull();
    }

    @Test
    void testBusinessPerspectiveNarrative() {
        // Arrange
        List<String> businessPerspective = List.of("BUSINESS");

        // Act
        Map<String, Object> result = FlowNarrativeGenerator.generateNarrative(
            mockEnrichedContext,
            "What is the business impact of our payment system?",
            businessPerspective,
            testNarrativeConfig,
            null
        );

        // Assert
        assertThat(result.get("status")).isEqualTo("success");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> narrativeVariants = (Map<String, Object>) result.get("narrativeVariants");
        assertThat(narrativeVariants).containsKey("BUSINESS");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> businessNarrative = (Map<String, Object>) narrativeVariants.get("BUSINESS");
        assertThat(businessNarrative.get("perspective")).isEqualTo("BUSINESS");
    }

    @Test
    void testInteractiveElementGeneration() {
        // Arrange
        Map<String, Object> interactiveConfig = Map.of(
            "interactivityLevel", "INTERACTIVE",
            "detailLevel", "HIGH"
        );

        // Act
        Map<String, Object> result = FlowNarrativeGenerator.generateNarrative(
            mockEnrichedContext,
            testQuery,
            testPerspectives,
            interactiveConfig,
            null
        );

        // Assert
        assertThat(result.get("status")).isEqualTo("success");
        assertThat(result).containsKey("interactiveElements");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> interactiveElements = (Map<String, Object>) result.get("interactiveElements");
        assertThat(interactiveElements).isNotEmpty();
    }

    @Test
    void testValidationAndQualityScoring() {
        // Act
        Map<String, Object> result = FlowNarrativeGenerator.generateNarrative(
            mockEnrichedContext,
            testQuery,
            testPerspectives,
            testNarrativeConfig,
            null
        );

        // Assert
        assertThat(result).containsKey("validationResults");
        assertThat(result).containsKey("readabilityScores");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> validationResults = (Map<String, Object>) result.get("validationResults");
        assertThat(validationResults).containsKey("overallScore");
        
        Double validationScore = (Double) validationResults.get("overallScore");
        assertThat(validationScore).isBetween(0.0, 1.0);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> readabilityScores = (Map<String, Object>) result.get("readabilityScores");
        assertThat(readabilityScores).containsKeys("TECHNICAL", "BUSINESS");
    }

    @Test
    void testContextStateUpdates() {
        // Test context state handling with null context (should not throw error)
        Map<String, Object> result = FlowNarrativeGenerator.generateNarrative(
            mockEnrichedContext,
            testQuery,
            testPerspectives,
            testNarrativeConfig,
            null
        );

        // Assert - should handle null context gracefully
        assertThat(result.get("status")).isEqualTo("success");
    }

    @Test
    void testErrorHandling() {
        // Test null enriched context
        Map<String, Object> result = FlowNarrativeGenerator.generateNarrative(
            null,
            testQuery,
            testPerspectives,
            testNarrativeConfig,
            null
        );

        assertThat(result.get("status")).isEqualTo("error");
        assertThat(result.get("error")).asString().contains("Enriched context cannot be null or empty");

        // Test null query
        result = FlowNarrativeGenerator.generateNarrative(
            mockEnrichedContext,
            null,
            testPerspectives,
            testNarrativeConfig,
            null
        );

        assertThat(result.get("status")).isEqualTo("error");
        assertThat(result.get("error")).asString().contains("Original query cannot be null or empty");
    }

    @Test
    void testDefaultPerspectiveHandling() {
        // Test with null perspectives
        Map<String, Object> result = FlowNarrativeGenerator.generateNarrative(
            mockEnrichedContext,
            testQuery,
            null,
            testNarrativeConfig,
            null
        );

        assertThat(result.get("status")).isEqualTo("success");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> narrativeVariants = (Map<String, Object>) result.get("narrativeVariants");
        assertThat(narrativeVariants).containsKey("TECHNICAL"); // Default perspective
    }

    // Helper method to create comprehensive mock data
    private Map<String, Object> createMockEnrichedContext() {
        Map<String, Object> context = new HashMap<>();
        
        // Mock structural analysis
        context.put("structuralAnalysis", Map.of(
            "entities", List.of(
                Map.of("name", "PaymentService", "type", "CLASS", "importance", 0.9),
                Map.of("name", "TransactionProcessor", "type", "CLASS", "importance", 0.8)
            ),
            "relationships", List.of(
                Map.of("sourceId", "PaymentService", "targetId", "TransactionProcessor", "type", "CALLS", "strength", 0.9)
            ),
            "structuralFindings", List.of(
                Map.of("title", "Service Layer Pattern", "description", "Clean service layer implementation", "confidence", 0.85)
            )
        ));
        
        // Mock execution analysis
        context.put("executionAnalysis", Map.of(
            "executionPaths", List.of(
                Map.of("title", "Payment Flow", "description", "Standard payment processing path", "confidence", 0.9, "steps", List.of("validate", "process", "confirm"))
            )
        ));
        
        // Mock context analysis
        context.put("contextAnalysis", Map.of(
            "contextEnrichments", List.of(
                Map.of("title", "Business Context", "description", "Payment system business rules", "relevance", 0.8)
            )
        ));
        
        // Mock semantic analysis
        context.put("semanticAnalysis", Map.of(
            "entities", List.of(
                Map.of("name", "SecurityManager", "type", "UTILITY", "importance", 0.7)
            ),
            "relationships", List.of(
                Map.of("sourceId", "PaymentService", "targetId", "SecurityManager", "type", "USES", "strength", 0.8)
            )
        ));
        
        return context;
    }
}