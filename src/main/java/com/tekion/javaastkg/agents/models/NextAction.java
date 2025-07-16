package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the next action to be taken in the execution flow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NextAction {
    private ActionType type;
    private String toolName;
    private String description;
    private Object parameters;
    
    public enum ActionType {
        CALL_TOOL,
        PROCEED_TO_SYNTHESIS,
        REQUEST_VALIDATION,
        PAUSE_FOR_HUMAN,
        TRY_ALTERNATIVE_STRATEGY,
        SET_CONTEXT_LIMIT,
        ENABLE_PARALLEL_EXECUTION,
        RETRY_WITH_ALTERNATIVE,
        ESCALATE_TO_HUMAN
    }
    
    public static NextAction callTool(String toolName) {
        return NextAction.builder()
            .type(ActionType.CALL_TOOL)
            .toolName(toolName)
            .description("Call " + toolName + " tool")
            .build();
    }
    
    public static NextAction proceedToSynthesis() {
        return NextAction.builder()
            .type(ActionType.PROCEED_TO_SYNTHESIS)
            .description("Proceed to narrative generation")
            .build();
    }
}