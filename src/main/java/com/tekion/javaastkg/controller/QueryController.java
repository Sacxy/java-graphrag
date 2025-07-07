package com.tekion.javaastkg.controller;

import com.tekion.javaastkg.model.QueryModels;
import com.tekion.javaastkg.query.QueryOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;

/**
 * REST controller for handling knowledge queries.
 * Provides endpoints for querying the knowledge graph.
 */
@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "*")
@Slf4j
public class QueryController {

    private final QueryOrchestrator queryOrchestrator;

    @Autowired
    public QueryController(QueryOrchestrator queryOrchestrator) {
        this.queryOrchestrator = queryOrchestrator;
    }

    /**
     * Main query endpoint for natural language queries
     */
    @PostMapping("/query")
    public ResponseEntity<QueryModels.QueryResult> query(@Valid @RequestBody QueryModels.QueryRequest request) {
        log.info("Received query: {}", request.getQuery());

        try {
            // Process query
            QueryModels.QueryResult result = queryOrchestrator.query(request.getQuery());

            log.info("Query processed successfully with confidence: {}", result.getConfidence());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Query processing failed", e);

            // Return error response
            QueryModels.QueryResult errorResult = QueryModels.QueryResult.builder()
                    .query(request.getQuery())
                    .summary("An error occurred while processing your query.")
                    .confidence(0.0)
                    .metadata(Map.of(
                            "error", true,
                            "errorMessage", e.getMessage() != null ? e.getMessage() : "Unknown error"
                    ))
                    .build();

            return ResponseEntity.internalServerError().body(errorResult);
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Knowledge Engine Query API",
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Get system statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        // In a real implementation, gather actual statistics
        return ResponseEntity.ok(Map.of(
                "status", "operational",
                "totalNodes", "calculating...",
                "totalRelationships", "calculating...",
                "indexStatus", "active"
        ));
    }
}
