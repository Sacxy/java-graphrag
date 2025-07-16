package com.tekion.javaastkg.controller;

import com.google.adk.agents.BaseAgent;
// import com.google.adk.tools.ToolContext; // Not needed for current implementation
import com.tekion.javaastkg.adk.agents.IntelligentOrchestrator;
import com.tekion.javaastkg.adk.config.AdkAgentRunner;
import com.tekion.javaastkg.model.QueryModels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for ADK Agent Integration
 * 
 * Provides endpoints to interact with the new ADK-based intelligent agents
 * while maintaining compatibility with existing infrastructure.
 */
@RestController
@RequestMapping("/api/adk")
@Slf4j
@CrossOrigin(origins = "*")
public class AdkAgentController {
    
    private final AdkAgentRunner agentRunner;
    private final Environment environment;
    private final IntelligentOrchestrator intelligentOrchestrator;
    private final DateTimeFormatter logFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    @Autowired
    public AdkAgentController(AdkAgentRunner agentRunner, Environment environment, 
                             @Qualifier("adkIntelligentOrchestrator") IntelligentOrchestrator intelligentOrchestrator) {
        this.agentRunner = agentRunner;
        this.environment = environment;
        this.intelligentOrchestrator = intelligentOrchestrator;
        initializeLogDirectory();
    }
    
    /**
     * Initialize the tmp/logs directory for capturing agent execution logs
     */
    private void initializeLogDirectory() {
        File logDir = new File("tmp/logs");
        if (!logDir.exists()) {
            if (logDir.mkdirs()) {
                log.info("Created log directory: {}", logDir.getAbsolutePath());
            }
        }
    }
    
    /**
     * Main query endpoint for ADK agent processing
     */
    @PostMapping("/query")
    public CompletableFuture<ResponseEntity<AdkQueryResponse>> processQuery(
            @Valid @RequestBody AdkQueryRequest request) {
        
        String logFileName = String.format("adk_query_%s.log", 
                                         LocalDateTime.now().format(logFormatter));
        File logFile = new File("tmp/logs/" + logFileName);
        
        log.info("Processing ADK query: {} | Log file: {}", request.getQuery(), logFile.getName());
        
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try (FileWriter logWriter = new FileWriter(logFile, true)) {
                // Log request
                logToFile(logWriter, "=== ADK QUERY PROCESSING START ===");
                logToFile(logWriter, "Query: " + request.getQuery());
                logToFile(logWriter, "Timestamp: " + LocalDateTime.now());
                logToFile(logWriter, "Options: " + request.getOptions());
                logToFile(logWriter, "\n--- Starting Agent Execution ---\n");
                
                // Get the orchestrator agent from Spring-managed bean (API key already configured)
                BaseAgent orchestrator = intelligentOrchestrator.getAgent();
                logToFile(logWriter, "Agent: " + orchestrator.name());
                
                // Execute the agent with the query string
                String result = agentRunner.runAgent(orchestrator, request.getQuery());
                
                // Log result
                logToFile(logWriter, "\n--- Agent Execution Complete ---\n");
                logToFile(logWriter, "Result Length: " + result.length() + " characters");
                logToFile(logWriter, "Execution Time: " + (System.currentTimeMillis() - startTime) + "ms");
                logToFile(logWriter, "\n--- Full Result ---\n");
                logToFile(logWriter, result);
                
                // Create basic metadata
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("agentName", orchestrator.name());
                metadata.put("queryLength", request.getQuery().length());
                
                AdkQueryResponse response = AdkQueryResponse.builder()
                    .query(request.getQuery())
                    .result(result)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .logFile(logFileName)
                    .metadata(metadata)
                    .success(true)
                    .build();
                
                logToFile(logWriter, "\n=== ADK QUERY PROCESSING END ===");
                
                return ResponseEntity.ok(response);
                
            } catch (Exception e) {
                log.error("Error processing ADK query", e);
                
                try (FileWriter errorWriter = new FileWriter(logFile, true)) {
                    logToFile(errorWriter, "\n--- ERROR OCCURRED ---");
                    logToFile(errorWriter, "Error: " + e.getClass().getName() + ": " + e.getMessage());
                    logToFile(errorWriter, "Stack trace:");
                    for (StackTraceElement element : e.getStackTrace()) {
                        logToFile(errorWriter, "  at " + element.toString());
                    }
                } catch (IOException logError) {
                    log.error("Failed to write error to log file", logError);
                }
                
                AdkQueryResponse errorResponse = AdkQueryResponse.builder()
                    .query(request.getQuery())
                    .result("Error: " + e.getMessage())
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .logFile(logFileName)
                    .error(e.getMessage())
                    .success(false)
                    .build();
                
                return ResponseEntity.status(500).body(errorResponse);
            }
        });
    }
    
    /**
     * Health check endpoint for ADK agents
     */
    @GetMapping("/health")
    // @Operation(summary = "Check ADK agent health", 
    //            description = "Verifies ADK agents are properly initialized and ready")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            BaseAgent orchestrator = intelligentOrchestrator.getAgent();
            
            health.put("status", "healthy");
            health.put("orchestratorName", orchestrator.name());
            String description = orchestrator.description();
            health.put("orchestratorDescription", description != null ? description : "No description");
            health.put("apiKeyConfigured", System.getenv("GOOGLE_API_KEY") != null);
            health.put("logDirectory", new File("tmp/logs").getAbsolutePath());
            health.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            health.put("status", "unhealthy");
            health.put("error", e.getMessage());
            health.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(503).body(health);
        }
    }
    
    /**
     * Test endpoint with a simple query
     */
    @GetMapping("/test")
    // @Operation(summary = "Test ADK agents with simple query", 
    //            description = "Runs a simple test query through the ADK agents")
    public CompletableFuture<ResponseEntity<AdkQueryResponse>> testAgent(
            @RequestParam(defaultValue = "What does UserService do?") String query) {
        
        AdkQueryRequest request = AdkQueryRequest.builder()
            .query(query)
            .build();
            
        return processQuery(request);
    }
    
    
    /**
     * Helper method to write to log file
     */
    private void logToFile(FileWriter writer, String message) throws IOException {
        writer.write(message + "\n");
        writer.flush();
    }
    
    /**
     * Request model for ADK queries
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AdkQueryRequest {
        @NotBlank(message = "Query cannot be blank")
        private String query;
        
        private Map<String, String> options;
    }
    
    /**
     * Response model for ADK queries
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AdkQueryResponse {
        private String query;
        private String result;
        private long executionTimeMs;
        private String logFile;
        private Map<String, Object> metadata;
        private boolean success;
        private String error;
    }
}