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
 * Service responsible for generating and storing vector embeddings for Description and FileDoc nodes.
 * Uses batching and parallel processing for efficiency.
 */
@Service
@Slf4j
public class VectorizationService {

    private final Driver neo4jDriver;
    private final SessionConfig sessionConfig;
    private final EmbeddingModel documentEmbeddingModel;
    private final ExecutorService executorService;

    @org.springframework.beans.factory.annotation.Value("${ingestion.batch.size:50}")
    private int batchSize;

    @org.springframework.beans.factory.annotation.Value("${llm.voyage.dimension:1024}")
    private int embeddingDimension;

    @Autowired
    public VectorizationService(Driver neo4jDriver,
                                SessionConfig sessionConfig,
                                @Qualifier("documentEmbeddingModel") EmbeddingModel documentEmbeddingModel) {
        this.neo4jDriver = neo4jDriver;
        this.sessionConfig = sessionConfig;
        this.documentEmbeddingModel = documentEmbeddingModel;
        this.executorService = Executors.newFixedThreadPool(3);
    }

    /**
     * Vectorizes all Description and FileDoc nodes that don't have embeddings yet
     */
    public void vectorizeDocuments() {
        vectorizeDescriptions();
        vectorizeFileDocs();
        createVectorIndexesIfNeeded();
    }
    
