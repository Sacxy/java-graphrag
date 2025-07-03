package com.tekion.javaastkg.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tekion.javaastkg.model.AnalysisResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client service for interacting with the Spoon AST service.
 * Handles API communication and error recovery.
 */
@Service
@Slf4j
public class SpoonASTClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spoon.api.timeout:30000}")
    private int timeout;
    
    @Value("${spoon.api.polling.interval:5000}")
    private int pollingInterval;
    
    @Value("${spoon.api.polling.max-attempts:60}")
    private int maxPollingAttempts;

    public SpoonASTClient(@Value("${spoon.api.url}") String spoonApiUrl) {
        // Configure larger buffer size for handling large AST responses
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(clientCodecConfigurer -> {
                    clientCodecConfigurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024); // 10MB buffer
                })
                .build();
        
        this.webClient = WebClient.builder()
                .baseUrl(spoonApiUrl)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("dealerid", "0")
                .defaultHeader("oemid", "0")
                .defaultHeader("programid", "0")
                .defaultHeader("tenantid", "0")
                .defaultHeader("userId", "0")
                .exchangeStrategies(strategies)
                .build();

        log.info("Initialized Spoon AST client with URL: {} and 10MB buffer size", spoonApiUrl);
    }

    /**
     * Fetches the AST data for a given repository path using polling mechanism.
     * 1. Start analysis
     * 2. Poll for status
     * 3. Get results when ready
     */
    public AnalysisResult fetchAST(String spoonUrl) {
        log.info("Starting Spoon AST analysis for spoonUrl: {}", spoonUrl);

        try {
            // Step 1: Start analysis
            String analysisId = startAnalysis(spoonUrl);
            log.info("Analysis started with ID: {}", analysisId);

            // Step 2: Poll for completion
            waitForCompletion(analysisId, spoonUrl);
            log.info("Analysis completed for ID: {}", analysisId);

            // Step 3: Get results
            AnalysisResult result = getResults(analysisId);
            
            if (result != null) {
                log.info("Successfully fetched analysis result with {} nodes and {} edges",
                        result.getNodes() != null ? result.getNodes().size() : 0, 
                        result.getEdges() != null ? result.getEdges().size() : 0);
            }

            return result;

        } catch (Exception e) {
            log.error("Failed to fetch AST from Spoon service", e);
            throw new RuntimeException("AST fetch failed for repository: " + spoonUrl, e);
        }
    }
    
    /**
     * Start analysis and return analysis ID
     */
    private String startAnalysis(String spoonUrl) {
        Map<String, Object> response = webClient.post()
                .uri(spoonUrl + "/analyze")
                .bodyValue(new HashMap<>())
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(timeout))
                .block();

        response = (Map<String, Object>) response.get("data");
        if (response == null || !response.containsKey("analysisId")) {
            throw new RuntimeException("Failed to start analysis - no analysis ID returned");
        }

        return response.get("analysisId").toString();
    }
    
    /**
     * Poll for analysis completion
     */
    private void waitForCompletion(String analysisId, String spoonUrl) throws InterruptedException {
        int attempts = 0;
        
        while (attempts < maxPollingAttempts) {
            Map<String, Object> status = webClient.get()
                    .uri(spoonUrl + "/status/{analysisId}", analysisId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            status = (Map<String, Object>) status.get("data");
            if (status == null) {
                throw new RuntimeException("Failed to get status for analysis ID: " + analysisId);
            }

            String state = status.get("status").toString();
            log.debug("Analysis {} status: {}", analysisId, state);

            if ("COMPLETED".equalsIgnoreCase(state) || "SUCCESS".equalsIgnoreCase(state)) {
                return;
            } else if ("FAILED".equalsIgnoreCase(state) || "ERROR".equalsIgnoreCase(state)) {
                String errorMsg = status.containsKey("error") ? status.get("error").toString() : "Unknown error";
                throw new RuntimeException("Analysis failed: " + errorMsg);
            }

            // Wait before next poll
            Thread.sleep(pollingInterval);
            attempts++;
        }

        throw new RuntimeException("Analysis timed out after " + (maxPollingAttempts * pollingInterval / 1000) + " seconds");
    }
    
    /**
     * Get analysis results
     */
    private AnalysisResult getResults(String analysisId) {
        log.info("Fetching results for analysis ID: {}", analysisId);
        
        Map<String, Object> result = webClient.get()
                .uri("/results/{analysisId}", analysisId)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(timeout))
                .doOnError(error -> log.error("Error fetching results for analysis {}: {}", analysisId, error.getMessage()))
                .block();

        result = (Map<String, Object>) result.get("data");
        log.info("Results fetched successfully for analysis ID: {}", analysisId);
        AnalysisResult analysisResult = objectMapper.convertValue(result, AnalysisResult.class);
        
//        // Log quick summary of received data
//        if (analysisResult != null) {
//            log.info("Analysis Result Summary:");
//            log.info("  - Status: {}", analysisResult.getStatus());
//            log.info("  - Progress: {}%", analysisResult.getProgress());
//            log.info("  - Current Phase: {}", analysisResult.getCurrentPhase());
//            log.info("  - Total Nodes: {}", analysisResult.getNodes() != null ? analysisResult.getNodes().size() : 0);
//            log.info("  - Total Edges: {}", analysisResult.getEdges() != null ? analysisResult.getEdges().size() : 0);
//
//            if (analysisResult.getMetadata() != null) {
//                log.info("  - Analysis Duration: {} ms", analysisResult.getMetadata().getAnalysisDurationMs());
//                log.info("  - Files Processed: {}", analysisResult.getMetadata().getFilesProcessed());
//            }
//        }

        return analysisResult;
    }

    /**
     * Health check for the Spoon service
     */
    public boolean isHealthy() {
        try {
            // Try a simple GET request to the base URL to check connectivity
            webClient.get()
                    .uri("/")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            return true;
        } catch (Exception e) {
            log.warn("Spoon service health check failed", e);
            return false;
        }
    }
}
