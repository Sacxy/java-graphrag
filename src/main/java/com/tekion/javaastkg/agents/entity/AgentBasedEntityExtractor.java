package com.tekion.javaastkg.agents.entity;

import com.tekion.javaastkg.agents.entity.IntelligentEntityExtractor;
import com.tekion.javaastkg.model.ExtractedEntities;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Modern entity extraction service using intelligent agents.
 * Provides high-quality entity extraction through multiple specialized agents.
 *
 * Applied 12-Factor Principles:
 * - Factor 1: Structured outputs (maintains EntityExtractor interface)
 * - Factor 8: Owns control flow (delegates to intelligent agent system)
 * - Factor 12: Stateless (delegates to stateless agents)
 */
@Service
@Primary
@ConditionalOnProperty(
    name = "entity.extraction.agent.enabled",
    havingValue = "true",
    matchIfMissing = true
)
@Slf4j
public class AgentBasedEntityExtractor {

    private final IntelligentEntityExtractor intelligentExtractor;

    public AgentBasedEntityExtractor(IntelligentEntityExtractor intelligentExtractor) {
        // Validate required dependencies
        if (intelligentExtractor == null) {
            throw new IllegalArgumentException("IntelligentEntityExtractor cannot be null");
        }

        this.intelligentExtractor = intelligentExtractor;

        log.info("AgentBasedEntityExtractor initialized with intelligent extraction system");
    }


    /**
     * Extracts entities from a query using the agent-based system
     * Returns structured entity extraction results
     */
    public ExtractedEntities extract(String query) {
        // Input validation
        if (query == null) {
            log.warn("Received null query, returning empty result");
            return createEmptyResult();
        }

        if (query.trim().isEmpty()) {
            log.warn("Received empty query, returning empty result");
            return createEmptyResult();
        }

        log.info("Agent-based entity extraction for query: {}", query);

        try {
            // Use the intelligent agent system
            ExtractedEntities agentResult = intelligentExtractor.extract(query);

            if (agentResult == null) {
                log.warn("IntelligentEntityExtractor returned null result for query: {}", query);
                return createEmptyResult();
            }

            // Result is already in the correct format
            ExtractedEntities result = agentResult;

            log.info("Agent-based extraction completed successfully. Found {} total entities",
                    agentResult.getTotalCount());

            return result;

        } catch (Exception e) {
            log.error("Agent-based extraction failed for query '{}': {}", query, e.getMessage(), e);
            return createEmptyResult();
        }
    }





    /**
     * Creates an empty result with proper null safety
     */
    private ExtractedEntities createEmptyResult() {
        return ExtractedEntities.builder()
                .classes(Collections.emptyList())
                .methods(Collections.emptyList())
                .packages(Collections.emptyList())
                .terms(Collections.emptyList())
                .build();
    }
}