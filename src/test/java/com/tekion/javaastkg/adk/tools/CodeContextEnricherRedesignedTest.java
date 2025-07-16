package com.tekion.javaastkg.adk.tools;

import com.tekion.javaastkg.service.Neo4jService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.*;

/**
 * Test suite for Code Context Enricher Tool
 * 
 * Tests the comprehensive context analysis functionality including:
 * - Context gathering from multiple sources
 * - Quality assessment and scoring
 * - Gap identification
 * - Recommendation generation
 */
@ExtendWith(MockitoExtension.class)
public class CodeContextEnricherRedesignedTest {

    @Mock(lenient = true)
    private Neo4jService neo4jService;

    @BeforeEach
    void setUp() {
        CodeContextEnricherRedesigned.initialize(neo4jService);
    }

    @Test
    void testBasicContextAnalysis() {
        // Use lenient stubbing to match all possible queries
        when(neo4jService.executeCypherQuery(anyString(), any(Map.class)))
            .thenAnswer(invocation -> {
                String query = invocation.getArgument(0);
                if (query.contains("HAS_DESCRIPTION")) {
                    return List.of(
                        Map.of("content", "Processes payment transactions with validation", "type", "llm_generated", "createdAt", System.currentTimeMillis()),
                        Map.of("content", "Validates input parameters", "type", "inline", "createdAt", System.currentTimeMillis())
                    );
                } else if (query.contains("FileDoc")) {
                    return List.of(
                        Map.of("content", "README content for payment service", "fileName", "PaymentService.md")
                    );
                } else if (query.contains("CALLS") && query.contains("caller")) {
                    return List.of(
                        Map.of("callerId", "PaymentController.processPayment", "callerName", "processPayment", 
                               "callerClass", "PaymentController", "callerPackage", "com.example.controller")
                    );
                } else if (query.contains("CALLS") && query.contains("callee")) {
                    return List.of(
                        Map.of("calleeId", "ValidationService.validate", "calleeName", "validate",
                               "calleeClass", "ValidationService", "calleePackage", "com.example.service")
                    );
                } else if (query.contains("USES_FIELD")) {
                    return List.of(
                        Map.of("fieldId", "paymentAmount", "fieldName", "amount", "fieldType", "BigDecimal")
                    );
                } else if (query.contains("Test") && query.contains("Class")) {
                    return List.of(
                        Map.of("testClassName", "PaymentServiceTest", "packageName", "com.example.service")
                    );
                } else if (query.contains("test") && query.contains("Method")) {
                    return List.of(
                        Map.of("testMethodName", "testProcessPayment", "testClassName", "PaymentServiceTest")
                    );
                } else {
                    return List.of(); // Return empty list for unmatched queries
                }
            });

        List<String> entityIds = List.of("com.example.PaymentService.processPayment");
        Map<String, Object> analysisConfig = Map.of(
            "analysisDepth", "MODERATE",
            "focusAreas", List.of("DOCUMENTATION", "TESTS", "USAGE"),
            "qualityThresholds", Map.of("documentation", 0.6, "tests", 0.5, "usage", 0.4),
            "maxEntityDepth", 5
        );
        Map<String, Object> qualityCriteria = Map.of(
            "documentationWeight", 0.3,
            "testWeight", 0.25,
            "usageWeight", 0.25,
            "businessWeight", 0.2
        );

        Map<String, Object> result = CodeContextEnricherRedesigned.analyzeContextQuality(
            entityIds, analysisConfig, qualityCriteria, null);

        // Verify successful execution
        assertEquals("success", result.get("status"));
        
        // Verify entity analyses
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entityAnalyses = (List<Map<String, Object>>) result.get("entityAnalyses");
        assertNotNull(entityAnalyses);
        assertEquals(1, entityAnalyses.size());
        
        Map<String, Object> entityAnalysis = entityAnalyses.get(0);
        assertEquals("com.example.PaymentService.processPayment", entityAnalysis.get("entityId"));
        
        // Verify documentation context
        @SuppressWarnings("unchecked")
        Map<String, Object> documentation = (Map<String, Object>) entityAnalysis.get("documentation");
        assertNotNull(documentation);
        assertTrue((Boolean) documentation.get("hasDocumentation"));
        assertTrue((Double) documentation.get("completenessScore") > 0);
        
        // Verify usage context
        @SuppressWarnings("unchecked")
        Map<String, Object> usage = (Map<String, Object>) entityAnalysis.get("usage");
        assertNotNull(usage);
        assertTrue((Boolean) usage.get("hasUsageExamples"));
        assertTrue((Integer) usage.get("callerCount") > 0);
        
        // Verify quality assessment
        @SuppressWarnings("unchecked")
        Map<String, Object> qualityAssessment = (Map<String, Object>) result.get("qualityAssessment");
        assertNotNull(qualityAssessment);
        assertTrue((Double) qualityAssessment.get("overallScore") >= 0.0);
        assertNotNull(qualityAssessment.get("overallGrade"));
        
        // Verify gaps identification
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contextGaps = (List<Map<String, Object>>) result.get("contextGaps");
        assertNotNull(contextGaps);
        
        // Verify recommendations
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recommendations = (List<Map<String, Object>>) result.get("recommendations");
        assertNotNull(recommendations);
        
        // Verify metadata
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.get("analysisMetadata");
        assertNotNull(metadata);
        assertEquals(1, metadata.get("entitiesAnalyzed"));
        assertTrue((Long) metadata.get("executionTimeMs") > 0);
        
        // Verify next actions
        @SuppressWarnings("unchecked")
        List<String> nextActions = (List<String>) result.get("nextActions");
        assertNotNull(nextActions);
        assertFalse(nextActions.isEmpty());
    }

