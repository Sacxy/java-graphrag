package com.tekion.javaastkg.agents.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages context window usage and compression for agent execution
 */
@Component
@Slf4j
public class ContextWindowManager {
    
    private static final int DEFAULT_MAX_TOKENS = 180000; // Reserve space for response
    private static final int SAFETY_BUFFER = 20000;
    
    private final int maxTokens;
    private final TokenCounter tokenCounter;
    private final Map<String, SessionContext> sessionContexts;
    
    public ContextWindowManager() {
        this(DEFAULT_MAX_TOKENS);
    }
    
    public ContextWindowManager(int maxTokens) {
        this.maxTokens = maxTokens;
        this.tokenCounter = new TokenCounter();
        this.sessionContexts = new ConcurrentHashMap<>();
    }
    
    /**
     * Check if we can add new content without exceeding limits
     */
    public boolean canAddContent(String sessionId, String newContent) {
        SessionContext context = sessionContexts.get(sessionId);
        if (context == null) {
            return true; // New session
        }
        
        int currentTokens = tokenCounter.count(context.getCurrentContent());
        int newTokens = tokenCounter.count(newContent);
        
        return currentTokens + newTokens <= maxTokens - SAFETY_BUFFER;
    }
    
    /**
     * Add content to session context, compressing if necessary
     */
    public void addContent(String sessionId, String content, ContentType type) {
        SessionContext context = sessionContexts.computeIfAbsent(sessionId, k -> new SessionContext());
        
        if (!canAddContent(sessionId, content)) {
            compressSessionContext(context);
        }
        
        context.addContent(content, type);
        log.debug("Added {} tokens to session {}", tokenCounter.count(content), sessionId);
    }
    
    /**
     * Get current context for session
     */
    public String getSessionContext(String sessionId) {
        SessionContext context = sessionContexts.get(sessionId);
        return context != null ? context.getCurrentContent() : "";
    }
    
    /**
     * Clear session context
     */
    public void clearSession(String sessionId) {
        sessionContexts.remove(sessionId);
        log.debug("Cleared context for session {}", sessionId);
    }
    
    /**
     * Compress session context to free up space
     */
    private void compressSessionContext(SessionContext context) {
        log.info("Compressing session context to free up space");
        
        // Compress older content
        context.compressOldContent();
        
        // Archive detailed data if still too large
        if (tokenCounter.count(context.getCurrentContent()) > maxTokens - SAFETY_BUFFER) {
            context.archiveDetailedContent();
        }
    }
    
    /**
     * Simple token counter implementation
     */
    public static class TokenCounter {
        
        public int count(String text) {
            if (text == null || text.isEmpty()) {
                return 0;
            }
            
            // Rough approximation: 1 token ≈ 4 characters for English text
            // This is a simplification - in production you'd use tiktoken or similar
            return text.length() / 4;
        }
    }
    
    /**
     * Content type for different kinds of context
     */
    public enum ContentType {
        QUERY,
        TOOL_RESULT,
        COMPRESSED_SUMMARY,
        METADATA
    }
    
    /**
     * Session-specific context management
     */
    private static class SessionContext {
        private StringBuilder currentContent = new StringBuilder();
        private StringBuilder archivedContent = new StringBuilder();
        
        void addContent(String content, ContentType type) {
            currentContent.append("[").append(type).append("] ").append(content).append("\n");
        }
        
        String getCurrentContent() {
            return currentContent.toString();
        }
        
        void compressOldContent() {
            // Move oldest content to compressed format
            String content = currentContent.toString();
            if (content.length() > 10000) {
                String toCompress = content.substring(0, content.length() / 2);
                String compressed = compressText(toCompress);
                
                currentContent = new StringBuilder();
                currentContent.append("[COMPRESSED] ").append(compressed).append("\n");
                currentContent.append(content.substring(content.length() / 2));
            }
        }
        
        void archiveDetailedContent() {
            // Move detailed content to archive
            String content = currentContent.toString();
            archivedContent.append(content);
            currentContent = new StringBuilder();
            currentContent.append("[ARCHIVED] Previous context archived\n");
        }
        
        private String compressText(String text) {
            // Simple compression - in production use more sophisticated methods
            return "Summary: " + text.substring(0, Math.min(200, text.length())) + "...";
        }
    }
}