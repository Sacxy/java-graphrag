package com.tekion.javaastkg.adk;

import com.tekion.javaastkg.adk.tools.QueryIntentClassifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🧪 ADK Integration Test
 * 
 * Verifies that the ADK implementation works correctly and can classify intents.
 */
class AdkIntegrationTest {
    
    @Test
    @DisplayName("Query Intent Classifier should classify simple queries")
    void testQueryIntentClassification() {
        // Test query
        String query = "How does authentication work in the system?";
        
        // Call the ADK FunctionTool
        Map<String, Object> result = QueryIntentClassifier.classifyIntentSimple(
            query, 
            null, 
            false
        );
        
        // Verify the result
        assertThat(result).isNotNull();
        assertThat(result.get("status")).isEqualTo("success");
        assertThat(result.get("intent")).isNotNull();
        assertThat(result.get("confidence")).isInstanceOf(Double.class);
        assertThat((Double) result.get("confidence")).isGreaterThan(0.0);
        
        System.out.println("✅ ADK Tool Classification Result:");
        System.out.println("   Intent: " + result.get("intent"));
        System.out.println("   Confidence: " + result.get("confidence"));
        System.out.println("   Explanation: " + result.get("explanation"));
    }
    
    @Test
    @DisplayName("Query Intent Classifier should handle debug queries")
    void testDebugQueryClassification() {
        String query = "Why does the login method throw NullPointerException?";
        
        Map<String, Object> result = QueryIntentClassifier.classifyIntentSimple(
            query, 
            null, 
            false
        );
        
        assertThat(result.get("status")).isEqualTo("success");
        assertThat(result.get("intent")).isEqualTo("DEBUG_ISSUE");
        assertThat(result.get("recommendedStrategy")).isEqualTo("SURGICAL");
        
        System.out.println("✅ Debug Query Classification:");
        System.out.println("   Intent: " + result.get("intent"));
        System.out.println("   Strategy: " + result.get("recommendedStrategy"));
    }
    
    @Test
    @DisplayName("Query Intent Classifier should handle empty queries gracefully")
    void testEmptyQueryHandling() {
        Map<String, Object> result = QueryIntentClassifier.classifyIntentSimple(
            "", 
            null, 
            false
        );
        
        assertThat(result.get("status")).isEqualTo("error");
        assertThat(result.get("confidence")).isEqualTo(0.0);
    }
}