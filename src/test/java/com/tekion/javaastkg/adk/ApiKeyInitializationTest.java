package com.tekion.javaastkg.adk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔑 API Key Initialization Test
 * 
 * This test verifies that the Google API key is properly available
 * for ADK initialization before any ADK classes are loaded.
 */
public class ApiKeyInitializationTest {
    
    @BeforeAll
    static void displayEnvironmentInfo() {
        System.out.println("=== ADK Environment Diagnostic ===");
        
        // Check environment variable
        String envApiKey = System.getenv("GOOGLE_API_KEY");
        System.out.println("Environment GOOGLE_API_KEY: " + 
            (envApiKey != null ? envApiKey.substring(0, 10) + "..." : "NULL"));
        
        // Check system property
        String sysApiKey = System.getProperty("GOOGLE_API_KEY");
        System.out.println("System property GOOGLE_API_KEY: " + 
            (sysApiKey != null ? sysApiKey.substring(0, 10) + "..." : "NULL"));
        
        // Check which one ADK would use
        String adkApiKey = System.getenv("GOOGLE_API_KEY");
        if (adkApiKey == null) {
            adkApiKey = System.getProperty("GOOGLE_API_KEY");
        }
        System.out.println("ADK would use: " + 
            (adkApiKey != null ? adkApiKey.substring(0, 10) + "..." : "NULL"));
        
        System.out.println("=====================================\n");
    }
    
    @Test
    @DisplayName("API key should be available via environment variable")
    void testEnvironmentVariableApiKey() {
        String apiKey = System.getenv("GOOGLE_API_KEY");
        assertThat(apiKey).isNotNull();
        assertThat(apiKey).isNotEmpty();
        assertThat(apiKey).startsWith("AIzaSy");
        
        System.out.println("✅ Environment variable API key is available");
    }
    
    @Test
    @DisplayName("API key should be available via system property")
    void testSystemPropertyApiKey() {
        String apiKey = System.getProperty("GOOGLE_API_KEY");
        assertThat(apiKey).isNotNull();
        assertThat(apiKey).isNotEmpty();
        assertThat(apiKey).startsWith("AIzaSy");
        
        System.out.println("✅ System property API key is available");
    }
    
    @Test
    @DisplayName("ADK should be able to find API key using standard lookup")
    void testAdkApiKeyLookup() {
        // This mimics how ADK looks for the API key
        String apiKey = System.getenv("GOOGLE_API_KEY");
        if (apiKey == null) {
            apiKey = System.getProperty("GOOGLE_API_KEY");
        }
        
        assertThat(apiKey).isNotNull();
        assertThat(apiKey).isNotEmpty();
        assertThat(apiKey).startsWith("AIzaSy");
        
        System.out.println("✅ ADK API key lookup successful");
    }
    
    @Test
    @DisplayName("Can access ADK classes without initialization error")
    void testAdkClassLoading() {
        try {
            // Try to access ADK classes that would trigger static initialization
            Class.forName("com.google.adk.models.LlmRegistry");
            System.out.println("✅ ADK LlmRegistry loaded successfully");
        } catch (ClassNotFoundException e) {
            System.err.println("❌ ADK classes not found: " + e.getMessage());
            throw new RuntimeException("ADK classes not available", e);
        } catch (ExceptionInInitializerError e) {
            System.err.println("❌ ADK initialization failed: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Cause: " + e.getCause().getMessage());
            }
            throw new RuntimeException("ADK initialization failed", e);
        }
    }
}