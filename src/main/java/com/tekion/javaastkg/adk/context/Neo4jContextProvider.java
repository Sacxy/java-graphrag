package com.tekion.javaastkg.adk.context;

import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * Neo4j Context Provider
 * 
 * Provides static access to Neo4j driver and session configuration
 * for ADK tools. This allows tools to access the database without
 * requiring direct dependency injection, which is not supported
 * in ADK FunctionTool static methods.
 */
@Slf4j
public class Neo4jContextProvider {
    
    private static Driver neo4jDriver;
    private static SessionConfig sessionConfig;
    private static boolean initialized = false;
    
    /**
     * Initialize the provider with Neo4j dependencies
     */
    public static synchronized void initialize(Driver driver, SessionConfig config) {
        if (initialized) {
            log.warn("Neo4jContextProvider already initialized, skipping re-initialization");
            return;
        }
        
        neo4jDriver = driver;
        sessionConfig = config;
        initialized = true;
        
        log.info("Neo4jContextProvider initialized successfully");
    }
    
    /**
     * Get the Neo4j driver
     */
    public static Driver getNeo4jDriver() {
        if (!initialized) {
            throw new IllegalStateException(
                "Neo4jContextProvider not initialized. Call initialize() first.");
        }
        return neo4jDriver;
    }
    
    /**
     * Get the session configuration
     */
    public static SessionConfig getSessionConfig() {
        if (!initialized) {
            throw new IllegalStateException(
                "Neo4jContextProvider not initialized. Call initialize() first.");
        }
        return sessionConfig;
    }
    
    /**
     * Check if provider is initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Reset the provider (mainly for testing)
     */
    public static synchronized void reset() {
        neo4jDriver = null;
        sessionConfig = null;
        initialized = false;
    }
}