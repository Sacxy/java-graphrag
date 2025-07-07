package com.tekion.javaastkg.query.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tekion.javaastkg.model.QueryModels;
import com.tekion.javaastkg.util.LLMRateLimiter;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for generating natural language summaries of retrieval results.
 * Takes structured retrieval results and creates human-readable explanations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GenerationService {
    
    private final ChatLanguageModel llm;
    private final ObjectMapper objectMapper;
    private final LLMRateLimiter rateLimiter;
    
    /**
     * Generates natural language summary from retrieval results
     */
    public String generateNaturalSummary(String query, QueryModels.RetrievalResult retrievalResult) {
        log.debug("Generating natural language summary for query: {}", query);
        
        try {
            if (retrievalResult == null || retrievalResult.getGraphContext() == null) {
                log.warn("No retrieval result available for generation");
                return "No relevant information found for the query.";
            }
            
            String prompt = buildNaturalLanguagePrompt(query, retrievalResult);
            String summary = rateLimiter.executeWithRateLimit(
                () -> llm.generate(prompt), 
                "Natural language generation for query: " + query
            );
            
            log.info("Generated natural language summary for query: {}", query);
            log.info("Generated Answer: {}", summary.trim());
            return summary.trim();
        } catch (Exception e) {
            log.error("Natural language generation failed for query: {}", query, e);
            return "Unable to generate summary due to technical error: " + e.getMessage();
        }
    }
    
    /**
     * Builds the natural language generation prompt
     */
    private String buildNaturalLanguagePrompt(String query, QueryModels.RetrievalResult retrievalResult) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an expert software architect analyzing a Java codebase. Answer the user's query in natural language based on the code context provided.\n\n");
        prompt.append("User Query: ").append(query).append("\n\n");
        
        // Check if we have any results
        if (retrievalResult.getGraphContext() == null || 
            (retrievalResult.getGraphContext().getMethods().isEmpty() && 
             retrievalResult.getGraphContext().getClasses().isEmpty())) {
            prompt.append("No relevant code components were found for this query.\n\n");
            prompt.append("Please provide a helpful response explaining that no specific code was found related to '" + query + "' and suggest what the user might be looking for or how they could refine their query to be more specific about the codebase components they want to understand.\n\n");
        } else {
            prompt.append("Retrieved Context from the Codebase:\n\n");
            
            // Add methods with scores
            for (var method : retrievalResult.getGraphContext().getMethods()) {
                prompt.append(String.format("=== METHOD: %s ===\n", method.getName()));
                prompt.append("Signature: ").append(method.getSignature()).append("\n");
                if (method.getClassName() != null) {
                    prompt.append("Class: ").append(method.getClassName()).append("\n");
                }
                if (method.getBusinessTags() != null && !method.getBusinessTags().isEmpty()) {
                    prompt.append("Tags: ").append(String.join(", ", method.getBusinessTags())).append("\n");
                }
                // Add relevance score if available
                if (retrievalResult.getScoreMap() != null && retrievalResult.getScoreMap().containsKey(method.getId())) {
                    prompt.append("Relevance Score: ").append(String.format("%.2f", retrievalResult.getScoreMap().get(method.getId()))).append("\n");
                }
                prompt.append("\n");
            }
            
            // Add classes
            for (var clazz : retrievalResult.getGraphContext().getClasses()) {
                prompt.append(String.format("=== CLASS: %s ===\n", clazz.getName()));
                prompt.append("Full Name: ").append(clazz.getFullName()).append("\n");
                if (clazz.getPackageName() != null) {
                    prompt.append("Package: ").append(clazz.getPackageName()).append("\n");
                }
                prompt.append("Type: ").append(clazz.getType()).append("\n");
                prompt.append("Interface: ").append(clazz.isInterface()).append("\n");
                prompt.append("Abstract: ").append(clazz.isAbstract()).append("\n\n");
            }
        }
        
        prompt.append("""
            Based on the context above, provide a clear, conversational answer to the user's query in natural language.
            
            Guidelines:
            - Answer directly in natural language, not JSON
            - Be specific about what was found in the codebase
            - If no relevant code was found, explain this clearly and suggest how the user could refine their query
            - Include specific class names, method names, and relationships when relevant
            - Provide insights about how the code works and what it does
            - If the query asks about something not found, suggest what might be available instead
            - Keep the response conversational and helpful
            
            Do not return JSON. Return only natural language text.
            """);
        
        return prompt.toString();
    }
    
}