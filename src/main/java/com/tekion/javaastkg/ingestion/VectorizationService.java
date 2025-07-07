package com.tekion.javaastkg.ingestion;


import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Service responsible for generating and storing vector embeddings for enriched methods.
 * Uses batching and parallel processing for efficiency.
 */
@Service
@Slf4j
public class VectorizationService {

    private final Driver neo4jDriver;
    private final SessionConfig sessionConfig;
    private final EmbeddingModel embeddingModel;
    private final ExecutorService executorService;

    @org.springframework.beans.factory.annotation.Value("${ingestion.batch.size:50}")
    private int batchSize;

    @org.springframework.beans.factory.annotation.Value("${llm.voyage.dimension}")
    private int embeddingDimension;

    @Autowired
    public VectorizationService(Driver neo4jDriver,
                                SessionConfig sessionConfig,
                                @Qualifier("documentEmbeddingModel") EmbeddingModel embeddingModel) {
        this.neo4jDriver = neo4jDriver;
        this.sessionConfig = sessionConfig;
        this.embeddingModel = embeddingModel;
        this.executorService = Executors.newFixedThreadPool(3);
    }

    /**
     * Vectorizes all enriched methods that don't have embeddings yet
     */
    public void vectorizeEnrichedMethods() {
        List<MethodToVectorize> methods = getEnrichedMethodsWithoutEmbeddings();
        log.info("Found {} enriched methods to vectorize", methods.size());

        if (methods.isEmpty()) {
            createVectorIndexIfNeeded();
            return;
        }

        // Process in batches for efficiency
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < methods.size(); i += batchSize) {
            List<MethodToVectorize> batch = methods.subList(i,
                    Math.min(i + batchSize, methods.size()));

            CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                    processBatch(batch), executorService);
            futures.add(future);
        }

        // Wait for all batches to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Create vector index after all embeddings are stored
        createVectorIndexIfNeeded();

        log.info("Vectorization completed for {} methods", methods.size());
    }

    /**
     * Retrieves enriched methods that need vectorization
     */
    private List<MethodToVectorize> getEnrichedMethodsWithoutEmbeddings() {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                MATCH (m:Method)
                WHERE m.summary IS NOT NULL
                  AND m.embedding IS NULL
                RETURN m.id as id,
                       m.signature as signature,
                       m.name as name,
                       m.summary as summary,
                       m.detailedExplanation as explanation,
                       m.businessTags as businessTags,
                       m.technicalTags as technicalTags,
                       COALESCE(m.returnType, 'void') as returnType,
                       COALESCE(m.className, 'Unknown') as className,
                       COALESCE(m.isStatic, false) as isStatic,
                       COALESCE(m.isPublic, true) as isPublic,
                       COALESCE(m.isAbstract, false) as isAbstract,
                       COALESCE(m.parameterCount, 0) as parameterCount,
                       COALESCE(m.complexity, 'unknown') as complexity
                LIMIT 1000
                """;

            return session.run(query)
                    .list(record -> new MethodToVectorize(
                            record.get("id").asString(),
                            record.get("signature").asString(),
                            record.get("name").asString(),
                            record.get("summary").asString(),
                            record.get("explanation").asString(),
                            record.get("businessTags").asList(Value::asString),
                            record.get("technicalTags").asList(Value::asString),
                            record.get("returnType").asString(),
                            record.get("className").asString(),
                            record.get("isStatic").asBoolean(),
                            record.get("isPublic").asBoolean(),
                            record.get("isAbstract").asBoolean(),
                            record.get("parameterCount").asInt(),
                            record.get("complexity").asString()
                    ));
        }
    }

    /**
     * Processes a batch of methods for vectorization
     */
    private void processBatch(List<MethodToVectorize> batch) {
        log.debug("Processing vectorization batch of {} methods", batch.size());

        try {
            // Create composite documents for each method
            List<TextSegment> documents = batch.stream()
                    .map(this::createCompositeDocument)
                    .collect(Collectors.toList());

            // Generate embeddings in batch
            List<Embedding> embeddings = embeddingModel.embedAll(documents).content();

            // Store embeddings in Neo4j
            storeEmbeddings(batch, embeddings);

        } catch (Exception e) {
            log.error("Failed to process vectorization batch", e);
        }
    }

    /**
     * Creates a composite document that captures all relevant information about a method
     */
    private TextSegment createCompositeDocument(MethodToVectorize method) {
        StringBuilder doc = new StringBuilder();

        // Include structured information
        doc.append("Method: ").append(method.name).append("\n");
        doc.append("Class: ").append(method.className).append("\n");
        doc.append("Signature: ").append(method.signature).append("\n");
        doc.append("Returns: ").append(method.returnType).append("\n");
        doc.append("Parameters: ").append(method.parameterCount).append("\n");
        doc.append("Complexity: ").append(method.complexity).append("\n");
        
        // Include method modifiers
        if (method.isStatic) doc.append("Static method\n");
        if (method.isAbstract) doc.append("Abstract method\n");
        if (!method.isPublic) doc.append("Non-public method\n");

        // Include semantic information
        doc.append("Summary: ").append(method.summary).append("\n");
        doc.append("Description: ").append(method.explanation).append("\n");

        // Include tags for better semantic matching
        if (!method.businessTags.isEmpty()) {
            doc.append("Business Context: ").append(String.join(", ", method.businessTags)).append("\n");
        }
        if (!method.technicalTags.isEmpty()) {
            doc.append("Technical Tags: ").append(String.join(", ", method.technicalTags)).append("\n");
        }
        return TextSegment.from(doc.toString());
    }

    /**
     * Stores embeddings in Neo4j in batch
     */
    private void storeEmbeddings(List<MethodToVectorize> methods, List<Embedding> embeddings) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                UNWIND $updates AS update
                MATCH (m:Method {id: update.id})
                SET m.embedding = update.embedding,
                    m.vectorizedAt = datetime()
                RETURN count(m) as updated
                """;

            List<Map<String, Object>> updates = new ArrayList<>();
            for (int i = 0; i < methods.size(); i++) {
                updates.add(Map.of(
                        "id", methods.get(i).getId(),
                        "embedding", embeddings.get(i).vector()
                ));
            }

            session.run(query, Map.of("updates", updates)).consume();
            log.debug("Stored {} embeddings in Neo4j", updates.size());
        }
    }

    /**
     * Creates a vector index in Neo4j for similarity search
     */
    private void createVectorIndexIfNeeded() {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            // Check if index exists
            String checkQuery = "SHOW INDEXES WHERE name = 'method_embeddings'";
            List<Record> existing = session.run(checkQuery).list();

            if (existing.isEmpty()) {
                log.info("Creating vector index 'method_embeddings'");

                String createIndexQuery = String.format("""
                    CREATE VECTOR INDEX method_embeddings IF NOT EXISTS
                    FOR (m:Method) ON (m.embedding)
                    OPTIONS { 
                        indexConfig: {
                            `vector.dimensions`: %d,
                            `vector.similarity_function`: 'cosine'
                        }
                    }
                    """, embeddingDimension);

                session.run(createIndexQuery).consume();
                log.info("Vector index created successfully");
            } else {
                log.info("Vector index already exists");
            }
        } catch (Exception e) {
            log.error("Failed to create vector index", e);
        }
    }

    /**
     * Data class for methods to vectorize
     */
    @Data
    @AllArgsConstructor
    private static class MethodToVectorize {
        private String id;
        private String signature;
        private String name;
        private String summary;
        private String explanation;
        private List<String> businessTags;
        private List<String> technicalTags;
        private String returnType;
        private String className;
        private boolean isStatic;
        private boolean isPublic;
        private boolean isAbstract;
        private int parameterCount;
        private String complexity;
    }
}
