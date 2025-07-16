package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Defines points where human interaction is needed or beneficial
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HumanInteractionPoint {
    private InteractionType type;
    private String trigger;
    private String prompt;
    private boolean required;
    private long timeoutMs;
    
    public enum InteractionType {
        APPROVAL_NEEDED,
        CLARIFICATION_NEEDED,
        REVIEW_REQUESTED,
        DECISION_REQUIRED,
        VALIDATION_NEEDED
    }
}