    @Test
    void testErrorHandling() {
        // Test with null entity IDs
        Map<String, Object> result = CodeContextEnricherRedesigned.analyzeContextQuality(
            null, Map.of(), Map.of(), null);
        
        assertEquals("error", result.get("status"));
        assertTrue(((String) result.get("error")).contains("Entity IDs cannot be null"));
    }

    @Test
    void testEmptyEntityList() {
        // Test with empty entity list
        Map<String, Object> result = CodeContextEnricherRedesigned.analyzeContextQuality(
            List.of(), Map.of(), Map.of(), null);
        
        assertEquals("error", result.get("status"));
        assertTrue(((String) result.get("error")).contains("Entity IDs cannot be null or empty"));
    }

    @Test
    void testDatabaseError() {
        // Mock database error
        when(neo4jService.executeCypherQuery(anyString(), any(Map.class)))
            .thenThrow(new RuntimeException("Database connection failed"));
        
        List<String> entityIds = List.of("com.example.TestService.method");
        Map<String, Object> result = CodeContextEnricherRedesigned.analyzeContextQuality(
            entityIds, Map.of(), Map.of(), null);
        
        assertEquals("error", result.get("status"));
        assertTrue(((String) result.get("error")).contains("Context analysis failed"));
    }

    @Test
    void testComprehensiveAnalysisStrategy() {
        // Mock comprehensive data for multiple entities using the same answer strategy
        when(neo4jService.executeCypherQuery(anyString(), any(Map.class)))
            .thenAnswer(invocation -> {
                String query = invocation.getArgument(0);
                if (query.contains("HAS_DESCRIPTION")) {
                    return List.of(
                        Map.of("content", "Comprehensive documentation", "type", "llm_generated", "createdAt", System.currentTimeMillis())
                    );
                } else if (query.contains("FileDoc")) {
                    return List.of(
                        Map.of("content", "Comprehensive README content", "fileName", "README.md")
                    );
                } else {
                    return List.of(); // Return empty list for other queries
                }
            });

        List<String> entityIds = List.of(
            "com.example.Service1.method1", 
            "com.example.Service2.method2"
        );
        
        Map<String, Object> analysisConfig = Map.of(
            "analysisDepth", "COMPREHENSIVE",
            "focusAreas", List.of("DOCUMENTATION", "TESTS", "USAGE", "BUSINESS")
        );
        
        Map<String, Object> result = CodeContextEnricherRedesigned.analyzeContextQuality(
            entityIds, analysisConfig, Map.of(), null);
        
        assertEquals("success", result.get("status"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.get("analysisMetadata");
        assertEquals("COMPREHENSIVE", metadata.get("strategy"));
        assertEquals(2, metadata.get("entitiesAnalyzed"));
    }

    @Test
    void testQualityGrading() {
        // Mock high-quality data using the same answer strategy
        when(neo4jService.executeCypherQuery(anyString(), any(Map.class)))
            .thenAnswer(invocation -> {
                String query = invocation.getArgument(0);
                if (query.contains("HAS_DESCRIPTION")) {
                    return List.of(
                        Map.of("content", "Comprehensive documentation with detailed explanations of parameters, return values, usage examples, error handling, performance considerations, security implications, and business logic", "type", "llm_generated", "createdAt", System.currentTimeMillis()),
                        Map.of("content", "Extensive inline documentation explaining complex algorithms, data structures, and optimization strategies", "type", "inline", "createdAt", System.currentTimeMillis()),
                        Map.of("content", "Additional business context documentation explaining domain requirements and constraints", "type", "business", "createdAt", System.currentTimeMillis())
                    );
                } else if (query.contains("FileDoc")) {
                    return List.of(
                        Map.of("content", "Comprehensive README with extensive examples, usage patterns, configuration options, troubleshooting guide, and performance benchmarks", "fileName", "README.md"),
                        Map.of("content", "Detailed API documentation with endpoint specifications", "fileName", "api-docs.md"),
                        Map.of("content", "Architecture overview with system design patterns", "fileName", "architecture.md")
                    );
                } else if (query.contains("CALLS") && query.contains("caller")) {
                    return List.of(
                        Map.of("callerId", "Service1", "callerName", "method1", "callerClass", "Service1", "callerPackage", "com.example.service"),
                        Map.of("callerId", "Service2", "callerName", "method2", "callerClass", "Service2", "callerPackage", "com.example.service"),
                        Map.of("callerId", "Service3", "callerName", "method3", "callerClass", "Service3", "callerPackage", "com.example.service"),
                        Map.of("callerId", "Controller1", "callerName", "handler1", "callerClass", "Controller1", "callerPackage", "com.example.controller"),
                        Map.of("callerId", "Controller2", "callerName", "handler2", "callerClass", "Controller2", "callerPackage", "com.example.controller")
                    );
                } else if (query.contains("CALLS") && query.contains("callee")) {
                    return List.of(
                        Map.of("calleeId", "Service3", "calleeName", "method3", "calleeClass", "Service3", "calleePackage", "com.example.service"),
                        Map.of("calleeId", "Service4", "calleeName", "method4", "calleeClass", "Service4", "calleePackage", "com.example.service"),
                        Map.of("calleeId", "Utils1", "calleeName", "utility1", "calleeClass", "Utils1", "calleePackage", "com.example.utils")
                    );
                } else if (query.contains("USES_FIELD")) {
                    return List.of(
                        Map.of("fieldId", "field1", "fieldName", "field1", "fieldType", "String"),
                        Map.of("fieldId", "field2", "fieldName", "field2", "fieldType", "Integer"),
                        Map.of("fieldId", "field3", "fieldName", "field3", "fieldType", "BigDecimal"),
                        Map.of("fieldId", "field4", "fieldName", "field4", "fieldType", "List<String>")
                    );
                } else if (query.contains("Test") && query.contains("Class")) {
                    return List.of(
                        Map.of("testClassName", "ServiceTest", "packageName", "com.example.service"),
                        Map.of("testClassName", "ServiceIntegrationTest", "packageName", "com.example.service"),
                        Map.of("testClassName", "ServicePerformanceTest", "packageName", "com.example.service"),
                        Map.of("testClassName", "ServiceEndToEndTest", "packageName", "com.example.service")
                    );
                } else if (query.contains("test") && query.contains("Method")) {
                    return List.of(
                        Map.of("testMethodName", "testMethodComprehensive", "testClassName", "ServiceTest"),
                        Map.of("testMethodName", "testMethodEdgeCases", "testClassName", "ServiceTest"),
                        Map.of("testMethodName", "testMethodErrorHandling", "testClassName", "ServiceTest"),
                        Map.of("testMethodName", "testMethodPerformance", "testClassName", "ServiceTest"),
                        Map.of("testMethodName", "testMethodIntegration", "testClassName", "ServiceIntegrationTest"),
                        Map.of("testMethodName", "testMethodEndToEnd", "testClassName", "ServiceEndToEndTest"),
                        Map.of("testMethodName", "testMethodLoadTesting", "testClassName", "ServicePerformanceTest")
                    );
                } else {
                    return List.of(); // Return empty list for unmatched queries
                }
            });

        List<String> entityIds = List.of("com.example.HighQualityService.excellentMethod");
        Map<String, Object> result = CodeContextEnricherRedesigned.analyzeContextQuality(
            entityIds, Map.of(), Map.of(), null);
        
        assertEquals("success", result.get("status"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> qualityAssessment = (Map<String, Object>) result.get("qualityAssessment");
        
        // Should achieve a good quality score with comprehensive data
        double overallScore = (Double) qualityAssessment.get("overallScore");
        assertTrue(overallScore > 0.5, "Expected good quality score but got: " + overallScore);
        
        String grade = (String) qualityAssessment.get("overallGrade");
        assertNotNull(grade);
        assertFalse(grade.equals("F"), "Should not get failing grade with comprehensive data");
    }

    @Test
    void testRecommendationGeneration() {
        // Mock poor quality data to trigger recommendations
        when(neo4jService.executeCypherQuery(anyString(), any(Map.class)))
            .thenReturn(List.of()); // No data = poor quality
        
        List<String> entityIds = List.of("com.example.PoorQualityService.poorMethod");
        Map<String, Object> result = CodeContextEnricherRedesigned.analyzeContextQuality(
            entityIds, Map.of(), Map.of(), null);
        
        assertEquals("success", result.get("status"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recommendations = (List<Map<String, Object>>) result.get("recommendations");
        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty(), "Should generate recommendations for poor quality code");
        
        // Check that recommendations have proper structure
        Map<String, Object> firstRec = recommendations.get(0);
        assertNotNull(firstRec.get("title"));
        assertNotNull(firstRec.get("description"));
        assertNotNull(firstRec.get("priority"));
        assertNotNull(firstRec.get("actionItems"));
    }
}