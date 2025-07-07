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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${query.retrieval.reranking.enabled:true}")
    private boolean reRankingEnabled;

    @Value("${query.retrieval.reranking.threshold:0.6}")
    private double reRankThreshold;

    @Value("${query.retrieval.reranking.final-limit:50}")
    private int finalLimit;

    @Value("${query.retrieval.reranking.batch-size:10}")
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
            
            // 2. Get descriptions for all nodes
            Map<String, String> nodeDescriptions = getNodeDescriptions(subGraph.getNodesList());
            
            // 3. Score each node based on description similarity
            List<RankedNode> rankedNodes = new ArrayList<>();
            
            for (GraphNode node : subGraph.getNodesList()) {
                String description = nodeDescriptions.get(node.getId());
                double similarity = calculateSimilarity(queryEmbedding, description);
                
                rankedNodes.add(RankedNode.builder()
                        .node(node)
                        .similarityScore(similarity)
                        .description(description)
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
     * Gets descriptions for nodes from various sources
     */
    private Map<String, String> getNodeDescriptions(List<GraphNode> nodes) {
        Map<String, String> descriptions = new HashMap<>();
        
        try (Session session = neo4jDriver.session(sessionConfig)) {
            // Process nodes in batches for efficiency
            for (int i = 0; i < nodes.size(); i += batchSize) {
                List<GraphNode> batch = nodes.subList(i, Math.min(i + batchSize, nodes.size()));
                Map<String, String> batchDescriptions = getDescriptionsForBatch(session, batch);
                descriptions.putAll(batchDescriptions);
            }
        } catch (Exception e) {
            log.error("Failed to fetch node descriptions", e);
        }
        
        return descriptions;
    }

    /**
     * Gets descriptions for a batch of nodes
     */
    private Map<String, String> getDescriptionsForBatch(Session session, List<GraphNode> nodes) {
        Map<String, String> descriptions = new HashMap<>();
        
        for (GraphNode node : nodes) {
            String description = getNodeDescription(session, node);
            descriptions.put(node.getId(), description);
        }
        
        return descriptions;
    }

    /**
     * Gets description for a single node from multiple sources
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
     * Calculates similarity between query embedding and description
     */
    private double calculateSimilarity(float[] queryEmbedding, String description) {
        if (description == null || description.trim().isEmpty()) {
            return 0.0;
        }
        
        try {
            float[] descEmbedding = embeddingModel.embed(description).content().vector();
            return cosineSimilarity(queryEmbedding, descEmbedding);
        } catch (Exception e) {
            log.debug("Failed to calculate similarity for description: {}", e.getMessage());
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