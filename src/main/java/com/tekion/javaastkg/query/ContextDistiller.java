package com.tekion.javaastkg.query;

import com.tekion.javaastkg.model.GraphEntities;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Distills retrieved context to only the most relevant pieces for answering the query.
 * Uses LLM-based relevance filtering to ensure high-quality context.
 */
@Service
@Slf4j
public class ContextDistiller {

    private final ChatLanguageModel llm;
    private final ExecutorService executorService;

    @Autowired
    public ContextDistiller(ChatLanguageModel llm) {
        this.llm = llm;
        this.executorService = Executors.newFixedThreadPool(3);
    }

    /**
     * Distills the graph context to only relevant pieces for the query
     */
    public List<RelevantContext> distill(String userQuery, GraphEntities.GraphContext graphContext) {
        log.info("Distilling context for query: {}", userQuery);

        List<ContextCandidate> candidates = extractCandidates(graphContext);
        log.debug("Extracted {} context candidates", candidates.size());

        if (candidates.isEmpty()) {
            return new ArrayList<>();
        }

        // Filter candidates in parallel for efficiency
        List<CompletableFuture<Optional<RelevantContext>>> futures = candidates.stream()
                .map(candidate -> CompletableFuture.supplyAsync(() ->
                        evaluateRelevance(userQuery, candidate), executorService))
                .collect(Collectors.toList());

        // Collect relevant contexts
        List<RelevantContext> relevantContexts = futures.stream()
                .map(CompletableFuture::join)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparing(RelevantContext::getRelevanceScore).reversed())
                .collect(Collectors.toList());

        log.info("Retained {} relevant contexts out of {} candidates",
                relevantContexts.size(), candidates.size());

        return relevantContexts;
    }

    /**
     * Extracts context candidates from the graph context
     */
    private List<ContextCandidate> extractCandidates(GraphEntities.GraphContext graphContext) {
        List<ContextCandidate> candidates = new ArrayList<>();

        // Add method contexts
        for (GraphEntities.MethodNode method : graphContext.getMethods()) {
            if (method.getSummary() != null) {
                candidates.add(new ContextCandidate(
                        "method",
                        method.getSignature(),
                        method.getName(),
                        method.getSummary(),
                        method.getDetailedExplanation(),
                        method.getBusinessTags(),
                        method.getMetadata()
                ));
            }
        }

        // Add class contexts if they have relevant information
        for (GraphEntities.ClassNode clazz : graphContext.getClasses()) {
            // For now, we'll include key classes
            // In a more sophisticated implementation, we might check for class-level documentation
            if (isKeyClass(clazz)) {
                candidates.add(new ContextCandidate(
                        "class",
                        clazz.getFullName(),
                        clazz.getName(),
                        "Class: " + clazz.getFullName(),
                        "Type: " + clazz.getType(),
                        List.of(clazz.getPackageName()),
                        clazz.getMetadata()
                ));
            }
        }

        return candidates;
    }

    /**
     * Determines if a class is key/important enough to include
     */
    private boolean isKeyClass(GraphEntities.ClassNode clazz) {
        // Include interfaces, abstract classes, or classes with certain patterns
        return clazz.isInterface() ||
                clazz.isAbstract() ||
                clazz.getName().endsWith("Service") ||
                clazz.getName().endsWith("Controller") ||
                clazz.getName().endsWith("Repository");
    }

    /**
     * Evaluates if a context candidate is relevant to the query
     */
    private Optional<RelevantContext> evaluateRelevance(String query, ContextCandidate candidate) {
        String prompt = String.format("""
            Evaluate if this code context helps answer the user's query.
            
            User Query: "%s"
            
            Context Type: %s
            Context Name: %s
            Summary: %s
            Details: %s
            Tags: %s
            
            Does this context help answer the query? Consider:
            1. Direct relevance to the query topic
            2. Provides necessary background information
            3. Shows implementation details mentioned in the query
            4. Contains business logic related to the query
            
            Respond with ONLY a JSON object:
            {
                "relevant": true/false,
                "relevanceScore": 0.0-1.0,
                "reason": "brief explanation"
            }
            """,
                query,
                candidate.getType(),
                candidate.getName(),
                candidate.getSummary(),
                candidate.getDetails() != null ? candidate.getDetails() : "N/A",
                String.join(", ", candidate.getTags())
        );

        try {
            String response = llm.generate(prompt);

            // Parse response
            RelevanceEvaluation eval = parseRelevanceEvaluation(response);

            if (eval.isRelevant()) {
                return Optional.of(new RelevantContext(
                        candidate,
                        eval.getRelevanceScore(),
                        eval.getReason()
                ));
            }

            return Optional.empty();

        } catch (Exception e) {
            log.warn("Failed to evaluate relevance for: {}", candidate.getIdentifier(), e);
            // On error, include with low score
            return Optional.of(new RelevantContext(candidate, 0.3, "Evaluation error"));
        }
    }

    /**
     * Parses the LLM's relevance evaluation response
     */
    private RelevanceEvaluation parseRelevanceEvaluation(String response) {
        // Simple JSON parsing - in production use proper JSON library
        try {
            boolean relevant = response.contains("\"relevant\": true") ||
                    response.contains("\"relevant\":true");

            double score = 0.5;
            if (response.contains("\"relevanceScore\":")) {
                int idx = response.indexOf("\"relevanceScore\":") + 17;
                int endIdx = response.indexOf(",", idx);
                if (endIdx == -1) endIdx = response.indexOf("}", idx);
                score = Double.parseDouble(response.substring(idx, endIdx).trim());
            }

            String reason = "Relevance determined by LLM";
            if (response.contains("\"reason\":")) {
                int idx = response.indexOf("\"reason\":") + 9;
                int endIdx = response.lastIndexOf("\"");
                reason = response.substring(idx + 1, endIdx);
            }

            return new RelevanceEvaluation(relevant, score, reason);

        } catch (Exception e) {
            // Default to including with medium score on parse error
            return new RelevanceEvaluation(true, 0.5, "Parse error");
        }
    }

    /**
     * Data class for context candidates
     */
    @Data
    @AllArgsConstructor
    public static class ContextCandidate {
        private String type;
        private String identifier;
        private String name;
        private String summary;
        private String details;
        private List<String> tags;
        private Map<String, Object> metadata;
    }

    /**
     * Data class for relevant context
     */
    @Data
    @AllArgsConstructor
    public static class RelevantContext {
        private ContextCandidate candidate;
        private double relevanceScore;
        private String relevanceReason;
    }

    /**
     * Data class for relevance evaluation
     */
    @Data
    @AllArgsConstructor
    private static class RelevanceEvaluation {
        private boolean relevant;
        private double relevanceScore;
        private String reason;
    }
}
