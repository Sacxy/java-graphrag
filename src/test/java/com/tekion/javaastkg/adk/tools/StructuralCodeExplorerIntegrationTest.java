package com.tekion.javaastkg.adk.tools;

import com.tekion.javaastkg.service.Neo4jService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Structural Code Explorer with actual Neo4j service (mock implementation)
 */
class StructuralCodeExplorerIntegrationTest {

    private Neo4jService neo4jService;

    @BeforeEach
    void setUp() {
        // Use actual Neo4j service with mock implementation
        neo4jService = new Neo4jService();
        StructuralCodeExplorer.initialize(neo4jService);
    }

    @Test
    void testStructuralExplorationWithMockData() {
        // Given - entities that will trigger mock responses
        List<String> seedEntities = List.of("PaymentService", "UserController");
        Map<String, Object> explorationScope = Map.of(
            "scope", "MODERATE",
            "maxDepth", 2,
            "maxNodes", 50
        );
        Map<String, Object> focusAreas = Map.of();

        // When
        Map<String, Object> result = StructuralCodeExplorer.exploreStructure(
            seedEntities, explorationScope, focusAreas, null
        );

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        
        // Verify basic structure
        assertTrue(result.containsKey("graph"));
        assertTrue(result.containsKey("patterns"));
        assertTrue(result.containsKey("insights"));
        assertTrue(result.containsKey("explorationMetadata"));
        
        System.out.println("=== Structural Code Explorer Results ===");
        System.out.println("Status: " + result.get("status"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.get("explorationMetadata");
        System.out.println("Strategy: " + metadata.get("strategy"));
        System.out.println("Nodes explored: " + metadata.get("nodesExplored"));
        System.out.println("Relationships found: " + metadata.get("relationshipsFound"));
        System.out.println("Patterns detected: " + metadata.get("patternsDetected"));
        System.out.println("Execution time: " + metadata.get("executionTimeMs") + "ms");
        
        // Verify we found some structural elements
        assertTrue((Integer) metadata.get("nodesExplored") > 0, "Should explore at least some nodes");
    }

    @Test
    void testLayeredArchitectureDetection() {
        // Given - Service entities that should trigger layered architecture pattern
        List<String> seedEntities = List.of("PaymentService", "OrderService", "UserService");
        Map<String, Object> explorationScope = Map.of(
            "scope", "LAYERED",
            "maxDepth", 2,
            "maxNodes", 50
        );

        // When
        Map<String, Object> result = StructuralCodeExplorer.exploreStructure(
            seedEntities, explorationScope, Map.of(), null
        );

        // Then
        assertEquals("success", result.get("status"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> patterns = (List<Map<String, Object>>) result.get("patterns");
        
        System.out.println("=== Detected Patterns ===");
        for (Map<String, Object> pattern : patterns) {
            System.out.println("Pattern: " + pattern.get("type"));
            System.out.println("Description: " + pattern.get("description"));
            System.out.println("Confidence: " + pattern.get("confidence"));
            System.out.println("---");
        }
        
        // Should detect some patterns or at least have insights
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> insights = (List<Map<String, Object>>) result.get("insights");
        
        // Either patterns or insights should be present
        assertTrue(patterns.size() > 0 || insights.size() > 0, 
            "Should detect at least some patterns or generate insights");
    }

    @Test
    void testGraphTraversal() {
        // Given - Controller that should have service dependencies
        List<String> seedEntities = List.of("UserController");
        Map<String, Object> explorationScope = Map.of(
            "scope", "COMPREHENSIVE",
            "maxDepth", 3,
            "maxNodes", 30
        );

        // When
        Map<String, Object> result = StructuralCodeExplorer.exploreStructure(
            seedEntities, explorationScope, Map.of(), null
        );

        // Then
        assertEquals("success", result.get("status"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> graph = (Map<String, Object>) result.get("graph");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");
        
        System.out.println("=== Graph Structure ===");
        System.out.println("Nodes found: " + nodes.size());
        System.out.println("Edges found: " + edges.size());
        
        System.out.println("=== Nodes ===");
        for (Map<String, Object> node : nodes) {
            System.out.println("- " + node.get("name") + " [" + node.get("type") + "] in " + node.get("packageName"));
        }
        
        System.out.println("=== Relationships ===");
        for (Map<String, Object> edge : edges) {
            System.out.println("- " + edge.get("sourceId") + " --[" + edge.get("type") + "]--> " + edge.get("targetId"));
        }
        
        // Should have found the controller and related services
        assertTrue(nodes.size() > 0, "Should find at least the controller node");
        
        // Verify we have a controller node
        boolean hasController = nodes.stream()
            .anyMatch(node -> node.get("name").toString().contains("Controller"));
        assertTrue(hasController, "Should contain a Controller node");
    }

    @Test
    void testInsightGeneration() {
        // Given - Multiple entities to trigger insights
        List<String> seedEntities = List.of("PaymentService", "PaymentController", "PaymentRepository");
        Map<String, Object> explorationScope = Map.of(
            "scope", "MODERATE",
            "maxDepth", 2,
            "maxNodes", 40
        );

        // When
        Map<String, Object> result = StructuralCodeExplorer.exploreStructure(
            seedEntities, explorationScope, Map.of(), null
        );

        // Then
        assertEquals("success", result.get("status"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> insights = (List<Map<String, Object>>) result.get("insights");
        
        System.out.println("=== Architectural Insights ===");
        for (Map<String, Object> insight : insights) {
            System.out.println("Title: " + insight.get("title"));
            System.out.println("Category: " + insight.get("category"));
            System.out.println("Severity: " + insight.get("severity"));
            System.out.println("Description: " + insight.get("description"));
            System.out.println("---");
        }
        
        // Should generate coupling and complexity insights at minimum
        assertTrue(insights.size() >= 2, "Should generate at least coupling and complexity insights");
        
        // Verify insight structure
        Map<String, Object> firstInsight = insights.get(0);
        assertNotNull(firstInsight.get("title"));
        assertNotNull(firstInsight.get("category"));
        assertNotNull(firstInsight.get("severity"));
        assertNotNull(firstInsight.get("description"));
    }

    @Test
    void testNextActionSuggestions() {
        // Given - Focused exploration to trigger expansion suggestion
        List<String> seedEntities = List.of("OrderService");
        Map<String, Object> explorationScope = Map.of(
            "scope", "FOCUSED",
            "maxDepth", 1,
            "maxNodes", 10
        );

        // When
        Map<String, Object> result = StructuralCodeExplorer.exploreStructure(
            seedEntities, explorationScope, Map.of(), null
        );

        // Then
        assertEquals("success", result.get("status"));
        
        @SuppressWarnings("unchecked")
        List<String> nextActions = (List<String>) result.get("nextActions");
        
        System.out.println("=== Next Action Suggestions ===");
        for (String action : nextActions) {
            System.out.println("- " + action);
        }
        
        // Should suggest expanding exploration for focused scope
        assertTrue(nextActions.contains("EXPAND_EXPLORATION"), 
            "Should suggest expanding exploration for focused scope");
    }
}