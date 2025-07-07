package com.tekion.javaastkg.query.services;

import com.tekion.javaastkg.query.services.EntityExtractor.ExtractedEntities;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service that performs parallel full-text and vector search operations.
 * Combines entity-based full-text search with semantic vector search.
 */
@Service
@Slf4j
public class ParallelSearchService {

    private final Driver neo4jDriver;
    private final SessionConfig sessionConfig;

    @Value("${query.retrieval.fulltext-search-limit:50}")
    private int fullTextSearchLimit;

    @Value("${query.retrieval.vector-search-limit:50}")
    private int vectorSearchLimit;

    public ParallelSearchService(Driver neo4jDriver, SessionConfig sessionConfig) {
        this.neo4jDriver = neo4jDriver;
        this.sessionConfig = sessionConfig;
    }

    /**
     * Performs full-text search based on extracted entities
     */
    @Async
    public CompletableFuture<List<SearchResult>> fullTextSearch(ExtractedEntities entities) {
        log.debug("Starting full-text search with entities: {}", entities);
        
        List<SearchResult> results = new ArrayList<>();
        
        try (Session session = neo4jDriver.session(sessionConfig)) {
            // Search methods
            if (!entities.getMethods().isEmpty()) {
                results.addAll(searchMethods(session, entities.getMethods()));
            }
            
            // Search classes
            if (!entities.getClasses().isEmpty()) {
                results.addAll(searchClasses(session, entities.getClasses()));
            }
            
            
            // Search descriptions if we have general terms
            if (!entities.getTerms().isEmpty()) {
                results.addAll(searchDescriptions(session, entities.getTerms()));
            }
            
            // Search file docs with all terms
            if (entities.hasEntities()) {
                results.addAll(searchFileDocs(session, entities.getAllEntities()));
            }
            
            log.debug("Full-text search completed with {} results", results.size());
            return CompletableFuture.completedFuture(results);
            
        } catch (Exception e) {
            log.error("Full-text search failed", e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    /**
     * Performs vector similarity search
     */
    @Async
    public CompletableFuture<List<SearchResult>> vectorSearch(float[] queryEmbedding) {
        log.debug("Starting vector search");
        
        List<SearchResult> results = new ArrayList<>();
        
        try (Session session = neo4jDriver.session(sessionConfig)) {
            // Search Description embeddings
            results.addAll(searchDescriptionEmbeddings(session, queryEmbedding));
            
            // Search FileDoc embeddings
            results.addAll(searchFileDocEmbeddings(session, queryEmbedding));
            
            log.debug("Vector search completed with {} results", results.size());
            return CompletableFuture.completedFuture(results);
            
        } catch (Exception e) {
            log.error("Vector search failed", e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    /**
     * Search methods by name
     */
    private List<SearchResult> searchMethods(Session session, List<String> methodNames) {
        String searchTerms = String.join(" OR ", methodNames);
        String query = """
            CALL db.index.fulltext.queryNodes('method_names', $searchTerms)
            YIELD node, score
            RETURN node.id as nodeId, 
                   node.name as name,
                   node.signature as signature,
                   node.className as className,
                   score,
                   'method' as type
            ORDER BY score DESC
            LIMIT $limit
            """;

        return session.run(query, Map.of("searchTerms", searchTerms, "limit", fullTextSearchLimit))
                .list(record -> SearchResult.builder()
                        .nodeId(record.get("nodeId").asString())
                        .name(record.get("name").asString())
                        .signature(record.get("signature").asString())
                        .className(record.get("className").asString())
                        .score(record.get("score").asDouble())
                        .type(record.get("type").asString())
                        .searchType("fulltext")
                        .build());
    }

    /**
     * Search classes by name
     */
    private List<SearchResult> searchClasses(Session session, List<String> classNames) {
        String searchTerms = String.join(" OR ", classNames);
        String query = """
            CALL db.index.fulltext.queryNodes('class_names', $searchTerms)
            YIELD node, score
            RETURN node.id as nodeId,
                   node.name as name,
                   node.fullName as signature,
                   node.packageName as className,
                   score,
                   'class' as type
            ORDER BY score DESC
            LIMIT $limit
            """;

        return session.run(query, Map.of("searchTerms", searchTerms, "limit", fullTextSearchLimit))
                .list(record -> SearchResult.builder()
                        .nodeId(record.get("nodeId").asString())
                        .name(record.get("name").asString())
                        .signature(record.get("signature").asString())
                        .className(record.get("className").asString())
                        .score(record.get("score").asDouble())
                        .type(record.get("type").asString())
                        .searchType("fulltext")
                        .build());
    }


    /**
     * Search descriptions by content
     */
    private List<SearchResult> searchDescriptions(Session session, List<String> terms) {
        String searchTerms = String.join(" OR ", terms);
        String query = """
            CALL db.index.fulltext.queryNodes('description_content', $searchTerms)
            YIELD node, score
            MATCH (code)-[:HAS_DESCRIPTION]->(node)
            RETURN code.id as nodeId,
                   code.name as name,
                   COALESCE(code.signature, code.fullName) as signature,
                   COALESCE(code.className, code.packageName) as className,
                   score,
                   labels(code)[0] as type
            ORDER BY score DESC
            LIMIT $limit
            """;

        return session.run(query, Map.of("searchTerms", searchTerms, "limit", fullTextSearchLimit))
                .list(record -> SearchResult.builder()
                        .nodeId(record.get("nodeId").asString())
                        .name(record.get("name").asString())
                        .signature(record.get("signature").asString())
                        .className(record.get("className").asString())
                        .score(record.get("score").asDouble())
                        .type(record.get("type").asString().toLowerCase())
                        .searchType("fulltext")
                        .build());
    }

    /**
     * Search file docs by content
     */
    private List<SearchResult> searchFileDocs(Session session, List<String> terms) {
        String searchTerms = String.join(" OR ", terms);
        String query = """
            CALL db.index.fulltext.queryNodes('file_doc_content', $searchTerms)
            YIELD node, score
            RETURN node.id as nodeId,
                   node.fileName as name,
                   node.fileName as signature,
                   node.packageName as className,
                   score,
                   'file_doc' as type
            ORDER BY score DESC
            LIMIT $limit
            """;

        return session.run(query, Map.of("searchTerms", searchTerms, "limit", fullTextSearchLimit))
                .list(record -> SearchResult.builder()
                        .nodeId(record.get("nodeId").asString())
                        .name(record.get("name").asString())
                        .signature(record.get("signature").asString())
                        .className(record.get("className").asString())
                        .score(record.get("score").asDouble())
                        .type(record.get("type").asString())
                        .searchType("fulltext")
                        .build());
    }

    /**
     * Search description embeddings
     */
    private List<SearchResult> searchDescriptionEmbeddings(Session session, float[] queryEmbedding) {
        String query = """
            CALL db.index.vector.queryNodes('description_embeddings', $k, $queryVector)
            YIELD node, score
            MATCH (code)-[:HAS_DESCRIPTION]->(node)
            RETURN code.id as nodeId,
                   code.name as name,
                   COALESCE(code.signature, code.fullName) as signature,
                   COALESCE(code.className, code.packageName) as className,
                   score,
                   labels(code)[0] as type
            ORDER BY score DESC
            """;

        return session.run(query, Map.of("k", vectorSearchLimit, "queryVector", queryEmbedding))
                .list(record -> SearchResult.builder()
                        .nodeId(record.get("nodeId").asString())
                        .name(record.get("name").asString())
                        .signature(record.get("signature").asString())
                        .className(record.get("className").asString())
                        .score(record.get("score").asDouble())
                        .type(record.get("type").asString().toLowerCase())
                        .searchType("semantic")
                        .build());
    }

    /**
     * Search file doc embeddings
     */
    private List<SearchResult> searchFileDocEmbeddings(Session session, float[] queryEmbedding) {
        String query = """
            CALL db.index.vector.queryNodes('file_doc_embeddings', $k, $queryVector)
            YIELD node, score
            RETURN node.id as nodeId,
                   node.fileName as name,
                   node.fileName as signature,
                   node.packageName as className,
                   score,
                   'file_doc' as type
            ORDER BY score DESC
            """;

        return session.run(query, Map.of("k", vectorSearchLimit, "queryVector", queryEmbedding))
                .list(record -> SearchResult.builder()
                        .nodeId(record.get("nodeId").asString())
                        .name(record.get("name").asString())
                        .signature(record.get("signature").asString())
                        .className(record.get("className").asString())
                        .score(record.get("score").asDouble())
                        .type(record.get("type").asString())
                        .searchType("semantic")
                        .build());
    }

    /**
     * Search method embeddings using vector similarity
     */
    @Async
    public CompletableFuture<List<SearchResult>> searchMethodEmbeddings(float[] queryEmbedding) {
        log.debug("Starting method embedding vector search");
        
        List<SearchResult> results = new ArrayList<>();
        
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                CALL db.index.vector.queryNodes('method_embeddings', $k, $queryVector)
                YIELD node, score
                RETURN node.id as nodeId,
                       node.name as name,
                       node.signature as signature,
                       node.className as className,
                       score,
                       'method' as type
                ORDER BY score DESC
                """;

            results = session.run(query, Map.of("k", vectorSearchLimit, "queryVector", queryEmbedding))
                    .list(record -> SearchResult.builder()
                            .nodeId(record.get("nodeId").asString())
                            .name(record.get("name").asString())
                            .signature(record.get("signature").asString())
                            .className(record.get("className").asString())
                            .score(record.get("score").asDouble())
                            .type(record.get("type").asString())
                            .searchType("semantic")
                            .build());

            log.debug("Method embedding search completed. Found {} results", results.size());
            
        } catch (Exception e) {
            log.error("Method embedding search failed", e);
        }
        
        return CompletableFuture.completedFuture(results);
    }

    /**
     * Search class embeddings using vector similarity
     */
    @Async
    public CompletableFuture<List<SearchResult>> searchClassEmbeddings(float[] queryEmbedding) {
        log.debug("Starting class embedding vector search");
        
        List<SearchResult> results = new ArrayList<>();
        
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                CALL db.index.vector.queryNodes('class_embeddings', $k, $queryVector)
                YIELD node, score
                RETURN node.id as nodeId,
                       node.name as name,
                       node.fullName as signature,
                       node.packageName as className,
                       score,
                       labels(node)[0] as type
                ORDER BY score DESC
                """;

            results = session.run(query, Map.of("k", vectorSearchLimit, "queryVector", queryEmbedding))
                    .list(record -> SearchResult.builder()
                            .nodeId(record.get("nodeId").asString())
                            .name(record.get("name").asString())
                            .signature(record.get("signature").asString())
                            .className(record.get("className").asString())
                            .score(record.get("score").asDouble())
                            .type(record.get("type").asString().toLowerCase())
                            .searchType("semantic")
                            .build());

            log.debug("Class embedding search completed. Found {} results", results.size());
            
        } catch (Exception e) {
            log.error("Class embedding search failed", e);
        }
        
        return CompletableFuture.completedFuture(results);
    }

    /**
     * Unified vector search across all node types with embeddings
     */
    @Async
    public CompletableFuture<List<SearchResult>> unifiedVectorSearch(float[] queryEmbedding) {
        log.debug("Starting unified vector search across all node types");
        
        List<CompletableFuture<List<SearchResult>>> searchFutures = new ArrayList<>();
        
        // Search all available vector indexes
        searchFutures.add(searchMethodEmbeddings(queryEmbedding));
        searchFutures.add(searchClassEmbeddings(queryEmbedding));
        searchFutures.add(vectorSearch(queryEmbedding)); // Existing description search
        
        // Combine all results
        return CompletableFuture.allOf(searchFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<SearchResult> allResults = new ArrayList<>();
                    for (CompletableFuture<List<SearchResult>> future : searchFutures) {
                        try {
                            allResults.addAll(future.get());
                        } catch (Exception e) {
                            log.error("Failed to get results from search future", e);
                        }
                    }
                    
                    // Sort by score and limit results
                    return allResults.stream()
                            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                            .limit(vectorSearchLimit * 2) // Allow more results for unified search
                            .toList();
                });
    }

    /**
     * Data class for search results
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResult {
        private String nodeId;
        private String name;
        private String signature;
        private String className;
        private double score;
        private String type; // method, class, package, file_doc
        private String searchType; // fulltext, semantic
    }
}