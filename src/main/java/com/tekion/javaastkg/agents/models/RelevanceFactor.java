package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Factors that contributed to an entity's relevance score
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelevanceFactor {
    private String factor;
    private double contribution;
    private String description;
    
    public static RelevanceFactor create(String factor, double contribution, String description) {
        return RelevanceFactor.builder()
            .factor(factor)
            .contribution(contribution)
            .description(description)
            .build();
    }
}