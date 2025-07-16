package com.tekion.javaastkg.agents.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects and reports metrics for agent execution
 */
@Component
@Slf4j
public class MetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, AtomicLong> toolUsageCounters;
    private final ConcurrentHashMap<String, AtomicLong> executionTimes;
    
    private final Counter successfulExecutions;
    private final Counter failedExecutions;
    private final Timer executionTimer;
    
    @Autowired(required = false)
    public MetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.toolUsageCounters = new ConcurrentHashMap<>();
        this.executionTimes = new ConcurrentHashMap<>();
        
        // Initialize metrics
        this.successfulExecutions = Counter.builder("agent.executions.successful")
            .description("Number of successful agent executions")
            .register(meterRegistry);
            
        this.failedExecutions = Counter.builder("agent.executions.failed")
            .description("Number of failed agent executions")
            .register(meterRegistry);
            
        this.executionTimer = Timer.builder("agent.execution.time")
            .description("Agent execution time")
            .register(meterRegistry);
    }
    
    // Default constructor for when MeterRegistry is not available
    public MetricsCollector() {
        this.meterRegistry = null;
        this.toolUsageCounters = new ConcurrentHashMap<>();
        this.executionTimes = new ConcurrentHashMap<>();
        this.successfulExecutions = null;
        this.failedExecutions = null;
        this.executionTimer = null;
    }
    
    /**
     * Record agent execution metrics
     */
    public void recordExecution(String userId, String sessionId, long executionTimeMs, boolean success) {
        if (success) {
            if (successfulExecutions != null) {
                successfulExecutions.increment();
            }
        } else {
            if (failedExecutions != null) {
                failedExecutions.increment();
            }
        }
        
        if (executionTimer != null) {
            executionTimer.record(Duration.ofMillis(executionTimeMs));
        }
        
        // Store in local counters as well
        String key = userId + ":" + sessionId;
        executionTimes.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(executionTimeMs);
        
        log.debug("Recorded execution: user={}, session={}, time={}ms, success={}", 
                 userId, sessionId, executionTimeMs, success);
    }
    
    /**
     * Record tool usage
     */
    public void recordToolUsage(String toolName, long executionTimeMs, boolean success) {
        String key = toolName + (success ? "_success" : "_failure");
        toolUsageCounters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        
        if (meterRegistry != null) {
            Counter.builder("agent.tool.usage")
                .tag("tool", toolName)
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .increment();
                
            Timer.builder("agent.tool.execution.time")
                .tag("tool", toolName)
                .register(meterRegistry)
                .record(Duration.ofMillis(executionTimeMs));
        }
        
        log.debug("Recorded tool usage: tool={}, time={}ms, success={}", toolName, executionTimeMs, success);
    }
    
    /**
     * Record strategy usage
     */
    public void recordStrategyUsage(String strategy, boolean success) {
        if (meterRegistry != null) {
            Counter.builder("agent.strategy.usage")
                .tag("strategy", strategy)
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .increment();
        }
        
        log.debug("Recorded strategy usage: strategy={}, success={}", strategy, success);
    }
    
    /**
     * Record context window usage
     */
    public void recordContextUsage(String sessionId, int tokensUsed, int maxTokens) {
        if (meterRegistry != null) {
            meterRegistry.gauge("agent.context.tokens.used", tokensUsed);
            meterRegistry.gauge("agent.context.tokens.max", maxTokens);
            meterRegistry.gauge("agent.context.usage.percentage", (double) tokensUsed / maxTokens * 100);
        }
        
        log.debug("Recorded context usage: session={}, tokens={}/{} ({}%)", 
                 sessionId, tokensUsed, maxTokens, (double) tokensUsed / maxTokens * 100);
    }
    
    /**
     * Get tool usage statistics
     */
    public long getToolUsageCount(String toolName, boolean success) {
        String key = toolName + (success ? "_success" : "_failure");
        AtomicLong counter = toolUsageCounters.get(key);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * Get average execution time for user/session
     */
    public double getAverageExecutionTime(String userId, String sessionId) {
        String key = userId + ":" + sessionId;
        AtomicLong totalTime = executionTimes.get(key);
        return totalTime != null ? totalTime.get() : 0.0;
    }
    
    /**
     * Clear metrics for session
     */
    public void clearSessionMetrics(String userId, String sessionId) {
        String key = userId + ":" + sessionId;
        executionTimes.remove(key);
        log.debug("Cleared metrics for session: user={}, session={}", userId, sessionId);
    }
}