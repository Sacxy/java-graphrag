package com.tekion.javaastkg.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the agent-based entity extraction system.
 * Follows 12-Factor App configuration principles.
 * 
 * Applied 12-Factor Principles:
 * - Factor 3: Store config in environment (externalized configuration)
 */
@Configuration
@ConfigurationProperties(prefix = "entity.extraction.agent")
@Data
public class AgentConfiguration {
    
    /**
     * Whether to enable the agent-based entity extraction system
     */
    private boolean enabled = true;
    
    /**
     * Whether to enable fallback to the original EntityExtractor on failure
     */
    private boolean fallbackEnabled = true;
    
    /**
     * Maximum time to wait for agent extraction (milliseconds)
     */
    private long maxExtractionTimeMs = 5000;
    
    /**
     * Minimum confidence threshold for including entities in results
     */
    private double minConfidenceThreshold = 0.3;
    
    /**
     * Maximum number of entities to return
     */
    private int maxResults = 50;
    
    /**
     * Whether to log detailed agent performance metrics
     */
    private boolean enablePerformanceLogging = false;
    
    /**
     * Whether to enable the codebase entity registry auto-refresh
     */
    private boolean enableRegistryAutoRefresh = true;
    
    /**
     * Registry refresh interval in milliseconds
     */
    private long registryRefreshIntervalMs = 3600000; // 1 hour
    
    /**
     * Pattern matching agent configuration
     */
    private PatternMatchingConfig patternMatching = new PatternMatchingConfig();
    
    /**
     * Fuzzy matching agent configuration
     */
    private FuzzyMatchingConfig fuzzyMatching = new FuzzyMatchingConfig();
    
    /**
     * Semantic matching agent configuration
     */
    private SemanticMatchingConfig semanticMatching = new SemanticMatchingConfig();
    
    @Data
    public static class PatternMatchingConfig {
        private boolean enabled = true;
        private double baseConfidence = 0.7;
        private int maxResults = 20;
    }
    
    @Data
    public static class FuzzyMatchingConfig {
        private boolean enabled = true;
        private int maxEditDistance = 2;
        private double minConfidence = 0.4;
        private int maxResults = 20;
    }
    
    @Data
    public static class SemanticMatchingConfig {
        private boolean enabled = true;
        private double minSimilarityThreshold = 0.6;
        private double highSimilarityThreshold = 0.8;
        private int maxResults = 15;
    }
}