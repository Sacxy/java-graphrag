package com.tekion.javaastkg.query;

import com.tekion.javaastkg.model.GraphEntities;
import com.tekion.javaastkg.model.QueryModels;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * Implements hybrid retrieval combining vector similarity search and graph traversal.
 * This provides both semantic similarity and structural context.
 */
@Service
@Slf4j
public class HybridRetriever {

    private final Driver neo4jDriver;
    private final SessionConfig sessionConfig;
    private final EmbeddingModel embeddingModel;

    @org.springframework.beans.factory.annotation.Value("${query.retrieval.vector-search-limit:10}")
    private int vectorSearchLimit;

    @org.springframework.beans.factory.annotation.Value("${query.retrieval.graph-expansion-depth:3}")
    private int graphExpansionDepth;

    @Autowired
    public HybridRetriever(Driver neo4jDriver,
                           SessionConfig sessionConfig,
                           @org.springframework.beans.factory.annotation.Qualifier("queryEmbeddingModel") EmbeddingModel embeddingModel) {
        this.neo4jDriver = neo4jDriver;
        this.sessionConfig = sessionConfig;
        this.embeddingModel = embeddingModel;
    }

    /**
     * Performs hybrid retrieval for a user query
     */
    public QueryModels.RetrievalResult retrieve(String query) {
        log.info("Performing hybrid retrieval for query: {}", query);

        try {
            // Step 1: Embed the query
            float[] queryVector = embeddingModel.embed(query).content().vector();
            log.info("Generated query vector: first 5 values = {}", Arrays.toString(Arrays.copyOf(queryVector, 5)));
            log.info("Query vector length: {}", queryVector.length);

            // Step 2: Vector similarity search
            Map<String, Double> vectorSearchResults = performVectorSearch(queryVector);
            List<String> topMethodIds = new ArrayList<>(vectorSearchResults.keySet());
            log.info("Vector search found {} methods", topMethodIds.size());

            // Step 3: Graph expansion from top results
            GraphEntities.GraphContext graphContext = performGraphExpansion(topMethodIds);

            // Step 4: Combine and rank results
            return QueryModels.RetrievalResult.builder()
                    .topMethodIds(topMethodIds)
                    .graphContext(graphContext)
                    .scoreMap(vectorSearchResults)
                    .build();

        } catch (Exception e) {
            log.error("Hybrid retrieval failed", e);
            throw new RuntimeException("Failed to perform hybrid retrieval", e);
        }
    }

    /**
     * Performs vector similarity search using Neo4j's vector index
     */
    private Map<String, Double> performVectorSearch(float[] queryVector) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            // First check if we have any methods with embeddings
            String checkQuery = "MATCH (m:Method) WHERE m.embedding IS NOT NULL RETURN count(m) as count";
            Long embeddingCount = session.run(checkQuery).single().get("count").asLong();
            log.info("Total methods with embeddings: {}", embeddingCount);
            
            if (embeddingCount == 0) {
                log.warn("No methods have embeddings! Run the vectorization process first.");
                return new LinkedHashMap<>();
            }
            
            // Check if vector index exists
            String indexCheckQuery = "SHOW INDEXES WHERE name = 'method_embeddings'";
            List<Record> indexes = session.run(indexCheckQuery).list();
            log.info("Vector index 'method_embeddings' exists: {}", !indexes.isEmpty());
            // Try without the class join first to see if that's the issue
            String query = """
                CALL db.index.vector.queryNodes('method_embeddings', 50, $queryVector)
                YIELD node, score
                RETURN id(node) as nodeId, 
                       node.signature as signature,
                       node.name as name,
                       node.summary as summary,
                       node.className as className,
                       score
                ORDER BY score DESC
                """;

            Map<String, Double> results = new LinkedHashMap<>();

            Map<String, Object> params = Map.of(
                    "limit", vectorSearchLimit,
                    "queryVector", queryVector
            );
            log.info("Executing vector search with limit={}, vector length={}", vectorSearchLimit, queryVector.length);
            
            List<Record> records;
            try {
                records = session.run(query, params).list();
                log.info("Vector search query returned {} records", records.size());
            } catch (Exception e) {
                log.error("Vector search query failed", e);
                return new LinkedHashMap<>();
            }

            for (Record record : records) {
                String nodeId = String.valueOf(record.get("nodeId").asLong());
                double score = record.get("score").asDouble();
                results.put(nodeId, score);

                log.debug("Vector search result: {} - {} (score: {})",
                        record.get("name").asString(),
                        record.get("className").asString(),
                        score);
            }

