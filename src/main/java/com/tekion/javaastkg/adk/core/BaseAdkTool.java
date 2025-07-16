package com.tekion.javaastkg.adk.core;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 🔧 Base ADK Tool - Foundation for all ADK FunctionTools
 * 
 * Provides common functionality for ADK function tools:
 * - Consistent parameter validation
 * - Standard response formatting
 * - Error handling patterns
 * - Performance monitoring
 * - Common utilities
 * 
 * This base class ensures all tools follow consistent patterns and provides
 * reusable functionality across the tool ecosystem.
 */
@Slf4j
public abstract class BaseAdkTool {
    
    /**
     * Standard response builder for successful operations
     */
    protected static Map<String, Object> successResponse(String operation, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("operation", operation);
        response.put("timestamp", System.currentTimeMillis());
        
        if (data instanceof Map) {
            response.putAll((Map<? extends String, ?>) data);
        } else {
            response.put("data", data);
        }
        
        return response;
    }
    
    /**
     * Standard response builder for error operations
     */
    protected static Map<String, Object> errorResponse(String operation, String error) {
        return Map.of(
            "status", "error",
            "operation", operation,
            "error", error,
            "timestamp", System.currentTimeMillis()
        );
    }
    
    /**
     * Standard response builder for error operations with details
     */
    protected static Map<String, Object> errorResponse(String operation, String error, Object details) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("operation", operation);
        response.put("error", error);
        response.put("details", details);
        response.put("timestamp", System.currentTimeMillis());
        
        return response;
    }
    
    /**
     * Validate required string parameter
     */
    protected static boolean isValidString(String value) {
        return value != null && !value.trim().isEmpty();
    }
    
    /**
     * Validate required parameter and return error if invalid
     */
    protected static Optional<Map<String, Object>> validateRequired(String paramName, String value, String operation) {
        if (!isValidString(value)) {
            return Optional.of(errorResponse(operation, paramName + " cannot be empty or null"));
        }
        return Optional.empty();
    }
    
    /**
     * Safe string extraction with default value
     */
    protected static String safeString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value.toString().trim();
    }
    
    /**
     * Safe number extraction with default value
     */
    protected static double safeDouble(Object value, double defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    /**
     * Safe integer extraction with default value
     */
    protected static int safeInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    /**
     * Safe boolean extraction with default value
     */
    protected static boolean safeBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
    
    /**
     * Safe map extraction with default value
     */
    @SuppressWarnings("unchecked")
    protected static Map<String, Object> safeMap(Object value, Map<String, Object> defaultValue) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return defaultValue;
    }
    
    /**
     * Safe list extraction with default value
     */
    @SuppressWarnings("unchecked")
    protected static List<Object> safeList(Object value, List<Object> defaultValue) {
        if (value instanceof List) {
            return (List<Object>) value;
        }
        return defaultValue;
    }
    
    /**
     * Create metadata map with common fields
     */
    protected static Map<String, Object> createMetadata(String toolName, long executionTime) {
        return Map.of(
            "toolName", toolName,
            "executionTimeMs", executionTime,
            "timestamp", System.currentTimeMillis()
        );
    }
    
    /**
     * Log tool execution start
     */
    protected static void logToolStart(String toolName, String operation) {
        log.debug("🔧 Starting tool execution: {} - {}", toolName, operation);
    }
    
    /**
     * Log tool execution completion
     */
    protected static void logToolComplete(String toolName, String operation, long executionTime) {
        log.debug("✅ Tool execution completed: {} - {} ({}ms)", toolName, operation, executionTime);
    }
    
    /**
     * Log tool execution error
     */
    protected static void logToolError(String toolName, String operation, String error) {
        log.error("❌ Tool execution failed: {} - {} - {}", toolName, operation, error);
    }
    
    /**
     * Extract tool context information safely
     */
    protected static Map<String, Object> extractToolContext(ToolContext ctx) {
        Map<String, Object> context = new HashMap<>();
        
        if (ctx != null) {
            // Extract any available context information
            // This is a placeholder - actual implementation depends on ToolContext structure
            context.put("contextAvailable", true);
            context.put("contextType", ctx.getClass().getSimpleName());
        } else {
            context.put("contextAvailable", false);
        }
        
        return context;
    }
}