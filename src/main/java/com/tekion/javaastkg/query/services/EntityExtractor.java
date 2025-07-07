package com.tekion.javaastkg.query.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tekion.javaastkg.util.LLMRateLimiter;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for extracting code entities from natural language queries.
 * Uses LLM to identify specific class names, method names, packages, and technical terms.
 */
@Service
@Slf4j
public class EntityExtractor {

    private final ChatLanguageModel llm;
    private final ObjectMapper objectMapper;
    private final LLMRateLimiter rateLimiter;

    public EntityExtractor(@Qualifier("contextDistillerModel") ChatLanguageModel llm,
                          LLMRateLimiter rateLimiter) {
        this.llm = llm;
        this.objectMapper = new ObjectMapper();
        this.rateLimiter = rateLimiter;
    }

    /**
     * Extracts entities from a natural language query
     */
    public ExtractedEntities extract(String query) {
        log.debug("Extracting entities from query: {}", query);

        String prompt = buildExtractionPrompt(query);
        
        try {
            String response = rateLimiter.executeWithRateLimit(
                () -> llm.generate(prompt),
                "Entity extraction for query: " + query
            );

            return parseResponse(response);
            
        } catch (Exception e) {
            log.error("Failed to extract entities from query: {}", query, e);
            // Return empty result on failure
            return ExtractedEntities.builder()
                    .classes(new ArrayList<>())
                    .methods(new ArrayList<>())
                    .packages(new ArrayList<>())
                    .terms(new ArrayList<>())
                    .build();
        }
    }

    /**
     * Builds the entity extraction prompt
     */
    private String buildExtractionPrompt(String query) {
        return """
            Extract code entities from this query: "%s"
            
            Look for:
            - Class names (e.g., UserService, Pipeline, DataProcessor)
            - Method names (e.g., processData, initialize, getUserById)
            - Package references (e.g., com.tekion, service, controller)
            - Technical terms that might match code elements
            
            Return ONLY a valid JSON object with this exact structure:
            {
                "classes": [],
                "methods": [],
                "packages": [],
                "terms": []
            }
            
            Guidelines:
            - Include probable class names (CamelCase, ends with common suffixes like Service, Controller, Manager)
            - Include probable method names (camelCase, action verbs)
            - Include package fragments mentioned in the query
            - Include technical terms that could be part of class/method names
            - If nothing is found for a category, use an empty array
            - Do not include explanations, just the JSON
            """.formatted(query);
    }

    /**
     * Parses the LLM response into ExtractedEntities
     */
    private ExtractedEntities parseResponse(String response) {
        try {
            // Clean up response
            response = response.trim();
            if (response.startsWith("```json")) {
                response = response.substring(7);
            }
            if (response.endsWith("```")) {
                response = response.substring(0, response.length() - 3);
            }
            
            // Parse JSON
            return objectMapper.readValue(response, ExtractedEntities.class);
            
        } catch (Exception e) {
            log.warn("Failed to parse entity extraction response, using empty result: {}", e.getMessage());
            return ExtractedEntities.builder()
                    .classes(new ArrayList<>())
                    .methods(new ArrayList<>())
                    .packages(new ArrayList<>())
                    .terms(new ArrayList<>())
                    .build();
        }
    }

    /**
     * Data class for extracted entities
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedEntities {
        private List<String> classes;
        private List<String> methods;
        private List<String> packages;
        private List<String> terms;
        
        /**
         * Returns true if any entities were extracted
         */
        public boolean hasEntities() {
            return !classes.isEmpty() || !methods.isEmpty() || 
                   !packages.isEmpty() || !terms.isEmpty();
        }
        
        /**
         * Returns all entities as a single list
         */
        public List<String> getAllEntities() {
            List<String> all = new ArrayList<>();
            all.addAll(classes);
            all.addAll(methods);
            all.addAll(packages);
            all.addAll(terms);
            return all;
        }
    }
}