package com.tekion.javaastkg.query.services;

import com.tekion.javaastkg.query.services.GraphExpander.GraphNode;
import com.tekion.javaastkg.query.services.GraphExpander.SubGraph;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for re-ranking expanded nodes based on embedding similarity to the original query.
 * This provides semantic refinement after graph expansion.
 */
@Service
@Slf4j
public class ReRankingService {

    private final Driver neo4jDriver;
    private final SessionConfig sessionConfig;
    private final EmbeddingModel embeddingModel;

    @org.springframework.beans.factory.annotation.Value("${query.retrieval.reranking.enabled:true}")
    private boolean reRankingEnabled;

    @org.springframework.beans.factory.annotation.Value("${query.retrieval.reranking.threshold:0.6}")
    private double reRankThreshold;

    @org.springframework.beans.factory.annotation.Value("${query.retrieval.reranking.final-limit:50}")
    private int finalLimit;

    @org.springframework.beans.factory.annotation.Value("${query.retrieval.reranking.batch-size:10}")
    private int batchSize;

    public ReRankingService(Driver neo4jDriver,
                           SessionConfig sessionConfig,
                           @Qualifier("queryEmbeddingModel") EmbeddingModel embeddingModel) {
        this.neo4jDriver = neo4jDriver;
        this.sessionConfig = sessionConfig;
        this.embeddingModel = embeddingModel;
    }

    /**
     * Re-ranks nodes in a subgraph based on embedding similarity to the original query
     */
    public List<RankedNode> reRank(SubGraph subGraph, String originalQuery) {
        if (!reRankingEnabled || subGraph.isEmpty()) {
            log.debug("Re-ranking disabled or empty subgraph, returning original order");
            return subGraph.getNodesList().stream()
                    .map(node -> RankedNode.builder()
                            .node(node)
                            .similarityScore(0.5) // Neutral score
                            .description("No re-ranking performed")
                            .build())
                    .collect(Collectors.toList());
        }

        log.debug("Re-ranking {} nodes based on query similarity", subGraph.getNodeCount());

        try {
            // 1. Generate query embedding
            float[] queryEmbedding = embeddingModel.embed(originalQuery).content().vector();
            
            // 2. Get descriptions and precomputed embeddings for all nodes
            Map<String, NodeEmbeddingData> nodeData = getNodeEmbeddingData(subGraph.getNodesList());
            
            // 3. Score each node based on precomputed embedding similarity
            List<RankedNode> rankedNodes = new ArrayList<>();
            
            for (GraphNode node : subGraph.getNodesList()) {
                NodeEmbeddingData data = nodeData.get(node.getId());
                double similarity = getPrecomputedSimilarity(queryEmbedding, data);
                
                rankedNodes.add(RankedNode.builder()
                        .node(node)
                        .similarityScore(similarity)
                        .description(data != null ? data.getDescription() : "No description available")
                        .build());
            }
            
            // 4. Sort by similarity score
            List<RankedNode> sortedNodes = rankedNodes.stream()
                    .sorted(Comparator.comparing(RankedNode::getSimilarityScore).reversed())
                    .collect(Collectors.toList());
            
            log.debug("Re-ranking completed. Top scores: {}", 
                     sortedNodes.stream()
                             .limit(5)
                             .map(n -> String.format("%.3f", n.getSimilarityScore()))
                             .collect(Collectors.joining(", ")));
            
            return sortedNodes;
            
        } catch (Exception e) {
            log.error("Re-ranking failed, returning original order", e);
            return subGraph.getNodesList().stream()
                    .map(node -> RankedNode.builder()
                            .node(node)
                            .similarityScore(0.0)
                            .description("Re-ranking failed: " + e.getMessage())
                            .build())
                    .collect(Collectors.toList());
        }
    }

    /**
     * Re-ranks and filters nodes, returning only those above threshold
     */
    public List<RankedNode> reRankAndFilter(SubGraph subGraph, String originalQuery) {
        List<RankedNode> rankedNodes = reRank(subGraph, originalQuery);
        
        List<RankedNode> filtered = rankedNodes.stream()
                .filter(node -> node.getSimilarityScore() >= reRankThreshold)
                .limit(finalLimit)
                .collect(Collectors.toList());
        
        log.debug("Filtered {} nodes above threshold {:.3f}, keeping top {}", 
                 filtered.size(), reRankThreshold, finalLimit);
        
        return filtered;
    }

