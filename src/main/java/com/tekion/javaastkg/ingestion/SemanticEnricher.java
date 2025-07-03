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
            // First, let's check what properties are available
            String checkQuery = "MATCH (m:Method) RETURN m LIMIT 1";
            Result checkResult = session.run(checkQuery);
            if (checkResult.hasNext()) {
                log.debug("Sample Method node properties: {}", checkResult.single().get("m").asNode().asMap());
            }
            
            // Updated query for new graph structure
            // Since properties are stored in the properties map, we need to check if summary exists
            String query = """
                MATCH (m:Method)
                WHERE m.summary IS NULL
                OPTIONAL MATCH (m)<-[:CONTAINS|HAS_METHOD]-(c)
                WHERE c:Class OR c:Interface
                RETURN m.id as id,
                       m.properties.signature as signature, 
                       m.properties.name as name,
                       m.properties.startLine as startLine, 
                       m.properties.endLine as endLine,
                       m.sourceFile as filePath,
                       c.properties.fullName as className
                LIMIT 1000
                """;

            List<MethodToEnrich> methods = session.run(query)
                    .list(record -> {
                        try {
                            // Handle potential nulls from properties map
                            String signature = record.get("signature").isNull() ? 
                                "unknown_" + record.get("id").asString() : record.get("signature").asString();
                            String name = record.get("name").isNull() ? 
                                "unknown" : record.get("name").asString();
                            int startLine = record.get("startLine").isNull() ? 
                                0 : record.get("startLine").asInt();
                            int endLine = record.get("endLine").isNull() ? 
                                0 : record.get("endLine").asInt();
                            String filePath = record.get("filePath").isNull() ? 
                                "" : record.get("filePath").asString();
                            String className = record.get("className").isNull() ? 
                                "Unknown" : record.get("className").asString();
                            
                            return new MethodToEnrich(signature, name, startLine, endLine, filePath, className);
                        } catch (Exception e) {
                            log.warn("Failed to process method record: {}", e.getMessage());
                            return null;
                        }
                    })
                    .stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            
            log.info("Found {} unenriched methods", methods.size());
            return methods;
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
        Path filePath = Path.of(method.filePath);

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
            // First try to match by properties.signature, then fall back to direct match
            String query = """
                MATCH (m:Method)
                WHERE m.properties.signature = $signature OR m.signature = $signature
                SET m.summary = $summary,
                    m.detailedExplanation = $detailedExplanation,
                    m.businessTags = $businessTags,
                    m.technicalTags = $technicalTags,
                    m.complexity = $complexity,
                    m.dependencies = $dependencies,
                    m.enrichedAt = datetime()
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
