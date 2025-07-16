package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Result of query intent classification and strategy recommendation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentClassificationResult {
    private IntentType intent;
    private double confidence;
    private List<CodeEntity> entities;
    private ExecutionStrategy strategy;
    private List<AmbiguityFlag> ambiguityFlags;
    private List<NextAction> nextActions;
    private String explanation;
    private Map<String, Object> metadata;
    private boolean needsClarification;
    private List<String> clarificationQuestions;
    
    public static IntentClassificationResult error(String message) {
        return IntentClassificationResult.builder()
            .intent(IntentType.GENERAL_INQUIRY)
            .confidence(0.0)
            .entities(List.of())
            .explanation("Error: " + message)
            .needsClarification(true)
            .build();
    }
    
    public boolean isHighConfidence() {
        return confidence > 0.8;
    }
    
    public boolean hasEntities() {
        return entities != null && !entities.isEmpty();
    }
}