    /**
     * Gets precomputed embeddings and descriptions for nodes from various sources
     */
    private Map<String, NodeEmbeddingData> getNodeEmbeddingData(List<GraphNode> nodes) {
        Map<String, NodeEmbeddingData> nodeData = new HashMap<>();
        
        try (Session session = neo4jDriver.session(sessionConfig)) {
            // Process nodes in batches for efficiency
            for (int i = 0; i < nodes.size(); i += batchSize) {
                List<GraphNode> batch = nodes.subList(i, Math.min(i + batchSize, nodes.size()));
                Map<String, NodeEmbeddingData> batchData = getEmbeddingDataForBatch(session, batch);
                nodeData.putAll(batchData);
            }
        } catch (Exception e) {
            log.error("Failed to fetch node embedding data", e);
        }
        
        return nodeData;
    }

    /**
     * Gets embedding data for a batch of nodes
     */
    private Map<String, NodeEmbeddingData> getEmbeddingDataForBatch(Session session, List<GraphNode> nodes) {
        Map<String, NodeEmbeddingData> nodeData = new HashMap<>();
        
        for (GraphNode node : nodes) {
            NodeEmbeddingData data = getNodeEmbeddingData(session, node);
            nodeData.put(node.getId(), data);
        }
        
        return nodeData;
    }

    /**
     * Gets embedding data for a single node from precomputed embeddings
     */
    private NodeEmbeddingData getNodeEmbeddingData(Session session, GraphNode node) {
        String nodeId = node.getId();
        String nodeType = node.getType().toLowerCase();
        
        try {
            // Try to get precomputed embedding based on node type
            switch (nodeType) {
                case "method":
                    return getMethodEmbeddingData(session, nodeId);
                case "class":
                case "interface":
                case "enum":
                    return getClassEmbeddingData(session, nodeId);
                case "description":
                    return getDescriptionEmbeddingData(session, nodeId);
                case "filedoc":
                    return getFileDocEmbeddingData(session, nodeId);
                default:
                    // For other node types, fall back to description-based approach
                    String description = getNodeDescription(session, node);
                    return new NodeEmbeddingData(null, description);
            }
        } catch (Exception e) {
            log.debug("Failed to get embedding data for node {}: {}", nodeId, e.getMessage());
            String fallbackDescription = getDescriptionFromProperties(node);
            return new NodeEmbeddingData(null, fallbackDescription);
        }
    }

    /**
     * Gets description for a single node from multiple sources (fallback method)
     */
    private String getNodeDescription(Session session, GraphNode node) {
        String nodeId = node.getId();
        String nodeType = node.getType().toLowerCase();
        
        try {
            // Try to get from HAS_DESCRIPTION relationship first
            String descFromRel = getDescriptionFromRelationship(session, nodeId);
            if (descFromRel != null && !descFromRel.trim().isEmpty()) {
                return descFromRel;
            }
            
            // Try to get from file docs if it's a file-related node
            if ("filedoc".equals(nodeType)) {
                return getFileDocContent(session, nodeId);
            }
            
            // Fall back to node properties
            return getDescriptionFromProperties(node);
            
        } catch (Exception e) {
            log.debug("Failed to get description for node {}: {}", nodeId, e.getMessage());
            return getDescriptionFromProperties(node);
        }
    }

    /**
     * Gets description from HAS_DESCRIPTION relationship
     */
    private String getDescriptionFromRelationship(Session session, String nodeId) {
        String query = """
            MATCH (n)-[:HAS_DESCRIPTION]->(d:Description)
            WHERE n.id = $nodeId OR toString(id(n)) = $nodeId
            RETURN d.content as description
            LIMIT 1
            """;
        
        try {
            Result result = session.run(query, Map.of("nodeId", nodeId));
            if (result.hasNext()) {
                return result.single().get("description").asString("");
            }
        } catch (Exception e) {
            log.trace("No description relationship found for node {}", nodeId);
        }
        
        return null;
    }

    /**
     * Gets content from FileDoc node
     */
    private String getFileDocContent(Session session, String nodeId) {
        String query = """
            MATCH (f:FileDoc)
            WHERE f.id = $nodeId OR toString(id(f)) = $nodeId
            RETURN f.content as content
            LIMIT 1
            """;
        
        try {
            Result result = session.run(query, Map.of("nodeId", nodeId));
            if (result.hasNext()) {
                return result.single().get("content").asString("");
            }
        } catch (Exception e) {
            log.trace("No file doc content found for node {}", nodeId);
        }
        
        return null;
    }

