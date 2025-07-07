package com.tekion.javaastkg.query.services;

import com.tekion.javaastkg.util.LLMRateLimiter;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Service for LLM interactions with rate limiting.
 * Provides a unified interface for all LLM operations in the query system.
 */
@Service
@Slf4j
public class LLMService {

    private final ChatLanguageModel contextDistillerModel;
    private final LLMRateLimiter rateLimiter;

    public LLMService(@Qualifier("contextDistillerModel") ChatLanguageModel contextDistillerModel,
                      LLMRateLimiter rateLimiter) {
        this.contextDistillerModel = contextDistillerModel;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Analyzes text using the LLM with rate limiting
     */
    public String analyzeText(String prompt) {
        log.debug("Analyzing text with LLM: {}", prompt.substring(0, Math.min(prompt.length(), 100)) + "...");
        
        try {
            return rateLimiter.executeWithRateLimit(
                () -> contextDistillerModel.generate(prompt),
                "Text analysis"
            );
        } catch (Exception e) {
            log.error("LLM text analysis failed", e);
            throw new RuntimeException("Failed to analyze text with LLM", e);
        }
    }

    /**
     * Classifies intent using the LLM
     */
    public String classifyIntent(String query, String context) {
        String prompt = String.format("""
            Classify the intent of this query in the context of code search:
            
            Query: %s
            Context: %s
            
            Provide a concise classification.
            """, query, context);
            
        return analyzeText(prompt);
    }

    /**
     * Extracts entities using the LLM
     */
    public String extractEntities(String query, String guidelines) {
        String prompt = String.format("""
            Extract entities from this query:
            
            Query: %s
            Guidelines: %s
            
            Return the results in the requested format.
            """, query, guidelines);
            
        return analyzeText(prompt);
    }
}