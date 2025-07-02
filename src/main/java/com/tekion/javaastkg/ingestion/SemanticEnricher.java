package com.tekion.javaastkg.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.neo4j.driver.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Service responsible for enriching method nodes with semantic information using LLMs.
 * Processes methods in parallel for efficiency.
 */
@Service
@Slf4j
public class SemanticEnricher {

    private final Driver neo4jDriver;
    private final SessionConfig sessionConfig;
    private final ChatLanguageModel llm;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;

    @Value("${project.source.path}")
    private String sourcePath;

    @Value("${ingestion.batch.size:50}")
    private int batchSize;

    @Autowired
    public SemanticEnricher(Driver neo4jDriver,
                            SessionConfig sessionConfig,
                            ChatLanguageModel llm) {
        this.neo4jDriver = neo4jDriver;
        this.sessionConfig = sessionConfig;
        this.llm = llm;
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newFixedThreadPool(5);
    }

    /**
     * Enriches all unenriched methods with semantic information
     */
    public void enrichMethods() {
        List<MethodToEnrich> methods = findUnenrichedMethods();
        log.info("Found {} methods to enrich semantically", methods.size());

        if (methods.isEmpty()) {
            return;
        }

        // Process in batches
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < methods.size(); i += batchSize) {
            List<MethodToEnrich> batch = methods.subList(i,
                    Math.min(i + batchSize, methods.size()));

            CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                    processBatch(batch), executorService);
            futures.add(future);
        }

        // Wait for all batches to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Semantic enrichment completed for {} methods", methods.size());
    }

    /**
     * Finds all methods that haven't been enriched yet
     */
    private List<MethodToEnrich> findUnenrichedMethods() {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                MATCH (m:Method)-[:DEFINED_IN]->(c:Class)
                WHERE m.summary IS NULL
                RETURN m.signature as signature, 
                       m.name as name,
                       m.startLine as startLine, 
                       m.endLine as endLine,
                       c.filePath as filePath,
                       c.fullName as className
                LIMIT 1000
                """;

            return session.run(query)
                    .list(record -> new MethodToEnrich(
                            record.get("signature").asString(),
                            record.get("name").asString(),
                            record.get("startLine").asInt(),
                            record.get("endLine").asInt(),
                            record.get("filePath").asString(),
                            record.get("className").asString()
                    ));
        }
    }

    /**
     * Processes a batch of methods for enrichment
     */
    private void processBatch(List<MethodToEnrich> batch) {
        log.debug("Processing batch of {} methods", batch.size());

        for (MethodToEnrich method : batch) {
            try {
                // Read method code
                String code = readMethodCode(method);

                // Get enrichment from LLM
                EnrichmentResult enrichment = callLLMForEnrichment(method, code);

                // Update Neo4j
                updateMethodNode(method.signature, enrichment);

                log.debug("Successfully enriched method: {}", method.signature);

            } catch (Exception e) {
                log.error("Failed to enrich method: {}", method.signature, e);
            }
        }
    }

    /**
     * Reads the actual method code from the source file
     */
    private String readMethodCode(MethodToEnrich method) throws IOException {
        Path filePath = Paths.get(sourcePath, method.filePath);

        if (!Files.exists(filePath)) {
            log.warn("Source file not found: {}", filePath);
            return "// Source code not available";
        }

        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);

        // Extract method lines (adjust for 0-based indexing)
        int startIdx = Math.max(0, method.startLine - 1);
        int endIdx = Math.min(lines.size(), method.endLine);

        return lines.subList(startIdx, endIdx)
                .stream()
                .collect(Collectors.joining("\n"));
    }

    /**
     * Calls the LLM to get semantic enrichment for the method
     */
    private EnrichmentResult callLLMForEnrichment(MethodToEnrich method, String code) {
        String prompt = String.format("""
            You are an expert Java software architect analyzing code for a knowledge graph.
            
            Analyze this Java method and provide insights:
            
            Class: %s
            Method: %s
            
            Code:
            ```java
            %s
            ```
            
            Provide a JSON response with EXACTLY this structure:
            {
                "summary": "One sentence describing the business purpose",
                "detailedExplanation": "A paragraph explaining the logic step-by-step",
                "businessTags": ["tag1", "tag2", "tag3"],
                "technicalTags": ["tag1", "tag2"],
                "complexity": "low|medium|high",
                "dependencies": ["external service or library names if any"]
            }
            
            Focus on:
            1. What business problem this method solves
            2. Key algorithms or patterns used
            3. External dependencies or integrations
            4. Error handling approach
            
            Respond ONLY with valid JSON, no additional text.
            """, method.className, method.name, code);

        String response = "";
        try {
            response = llm.generate(prompt);

            // Clean up response to ensure it's valid JSON
            response = response.trim();
            if (response.startsWith("```json")) {
                response = response.substring(7);
            }
            if (response.endsWith("```")) {
                response = response.substring(0, response.length() - 3);
            }

            // Parse JSON response
            return objectMapper.readValue(response, EnrichmentResult.class);

        } catch (Exception e) {
            log.error("Failed to parse LLM response for method: {}", method.signature, e);

            // Return default enrichment on failure
            return EnrichmentResult.builder()
                    .summary("Method " + method.name + " in " + method.className + "Response: " + response)
                    .detailedExplanation("Analysis pending")
                    .businessTags(List.of("unanalyzed"))
                    .technicalTags(List.of("java"))
                    .complexity("unknown")
                    .dependencies(List.of())
                    .build();
        }
    }

    /**
     * Updates the method node in Neo4j with enrichment data
     */
    private void updateMethodNode(String signature, EnrichmentResult enrichment) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                MATCH (m:Method {signature: $signature})
                SET m += {
                    summary: $summary,
                    detailedExplanation: $detailedExplanation,
                    businessTags: $businessTags,
                    technicalTags: $technicalTags,
                    complexity: $complexity,
                    dependencies: $dependencies,
                    enrichedAt: datetime()
                }
                """;

            session.run(query, Map.of(
                    "signature", signature,
                    "summary", enrichment.getSummary(),
                    "detailedExplanation", enrichment.getDetailedExplanation(),
                    "businessTags", enrichment.getBusinessTags(),
                    "technicalTags", enrichment.getTechnicalTags(),
                    "complexity", enrichment.getComplexity(),
                    "dependencies", enrichment.getDependencies()
            )).consume();
        }
    }

    /**
     * Data class for methods to enrich
     */
    @Data
    @AllArgsConstructor
    private static class MethodToEnrich {
        private String signature;
        private String name;
        private int startLine;
        private int endLine;
        private String filePath;
        private String className;
    }

    /**
     * Data class for LLM enrichment results
     */
    @Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EnrichmentResult {
        private String summary;
        private String detailedExplanation;
        private List<String> businessTags;
        private List<String> technicalTags;
        private String complexity;
        private List<String> dependencies;
    }
}
