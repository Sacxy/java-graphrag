package com.tekion.javaastkg.agents.entity.extraction;

import com.tekion.javaastkg.agents.entity.models.EntityMatch;
import com.tekion.javaastkg.agents.entity.models.QueryContext;

import java.util.List;

/**
 * Base interface for all entity extraction agents.
 * Implements Factor 12 (Stateless Reducer) and Factor 10 (Small Focused Agents).
 * 
 * Applied 12-Factor Principles:
 * - Factor 10: Each agent is small and focused (single responsibility)
 * - Factor 12: Agents are stateless reducers (pure functions)
 * - Factor 4: Tools as structured outputs (EntityMatch results)
 */
public interface ExtractionAgent {
    
    /**
     * Factor 12: Pure function - extracts entities from query context
     * Same input always produces same output (stateless reducer)
     * 
     * @param context The structured query context
     * @return List of entity matches with confidence scores
     */
    List<EntityMatch> extract(QueryContext context);
    
    /**
     * Checks if this agent can handle the given query context
     * Used for dynamic agent selection
     * 
     * @param context The query context to evaluate
     * @return true if this agent can process the context
     */
    boolean canHandle(QueryContext context);
    
    /**
     * Gets the confidence score for this agent handling the query
     * Used for agent ranking and selection
     * 
     * @param context The query context to evaluate
     * @return confidence score (0.0 to 1.0)
     */
    double getHandlingConfidence(QueryContext context);
    
    /**
     * Gets the name of this agent for logging and debugging
     * 
     * @return agent name
     */
    default String getAgentName() {
        return this.getClass().getSimpleName();
    }
}