            return results;
        }
    }

    /**
     * Expands the graph context around the top vector search results
     */
    private GraphEntities.GraphContext performGraphExpansion(List<String> nodeIds) {
        if (nodeIds.isEmpty()) {
            return GraphEntities.GraphContext.builder()
                    .methods(new ArrayList<>())
                    .classes(new ArrayList<>())
                    .relationships(new ArrayList<>())
                    .build();
        }

        try (Session session = neo4jDriver.session(sessionConfig)) {
            // Convert string IDs to long for Neo4j
            List<Long> longIds = nodeIds.stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toList());

            // Expand graph to include related methods and classes
            String expansionQuery = String.format("""
                MATCH (startNode:Method)
                WHERE id(startNode) IN $nodeIds
                CALL {
                    WITH startNode
                    MATCH path = (startNode)-[*0..%d]-(connected)
                    WHERE connected:Method OR connected:Class
                    RETURN connected, relationships(path) as rels
                }
                WITH collect(DISTINCT connected) as nodes,
                     collect(DISTINCT rels) as allRels
                UNWIND allRels as relList
                UNWIND relList as rel
                WITH nodes, collect(DISTINCT rel) as relationships
                RETURN nodes, relationships
                """, graphExpansionDepth);

            Record result = session.run(expansionQuery,
                    Map.of("nodeIds", longIds)).single();

            // Process nodes
            List<GraphEntities.MethodNode> methods = new ArrayList<>();
            List<GraphEntities.ClassNode> classes = new ArrayList<>();

            for (Value nodeValue : result.get("nodes").values()) {
                org.neo4j.driver.types.Node node = nodeValue.asNode();

                if (node.hasLabel("Method")) {
                    methods.add(buildMethodNode(node));
                } else if (node.hasLabel("Class")) {
                    classes.add(buildClassNode(node));
                }
            }

            // Process relationships
            List<GraphEntities.Relationship> relationships = new ArrayList<>();
            for (Value relValue : result.get("relationships").values()) {
                org.neo4j.driver.types.Relationship rel = relValue.asRelationship();
                relationships.add(buildRelationship(rel));
            }

            log.info("Graph expansion found {} methods, {} classes, {} relationships",
                    methods.size(), classes.size(), relationships.size());

            return GraphEntities.GraphContext.builder()
                    .methods(methods)
                    .classes(classes)
                    .relationships(relationships)
                    .metadata(Map.of(
                            "expansionDepth", graphExpansionDepth,
                            "startNodeCount", nodeIds.size()
                    ))
                    .build();
        }
    }

    /**
     * Builds a MethodNode from Neo4j node data
     */
    private GraphEntities.MethodNode buildMethodNode(org.neo4j.driver.types.Node node) {
        return GraphEntities.MethodNode.builder()
                .id(String.valueOf(node.id()))
                .signature(node.get("signature").asString())
                .name(node.get("name").asString())
                .className(node.get("className").asString(null))
                .summary(node.get("summary").asString(null))
                .detailedExplanation(node.get("detailedExplanation").asString(null))
                .businessTags(node.get("businessTags").isNull() ? 
                    List.of() : node.get("businessTags").asList(Value::asString))
                .metadata(extractMetadata(node))
                .build();
    }

    /**
     * Builds a ClassNode from Neo4j node data
     */
    private GraphEntities.ClassNode buildClassNode(org.neo4j.driver.types.Node node) {
        return GraphEntities.ClassNode.builder()
                .id(String.valueOf(node.id()))
                .fullName(node.get("fullName").asString())
                .name(node.get("name").asString())
                .packageName(node.get("package").asString(null))
                .type(node.get("type").asString(null))
                .isInterface(node.get("isInterface").asBoolean(false))
                .isAbstract(node.get("isAbstract").asBoolean(false))
                .metadata(extractMetadata(node))
                .build();
    }

    /**
     * Builds a Relationship from Neo4j relationship data
     */
    private GraphEntities.Relationship buildRelationship(org.neo4j.driver.types.Relationship rel) {
        return GraphEntities.Relationship.builder()
                .fromId(String.valueOf(rel.startNodeId()))
                .toId(String.valueOf(rel.endNodeId()))
                .type(rel.type())
                .properties(rel.asMap())
                .build();
    }

    /**
     * Extracts additional metadata from a node
     */
    private Map<String, Object> extractMetadata(org.neo4j.driver.types.Node node) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("startLine", node.get("startLine").asInt(0));
        metadata.put("endLine", node.get("endLine").asInt(0));
        metadata.put("complexity", node.get("complexity").asString("unknown"));
        metadata.put("annotations", node.get("annotations").isNull() ? 
            List.of() : node.get("annotations").asList(Value::asString));
        return metadata;
    }
}