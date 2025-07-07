package com.tekion.javaastkg.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Common rate limiting utility for all LLM service calls.
 * Handles rate limit exceptions with intelligent retry logic.
 */
@Component
@Slf4j
public class LLMRateLimiter {

    private static final Pattern WAIT_TIME_PATTERN = Pattern.compile("Please try again in ([0-9.]+)s");
    
    @Value("${llm.rate-limit.delay-ms:2000}")
    private long defaultDelayMs;
    
    @Value("${llm.rate-limit.max-retries:3}")
    private int maxRetries;

    /**
     * Executes an LLM call with rate limiting and retry logic
     */
    public <T> T executeWithRateLimit(Supplier<T> llmCall, String operationName) {
        return executeWithRateLimit(llmCall, operationName, maxRetries);
    }

    /**
     * Executes an LLM call with custom retry count
     */
    public <T> T executeWithRateLimit(Supplier<T> llmCall, String operationName, int retries) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= retries + 1; attempt++) {
            try {
                // Add delay between attempts (except first)
                if (attempt > 1) {
                    Thread.sleep(defaultDelayMs);
                }
                
                T result = llmCall.get();
                
                if (attempt > 1) {
                    log.info("{} succeeded on attempt {}", operationName, attempt);
                }
                
                return result;
                
            } catch (dev.ai4j.openai4j.OpenAiHttpException e) {
                lastException = e;
                
                if (e.getMessage().contains("rate_limit_exceeded")) {
                    long waitTime = extractWaitTime(e.getMessage());
                    log.warn("{} rate limit hit on attempt {}, waiting {}ms", operationName, attempt, waitTime);
                    
                    if (attempt <= retries) {
                        try {
                            Thread.sleep(waitTime);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted while waiting for rate limit", ie);
                        }
                    } else {
                        log.error("{} failed after {} attempts due to rate limits", operationName, retries + 1);
                        throw new RuntimeException(operationName + " failed after rate limit retries", e);
                    }
                } else {
                    // Non-rate-limit error, don't retry
                    log.error("{} failed with non-rate-limit error: {}", operationName, e.getMessage());
                    throw new RuntimeException(operationName + " failed", e);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during " + operationName, e);
                
            } catch (Exception e) {
                lastException = e;
                log.error("{} failed on attempt {} with error: {}", operationName, attempt, e.getMessage());
                
                if (attempt <= retries) {
                    try {
                        Thread.sleep(defaultDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting to retry", ie);
                    }
                } else {
                    throw new RuntimeException(operationName + " failed after " + (retries + 1) + " attempts", e);
                }
            }
        }
        
        throw new RuntimeException(operationName + " failed after all attempts", lastException);
    }
    
    /**
     * Extracts wait time from rate limit error message
     */
    private long extractWaitTime(String errorMessage) {
        Matcher matcher = WAIT_TIME_PATTERN.matcher(errorMessage);
        if (matcher.find()) {
            try {
                double seconds = Double.parseDouble(matcher.group(1));
                return (long) (seconds * 1000) + 500; // Add 500ms buffer
            } catch (NumberFormatException e) {
                log.warn("Could not parse wait time from error message, using default");
            }
        }
        return defaultDelayMs;
    }
}