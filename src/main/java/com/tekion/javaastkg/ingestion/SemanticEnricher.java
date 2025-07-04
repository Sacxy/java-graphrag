package com.tekion.javaastkg.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.neo4j.driver.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.core.io.ClassPathResource;
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
    private final String semanticEnrichmentPrompt;

    @Value("${project.source.path}")
    private String sourcePath;

    @Value("${ingestion.batch.size:50}")
    private int batchSize;
    
    @Value("${enrichment.delay.ms:1000}")
    private long delayBetweenCalls;
    
    @Value("${enrichment.max.concurrent:2}")
    private int maxConcurrentCalls;

    @Autowired
    public SemanticEnricher(Driver neo4jDriver,
                            SessionConfig sessionConfig,
                            @Qualifier("semanticEnricherModel") ChatLanguageModel llm,
                            @Value("${enrichment.max.concurrent:2}") int maxConcurrentCalls) {
        this.neo4jDriver = neo4jDriver;
        this.sessionConfig = sessionConfig;
        this.llm = llm;
        this.objectMapper = new ObjectMapper();
        this.maxConcurrentCalls = maxConcurrentCalls;
        // Reduce concurrent threads to avoid rate limits
        this.executorService = Executors.newFixedThreadPool(maxConcurrentCalls);
        // Load prompt template from resources
        this.semanticEnrichmentPrompt = loadPromptTemplate("prompts/semantic-enrichment.txt");
    }

    /**
     * Loads a prompt template from the classpath
     */
    private String loadPromptTemplate(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return FileUtils.readFileToString(resource.getFile(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load prompt template from {}", path, e);
            throw new RuntimeException("Failed to load prompt template", e);
        }
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

        // Process in smaller batches to avoid rate limits
        int effectiveBatchSize = Math.min(batchSize, 10); // Smaller batches for rate limiting
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        log.info("Processing {} methods in batches of {} with {} concurrent workers", 
                methods.size(), effectiveBatchSize, maxConcurrentCalls);

        for (int i = 0; i < methods.size(); i += effectiveBatchSize) {
            List<MethodToEnrich> batch = methods.subList(i,
                    Math.min(i + effectiveBatchSize, methods.size()));

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
                log.info("Sample Method node properties: {}", checkResult.single().get("m").asNode().asMap());
            }
            
            // Updated query - all properties are flattened on the node, not in a nested properties map
            String query = """
                MATCH (m:Method)
                WHERE m.summary IS NULL
                RETURN m.id as id,
                       m.signature as signature,
                       m.name as name,
                       m.startLine as startLine,
                       m.endLine as endLine,
                       m.filePath as filePath,
                       m.className as className,
                       m.returnType as returnType,
                       m.isStatic as isStatic,
                       m.isPublic as isPublic,
                       m.isAbstract as isAbstract,
                       m.parameterCount as parameterCount
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
                            
                            String id = record.get("id").isNull() ? 
                                "unknown_" + signature : record.get("id").asString();
                            String returnType = record.get("returnType").isNull() ? "void" : record.get("returnType").asString();
                            boolean isStatic = record.get("isStatic").isNull() ? false : record.get("isStatic").asBoolean();
                            boolean isPublic = record.get("isPublic").isNull() ? true : record.get("isPublic").asBoolean();
                            boolean isAbstract = record.get("isAbstract").isNull() ? false : record.get("isAbstract").asBoolean();
                            int parameterCount = record.get("parameterCount").isNull() ? 0 : record.get("parameterCount").asInt();
                            
                            return new MethodToEnrich(id, signature, name, startLine, endLine, filePath, className,
                                    returnType, isStatic, isPublic, isAbstract, parameterCount);
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
        log.info("Processing batch of {} methods", batch.size());

        for (MethodToEnrich method : batch) {
            try {
                // Read method code
                String code = readMethodCode(method);

                // Get enrichment from LLM
                EnrichmentResult enrichment = callLLMForEnrichment(method, code);

                // Update Neo4j  
                updateMethodNode(method.id, enrichment);

                log.info("Successfully enriched method: {}", method.signature);
                
                // Add delay between calls to respect rate limits
                if (delayBetweenCalls > 0) {
                    Thread.sleep(delayBetweenCalls);
                }

            } catch (dev.ai4j.openai4j.OpenAiHttpException e) {
                if (e.getMessage().contains("rate_limit_exceeded")) {
                    log.warn("Rate limit hit, waiting before retry: {}", e.getMessage());
                    try {
                        // Extract wait time from error message if available
                        String msg = e.getMessage();
                        if (msg.contains("Please try again in")) {
                            String waitTime = msg.substring(msg.indexOf("Please try again in") + 20);
                            waitTime = waitTime.substring(0, waitTime.indexOf("."));
                            long waitMs = Long.parseLong(waitTime.replaceAll("[^0-9]", ""));
                            log.info("Waiting {} ms as suggested by API", waitMs);
                            Thread.sleep(waitMs + 100); // Add buffer
                        } else {
                            // Default wait time
                            Thread.sleep(60000); // 1 minute
                        }
                        // Retry once after waiting
                        String code2 = readMethodCode(method);
                        EnrichmentResult enrichment = callLLMForEnrichment(method, code2);
                        updateMethodNode(method.id, enrichment);
                    } catch (Exception retryError) {
                        log.error("Failed to enrich method after retry: {}", method.signature, retryError);
                    }
                } else {
                    log.error("Failed to enrich method: {}", method.signature, e);
                }
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
        String prompt = String.format(semanticEnrichmentPrompt, method.className, method.name, code);

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
                    .build();
        }
    }

    /**
     * Updates the method node in Neo4j with enrichment data
     */
    private void updateMethodNode(String methodId, EnrichmentResult enrichment) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            // Match by unique ID which is more reliable
            String query = """
                MATCH (m:Method {id: $methodId})
                SET m.summary = $summary,
                    m.detailedExplanation = $detailedExplanation,
                    m.businessTags = $businessTags,
                    m.technicalTags = $technicalTags,
                    m.complexity = $complexity,
                    m.enrichedAt = datetime()
                RETURN count(m) as updated
                """;

            Result result = session.run(query, Map.of(
                    "methodId", methodId,
                    "summary", enrichment.getSummary(),
                    "detailedExplanation", enrichment.getDetailedExplanation(),
                    "businessTags", enrichment.getBusinessTags(),
                    "technicalTags", enrichment.getTechnicalTags(),
                    "complexity", enrichment.getComplexity()
            ));
            
            int updatedCount = result.single().get("updated").asInt();
            if (updatedCount > 0) {
                log.info("Successfully updated method: {}", methodId);
            } else {
                log.warn("No method found with ID: {}", methodId);
            }
        }
    }

    /**
     * Data class for methods to enrich
     */
    @Data
    @AllArgsConstructor
    private static class MethodToEnrich {
        private String id;
        private String signature;
        private String name;
        private int startLine;
        private int endLine;
        private String filePath;
        private String className;
        private String returnType;
        private boolean isStatic;
        private boolean isPublic;
        private boolean isAbstract;
        private int parameterCount;
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
    }
}
