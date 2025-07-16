package com.tekion.javaastkg.adk.tools;

import com.tekion.javaastkg.service.Neo4jService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 🔍 Semantic Code Hunter Test Suite
 * 
 * Comprehensive tests for the Semantic Code Hunter tool including:
 * - Input validation and parameter extraction
 * - Search strategy selection
 * - Result processing and filtering
 * - Error handling and edge cases
 * - ADK integration patterns with real Neo4j service
 */
class SemanticCodeHunterTest {
    
    @Mock
    private Neo4jService neo4jService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Initialize the static Neo4j service reference
        SemanticCodeHunter.initialize(neo4jService);
    }
    
    @Test
    @DisplayName("🎯 Should successfully hunt code entities with basic parameters")
    void shouldHuntCodeEntitiesSuccessfully() {
        // Given
        List<String> entities = Arrays.asList("PaymentService", "processPayment");
        Map<String, Object> searchCriteria = Map.of(
            "entityTypes", Arrays.asList("CLASS", "METHOD"),
            "scope", "MODERATE",
            "maxResults", 20
        );
        Map<String, Object> filters = Map.of();
        
        // Mock Neo4j service responses
        when(neo4jService.isHealthy()).thenReturn(true);
        when(neo4jService.executeFullTextSearch(eq("CLASS"), eq("PaymentService"), anyInt()))
            .thenReturn(Arrays.asList(createMockNeo4jResult("payment-service-1", "PaymentService", 0.9)));
        when(neo4jService.executeFullTextSearch(eq("METHOD"), eq("processPayment"), anyInt()))
            .thenReturn(Arrays.asList(createMockNeo4jResult("process-payment-1", "processPayment", 0.85)));
        
        // When
        Map<String, Object> result = SemanticCodeHunter.huntCode(entities, searchCriteria, filters, null);
        
        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertTrue(result.containsKey("matches"));
        assertTrue(result.containsKey("searchStrategy"));
        assertTrue(result.containsKey("confidence"));
        assertTrue(result.containsKey("nextActions"));
        
        // Verify matches structure
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertFalse(matches.isEmpty());
        
        // Verify first match has required fields
        Map<String, Object> firstMatch = matches.get(0);
        assertTrue(firstMatch.containsKey("entityId"));
        assertTrue(firstMatch.containsKey("entityType"));
        assertTrue(firstMatch.containsKey("name"));
        assertTrue(firstMatch.containsKey("confidence"));
        assertTrue(firstMatch.containsKey("matchReason"));
        assertTrue(firstMatch.containsKey("source"));
        
        // Verify metadata
        Map<String, Object> metadata = (Map<String, Object>) result.get("searchMetadata");
        assertTrue(metadata.containsKey("executionTimeMs"));
        assertTrue(metadata.containsKey("strategy"));
        assertTrue(metadata.containsKey("filteredResults"));
        assertTrue(metadata.containsKey("neo4jHealthy"));
        assertEquals(true, metadata.get("neo4jHealthy"));
    }
    
    /**
     * Helper method to create mock Neo4j result
     */
    private Map<String, Object> createMockNeo4jResult(String id, String name, double score) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("name", name);
        result.put("signature", "public class " + name);
        result.put("filePath", "/src/main/java/com/example/" + name + ".java");
        result.put("startLine", 10);
        result.put("score", score);
        return result;
    }
    
    @Test
    @DisplayName("❌ Should handle empty entities list gracefully")
    void shouldHandleEmptyEntitiesListGracefully() {
        // Given
        List<String> entities = Arrays.asList();
        Map<String, Object> searchCriteria = Map.of();
        Map<String, Object> filters = Map.of();
        
        // When
        Map<String, Object> result = SemanticCodeHunter.huntCode(entities, searchCriteria, filters, null);
        
        // Then
        assertNotNull(result);
        assertEquals("error", result.get("status"));
        assertTrue(result.get("error").toString().contains("Entities list cannot be empty"));
    }
    
    @Test
    @DisplayName("🔧 Should handle null parameters gracefully")
    void shouldHandleNullParametersGracefully() {
        // Given
        List<String> entities = Arrays.asList("TestClass");
        
        // When
        Map<String, Object> result = SemanticCodeHunter.huntCode(entities, null, null, null);
        
        // Then
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        
        // Should use default values
        Map<String, Object> metadata = (Map<String, Object>) result.get("searchMetadata");
        assertEquals("HYBRID", metadata.get("strategy"));
        assertEquals("MODERATE", metadata.get("scope"));
    }
    
    @Test
    @DisplayName("⚙️ Should select appropriate search strategy based on query characteristics")
    void shouldSelectAppropriateSearchStrategy() {
        // Test PRECISE strategy for specific entities with FOCUSED scope
        List<String> specificEntities = Arrays.asList("PaymentService");
        Map<String, Object> focusedCriteria = Map.of("scope", "FOCUSED");
        
        Map<String, Object> result = SemanticCodeHunter.huntCode(specificEntities, focusedCriteria, Map.of(), null);
        
        // Should use PRECISE strategy for focused, specific entities
        assertEquals("success", result.get("status"));
        String strategy = (String) result.get("searchStrategy");
        assertTrue(Arrays.asList("PRECISE", "HYBRID").contains(strategy));
    }
    
    @Test
    @DisplayName("🔍 Should handle conceptual search terms appropriately")
    void shouldHandleConceptualSearchTerms() {
        // Given
        List<String> conceptualEntities = Arrays.asList("payment processing logic", "user authentication");
        Map<String, Object> searchCriteria = Map.of("scope", "BROAD");
        
        // When
        Map<String, Object> result = SemanticCodeHunter.huntCode(conceptualEntities, searchCriteria, Map.of(), null);
        
        // Then
        assertEquals("success", result.get("status"));
        String strategy = (String) result.get("searchStrategy");
        assertTrue(Arrays.asList("SEMANTIC", "EXPLORATORY").contains(strategy));
    }
    
    @Test
    @DisplayName("🎛️ Should apply filters correctly")
    void shouldApplyFiltersCorrectly() {
        // Given
        List<String> entities = Arrays.asList("TestService", "TestController");
        Map<String, Object> searchCriteria = Map.of("maxResults", 10);
        Map<String, Object> filters = Map.of(
            "minConfidence", 0.8,
            "excludePatterns", Arrays.asList("test", "mock")
        );
        
        // When
        Map<String, Object> result = SemanticCodeHunter.huntCode(entities, searchCriteria, filters, null);
        
        // Then
        assertEquals("success", result.get("status"));
        
        // Verify filtering was applied
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        for (Map<String, Object> match : matches) {
            double confidence = (Double) match.get("confidence");
            assertTrue(confidence >= 0.8, "Match confidence should be >= 0.8");
        }
    }
    
    @Test
    @DisplayName("🔄 Should generate alternatives for poor results")
    void shouldGenerateAlternativesForPoorResults() {
        // Given
        List<String> entities = Arrays.asList("VerySpecificUnlikelyClassName");
        Map<String, Object> searchCriteria = Map.of();
        Map<String, Object> filters = Map.of();
        
        // When
        Map<String, Object> result = SemanticCodeHunter.huntCode(entities, searchCriteria, filters, null);
        
        // Then
        assertEquals("success", result.get("status"));
        
        // Should have alternatives
        List<String> alternatives = (List<String>) result.get("alternatives");
        assertNotNull(alternatives);
        
        // Should include wildcard suggestions
        assertTrue(alternatives.stream().anyMatch(alt -> alt.contains("*")));
    }
    
    @Test
    @DisplayName("📊 Should provide comprehensive metadata")
    void shouldProvideComprehensiveMetadata() {
        // Given
        List<String> entities = Arrays.asList("ServiceClass", "processMethod");
        Map<String, Object> searchCriteria = Map.of(
            "entityTypes", Arrays.asList("CLASS", "METHOD"),
            "scope", "MODERATE",
            "maxResults", 15
        );
        
        // When
        Map<String, Object> result = SemanticCodeHunter.huntCode(entities, searchCriteria, Map.of(), null);
        
        // Then
        assertEquals("success", result.get("status"));
        
        // Verify comprehensive metadata
        Map<String, Object> metadata = (Map<String, Object>) result.get("searchMetadata");
        assertNotNull(metadata.get("totalCandidates"));
        assertNotNull(metadata.get("filteredResults"));
        assertNotNull(metadata.get("executionTimeMs"));
        assertNotNull(metadata.get("strategy"));
        assertNotNull(metadata.get("scope"));
        assertNotNull(metadata.get("entityTypes"));
        
        // Verify execution time is reasonable
        Long executionTime = (Long) metadata.get("executionTimeMs");
        assertTrue(executionTime >= 0);
        assertTrue(executionTime < 5000, "Execution time should be reasonable");
    }
    
    @Test
    @DisplayName("🎯 Should provide appropriate next actions based on results")
    void shouldProvideAppropriateNextActions() {
        // Given
        List<String> entities = Arrays.asList("CommonService");
        Map<String, Object> searchCriteria = Map.of();
        
        // When
        Map<String, Object> result = SemanticCodeHunter.huntCode(entities, searchCriteria, Map.of(), null);
        
        // Then
        assertEquals("success", result.get("status"));
        
        List<String> nextActions = (List<String>) result.get("nextActions");
        assertNotNull(nextActions);
        assertFalse(nextActions.isEmpty());
        
        // Should suggest logical next steps
        assertTrue(nextActions.stream().anyMatch(action -> 
            action.contains("EXPLORE") || action.contains("ENRICH") || action.contains("FILTER")));
    }
    
    @Test
    @DisplayName("📝 Should provide clear explanations for agent understanding")
    void shouldProvideExplanationsForAgentUnderstanding() {
        // Given
        List<String> entities = Arrays.asList("PaymentProcessor");
        Map<String, Object> searchCriteria = Map.of();
        
        // When
        Map<String, Object> result = SemanticCodeHunter.huntCode(entities, searchCriteria, Map.of(), null);
        
        // Then
        assertEquals("success", result.get("status"));
        
        String explanation = (String) result.get("explanation");
        assertNotNull(explanation);
        assertFalse(explanation.trim().isEmpty());
        
        // Should mention strategy and confidence
        assertTrue(explanation.toLowerCase().contains("strategy"));
        assertTrue(explanation.toLowerCase().contains("confidence"));
    }
    
    @Test
    @DisplayName("🔧 Should handle various entity types configuration")
    void shouldHandleEntityTypesConfiguration() {
        // Given
        List<String> entities = Arrays.asList("TestEntity");
        Map<String, Object> searchCriteria = Map.of(
            "entityTypes", Arrays.asList("INTERFACE", "ENUM", "ANNOTATION")
        );
        
        // When
        Map<String, Object> result = SemanticCodeHunter.huntCode(entities, searchCriteria, Map.of(), null);
        
        // Then
        assertEquals("success", result.get("status"));
        
        Map<String, Object> metadata = (Map<String, Object>) result.get("searchMetadata");
        List<String> entityTypes = (List<String>) metadata.get("entityTypes");
        
        assertTrue(entityTypes.contains("INTERFACE"));
        assertTrue(entityTypes.contains("ENUM"));
        assertTrue(entityTypes.contains("ANNOTATION"));
    }
    
    @Test
    @DisplayName("⚡ Should handle performance edge cases")
    void shouldHandlePerformanceEdgeCases() {
        // Given - Large entity list
        List<String> manyEntities = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            manyEntities.add("Entity" + i);
        }
        Map<String, Object> searchCriteria = Map.of("maxResults", 100);
        
        // When
        Map<String, Object> result = SemanticCodeHunter.huntCode(manyEntities, searchCriteria, Map.of(), null);
        
        // Then
        assertEquals("success", result.get("status"));
        
        // Should handle large requests gracefully
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertNotNull(matches);
        
        // Execution time should still be reasonable
        Map<String, Object> metadata = (Map<String, Object>) result.get("searchMetadata");
        Long executionTime = (Long) metadata.get("executionTimeMs");
        assertTrue(executionTime < 10000, "Should handle large requests within reasonable time");
    }
}