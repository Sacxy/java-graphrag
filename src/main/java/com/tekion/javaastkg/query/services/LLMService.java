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
            You are an expert Java developer analyzing code search queries in an AST-based knowledge graph system.
            
            Query: %s
            Context: %s
            
            Classify the intent for Java codebase exploration. Consider these categories:
            
            IMPLEMENTATION: Understanding how code works, algorithms, business logic, design patterns
            USAGE: Finding where components are used, dependencies, references, calling patterns
            CONFIGURATION: Looking for Spring configuration, properties, settings, application setup
            DISCOVERY: Finding components that handle specific functionality or business logic
            STATUS: Interested in states, statuses, conditions, workflow phases
            ARCHITECTURE: Understanding system design, patterns, layering, component relationships
            
            Provide the primary intent category and confidence score (0.0-1.0).
            Format: INTENT_CATEGORY:CONFIDENCE_SCORE
            """, query, context);
            
        return analyzeText(prompt);
    }

    /**
     * Extracts entities using the LLM
     */
    public String extractEntities(String query, String guidelines) {
        String prompt = String.format("""
            You are an expert Java developer extracting entities from code search queries for an AST-based knowledge graph.
            
            Query: %s
            Guidelines: %s
            
            Extract Java-specific entities from the query. Focus on:
            
            CLASS_NAMES: Java class names (e.g., UserService, PaymentController, OrderEntity)
            METHOD_NAMES: Method names (e.g., calculateTax, validateUser, processPayment)
            PACKAGE_NAMES: Package names (e.g., com.example.service, org.springframework.web)
            ANNOTATIONS: Spring/Java annotations (e.g., @Service, @Controller, @Repository, @Autowired)
            DESIGN_PATTERNS: Design patterns (e.g., Factory, Builder, Strategy, Observer)
            BUSINESS_CONCEPTS: Domain entities and concepts (e.g., User, Order, Payment, Authentication)
            TECHNICAL_TERMS: Technical keywords (e.g., database, cache, validation, exception, configuration)
            
            Return entities in JSON format:
            {
                "classes": ["ClassName1", "ClassName2"],
                "methods": ["methodName1", "methodName2"],
                "packages": ["package.name1", "package.name2"],
                "annotations": ["@Annotation1", "@Annotation2"],
                "patterns": ["PatternName1", "PatternName2"],
                "concepts": ["BusinessConcept1", "BusinessConcept2"],
                "technical": ["TechnicalTerm1", "TechnicalTerm2"]
            }
            """, query, guidelines);
            
        return analyzeText(prompt);
    }
}