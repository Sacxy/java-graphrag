package com.tekion.javaastkg.controller;

import com.tekion.javaastkg.ingestion.*;
import com.tekion.javaastkg.model.SpoonAST;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Scheduler component that orchestrates the knowledge ingestion pipeline.
 * Runs on a schedule to keep the knowledge graph up-to-date.
 */
@Component
@RestController
@RequestMapping("/api/ingestion")
@Slf4j
public class IngestionScheduler {

    private final SpoonASTClient spoonClient;
    private final GraphBuilder graphBuilder;
    private final SemanticEnricher enricher;
    private final VectorizationService vectorizer;

    @Value("${spoon.api.url}")
    private String spoonUrl;

    @Value("${ingestion.schedule.enabled:false}")
    private boolean schedulingEnabled;

    private LocalDateTime lastRunTime;
    private boolean isRunning = false;

    @Autowired
    public IngestionScheduler(SpoonASTClient spoonClient,
                              GraphBuilder graphBuilder,
                              SemanticEnricher enricher,
                              VectorizationService vectorizer) {
        this.spoonClient = spoonClient;
        this.graphBuilder = graphBuilder;
        this.enricher = enricher;
        this.vectorizer = vectorizer;
    }


    /**
     * Scheduled ingestion pipeline execution (disabled by default)
     */
//    @Scheduled(fixedDelayString = "${ingestion.schedule.fixed-delay:3600000}")
//    public void scheduledIngestion() {
//        if (schedulingEnabled && !isRunning) {
//            runIngestionPipeline();
//        }
//    }

    /**
     * Runs the complete knowledge ingestion pipeline
     */
    public synchronized void runIngestionPipeline() {
        if (isRunning) {
            log.warn("Ingestion pipeline is already running, skipping this execution");
            return;
        }

        isRunning = true;
        LocalDateTime startTime = LocalDateTime.now();

        log.info("=== Starting Knowledge Ingestion Pipeline ===");
        log.info("SpoonUrl: {}", spoonUrl);
        log.info("Start time: {}", startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        try {
            // Step 1: Check Spoon service health
//            if (!spoonClient.isHealthy()) {
//                log.error("Spoon service is not healthy, aborting ingestion");
//                return;
//            }

            // Step 2: Fetch AST from Spoon (polling mechanism)
            log.info("Step 1/4: Starting AST analysis with Spoon service...");
            log.info("SpoonUrl path: {}", spoonUrl);
            SpoonAST ast = spoonClient.fetchAST(spoonUrl);

            if (ast == null || (ast.getClasses().isEmpty() && ast.getMethods().isEmpty())) {
                log.warn("No AST data received from Spoon service");
                return;
            }

            log.info("Received AST with {} classes, {} methods, {} endpoints",
                    ast.getClasses().size(),
                    ast.getMethods().size(),
                    ast.getApiEndpoints() != null ? ast.getApiEndpoints().size() : 0);

            // Step 3: Build knowledge graph
            log.info("Step 2/4: Building knowledge graph in Neo4j...");
            graphBuilder.buildGraph(ast);

            // Step 4: Enrich with semantic information
            log.info("Step 3/4: Enriching methods with semantic information...");
            enricher.enrichMethods();

            // Step 5: Generate vector embeddings
            log.info("Step 4/4: Generating vector embeddings...");
            vectorizer.vectorizeEnrichedMethods();

            // Pipeline completed successfully
            LocalDateTime endTime = LocalDateTime.now();
            long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();

            log.info("=== Knowledge Ingestion Pipeline Completed ===");
            log.info("Duration: {} seconds", durationSeconds);
            log.info("End time: {}", endTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            lastRunTime = endTime;

        } catch (Exception e) {
            log.error("Knowledge ingestion pipeline failed", e);

        } finally {
            isRunning = false;
        }
    }

    /**
     * Manual trigger for ingestion via REST API
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerManualIngestion() {
        log.info("Manual ingestion triggered via API");
        
        if (isRunning) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Ingestion pipeline is already running",
                "status", getStatus()
            ));
        }
        
        // Run in background thread to avoid blocking the API response
        new Thread(this::runIngestionPipeline).start();
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Ingestion pipeline started successfully",
            "executionId", java.util.UUID.randomUUID().toString(),
            "timestamp", java.time.LocalDateTime.now()
        ));
    }

    /**
     * Get the status of the ingestion pipeline
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "isRunning", isRunning,
                "lastRunTime", lastRunTime != null ? lastRunTime.toString() : "Never",
                "schedulingEnabled", schedulingEnabled,
                "repositoryPath", spoonUrl,
                "timestamp", java.time.LocalDateTime.now()
        ));
    }
}
