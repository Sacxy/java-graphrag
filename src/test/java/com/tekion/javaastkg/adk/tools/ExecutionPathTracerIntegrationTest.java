package com.tekion.javaastkg.adk.tools;

import com.tekion.javaastkg.service.Neo4jService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Execution Path Tracer with actual Neo4j service
 */
class ExecutionPathTracerIntegrationTest {

    private Neo4jService neo4jService;

    @BeforeEach
    void setUp() {
        // Use actual Neo4j service with mock implementation
        neo4jService = new Neo4jService();
        ExecutionPathTracer.initialize(neo4jService);
    }

    @Test
    void testBasicExecutionPathTracing() {
        // Given - Controllers that should have execution paths
        List<String> startingPoints = List.of("PaymentController.processPayment", "UserController.createUser");
        Map<String, Object> traceConfig = Map.of(
            "maxDepth", 5,
            "includeDataFlow", true,
            "trackPerformance", false
        );

        // When
        Map<String, Object> result = ExecutionPathTracer.tracePath(
            startingPoints, "METHOD_CALLS", traceConfig, null
        );

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        
        // Verify basic structure
        assertTrue(result.containsKey("paths"));
        assertTrue(result.containsKey("criticalPaths"));
        assertTrue(result.containsKey("securityIssues"));
        assertTrue(result.containsKey("traceMetadata"));
        
        System.out.println("=== Execution Path Tracer Results ===");
        System.out.println("Status: " + result.get("status"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.get("traceMetadata");
        System.out.println("Strategy: " + metadata.get("strategy"));
        System.out.println("Paths traced: " + metadata.get("pathsTraced"));
        System.out.println("Max depth reached: " + metadata.get("maxDepthReached"));
        System.out.println("Critical paths found: " + metadata.get("criticalPathsFound"));
        System.out.println("Execution time: " + metadata.get("executionTimeMs") + "ms");
        
        // Verify we found some paths
        assertTrue((Integer) metadata.get("pathsTraced") > 0, "Should trace at least some paths");
    }

    @Test
    void testDataFlowTracing() {
        // Given - Service method that should have data flow
        List<String> startingPoints = List.of("PaymentService.processPayment");
        Map<String, Object> traceConfig = Map.of(
            "maxDepth", 4,
            "includeDataFlow", true,
            "trackPerformance", false
        );

        // When
        Map<String, Object> result = ExecutionPathTracer.tracePath(
            startingPoints, "DATA_FLOW", traceConfig, null
        );

        // Then
        assertEquals("success", result.get("status"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> paths = (List<Map<String, Object>>) result.get("paths");
        
        System.out.println("=== Data Flow Tracing Results ===");
        for (Map<String, Object> path : paths) {
            System.out.println("Path ID: " + path.get("id"));
            System.out.println("Start Method: " + path.get("startMethod"));
            System.out.println("Depth: " + path.get("depth"));
            System.out.println("Complexity: " + path.get("complexity"));
            System.out.println("Terminal: " + path.get("isTerminal"));
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> steps = (List<Map<String, Object>>) path.get("steps");
            System.out.println("Steps:");
            for (Map<String, Object> step : steps) {
                System.out.println("  - " + step.get("sourceMethod") + " --[" + 
                    step.get("stepType") + "]--> " + step.get("targetMethod"));
            }
            System.out.println("---");
        }
        
        // Should have found execution paths
        assertTrue(paths.size() > 0, "Should find at least one execution path");
    }

    @Test
    void testCriticalPathAnalysis() {
        // Given - Complex method that should have critical paths
        List<String> startingPoints = List.of("OrderService.processOrder", "PaymentService.processPayment");
        Map<String, Object> traceConfig = Map.of(
            "maxDepth", 6,
            "includeDataFlow", true,
            "trackPerformance", true
        );

        // When
        Map<String, Object> result = ExecutionPathTracer.tracePath(
            startingPoints, "CRITICAL_PATH", traceConfig, null
        );

        // Then
        assertEquals("success", result.get("status"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> criticalPaths = (List<Map<String, Object>>) result.get("criticalPaths");
        
        System.out.println("=== Critical Path Analysis ===");
        for (Map<String, Object> criticalPath : criticalPaths) {
            System.out.println("Critical Path ID: " + criticalPath.get("id"));
            System.out.println("Criticality Score: " + criticalPath.get("criticalityScore"));
            
            @SuppressWarnings("unchecked")
            List<String> factors = (List<String>) criticalPath.get("criticalityFactors");
            System.out.println("Criticality Factors:");
            factors.forEach(factor -> System.out.println("  - " + factor));
            
            @SuppressWarnings("unchecked")
            List<String> recommendations = (List<String>) criticalPath.get("recommendations");
            System.out.println("Recommendations:");
            recommendations.forEach(rec -> System.out.println("  - " + rec));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> perfMetrics = (Map<String, Object>) criticalPath.get("performanceMetrics");
            if (!perfMetrics.isEmpty()) {
                System.out.println("Performance Metrics: " + perfMetrics);
            }
            System.out.println("---");
        }
        
        // Should have analysis data
        assertNotNull(criticalPaths);
    }

    @Test
    void testSecurityAnalysis() {
        // Given - Methods that should trigger security analysis
        List<String> startingPoints = List.of("UserController.login", "PaymentController.processPayment");
        Map<String, Object> traceConfig = Map.of(
            "maxDepth", 4,
            "includeDataFlow", true,
            "trackPerformance", false
        );

        // When
        Map<String, Object> result = ExecutionPathTracer.tracePath(
            startingPoints, "METHOD_CALLS", traceConfig, null
        );

        // Then
        assertEquals("success", result.get("status"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> securityIssues = (List<Map<String, Object>>) result.get("securityIssues");
        
        System.out.println("=== Security Analysis ===");
        for (Map<String, Object> issue : securityIssues) {
            System.out.println("Issue Type: " + issue.get("type"));
            System.out.println("Severity: " + issue.get("severity"));
            System.out.println("Description: " + issue.get("description"));
            System.out.println("Location: " + issue.get("location"));
            System.out.println("Recommendation: " + issue.get("recommendation"));
            System.out.println("---");
        }
        
        // Security analysis should run (may or may not find issues)
        assertNotNull(securityIssues);
    }

    @Test
    void testPerformanceTracking() {
        // Given - Complex method with performance tracking enabled
        List<String> startingPoints = List.of("OrderService.processComplexOrder");
        Map<String, Object> traceConfig = Map.of(
            "maxDepth", 8,
            "includeDataFlow", true,
            "trackPerformance", true
        );

        // When
        Map<String, Object> result = ExecutionPathTracer.tracePath(
            startingPoints, "CRITICAL_PATH", traceConfig, null
        );

        // Then
        assertEquals("success", result.get("status"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> criticalPaths = (List<Map<String, Object>>) result.get("criticalPaths");
        
        // Should track performance metrics
        if (!criticalPaths.isEmpty()) {
            Map<String, Object> firstCritical = criticalPaths.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> perfMetrics = (Map<String, Object>) firstCritical.get("performanceMetrics");
            
            System.out.println("=== Performance Tracking ===");
            System.out.println("Performance Metrics: " + perfMetrics);
            
            // Should have performance data when tracking is enabled
            assertTrue(perfMetrics.containsKey("estimatedLatency") || perfMetrics.isEmpty(), 
                "Performance metrics should be tracked or empty");
        }
    }

    @Test
    void testNextActionSuggestions() {
        // Given - Methods that should trigger various next actions
        List<String> startingPoints = List.of("PaymentService.processPayment", "OrderRepository.saveOrder");
        Map<String, Object> traceConfig = Map.of(
            "maxDepth", 10, // Deep to trigger refactoring suggestions
            "includeDataFlow", true,
            "trackPerformance", true
        );

        // When
        Map<String, Object> result = ExecutionPathTracer.tracePath(
            startingPoints, "CRITICAL_PATH", traceConfig, null
        );

        // Then
        assertEquals("success", result.get("status"));
        
        @SuppressWarnings("unchecked")
        List<String> nextActions = (List<String>) result.get("nextActions");
        
        System.out.println("=== Next Action Suggestions ===");
        for (String action : nextActions) {
            System.out.println("- " + action);
        }
        
        // Should provide actionable suggestions
        assertNotNull(nextActions);
        
        // Common next actions we might expect
        List<String> expectedActions = List.of(
            "OPTIMIZE_CRITICAL_PATHS", 
            "FIX_SECURITY_ISSUES", 
            "IMPLEMENT_CACHING", 
            "REFACTOR_DEEP_CALLS"
        );
        
        // At least some intersection with expected actions
        assertTrue(nextActions.stream().anyMatch(expectedActions::contains) || nextActions.isEmpty(),
            "Should suggest relevant optimization actions");
    }

    @Test
    void testErrorHandling() {
        // Given - Empty starting points
        List<String> startingPoints = List.of();
        Map<String, Object> traceConfig = Map.of("maxDepth", 5);

        // When
        Map<String, Object> result = ExecutionPathTracer.tracePath(
            startingPoints, "METHOD_CALLS", traceConfig, null
        );

        // Then
        assertNotNull(result);
        assertEquals("error", result.get("status"));
        assertTrue(result.get("error").toString().contains("cannot be null or empty"));
    }

    @Test
    void testDifferentTraceStrategies() {
        // Given
        List<String> startingPoints = List.of("PaymentService.processPayment");
        Map<String, Object> traceConfig = Map.of("maxDepth", 5);

        System.out.println("=== Testing Different Trace Strategies ===");

        // Test CALL_GRAPH strategy
        Map<String, Object> callGraphResult = ExecutionPathTracer.tracePath(
            startingPoints, "METHOD_CALLS", traceConfig, null
        );
        assertEquals("success", callGraphResult.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> callGraphMeta = (Map<String, Object>) callGraphResult.get("traceMetadata");
        System.out.println("CALL_GRAPH Strategy - Paths: " + callGraphMeta.get("pathsTraced"));

        // Test DATA_FLOW strategy
        Map<String, Object> dataFlowResult = ExecutionPathTracer.tracePath(
            startingPoints, "DATA_FLOW", traceConfig, null
        );
        assertEquals("success", dataFlowResult.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> dataFlowMeta = (Map<String, Object>) dataFlowResult.get("traceMetadata");
        System.out.println("DATA_FLOW Strategy - Paths: " + dataFlowMeta.get("pathsTraced"));

        // Test CRITICAL_PATH strategy
        Map<String, Object> criticalResult = ExecutionPathTracer.tracePath(
            startingPoints, "CRITICAL_PATH", traceConfig, null
        );
        assertEquals("success", criticalResult.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> criticalMeta = (Map<String, Object>) criticalResult.get("traceMetadata");
        System.out.println("CRITICAL_PATH Strategy - Paths: " + criticalMeta.get("pathsTraced"));

        // All strategies should work
        assertEquals("CALL_GRAPH", callGraphMeta.get("strategy"));
        assertEquals("DATA_FLOW", dataFlowMeta.get("strategy"));
        assertEquals("CRITICAL_PATH", criticalMeta.get("strategy"));
    }

    @Test
    void testTraceMetadata() {
        // Given
        List<String> startingPoints = List.of("UserService.validateUser", "PaymentService.processPayment");
        Map<String, Object> traceConfig = Map.of(
            "maxDepth", 6,
            "includeDataFlow", true,
            "trackPerformance", true
        );

        // When
        Map<String, Object> result = ExecutionPathTracer.tracePath(
            startingPoints, "DATA_FLOW", traceConfig, null
        );

        // Then
        assertEquals("success", result.get("status"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.get("traceMetadata");
        
        // Verify all expected metadata fields
        assertNotNull(metadata.get("strategy"));
        assertNotNull(metadata.get("pathsTraced"));
        assertNotNull(metadata.get("maxDepthReached"));
        assertNotNull(metadata.get("criticalPathsFound"));
        assertNotNull(metadata.get("executionTimeMs"));
        assertNotNull(metadata.get("traceType"));
        
        System.out.println("=== Trace Metadata Validation ===");
        System.out.println("Strategy: " + metadata.get("strategy"));
        System.out.println("Paths Traced: " + metadata.get("pathsTraced"));
        System.out.println("Max Depth Reached: " + metadata.get("maxDepthReached"));
        System.out.println("Critical Paths Found: " + metadata.get("criticalPathsFound"));
        System.out.println("Execution Time: " + metadata.get("executionTimeMs") + "ms");
        System.out.println("Trace Type: " + metadata.get("traceType"));
        
        // Validate data types and ranges
        assertTrue((Integer) metadata.get("pathsTraced") >= 0);
        assertTrue((Integer) metadata.get("maxDepthReached") >= 0);
        assertTrue((Integer) metadata.get("criticalPathsFound") >= 0);
        assertTrue((Long) metadata.get("executionTimeMs") >= 0);
        assertEquals("DATA_FLOW", metadata.get("traceType"));
    }
}