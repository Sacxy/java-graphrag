package com.tekion.javaastkg.adk;

import com.tekion.javaastkg.adk.tools.QueryIntentClassifierTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.Map;
import java.io.InputStream;
import java.util.Properties;

public class DebugQueryIntentClassifierTest {
    
    @BeforeAll
    static void verifyApiKeySetup() {
        // Verify API key is available (should be set by build.gradle)
        String apiKey = System.getenv("GOOGLE_API_KEY");
        if (apiKey == null) {
            apiKey = System.getProperty("GOOGLE_API_KEY");
        }
        
        if (apiKey != null && !apiKey.isEmpty()) {
            System.out.println("✅ API key available for tests: " + apiKey.substring(0, 10) + "...");
        } else {
            System.err.println("❌ No API key found in environment or system properties");
            System.err.println("Environment GOOGLE_API_KEY: " + System.getenv("GOOGLE_API_KEY"));
            System.err.println("System property GOOGLE_API_KEY: " + System.getProperty("GOOGLE_API_KEY"));
        }
    }
    
    @Test
    public void debugBasicCall() {
        String query = "Why does login() throw NullPointerException?";
        
        try {
            Map<String, Object> result = QueryIntentClassifierTool.classifyIntent(query, null, false, null);
            
            System.out.println("=== DEBUG: Full result ===");
            System.out.println(result);
            
            System.out.println("\n=== DEBUG: Status ===");
            System.out.println(result.get("status"));
            
            if ("error".equals(result.get("status"))) {
                System.out.println("\n=== DEBUG: Error ===");
                System.out.println(result.get("error"));
            }
            
            if ("success".equals(result.get("status"))) {
                System.out.println("\n=== DEBUG: Success details ===");
                System.out.println("Intent: " + result.get("intent"));
                System.out.println("Confidence: " + result.get("confidence"));
                System.out.println("Strategy: " + result.get("recommendedStrategy"));
            }
        } catch (Exception e) {
            System.err.println("=== DEBUG: Exception occurred ===");
            e.printStackTrace();
        }
    }
}