package com.tekion.javaastkg.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agents.toggle")
@Slf4j
public class AgentToggleConfig {
    
    private boolean patternMatching = true;
    private boolean fuzzyMatching = true;
    private boolean semanticMatching = true;
    private boolean luceneMatching = true;
    
    private PatternMatchingConfig pattern = new PatternMatchingConfig();
    private FuzzyMatchingConfig fuzzy = new FuzzyMatchingConfig();
    private SemanticMatchingConfig semantic = new SemanticMatchingConfig();
    private LuceneMatchingConfig lucene = new LuceneMatchingConfig();
    
    @PostConstruct
    public void logConfiguration() {
        log.info("Agent Toggle Configuration:");
        log.info("  Pattern Matching: {} (priority: {})", patternMatching, pattern.priority);
        log.info("  Fuzzy Matching: {} (priority: {})", fuzzyMatching, fuzzy.priority);
        log.info("  Semantic Matching: {} (priority: {})", semanticMatching, semantic.priority);
        log.info("  Lucene Matching: {} (priority: {})", luceneMatching, lucene.priority);
    }
    
    @Data
    public static class PatternMatchingConfig {
        private boolean enabled = true;
        private int priority = 1;
        private boolean exactMatch = true;
        private boolean prefixMatch = true;
        private boolean suffixMatch = true;
        private boolean wildcardMatch = true;
        private boolean compoundMatch = true;
    }
    
    @Data
    public static class FuzzyMatchingConfig {
        private boolean enabled = true;
        private int priority = 2;
        private int maxEditDistance = 2;
        private boolean phoneticMatch = true;
        private boolean abbreviationMatch = true;
        private boolean typoCorrection = true;
    }
    
    @Data
    public static class SemanticMatchingConfig {
        private boolean enabled = true;
        private int priority = 3;
        private boolean embeddingSearch = true;
        private boolean domainTerms = true;
        private double minSimilarityThreshold = 0.7;
    }
    
    @Data
    public static class LuceneMatchingConfig {
        private boolean enabled = true;
        private int priority = 4;
        private boolean standardSearch = true;
        private boolean fuzzySearch = true;
        private boolean wildcardSearch = true;
        private boolean phraseSearch = true;
        private boolean booleanSearch = true;
        private boolean highlighting = true;
        private int maxResults = 20;
    }
    
    public boolean isAnyAgentEnabled() {
        return patternMatching || fuzzyMatching || semanticMatching || luceneMatching;
    }
    
    public void enableAll() {
        this.patternMatching = true;
        this.fuzzyMatching = true;
        this.semanticMatching = true;
        this.luceneMatching = true;
        this.pattern.enabled = true;
        this.fuzzy.enabled = true;
        this.semantic.enabled = true;
        this.lucene.enabled = true;
    }
    
    public void disableAll() {
        this.patternMatching = false;
        this.fuzzyMatching = false;
        this.semanticMatching = false;
        this.luceneMatching = false;
        this.pattern.enabled = false;
        this.fuzzy.enabled = false;
        this.semantic.enabled = false;
        this.lucene.enabled = false;
    }
    
    public void enableOnly(String agentType) {
        disableAll();
        switch (agentType.toLowerCase()) {
            case "pattern":
                this.patternMatching = true;
                this.pattern.enabled = true;
                break;
            case "fuzzy":
                this.fuzzyMatching = true;
                this.fuzzy.enabled = true;
                break;
            case "semantic":
                this.semanticMatching = true;
                this.semantic.enabled = true;
                break;
            case "lucene":
                this.luceneMatching = true;
                this.lucene.enabled = true;
                break;
            default:
                log.warn("Unknown agent type: {}. Available types: pattern, fuzzy, semantic, lucene", agentType);
        }
    }
    
    public String getEnabledAgentsStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Enabled agents: ");
        
        if (patternMatching) status.append("Pattern(").append(pattern.priority).append(") ");
        if (fuzzyMatching) status.append("Fuzzy(").append(fuzzy.priority).append(") ");
        if (semanticMatching) status.append("Semantic(").append(semantic.priority).append(") ");
        if (luceneMatching) status.append("Lucene(").append(lucene.priority).append(") ");
        
        return status.toString().trim();
    }
}