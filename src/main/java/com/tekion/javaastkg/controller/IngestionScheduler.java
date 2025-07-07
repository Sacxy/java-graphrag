package com.tekion.javaastkg.controller;

import com.tekion.javaastkg.ingestion.GraphBuilder;
import com.tekion.javaastkg.ingestion.SemanticEnricher;
import com.tekion.javaastkg.ingestion.SpoonASTClient;
import com.tekion.javaastkg.ingestion.VectorizationService;
import com.tekion.javaastkg.model.AnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
            AnalysisResult analysisResult = spoonClient.fetchAST(spoonUrl);

            if (analysisResult == null || 
                (analysisResult.getNodes() == null || analysisResult.getNodes().isEmpty())) {
                log.warn("No analysis data received from Spoon service");
                return;
            }

            log.info("Received analysis result with {} nodes, {} edges",
                    analysisResult.getNodes() != null ? analysisResult.getNodes().size() : 0,
                    analysisResult.getEdges() != null ? analysisResult.getEdges().size() : 0);
            
//            // Log detailed metadata information
//            if (analysisResult.getMetadata() != null) {
//                GraphMetadata metadata = analysisResult.getMetadata();
//                log.info("=== Analysis Metadata ===");
//                log.info("Analysis Time: {} ({})",
//                    metadata.getAnalysisTime() != null ? new java.util.Date(metadata.getAnalysisTime()) : "N/A",
//                    metadata.getAnalysisTime() != null ? metadata.getAnalysisTime() : "N/A");
//                log.info("Analysis Duration: {} ms", metadata.getAnalysisDurationMs());
//                log.info("Files Processed: {}", metadata.getFilesProcessed());
//                log.info("Analyzed Paths: {}", metadata.getAnalyzedPaths());
//
//                // Log node type distribution
//                if (metadata.getNodeTypeCount() != null && !metadata.getNodeTypeCount().isEmpty()) {
//                    log.info("Node Type Distribution:");
//                    metadata.getNodeTypeCount().forEach((type, count) ->
//                        log.info("  - {}: {}", type, count));
//                }
//
//                // Log edge type distribution
//                if (metadata.getEdgeTypeCount() != null && !metadata.getEdgeTypeCount().isEmpty()) {
//                    log.info("Edge Type Distribution:");
//                    metadata.getEdgeTypeCount().forEach((type, count) ->
//                        log.info("  - {}: {}", type, count));
//                }
//
//                // Log graph statistics
//                log.info("Graph Statistics:");
//                log.info("  - Density: {}", metadata.getDensity());
//                log.info("  - Max Package Depth: {}", metadata.getMaxPackageDepth());
//                log.info("  - Average Methods per Class: {}", metadata.getAverageMethodsPerClass());
//                log.info("  - Average Fields per Class: {}", metadata.getAverageFieldsPerClass());
//
//                // Log circular dependencies if found
//                if (metadata.getHasCycles() != null && metadata.getHasCycles()) {
//                    log.warn("Circular Dependencies Detected!");
//                    log.warn("  - Max Cycle Length: {}", metadata.getMaxCycleLength());
//                    if (metadata.getCircularDependencies() != null && !metadata.getCircularDependencies().isEmpty()) {
//                        log.warn("  - Circular Dependency Paths: {}", metadata.getCircularDependencies().size());
//                        metadata.getCircularDependencies().forEach(cycle ->
//                            log.warn("    - Cycle: {}", String.join(" -> ", cycle)));
//                    }
//                }
//
//                // Log errors if any
//                if (metadata.getErrorCount() != null && metadata.getErrorCount() > 0) {
//                    log.error("Analysis Errors Encountered: {}", metadata.getErrorCount());
//                    if (metadata.getErrors() != null) {
//                        metadata.getErrors().forEach(error -> log.error("  - {}", error));
//                    }
//                }
//
//                log.info("=== End Analysis Metadata ===");
//            }

            // Step 3: Build knowledge graph
            log.info("Step 2/4: Building knowledge graph in Neo4j...");
            graphBuilder.buildGraph(analysisResult);

            // Step 4: Create description nodes with semantic information
            log.info("Step 3/4: Creating description nodes with semantic information...");
            enricher.createDescriptionNodes();

            // Step 5: Generate vector embeddings for documents
            log.info("Step 4/4: Generating vector embeddings for descriptions and file docs...");
            vectorizer.vectorizeDocuments();

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

    /**
     * Fetch fresh analysis result from SpoonAST service
     */
    @GetMapping("/analysis")
    public ResponseEntity<AnalysisResult> getAnalysisResult() {
        log.info("Fetching fresh analysis result from SpoonAST service");
        
        try {
            AnalysisResult result = spoonClient.fetchAST(spoonUrl);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to fetch analysis result", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
