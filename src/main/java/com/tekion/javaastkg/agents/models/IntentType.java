package com.tekion.javaastkg.agents.models;

/**
 * Types of user intents that can be classified from queries
 */
public enum IntentType {
    // Understanding intents
    UNDERSTAND_FLOW("Understanding how code flows/executes"),
    UNDERSTAND_ARCHITECTURE("Understanding system structure"),
    UNDERSTAND_FUNCTIONALITY("Understanding what code does"),
    
    // Debugging intents  
    DEBUG_ISSUE("Investigating specific problems"),
    FIND_BUG("Locating source of bugs"),
    TRACE_ERROR("Tracing error conditions"),
    
    // Analysis intents
    COMPARE_IMPLEMENTATIONS("Comparing different approaches"),
    ANALYZE_ALTERNATIVES("Analyzing alternative solutions"),
    ASSESS_IMPACT("Assessing change impact"),
    FIND_DEPENDENCIES("Finding code dependencies"),
    
    // Discovery intents
    FIND_CODE_ENTITIES("Finding specific code elements"),
    EXPLORE_CODEBASE("General codebase exploration"),
    
    // General
    GENERAL_INQUIRY("General questions"),
    CLARIFICATION_NEEDED("Ambiguous queries needing clarification");
    
    private final String description;
    
    IntentType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}