package com.tekion.javaastkg.adk;

import com.tekion.javaastkg.adk.tools.QueryIntentClassifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.io.InputStream;

/**
 * 🧪 Test Suite for Multi-Agent QueryIntentClassifier
 * 
 * Verifies that the refactored QueryIntentClassifier works with its internal sub-agent structure.
 */
public class QueryIntentClassifierMultiAgentTest {
    
    @BeforeAll
    static void setupApiKey() {
        // Load Google API key from application.yml for test
        try (InputStream input = QueryIntentClassifierMultiAgentTest.class.getClassLoader().getResourceAsStream("application.yml")) {
            if (input != null) {
                // Simple approach: read the file as text and extract the key
                String content = new String(input.readAllBytes());
                String[] lines = content.split("\n");
                for (String line : lines) {
                    if (line.trim().startsWith("key:")) {
                        String apiKey = line.substring(line.indexOf(":") + 1).trim();
                        if (!apiKey.isEmpty() && !"YOUR_GOOGLE_API_KEY_HERE".equals(apiKey)) {
                            System.setProperty("GOOGLE_API_KEY", apiKey);
                            System.out.println("✅ Google API key set for test");
                            return;
                        }
                    }
                }
            }
            System.err.println("❌ Could not load Google API key from application.yml");
        } catch (Exception e) {
            System.err.println("❌ Error loading Google API key: " + e.getMessage());
        }
    }
    
    @Test
    @DisplayName("🎯 Should classify debug intent correctly")
    public void shouldClassifyDebugIntent() {
        // Given
        String debugQuery = "Why does login() throw NullPointerException?";
        
        // When
        Map<String, Object> result = QueryIntentClassifier.classifyIntent(debugQuery, null, false, null);
        
        // Then
        assertEquals("success", result.get("status"));
        assertTrue(result.containsKey("intent"));
        assertTrue(result.containsKey("confidence"));
        assertTrue(result.containsKey("recommendedStrategy"));
        assertTrue(result.containsKey("nextActions"));
        assertTrue(result.containsKey("classificationMethod"));
        assertEquals("INTELLIGENT_AGENT", result.get("classificationMethod"));
        
        System.out.println("✅ Debug Query Classification Result: " + result);
    }
    
    @Test
    @DisplayName("🔍 Should classify understanding intent correctly")
    public void shouldClassifyUnderstandingIntent() {
        // Given
        String understandingQuery = "What does PaymentService.processPayment() do?";
        
        // When
        Map<String, Object> result = QueryIntentClassifier.classifyIntent(understandingQuery, null, false, null);
        
        // Then
        assertEquals("success", result.get("status"));
        assertTrue(result.containsKey("intent"));
        assertTrue(result.containsKey("confidence"));
        assertTrue(result.containsKey("recommendedStrategy"));
        
        double confidence = (Double) result.get("confidence");
        assertTrue(confidence > 0.0, "Confidence should be greater than 0");
        assertTrue(confidence <= 1.0, "Confidence should be at most 1.0");
        
        System.out.println("✅ Understanding Query Classification Result: " + result);
    }
    
    @Test
    @DisplayName("🏗️ Should classify architecture intent correctly")
    public void shouldClassifyArchitectureIntent() {
        // Given
        String architectureQuery = "Show me the architecture of the payment system";
        
        // When
        Map<String, Object> result = QueryIntentClassifier.classifyIntent(architectureQuery, null, false, null);
        
        // Then
        assertEquals("success", result.get("status"));
        assertTrue(result.containsKey("metadata"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
        assertTrue(metadata.containsKey("agentUsed"));
        assertTrue((Boolean) metadata.get("agentUsed"));
        
        System.out.println("✅ Architecture Query Classification Result: " + result);
    }
    
    @Test
    @DisplayName("⚠️ Should handle empty query gracefully")
    public void shouldHandleEmptyQuery() {
        // Given
        String emptyQuery = "";
        
        // When
        Map<String, Object> result = QueryIntentClassifier.classifyIntent(emptyQuery, null, false, null);
        
        // Then
        assertEquals("error", result.get("status"));
        assertTrue(result.containsKey("error"));
        assertEquals(0.0, result.get("confidence"));
        
        System.out.println("✅ Empty Query Handling Result: " + result);
    }
    
    @Test
    @DisplayName("🔗 Should handle query with context")
    public void shouldHandleQueryWithContext() {
        // Given
        String query = "How does it connect to the database?";
        String context = "Previous discussion about PaymentService";
        
        // When
        Map<String, Object> result = QueryIntentClassifier.classifyIntent(query, context, false, null);
        
        // Then
        assertEquals("success", result.get("status"));
        assertTrue(result.containsKey("explanation"));
        
        String explanation = (String) result.get("explanation");
        assertNotNull(explanation);
        assertFalse(explanation.isEmpty());
        
        System.out.println("✅ Query with Context Result: " + result);
    }
    
    @Test
    @DisplayName("🔄 Should handle multi-intent queries")
    public void shouldHandleMultiIntentQueries() {
        // Given
        String complexQuery = "Compare PaymentService and OrderService and explain their performance";
        
        // When
        Map<String, Object> result = QueryIntentClassifier.classifyIntent(complexQuery, null, true, null);
        
        // Then
        assertEquals("success", result.get("status"));
        assertTrue(result.containsKey("metadata"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
        assertTrue(metadata.containsKey("toolsInvoked"));
        
        System.out.println("✅ Multi-Intent Query Result: " + result);
    }
    
    @Test
    @DisplayName("⏱️ Should include execution timing")
    public void shouldIncludeExecutionTiming() {
        // Given
        String query = "Find all methods in UserController";
        
        // When
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = QueryIntentClassifier.classifyIntent(query, null, false, null);
        long endTime = System.currentTimeMillis();
        
        // Then
        assertEquals("success", result.get("status"));
        assertTrue(result.containsKey("executionTimeMs"));
        
        Long executionTime = (Long) result.get("executionTimeMs");
        assertNotNull(executionTime);
        assertTrue(executionTime >= 0);
        assertTrue(executionTime <= (endTime - startTime + 100)); // Allow some buffer
        
        System.out.println("✅ Execution timing: " + executionTime + "ms");
        System.out.println("✅ Query Classification with Timing Result: " + result);
    }
    
    @Test
    @DisplayName("🔧 Should maintain backward compatibility")
    public void shouldMaintainBackwardCompatibility() {
        // Given - Test the same scenarios that worked in the old implementation
        String[] testQueries = {
            "Why does login() throw NullPointerException?",
            "What is PaymentService?",
            "How does payment processing work?",
            "Where is the UserRepository?",
            "Compare ServiceA and ServiceB"
        };
        
        // When & Then
        for (String query : testQueries) {
            Map<String, Object> result = QueryIntentClassifier.classifyIntent(query, null, false, null);
            
            assertEquals("success", result.get("status"), "Query failed: " + query);
            assertTrue(result.containsKey("intent"), "Missing intent for: " + query);
            assertTrue(result.containsKey("confidence"), "Missing confidence for: " + query);
            assertTrue(result.containsKey("recommendedStrategy"), "Missing strategy for: " + query);
            assertTrue(result.containsKey("nextActions"), "Missing next actions for: " + query);
            
            System.out.println("✅ Backward compatibility verified for: " + query);
        }
    }
}