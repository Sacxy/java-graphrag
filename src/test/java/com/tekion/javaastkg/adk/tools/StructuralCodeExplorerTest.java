package com.tekion.javaastkg.adk.tools;

import com.tekion.javaastkg.service.Neo4jService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for Structural Code Explorer ADK tool
 */
@ExtendWith(MockitoExtension.class)
class StructuralCodeExplorerTest {

    @Mock
    private Neo4jService neo4jService;

    @BeforeEach
    void setUp() {
        // Initialize the tool with mocked Neo4j service
        StructuralCodeExplorer.initialize(neo4jService);
    }

    @Test
    void testBasicStructuralExploration() {
        // Given
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
        
        // Verify structure of response
        assertTrue(result.containsKey("graph"));
        assertTrue(result.containsKey("patterns"));
        assertTrue(result.containsKey("insights"));
        assertTrue(result.containsKey("explorationMetadata"));
        assertTrue(result.containsKey("nextActions"));
        
        // Verify metadata
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.get("explorationMetadata");
        assertNotNull(metadata.get("strategy"));
        assertNotNull(metadata.get("executionTimeMs"));
        assertTrue((Integer) metadata.get("nodesExplored") >= 0);
        assertTrue((Integer) metadata.get("relationshipsFound") >= 0);
    }

    @Test
    void testFocusedExplorationStrategy() {
        // Given
        List<String> seedEntities = List.of("UserService");
        Map<String, Object> explorationScope = Map.of(
            "scope", "FOCUSED",
            "maxDepth", 1,
            "maxNodes", 20
        );

        // When
        Map<String, Object> result = StructuralCodeExplorer.exploreStructure(
            seedEntities, explorationScope, Map.of(), null
        );

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.get("explorationMetadata");
        assertEquals("FOCUSED", metadata.get("strategy"));
    }

    @Test
    void testComprehensiveExplorationStrategy() {
        // Given
        List<String> seedEntities = List.of("OrderService");
        Map<String, Object> explorationScope = Map.of(
            "scope", "COMPREHENSIVE",
            "maxDepth", 4,
            "maxNodes", 100
        );

        // When
        Map<String, Object> result = StructuralCodeExplorer.exploreStructure(
            seedEntities, explorationScope, Map.of(), null
        );

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.get("explorationMetadata");
        assertEquals("COMPREHENSIVE", metadata.get("strategy"));
    }

    @Test
    void testEmptyEntitiesError() {
        // Given
        List<String> seedEntities = List.of();
        Map<String, Object> explorationScope = Map.of("scope", "MODERATE");

        // When
        Map<String, Object> result = StructuralCodeExplorer.exploreStructure(
            seedEntities, explorationScope, Map.of(), null
        );

        // Then
        assertNotNull(result);
        assertEquals("error", result.get("status"));
        assertTrue(result.get("error").toString().contains("cannot be null or empty"));
    }

    @Test
    void testPatternDetection() {
        // Given - entities that should trigger pattern detection
        List<String> seedEntities = List.of("PaymentService", "UserService", "OrderService");
        Map<String, Object> explorationScope = Map.of(
            "scope", "MODERATE",
            "maxDepth", 3,
            "maxNodes", 50
        );

        // When
        Map<String, Object> result = StructuralCodeExplorer.exploreStructure(
            seedEntities, explorationScope, Map.of(), null
        );

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> patterns = (List<Map<String, Object>>) result.get("patterns");
        assertNotNull(patterns);
        
        // Verify we can detect some patterns (exact patterns depend on mock data)
        // At minimum, we should have insights about the structure
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> insights = (List<Map<String, Object>>) result.get("insights");
        assertNotNull(insights);
        assertTrue(insights.size() > 0);
    }

    @Test
    void testGraphStructure() {
        // Given
        List<String> seedEntities = List.of("UserController");
        Map<String, Object> explorationScope = Map.of(
            "scope", "LAYERED",
            "maxDepth", 2,
            "maxNodes", 30
        );

        // When
        Map<String, Object> result = StructuralCodeExplorer.exploreStructure(
            seedEntities, explorationScope, Map.of(), null
        );

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> graph = (Map<String, Object>) result.get("graph");
        assertNotNull(graph);
        
        // Verify graph has nodes and edges
        assertTrue(graph.containsKey("nodes"));
        assertTrue(graph.containsKey("edges"));
        assertTrue(graph.containsKey("nodeCount"));
        assertTrue(graph.containsKey("edgeCount"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        assertTrue(nodes.size() > 0);
        
        // Verify node structure
        Map<String, Object> firstNode = nodes.get(0);
        assertTrue(firstNode.containsKey("id"));
        assertTrue(firstNode.containsKey("name"));
        assertTrue(firstNode.containsKey("type"));
    }

    @Test
    void testNextActionsGeneration() {
        // Given
        List<String> seedEntities = List.of("PaymentService", "PaymentRepository");
        Map<String, Object> explorationScope = Map.of(
            "scope", "FOCUSED",
            "maxDepth", 2,
            "maxNodes", 25
        );

        // When
        Map<String, Object> result = StructuralCodeExplorer.exploreStructure(
            seedEntities, explorationScope, Map.of(), null
        );

        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        
        @SuppressWarnings("unchecked")
        List<String> nextActions = (List<String>) result.get("nextActions");
        assertNotNull(nextActions);
        
        // Should suggest expanding exploration for focused scope
        assertTrue(nextActions.contains("EXPAND_EXPLORATION"));
    }
}