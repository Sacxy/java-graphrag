package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Defines retry behavior for failed operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryStrategy {
    private int maxRetries;
    private long delayMs;
    private RetryType retryType;
    private double backoffMultiplier;
    
    public enum RetryType {
        FIXED_DELAY,
        EXPONENTIAL_BACKOFF,
        LINEAR_BACKOFF
    }
    
    public static RetryStrategy standard() {
        return RetryStrategy.builder()
            .maxRetries(3)
            .delayMs(1000)
            .retryType(RetryType.EXPONENTIAL_BACKOFF)
            .backoffMultiplier(2.0)
            .build();
    }
}