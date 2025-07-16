package com.tekion.javaastkg.agents.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit breaker to prevent runaway agent execution and protect system resources
 */
@Component
@Slf4j
public class AgentCircuitBreaker {
    
    private static final int DEFAULT_MAX_TOOL_CALLS = 10;
    private static final Duration DEFAULT_MAX_EXECUTION_TIME = Duration.ofMinutes(3);
    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration RECOVERY_TIMEOUT = Duration.ofMinutes(1);
    
    private final int maxToolCalls;
    private final Duration maxExecutionTime;
    
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    private final AtomicReference<Instant> lastFailureTime = new AtomicReference<>(Instant.now());
    
    public AgentCircuitBreaker() {
        this(DEFAULT_MAX_TOOL_CALLS, DEFAULT_MAX_EXECUTION_TIME);
    }
    
    public AgentCircuitBreaker(int maxToolCalls, Duration maxExecutionTime) {
        this.maxToolCalls = maxToolCalls;
        this.maxExecutionTime = maxExecutionTime;
    }
    
    /**
     * Check if execution should be allowed
     */
    public boolean allowExecution() {
        CircuitState currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                return true;
                
            case OPEN:
                // Check if recovery timeout has passed
                if (Duration.between(lastFailureTime.get(), Instant.now()).compareTo(RECOVERY_TIMEOUT) > 0) {
                    // Try to move to half-open state
                    if (state.compareAndSet(CircuitState.OPEN, CircuitState.HALF_OPEN)) {
                        log.info("Circuit breaker moving from OPEN to HALF_OPEN state");
                        return true;
                    }
                }
                return false;
                
            case HALF_OPEN:
                // Allow limited execution to test if service has recovered
                return true;
                
            default:
                return false;
        }
    }
    
    /**
     * Record successful execution
     */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        
        CircuitState currentState = state.get();
        if (currentState == CircuitState.HALF_OPEN) {
            // Service has recovered, close the circuit
            if (state.compareAndSet(CircuitState.HALF_OPEN, CircuitState.CLOSED)) {
                log.info("Circuit breaker moving from HALF_OPEN to CLOSED state - service recovered");
            }
        }
    }
    
    /**
     * Record failed execution
     */
    public void recordFailure() {
        lastFailureTime.set(Instant.now());
        int failures = consecutiveFailures.incrementAndGet();
        
        if (failures >= FAILURE_THRESHOLD) {
            CircuitState currentState = state.get();
            if (currentState == CircuitState.CLOSED || currentState == CircuitState.HALF_OPEN) {
                if (state.compareAndSet(currentState, CircuitState.OPEN)) {
                    log.warn("Circuit breaker OPENED due to {} consecutive failures", failures);
                }
            }
        }
    }
    
    /**
     * Check if execution should continue based on tool calls and time
     */
    public boolean shouldContinueExecution(int toolCallCount, Instant executionStartTime) {
        // Check tool call limit
        if (toolCallCount >= maxToolCalls) {
            log.warn("Execution stopped: too many tool calls ({})", toolCallCount);
            return false;
        }
        
        // Check execution time limit
        Duration executionDuration = Duration.between(executionStartTime, Instant.now());
        if (executionDuration.compareTo(maxExecutionTime) > 0) {
            log.warn("Execution stopped: timeout after {}", executionDuration);
            return false;
        }
        
        return true;
    }
    
    /**
     * Get current circuit state
     */
    public CircuitState getCurrentState() {
        return state.get();
    }
    
    /**
     * Get current failure count
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }
    
    /**
     * Reset circuit breaker to closed state
     */
    public void reset() {
        consecutiveFailures.set(0);
        state.set(CircuitState.CLOSED);
        log.info("Circuit breaker reset to CLOSED state");
    }
    
    /**
     * Circuit breaker states
     */
    public enum CircuitState {
        CLOSED,    // Normal operation
        OPEN,      // Blocking requests due to failures
        HALF_OPEN  // Testing if service has recovered
    }
}