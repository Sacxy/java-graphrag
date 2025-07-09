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
        log.info("GENERATION_SERVICE: Generating natural language summary for query: {}", query);
        
        try {
            if (retrievalResult == null || retrievalResult.getGraphContext() == null) {
                log.warn("GENERATION_SERVICE: No retrieval result available for generation");
                return "No relevant information found for the query.";
            }
            
            log.info("GENERATION_SERVICE: Input data - {} methods, {} classes, {} score entries", 
                    retrievalResult.getGraphContext().getMethods().size(),
                    retrievalResult.getGraphContext().getClasses().size(),
                    retrievalResult.getScoreMap().size());
            
            // Log some sample data being sent to LLM
            retrievalResult.getGraphContext().getMethods().stream()
                    .limit(3)
                    .forEach(method -> log.info("GENERATION_SERVICE: Method for LLM - {} (signature: {})", 
                            method.getName(), method.getSignature()));
            
            retrievalResult.getGraphContext().getClasses().stream()
                    .limit(3)
                    .forEach(clazz -> log.info("GENERATION_SERVICE: Class for LLM - {} (type: {})", 
                            clazz.getName(), clazz.getType()));
            
            String prompt = buildNaturalLanguagePrompt(query, retrievalResult);
            log.info("GENERATION_SERVICE: Built prompt with {} characters", prompt.length());
            
            String summary = rateLimiter.executeWithRateLimit(
                () -> llm.generate(prompt), 
                "Natural language generation for query: " + query
            );
            
            log.info("GENERATION_SERVICE: Generated natural language summary for query: {}", query);
            log.info("GENERATION_SERVICE: Generated Answer: {}", summary.trim());
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
        
        prompt.append("You are an expert Java software architect with deep expertise in enterprise codebases, Spring Framework, and modern Java patterns. You're analyzing a comprehensive AST-based knowledge graph to help developers understand and navigate complex Java applications.\n\n");
        prompt.append("User Query: ").append(query).append("\n\n");
        
        // Check if we have any results
        if (retrievalResult.getGraphContext() == null || 
            (retrievalResult.getGraphContext().getMethods().isEmpty() && 
             retrievalResult.getGraphContext().getClasses().isEmpty())) {
            log.warn("GENERATION_SERVICE: No methods or classes found in graph context for prompt building");
            prompt.append("No relevant code components were found for this query.\n\n");
            prompt.append("Please provide a helpful response explaining that no specific code was found related to '" + query + "' and suggest what the user might be looking for or how they could refine their query to be more specific about the codebase components they want to understand.\n\n");
        } else {
            log.info("GENERATION_SERVICE: Building prompt with {} methods and {} classes", 
                    retrievalResult.getGraphContext().getMethods().size(),
                    retrievalResult.getGraphContext().getClasses().size());
            
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
            
            log.info("GENERATION_SERVICE: Final prompt includes {} methods and {} classes", 
                    retrievalResult.getGraphContext().getMethods().size(),
                    retrievalResult.getGraphContext().getClasses().size());
        }
        
        prompt.append("""
            Based on the AST knowledge graph context above, provide a comprehensive, developer-focused answer to the user's query.
            
            RESPONSE STRUCTURE:
            1. **Direct Answer**: Address the query with specific findings from the codebase
            2. **Code Context**: Explain how the found components work together
            3. **Architectural Insights**: Describe patterns, frameworks, and design decisions
            4. **Practical Guidance**: Suggest next steps, related components, or areas to explore
            
            JAVA-SPECIFIC GUIDELINES:
            - Identify Spring annotations and their architectural implications (@Service, @Controller, @Repository, @Configuration)
            - Explain dependency injection patterns and component relationships
            - Describe JPA/Hibernate patterns and database interactions when present
            - Highlight design patterns (Factory, Builder, Strategy, Observer, etc.)
            - Explain exception handling strategies and error boundaries
            - Mention performance considerations (caching, lazy loading, batching)
            - Identify business domain concepts and domain-driven design patterns
            
            RESPONSE QUALITY:
            - Use technical terminology appropriately for Java developers
            - Explain complex relationships between classes and methods
            - Suggest related searches or code exploration paths
            - If no results found, explain why and suggest refinements based on common Java patterns
            - Include method signatures, class hierarchies, and package structures when relevant
            - Reference specific line numbers or code snippets when available
            
            QUERY-SPECIFIC GUIDANCE:
            - **Implementation queries**: Explain how the code works, patterns used, and design decisions
            - **Usage queries**: Show where components are used, dependencies, and integration points
            - **Configuration queries**: Explain configuration purposes, impacts, and related settings
            - **Discovery queries**: Identify components that handle specific functionality
            - **Architecture queries**: Explain system design, layering, and component relationships
            
            Be conversational yet technical. Focus on actionable insights for Java developers.
            Do not return JSON. Return only natural language text.
            """);
        
        return prompt.toString();
    }
    
}