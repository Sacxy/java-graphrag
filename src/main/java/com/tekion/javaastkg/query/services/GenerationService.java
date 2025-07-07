package com.tekion.javaastkg.query.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tekion.javaastkg.model.QueryModels;
import com.tekion.javaastkg.query.ContextDistiller;
import com.tekion.javaastkg.query.QueryExecutionContext;
import com.tekion.javaastkg.util.LLMRateLimiter;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for the generation step in the query processing pipeline.
 * Uses LLM to generate structured answers based on distilled context.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GenerationService {
    
    private final ChatLanguageModel llm;
    private final ObjectMapper objectMapper;
    private final LLMRateLimiter rateLimiter;
    
    /**
     * Executes the generation step
     */
    @Async("llmExecutor")
    public CompletableFuture<QueryExecutionContext> generate(QueryExecutionContext context) {
        log.debug("Executing generation [{}]", context.getExecutionId());
        
        try {
            if (context.getDistilledContext() == null || context.getDistilledContext().isEmpty()) {
                log.warn("No distilled context available for generation [{}]", context.getExecutionId());
                
                // Create a basic result indicating no context
                QueryModels.QueryResult basicResult = QueryModels.QueryResult.builder()
                        .query(context.getOriginalQuery())
                        .summary("No relevant context found for the query.")
                        .components(new ArrayList<>())
                        .relationships(new ArrayList<>())
                        .confidence(0.0)
                        .metadata(Map.of("noContext", true))
                        .build();
                
                context.setGeneratedResult(basicResult);
                return CompletableFuture.completedFuture(context);
            }
            
            String prompt = buildGenerationPrompt(context);
            String response = rateLimiter.executeWithRateLimit(
                () -> llm.generate(prompt), 
                "LLM Generation for query: " + context.getOriginalQuery()
            );
            
            QueryModels.QueryResult result = parseGeneratedResponse(response, context);
            context.setGeneratedResult(result);
            
            // Add generation metadata
            context.getMetadata().put("generationAttempt", context.getRefinementCount() + 1);
            context.getMetadata().put("promptLength", prompt.length());
            context.getMetadata().put("responseLength", response.length());
            
            // Log detailed information about generated result
            log.info("Generation completed - Summary: '{}' [{}]", 
                    result.getSummary(), context.getExecutionId());
            log.info("Generated {} components and {} relationships [{}]", 
                    result.getComponents().size(), 
                    result.getRelationships().size(),
                    context.getExecutionId());
            
            // Log top 3 components for visibility
            result.getComponents().stream()
                    .limit(3)
                    .forEach(comp -> log.info("Generated component: {} - {}", 
                            comp.getType(), comp.getName()));
            
            return CompletableFuture.completedFuture(context);
        } catch (Exception e) {
            log.error("Generation failed [{}]", context.getExecutionId(), e);
            context.getMetadata().put("generationError", e.getMessage());
            return CompletableFuture.failedFuture(new RuntimeException("Generation step failed", e));
        }
    }
    
    /**
     * Builds the generation prompt with all context
     */
    private String buildGenerationPrompt(QueryExecutionContext context) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an expert software architect analyzing a Java codebase.\n\n");
        prompt.append("User Query: ").append(context.getOriginalQuery()).append("\n\n");
        prompt.append("Relevant Context from the Codebase:\n\n");
        
        // Include distilled context
        for (ContextDistiller.RelevantContext ctx : context.getDistilledContext()) {
            prompt.append(String.format("=== %s: %s ===\n",
                    ctx.getCandidate().getType().toUpperCase(),
                    ctx.getCandidate().getName()));
            prompt.append("Summary: ").append(ctx.getCandidate().getSummary()).append("\n");
            if (ctx.getCandidate().getDetails() != null) {
                prompt.append("Details: ").append(ctx.getCandidate().getDetails()).append("\n");
            }
            prompt.append("Relevance: ").append(ctx.getRelevanceReason()).append("\n\n");
        }
        
        // Add refinement context if this is a refinement
        if (context.getRefinementCount() > 0) {
            prompt.append("\n=== REFINEMENT CONTEXT ===\n");
            prompt.append("This is refinement attempt #").append(context.getRefinementCount()).append("\n");
            if (!context.getVerificationErrors().isEmpty()) {
                prompt.append("Previous errors to fix:\n");
                for (String error : context.getVerificationErrors()) {
                    prompt.append("- ").append(error).append("\n");
                }
            }
            prompt.append("Please provide a corrected answer.\n\n");
        }
        
        prompt.append("""
            Based on the context above, provide a comprehensive answer to the user's query.
            
            Your response must be a valid JSON object with this structure:
            {
                "summary": "A clear, concise answer to the query",
                "components": [
                    {
                        "type": "method|class",
                        "signature": "full signature or class name",
                        "name": "simple name",
                        "summary": "what this component does",
                        "relevanceScore": 0.0-1.0
                    }
                ],
                "relationships": [
                    {
                        "description": "explanation of the relationship",
                        "fromComponent": "component name",
                        "toComponent": "component name",
                        "relationshipType": "CALLS|IMPLEMENTS|EXTENDS|USES"
                    }
                ],
                "metadata": {
                    "keyInsights": ["insight1", "insight2"],
                    "suggestedNextSteps": ["step1", "step2"]
                }
            }
            
            Focus on accuracy and ensure all mentioned relationships actually exist in the code.
            """);
        
        return prompt.toString();
    }
    
    /**
     * Parses the LLM-generated response into a QueryResult
     */
    private QueryModels.QueryResult parseGeneratedResponse(String response, QueryExecutionContext context) {
        try {
            // Clean response
            response = response.trim();
            if (response.startsWith("```json")) {
                response = response.substring(7);
            }
            if (response.endsWith("```")) {
                response = response.substring(0, response.length() - 3);
            }
            
            // Parse JSON
            Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
            
            // Build result
            QueryModels.QueryResult.QueryResultBuilder builder = QueryModels.QueryResult.builder()
                    .query(context.getOriginalQuery())
                    .summary((String) parsed.get("summary"));
            
            // Parse components
            List<QueryModels.RelevantComponent> components = new ArrayList<>();
            if (parsed.containsKey("components")) {
                List<Map<String, Object>> componentList = (List<Map<String, Object>>) parsed.get("components");
                for (Map<String, Object> comp : componentList) {
                    components.add(QueryModels.RelevantComponent.builder()
                            .type((String) comp.get("type"))
                            .signature((String) comp.get("signature"))
                            .name((String) comp.get("name"))
                            .summary((String) comp.get("summary"))
                            .relevanceScore(((Number) comp.getOrDefault("relevanceScore", 0.8)).doubleValue())
                            .build());
                }
            }
            builder.components(components);
            
            // Parse relationships
            List<QueryModels.RelationshipInsight> relationships = new ArrayList<>();
            if (parsed.containsKey("relationships")) {
                List<Map<String, Object>> relList = (List<Map<String, Object>>) parsed.get("relationships");
                for (Map<String, Object> rel : relList) {
                    relationships.add(QueryModels.RelationshipInsight.builder()
                            .description((String) rel.get("description"))
                            .fromComponent((String) rel.get("fromComponent"))
                            .toComponent((String) rel.get("toComponent"))
                            .relationshipType((String) rel.get("relationshipType"))
                            .verified(false)
                            .build());
                }
            }
            builder.relationships(relationships);
            
            // Parse metadata
            Map<String, Object> metadata = (Map<String, Object>) parsed.getOrDefault("metadata", new HashMap<>());
            builder.metadata(metadata);
            
            return builder.build();
            
        } catch (Exception e) {
            log.error("Failed to parse LLM response [{}]", context.getExecutionId(), e);
            
            // Return a basic result on parse error
            return QueryModels.QueryResult.builder()
                    .query(context.getOriginalQuery())
                    .summary("Failed to parse response: " + e.getMessage())
                    .components(new ArrayList<>())
                    .relationships(new ArrayList<>())
                    .metadata(Map.of("parseError", true))
                    .confidence(0.0)
                    .build();
        }
    }
}