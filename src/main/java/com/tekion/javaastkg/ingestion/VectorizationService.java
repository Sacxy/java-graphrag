package com.tekion.javaastkg.ingestion;


import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
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
    
    @org.springframework.beans.factory.annotation.Value("${vectorization.force-recreate-indexes:true}")
    private boolean forceRecreateIndexes;

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
        vectorizeMethodNodes();
        vectorizeClassNodes();
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
     * Vectorizes Method nodes without embeddings
     */
    public void vectorizeMethodNodes() {
        List<MethodToVectorize> methods = getMethodsWithoutEmbeddings();
        log.info("Found {} methods to vectorize", methods.size());

        if (methods.isEmpty()) {
            return;
        }

        // Process in batches for efficiency
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < methods.size(); i += batchSize) {
            List<MethodToVectorize> batch = methods.subList(i,
                    Math.min(i + batchSize, methods.size()));

            CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                    processMethodBatch(batch), executorService);
            futures.add(future);
        }

        // Wait for all batches to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Vectorization completed for {} methods", methods.size());
    }
    
    /**
     * Vectorizes Class nodes without embeddings
     */
    public void vectorizeClassNodes() {
        List<ClassToVectorize> classes = getClassesWithoutEmbeddings();
        log.info("Found {} classes to vectorize", classes.size());

        if (classes.isEmpty()) {
            return;
        }

        // Process in batches for efficiency
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < classes.size(); i += batchSize) {
            List<ClassToVectorize> batch = classes.subList(i,
                    Math.min(i + batchSize, classes.size()));

            CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                    processClassBatch(batch), executorService);
            futures.add(future);
        }

        // Wait for all batches to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Vectorization completed for {} classes", classes.size());
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
        createMethodVectorIndex();
        createClassVectorIndex();
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
     * Creates vector index for Method nodes
     */
    private void createMethodVectorIndex() {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            // Check if index exists
            String checkQuery = "SHOW INDEXES WHERE name = 'method_embeddings'";
            List<Record> existing = session.run(checkQuery).list();

            if (!existing.isEmpty()) {
                if (forceRecreateIndexes) {
                    log.info("Method vector index already exists, dropping and recreating...");
                    try {
                        session.run("DROP INDEX method_embeddings").consume();
                        log.info("Dropped existing method vector index");
                        // Wait a bit for the index to be fully dropped
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        log.warn("Failed to drop existing method vector index: {}", e.getMessage());
                    }
                } else {
                    log.info("Method vector index already exists, skipping recreation");
                    return;
                }
            }

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
            log.info("Method vector index created successfully");
        } catch (Exception e) {
            log.error("Failed to create method vector index", e);
        }
    }
    
    /**
     * Creates vector index for Class nodes
     */
    private void createClassVectorIndex() {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            // Check if index exists
            String checkQuery = "SHOW INDEXES WHERE name = 'class_embeddings'";
            List<Record> existing = session.run(checkQuery).list();

            if (!existing.isEmpty()) {
                log.info("Class vector index already exists, dropping and recreating...");
                try {
                    session.run("DROP INDEX class_embeddings").consume();
                    log.info("Dropped existing class vector index");
                    // Wait a bit for the index to be fully dropped
                    Thread.sleep(1000);
                } catch (Exception e) {
                    log.warn("Failed to drop existing class vector index: {}", e.getMessage());
                }
            }

            log.info("Creating vector index 'class_embeddings'");
            String createIndexQuery = String.format("""
                CREATE VECTOR INDEX class_embeddings IF NOT EXISTS
                FOR (c:Class) ON (c.embedding)
                OPTIONS { 
                    indexConfig: {
                        `vector.dimensions`: %d,
                        `vector.similarity_function`: 'cosine'
                    }
                }
                """, embeddingDimension);

            session.run(createIndexQuery).consume();
            log.info("Class vector index created successfully");
        } catch (Exception e) {
            log.error("Failed to create class vector index", e);
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

            if (!existing.isEmpty()) {
                log.info("Method full-text index already exists, dropping and recreating...");
                try {
                    session.run("DROP INDEX method_names").consume();
                    log.info("Dropped existing method full-text index");
                    Thread.sleep(1000);
                } catch (Exception e) {
                    log.warn("Failed to drop existing method full-text index: {}", e.getMessage());
                }
            }

            log.info("Creating full-text index 'method_names'");
            String createIndexQuery = """
                CREATE FULLTEXT INDEX method_names IF NOT EXISTS
                FOR (m:Method) ON EACH [m.name, m.signature]
                """;
            session.run(createIndexQuery).consume();
            log.info("Method full-text index created successfully");
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

            if (!existing.isEmpty()) {
                log.info("Class full-text index already exists, dropping and recreating...");
                try {
                    session.run("DROP INDEX class_names").consume();
                    log.info("Dropped existing class full-text index");
                    Thread.sleep(1000);
                } catch (Exception e) {
                    log.warn("Failed to drop existing class full-text index: {}", e.getMessage());
                }
            }

            log.info("Creating full-text index 'class_names'");
            String createIndexQuery = """
                CREATE FULLTEXT INDEX class_names IF NOT EXISTS
                FOR (c:Class|Interface|Enum|AnnotationType) ON EACH [c.name, c.fullName]
                """;
            session.run(createIndexQuery).consume();
            log.info("Class full-text index created successfully");
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

            if (!existing.isEmpty()) {
                log.info("Description full-text index already exists, dropping and recreating...");
                try {
                    session.run("DROP INDEX description_content").consume();
                    log.info("Dropped existing description full-text index");
                    Thread.sleep(1000);
                } catch (Exception e) {
                    log.warn("Failed to drop existing description full-text index: {}", e.getMessage());
                }
            }

            log.info("Creating full-text index 'description_content'");
            String createIndexQuery = """
                CREATE FULLTEXT INDEX description_content IF NOT EXISTS
                FOR (d:Description) ON EACH [d.content]
                """;
            session.run(createIndexQuery).consume();
            log.info("Description full-text index created successfully");
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
     * Retrieves Method nodes that need vectorization
     */
    private List<MethodToVectorize> getMethodsWithoutEmbeddings() {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                MATCH (m:Method)
                WHERE m.embedding IS NULL
                OPTIONAL MATCH (m)-[:HAS_DESCRIPTION]->(d:Description)
                RETURN m.id as id,
                       m.name as name,
                       m.signature as signature,
                       m.className as className,
                       collect(d.content) as descriptions
                LIMIT 1000
                """;

            return session.run(query)
                    .list(record -> new MethodToVectorize(
                            record.get("id").asString(),
                            record.get("name").asString(),
                            record.get("signature").asString(),
                            record.get("className").asString(),
                            record.get("descriptions").asList(Value::asString)
                    ));
        }
    }
    
    /**
     * Retrieves Class nodes that need vectorization
     */
    private List<ClassToVectorize> getClassesWithoutEmbeddings() {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                MATCH (c:Class)
                WHERE c.embedding IS NULL
                OPTIONAL MATCH (c)-[:HAS_DESCRIPTION]->(d:Description)
                RETURN c.id as id,
                       c.name as name,
                       c.fullName as fullName,
                       c.packageName as packageName,
                       c.type as type,
                       collect(d.content) as descriptions
                LIMIT 1000
                """;

            return session.run(query)
                    .list(record -> new ClassToVectorize(
                            record.get("id").asString(),
                            record.get("name").asString(),
                            record.get("fullName").asString(),
                            record.get("packageName").asString(),
                            record.get("type").asString(),
                            record.get("descriptions").asList(Value::asString)
                    ));
        }
    }
    
    /**
     * Processes a batch of methods for vectorization
     */
    private void processMethodBatch(List<MethodToVectorize> batch) {
        log.debug("Processing method vectorization batch of {} items", batch.size());

        try {
            // Build embedding text for each method
            List<TextSegment> documents = batch.stream()
                    .map(this::buildMethodEmbeddingText)
                    .map(TextSegment::from)
                    .collect(Collectors.toList());

            // Generate embeddings in batch
            List<Embedding> embeddings = documentEmbeddingModel.embedAll(documents).content();

            // Store embeddings in Neo4j
            storeMethodEmbeddings(batch, embeddings);

        } catch (Exception e) {
            log.error("Failed to process method vectorization batch", e);
        }
    }
    
    /**
     * Processes a batch of classes for vectorization
     */
    private void processClassBatch(List<ClassToVectorize> batch) {
        log.debug("Processing class vectorization batch of {} items", batch.size());

        try {
            // Build embedding text for each class
            List<TextSegment> documents = batch.stream()
                    .map(this::buildClassEmbeddingText)
                    .map(TextSegment::from)
                    .collect(Collectors.toList());

            // Generate embeddings in batch
            List<Embedding> embeddings = documentEmbeddingModel.embedAll(documents).content();

            // Store embeddings in Neo4j
            storeClassEmbeddings(batch, embeddings);

        } catch (Exception e) {
            log.error("Failed to process class vectorization batch", e);
        }
    }
    
    /**
     * Builds embedding text for a method node
     */
    private String buildMethodEmbeddingText(MethodToVectorize method) {
        StringBuilder text = new StringBuilder();
        text.append("Method: ").append(method.getName())
            .append(" in class ").append(method.getClassName())
            .append(". Signature: ").append(method.getSignature());
            
        if (method.getDescriptions() != null && !method.getDescriptions().isEmpty()) {
            text.append(". Description: ");
            text.append(String.join(" ", method.getDescriptions()));
        }
        
        return text.toString();
    }
    
    /**
     * Builds embedding text for a class node
     */
    private String buildClassEmbeddingText(ClassToVectorize classNode) {
        StringBuilder text = new StringBuilder();
        text.append("Class: ").append(classNode.getName());
        
        if (classNode.getPackageName() != null) {
            text.append(" in package ").append(classNode.getPackageName());
        }
        
        if (classNode.getType() != null) {
            text.append(". Type: ").append(classNode.getType());
        }
        
        if (classNode.getDescriptions() != null && !classNode.getDescriptions().isEmpty()) {
            text.append(". Description: ");
            text.append(String.join(" ", classNode.getDescriptions()));
        }
        
        return text.toString();
    }
    
    /**
     * Stores method embeddings in Neo4j in batch
     */
    private void storeMethodEmbeddings(List<MethodToVectorize> methods, List<Embedding> embeddings) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                UNWIND $updates AS update
                MATCH (m:Method {id: update.id})
                SET m.embedding = update.embedding,
                    m.embeddingText = update.embeddingText,
                    m.vectorizedAt = datetime()
                RETURN count(m) as updated
                """;

            List<Map<String, Object>> updates = new ArrayList<>();
            for (int i = 0; i < methods.size(); i++) {
                updates.add(Map.of(
                        "id", methods.get(i).getId(),
                        "embedding", embeddings.get(i).vector(),
                        "embeddingText", buildMethodEmbeddingText(methods.get(i))
                ));
            }

            session.run(query, Map.of("updates", updates)).consume();
            log.debug("Stored {} method embeddings in Neo4j", updates.size());
        }
    }
    
    /**
     * Stores class embeddings in Neo4j in batch
     */
    private void storeClassEmbeddings(List<ClassToVectorize> classes, List<Embedding> embeddings) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                UNWIND $updates AS update
                MATCH (c:Class {id: update.id})
                SET c.embedding = update.embedding,
                    c.embeddingText = update.embeddingText,
                    c.vectorizedAt = datetime()
                RETURN count(c) as updated
                """;

            List<Map<String, Object>> updates = new ArrayList<>();
            for (int i = 0; i < classes.size(); i++) {
                updates.add(Map.of(
                        "id", classes.get(i).getId(),
                        "embedding", embeddings.get(i).vector(),
                        "embeddingText", buildClassEmbeddingText(classes.get(i))
                ));
            }

            session.run(query, Map.of("updates", updates)).consume();
            log.debug("Stored {} class embeddings in Neo4j", updates.size());
        }
    }

    /**
     * Data class for methods to vectorize
     */
    @Data
    @AllArgsConstructor
    private static class MethodToVectorize {
        private String id;
        private String name;
        private String signature;
        private String className;
        private List<String> descriptions;
    }
    
    /**
     * Data class for classes to vectorize
     */
    @Data
    @AllArgsConstructor
    private static class ClassToVectorize {
        private String id;
        private String name;
        private String fullName;
        private String packageName;
        private String type;
        private List<String> descriptions;
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
