package com.tekion.javaastkg.agents.models;

/**
 * Types of execution workflows for different query patterns
 */
public enum WorkflowType {
    EXPLORATORY,      // For broad queries like "How does authentication work?"
    SURGICAL,         // For specific issues like "Why does login() throw NPE?"
    COMPARATIVE,      // For comparison queries like "Compare payment implementations"
    IMPACT_ANALYSIS,  // For impact queries like "What happens if I change this?"
    SIMPLE_CHAIN,     // Default linear execution
    PARALLEL_SEARCH   // Multiple parallel tool execution
}