    /**
     * Gets description from node properties as fallback
     */
    private String getDescriptionFromProperties(GraphNode node) {
        Map<String, Object> props = node.getProperties();
        if (props == null) {
            return "Node: " + node.getName();
        }
        
        StringBuilder desc = new StringBuilder();
        
        // Add node type and name
        desc.append(node.getType()).append(": ").append(node.getName());
        
        // Add signature if available
        if (props.containsKey("signature")) {
            desc.append(" - ").append(props.get("signature"));
        }
        
        // Add class name for methods
        if (props.containsKey("className")) {
            desc.append(" in ").append(props.get("className"));
        }
        
        // Add business tags if available
        if (props.containsKey("businessTags")) {
            Object tags = props.get("businessTags");
            if (tags instanceof List && !((List<?>) tags).isEmpty()) {
                desc.append(" (Tags: ").append(String.join(", ", (List<String>) tags)).append(")");
            }
        }
        
        return desc.toString();
    }

    /**
     * Gets precomputed similarity between query embedding and node embeddings
     */
    private double getPrecomputedSimilarity(float[] queryEmbedding, NodeEmbeddingData data) {
        if (data == null || data.getEmbedding() == null) {
            log.debug("No precomputed embedding available for node");
            return 0.0;
        }
        
        try {
            return cosineSimilarity(queryEmbedding, data.getEmbedding());
        } catch (Exception e) {
            log.debug("Failed to calculate similarity with precomputed embedding: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Calculates cosine similarity between two embeddings
     */
    private double cosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1.length != embedding2.length) {
            log.warn("Embedding dimension mismatch: {} vs {}", embedding1.length, embedding2.length);
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Applies re-ranking to a SubGraph and returns a filtered SubGraph
     */
    public SubGraph applyReRanking(SubGraph subGraph, String originalQuery) {
        List<RankedNode> rankedNodes = reRankAndFilter(subGraph, originalQuery);
        
        Set<String> acceptedNodeIds = rankedNodes.stream()
                .map(RankedNode::getNodeId)
                .collect(Collectors.toSet());
        
        Map<String, GraphNode> filteredNodes = subGraph.getNodes().entrySet().stream()
                .filter(entry -> acceptedNodeIds.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        
        List<GraphExpander.GraphRelationship> filteredRels = subGraph.getRelationships().stream()
                .filter(rel -> acceptedNodeIds.contains(rel.getStartNodeId()) && 
                              acceptedNodeIds.contains(rel.getEndNodeId()))
                .collect(Collectors.toList());
        
        return SubGraph.builder()
                .nodes(filteredNodes)
                .relationships(filteredRels)
                .metadata(Map.of(
                        "reRanked", true,
                        "originalNodeCount", subGraph.getNodeCount(),
                        "reRankedNodeCount", filteredNodes.size(),
                        "reRankThreshold", reRankThreshold
                ))
                .build();
    }

    /**
     * Gets method embedding data with precomputed embedding
     */
    private NodeEmbeddingData getMethodEmbeddingData(Session session, String nodeId) {
        String query = """
            MATCH (m:Method)
            WHERE m.id = $nodeId
            OPTIONAL MATCH (m)-[:HAS_DESCRIPTION]->(d:Description)
            RETURN m.embedding as embedding,
                   m.embeddingText as embeddingText,
                   collect(d.content) as descriptions
            LIMIT 1
            """;
        
        try {
            Result result = session.run(query, Map.of("nodeId", nodeId));
            if (result.hasNext()) {
                Record record = result.single();
                
                float[] embedding = null;
                if (!record.get("embedding").isNull()) {
                    List<Object> embeddingList = record.get("embedding").asList();
                    embedding = new float[embeddingList.size()];
                    for (int i = 0; i < embeddingList.size(); i++) {
                        embedding[i] = ((Number) embeddingList.get(i)).floatValue();
                    }
                }
                
                String description = record.get("embeddingText").asString("");
                if (description.isEmpty()) {
                    List<String> descriptions = record.get("descriptions").asList(Value::asString);
                    description = String.join(" ", descriptions);
                }
                
                return new NodeEmbeddingData(embedding, description);
            }
        } catch (Exception e) {
            log.debug("Failed to get method embedding data for {}: {}", nodeId, e.getMessage());
        }
        
        return new NodeEmbeddingData(null, "Method node");
    }
    
    /**
     * Gets class embedding data with precomputed embedding
     */
    private NodeEmbeddingData getClassEmbeddingData(Session session, String nodeId) {
        String query = """
            MATCH (c:Class|Interface|Enum)
            WHERE c.id = $nodeId
            OPTIONAL MATCH (c)-[:HAS_DESCRIPTION]->(d:Description)
            RETURN c.embedding as embedding,
                   c.embeddingText as embeddingText,
                   c.name as name,
                   collect(d.content) as descriptions
            LIMIT 1
            """;
        
        try {
            Result result = session.run(query, Map.of("nodeId", nodeId));
            if (result.hasNext()) {
                Record record = result.single();
                
                float[] embedding = null;
                if (!record.get("embedding").isNull()) {
                    List<Object> embeddingList = record.get("embedding").asList();
                    embedding = new float[embeddingList.size()];
                    for (int i = 0; i < embeddingList.size(); i++) {
                        embedding[i] = ((Number) embeddingList.get(i)).floatValue();
                    }
                }
                
                String description = record.get("embeddingText").asString("");
                if (description.isEmpty()) {
                    List<String> descriptions = record.get("descriptions").asList(Value::asString);
                    String name = record.get("name").asString("");
                    description = "Class: " + name + " " + String.join(" ", descriptions);
                }
                
                return new NodeEmbeddingData(embedding, description);
            }
        } catch (Exception e) {
            log.debug("Failed to get class embedding data for {}: {}", nodeId, e.getMessage());
        }
        
        return new NodeEmbeddingData(null, "Class node");
    }
    
    /**
     * Gets description embedding data
     */
    private NodeEmbeddingData getDescriptionEmbeddingData(Session session, String nodeId) {
        String query = """
            MATCH (d:Description)
            WHERE d.id = $nodeId
            RETURN d.embedding as embedding, d.content as content
            LIMIT 1
            """;
        
        try {
            Result result = session.run(query, Map.of("nodeId", nodeId));
            if (result.hasNext()) {
                Record record = result.single();
                
                float[] embedding = null;
                if (!record.get("embedding").isNull()) {
                    List<Object> embeddingList = record.get("embedding").asList();
                    embedding = new float[embeddingList.size()];
                    for (int i = 0; i < embeddingList.size(); i++) {
                        embedding[i] = ((Number) embeddingList.get(i)).floatValue();
                    }
                }
                
                String description = record.get("content").asString("");
                return new NodeEmbeddingData(embedding, description);
            }
        } catch (Exception e) {
            log.debug("Failed to get description embedding data for {}: {}", nodeId, e.getMessage());
        }
        
        return new NodeEmbeddingData(null, "Description node");
    }
    
    /**
     * Gets file doc embedding data
     */
    private NodeEmbeddingData getFileDocEmbeddingData(Session session, String nodeId) {
        String query = """
            MATCH (f:FileDoc)
            WHERE f.id = $nodeId
            RETURN f.embedding as embedding, f.content as content, f.fileName as fileName
            LIMIT 1
            """;
        
        try {
            Result result = session.run(query, Map.of("nodeId", nodeId));
            if (result.hasNext()) {
                Record record = result.single();
                
                float[] embedding = null;
                if (!record.get("embedding").isNull()) {
                    List<Object> embeddingList = record.get("embedding").asList();
                    embedding = new float[embeddingList.size()];
                    for (int i = 0; i < embeddingList.size(); i++) {
                        embedding[i] = ((Number) embeddingList.get(i)).floatValue();
                    }
                }
                
                String description = record.get("content").asString("");
                if (description.isEmpty()) {
                    description = "File: " + record.get("fileName").asString("");
                }
                
                return new NodeEmbeddingData(embedding, description);
            }
        } catch (Exception e) {
            log.debug("Failed to get file doc embedding data for {}: {}", nodeId, e.getMessage());
        }
        
        return new NodeEmbeddingData(null, "File document");
    }

    /**
     * Data class for node embedding data
     */
    @Data
    @AllArgsConstructor
    private static class NodeEmbeddingData {
        private float[] embedding;
        private String description;
    }

    /**
     * Data class for ranked nodes
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankedNode {
        private GraphNode node;
        private double similarityScore;
        private String description;
        
        public String getNodeId() {
            return node != null ? node.getId() : null;
        }
        
        public String getNodeType() {
            return node != null ? node.getType() : null;
        }
        
        public String getNodeName() {
            return node != null ? node.getName() : null;
        }
    }
}