    /**
     * Vectorizes Description nodes without embeddings
     */
    public void vectorizeDescriptions() {
        List<DocumentToVectorize> descriptions = getDescriptionsWithoutEmbeddings();
        log.info("Found {} descriptions to vectorize", descriptions.size());

        if (descriptions.isEmpty()) {
            return;
        }

        // Process in batches for efficiency
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < descriptions.size(); i += batchSize) {
            List<DocumentToVectorize> batch = descriptions.subList(i,
                    Math.min(i + batchSize, descriptions.size()));

            CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                    processDescriptionBatch(batch), executorService);
            futures.add(future);
        }

        // Wait for all batches to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Vectorization completed for {} descriptions", descriptions.size());
    }
    
    /**
     * Vectorizes FileDoc nodes without embeddings
     */
    public void vectorizeFileDocs() {
        List<DocumentToVectorize> fileDocs = getFileDocsWithoutEmbeddings();
        log.info("Found {} file docs to vectorize", fileDocs.size());

        if (fileDocs.isEmpty()) {
            return;
        }

        // Process in batches for efficiency
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < fileDocs.size(); i += batchSize) {
            List<DocumentToVectorize> batch = fileDocs.subList(i,
                    Math.min(i + batchSize, fileDocs.size()));

            CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                    processFileDocBatch(batch), executorService);
            futures.add(future);
        }

        // Wait for all batches to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Vectorization completed for {} file docs", fileDocs.size());
    }

    /**
     * Retrieves Description nodes that need vectorization
     */
    private List<DocumentToVectorize> getDescriptionsWithoutEmbeddings() {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                MATCH (d:Description)
                WHERE d.embedding IS NULL
                  AND d.content IS NOT NULL
                RETURN d.id as id,
                       d.content as content,
                       d.type as type,
                       d.sourceFile as sourceFile
                LIMIT 1000
                """;

            return session.run(query)
                    .list(record -> new DocumentToVectorize(
                            record.get("id").asString(),
                            record.get("content").asString(),
                            record.get("type").asString(),
                            record.get("sourceFile").asString(),
                            "Description"
                    ));
        }
    }
    
    /**
     * Retrieves FileDoc nodes that need vectorization
     */
    private List<DocumentToVectorize> getFileDocsWithoutEmbeddings() {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                MATCH (f:FileDoc)
                WHERE f.embedding IS NULL
                  AND f.content IS NOT NULL
                RETURN f.id as id,
                       f.content as content,
                       f.fileName as fileName,
                       f.packageName as packageName
                LIMIT 1000
                """;

            return session.run(query)
                    .list(record -> new DocumentToVectorize(
                            record.get("id").asString(),
                            record.get("content").asString(),
                            record.get("fileName").asString(),
                            record.get("packageName").asString(),
                            "FileDoc"
                    ));
        }
    }

    /**
     * Processes a batch of descriptions for vectorization
     */
    private void processDescriptionBatch(List<DocumentToVectorize> batch) {
        log.debug("Processing description vectorization batch of {} items", batch.size());

        try {
            // Create text segments for each description
            List<TextSegment> documents = batch.stream()
                    .map(doc -> TextSegment.from(doc.getContent()))
                    .collect(Collectors.toList());

            // Generate embeddings in batch
            List<Embedding> embeddings = documentEmbeddingModel.embedAll(documents).content();

            // Store embeddings in Neo4j
            storeDescriptionEmbeddings(batch, embeddings);

        } catch (Exception e) {
            log.error("Failed to process description vectorization batch", e);
        }
    }
    
    /**
     * Processes a batch of file docs for vectorization
     */
    private void processFileDocBatch(List<DocumentToVectorize> batch) {
        log.debug("Processing file doc vectorization batch of {} items", batch.size());

        try {
            // Create text segments for each file doc
            List<TextSegment> documents = batch.stream()
                    .map(doc -> TextSegment.from(doc.getContent()))
                    .collect(Collectors.toList());

            // Generate embeddings in batch
            List<Embedding> embeddings = documentEmbeddingModel.embedAll(documents).content();

            // Store embeddings in Neo4j
            storeFileDocEmbeddings(batch, embeddings);

        } catch (Exception e) {
            log.error("Failed to process file doc vectorization batch", e);
        }
    }

    /**
     * Stores description embeddings in Neo4j in batch
     */
    private void storeDescriptionEmbeddings(List<DocumentToVectorize> descriptions, List<Embedding> embeddings) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                UNWIND $updates AS update
                MATCH (d:Description {id: update.id})
                SET d.embedding = update.embedding,
                    d.vectorizedAt = datetime()
                RETURN count(d) as updated
                """;

            List<Map<String, Object>> updates = new ArrayList<>();
            for (int i = 0; i < descriptions.size(); i++) {
                updates.add(Map.of(
                        "id", descriptions.get(i).getId(),
                        "embedding", embeddings.get(i).vector()
                ));
            }

            session.run(query, Map.of("updates", updates)).consume();
            log.debug("Stored {} description embeddings in Neo4j", updates.size());
        }
    }
    
    /**
     * Stores file doc embeddings in Neo4j in batch
     */
    private void storeFileDocEmbeddings(List<DocumentToVectorize> fileDocs, List<Embedding> embeddings) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                UNWIND $updates AS update
                MATCH (f:FileDoc {id: update.id})
                SET f.embedding = update.embedding,
                    f.vectorizedAt = datetime()
                RETURN count(f) as updated
                """;

            List<Map<String, Object>> updates = new ArrayList<>();
            for (int i = 0; i < fileDocs.size(); i++) {
                updates.add(Map.of(
                        "id", fileDocs.get(i).getId(),
                        "embedding", embeddings.get(i).vector()
                ));
            }

            session.run(query, Map.of("updates", updates)).consume();
            log.debug("Stored {} file doc embeddings in Neo4j", updates.size());
        }
    }

    /**
     * Creates vector indexes in Neo4j for similarity search
     */
    private void createVectorIndexesIfNeeded() {
        createDescriptionVectorIndex();
        createFileDocVectorIndex();
        createFullTextIndexesIfNeeded();
    }
    
    /**
     * Creates full-text indexes for search functionality
     */
    private void createFullTextIndexesIfNeeded() {
        createMethodFullTextIndex();
        createClassFullTextIndex();
        createDescriptionFullTextIndex();
        createFileDocFullTextIndex();
    }
    
    /**
     * Creates vector index for Description nodes
     */
    private void createDescriptionVectorIndex() {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            // Check if index exists
            String checkQuery = "SHOW INDEXES WHERE name = 'description_embeddings'";
            List<Record> existing = session.run(checkQuery).list();

            if (existing.isEmpty()) {
                log.info("Creating vector index 'description_embeddings'");

                String createIndexQuery = String.format("""
                    CREATE VECTOR INDEX description_embeddings IF NOT EXISTS
                    FOR (d:Description) ON (d.embedding)
                    OPTIONS { 
                        indexConfig: {
                            `vector.dimensions`: %d,
                            `vector.similarity_function`: 'cosine'
                        }
                    }
                    """, embeddingDimension);

                session.run(createIndexQuery).consume();
                log.info("Description vector index created successfully");
            } else {
                log.info("Description vector index already exists");
            }
        } catch (Exception e) {
            log.error("Failed to create description vector index", e);
        }
    }
    
    /**
     * Creates vector index for FileDoc nodes
     */
    private void createFileDocVectorIndex() {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            // Check if index exists
            String checkQuery = "SHOW INDEXES WHERE name = 'file_doc_embeddings'";
            List<Record> existing = session.run(checkQuery).list();

            if (existing.isEmpty()) {
                log.info("Creating vector index 'file_doc_embeddings'");

                String createIndexQuery = String.format("""
                    CREATE VECTOR INDEX file_doc_embeddings IF NOT EXISTS
                    FOR (f:FileDoc) ON (f.embedding)
                    OPTIONS { 
                        indexConfig: {
                            `vector.dimensions`: %d,
                            `vector.similarity_function`: 'cosine'
                        }
                    }
                    """, embeddingDimension);

                session.run(createIndexQuery).consume();
                log.info("FileDoc vector index created successfully");
            } else {
                log.info("FileDoc vector index already exists");
            }
        } catch (Exception e) {
            log.error("Failed to create file doc vector index", e);
        }
    }

    /**
     * Creates full-text index for Method nodes
     */
    private void createMethodFullTextIndex() {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            // Check if index exists
            String checkQuery = "SHOW INDEXES WHERE name = 'method_names'";
            List<Record> existing = session.run(checkQuery).list();

            if (existing.isEmpty()) {
                log.info("Creating full-text index 'method_names'");
                String createIndexQuery = """
                    CREATE FULLTEXT INDEX method_names IF NOT EXISTS
                    FOR (m:Method) ON EACH [m.name, m.signature]
                    """;
                session.run(createIndexQuery).consume();
                log.info("Method full-text index created successfully");
            } else {
                log.info("Method full-text index already exists");
            }
        } catch (Exception e) {
            log.error("Failed to create method full-text index", e);
        }
    }
    
    /**
     * Creates full-text index for Class nodes
     */
    private void createClassFullTextIndex() {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            // Check if index exists
            String checkQuery = "SHOW INDEXES WHERE name = 'class_names'";
            List<Record> existing = session.run(checkQuery).list();

            if (existing.isEmpty()) {
                log.info("Creating full-text index 'class_names'");
                String createIndexQuery = """
                    CREATE FULLTEXT INDEX class_names IF NOT EXISTS
                    FOR (c:Class|Interface|Enum|AnnotationType) ON EACH [c.name, c.fullName]
                    """;
                session.run(createIndexQuery).consume();
                log.info("Class full-text index created successfully");
            } else {
                log.info("Class full-text index already exists");
            }
        } catch (Exception e) {
            log.error("Failed to create class full-text index", e);
        }
    }
    
    /**
     * Creates full-text index for Description nodes
     */
    private void createDescriptionFullTextIndex() {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            // Check if index exists
            String checkQuery = "SHOW INDEXES WHERE name = 'description_content'";
            List<Record> existing = session.run(checkQuery).list();

            if (existing.isEmpty()) {
                log.info("Creating full-text index 'description_content'");
                String createIndexQuery = """
                    CREATE FULLTEXT INDEX description_content IF NOT EXISTS
                    FOR (d:Description) ON EACH [d.content]
                    """;
                session.run(createIndexQuery).consume();
                log.info("Description full-text index created successfully");
            } else {
                log.info("Description full-text index already exists");
            }
        } catch (Exception e) {
            log.error("Failed to create description full-text index", e);
        }
    }
    
    /**
     * Creates full-text index for FileDoc nodes
     */
    private void createFileDocFullTextIndex() {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            // Check if index exists
            String checkQuery = "SHOW INDEXES WHERE name = 'file_doc_content'";
            List<Record> existing = session.run(checkQuery).list();

            if (existing.isEmpty()) {
                log.info("Creating full-text index 'file_doc_content'");
                String createIndexQuery = """
                    CREATE FULLTEXT INDEX file_doc_content IF NOT EXISTS
                    FOR (f:FileDoc) ON EACH [f.content, f.fileName]
                    """;
                session.run(createIndexQuery).consume();
                log.info("FileDoc full-text index created successfully");
            } else {
                log.info("FileDoc full-text index already exists");
            }
        } catch (Exception e) {
            log.error("Failed to create file doc full-text index", e);
        }
    }

    /**
     * Data class for documents to vectorize
     */
    @Data
    @AllArgsConstructor
    private static class DocumentToVectorize {
        private String id;
        private String content;
        private String metadata1; // type/fileName
        private String metadata2; // sourceFile/packageName
        private String nodeType; // "Description" or "FileDoc"
    }
}
