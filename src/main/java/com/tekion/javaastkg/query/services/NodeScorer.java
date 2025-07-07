package com.tekion.javaastkg.query.services;

import com.tekion.javaastkg.query.services.GraphExpander.GraphNode;
import com.tekion.javaastkg.query.services.GraphExpander.SubGraph;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Component for scoring nodes based on multiple criteria including search relevance,
 * graph distance, and node properties.
 */
@Component
@Slf4j
public class NodeScorer {

    @Value("${query.retrieval.scoring.fulltext-weight:0.4}")
    private double fullTextWeight;

    @Value("${query.retrieval.scoring.vector-weight:0.6}")
    private double vectorWeight;

    @Value("${query.retrieval.scoring.distance-penalty:0.1}")
    private double graphDistancePenalty;

    @Value("${query.retrieval.scoring.min-score:0.1}")
    private double minScore;

    @Value("${query.retrieval.scoring.node-type-boost:0.2}")
    private double nodeTypeBoost;

    /**
     * Calculates scores for nodes in a subgraph based on multiple factors
     */
    public Map<String, Double> calculateNodeScores(SubGraph subGraph,
                                                  Map<String, Double> fullTextScores,
                                                  Map<String, Double> vectorScores,
                                                  List<String> startNodeIds) {
        log.debug("Calculating scores for {} nodes", subGraph.getNodeCount());

        Map<String, Double> nodeScores = new HashMap<>();
        Map<String, Integer> nodeDistances = calculateDistancesFromStart(subGraph, startNodeIds);

        for (GraphNode node : subGraph.getNodesList()) {
            String nodeId = node.getId();
            double score = calculateScore(
                    nodeId,
                    fullTextScores,
                    vectorScores,
                    nodeDistances.getOrDefault(nodeId, Integer.MAX_VALUE),
                    node
            );
            
            if (score >= minScore) {
                nodeScores.put(nodeId, score);
            }
        }

        log.debug("Scored {} nodes above minimum threshold {}", nodeScores.size(), minScore);
        return nodeScores;
    }

    /**
     * Calculates score for a single node
     */
    public double calculateScore(String nodeId,
                               Map<String, Double> fullTextScores,
                               Map<String, Double> vectorScores,
                               int distanceFromStart,
                               GraphNode node) {
        
        // Base scores from search results
        double ftScore = fullTextScores.getOrDefault(nodeId, 0.0) * fullTextWeight;
        double vecScore = vectorScores.getOrDefault(nodeId, 0.0) * vectorWeight;
        
        // Distance penalty (nodes farther from start get lower scores)
        double distancePenalty = Math.min(distanceFromStart * graphDistancePenalty, 0.5);
        
        // Node type boost (prioritize certain types)
        double typeBoost = calculateNodeTypeBoost(node);
        
        // Property-based boost
        double propertyBoost = calculatePropertyBoost(node);
        
        double finalScore = (ftScore + vecScore + typeBoost + propertyBoost) - distancePenalty;
        
        log.trace("Score for {}: ft={:.3f}, vec={:.3f}, type={:.3f}, prop={:.3f}, dist=-{:.3f} = {:.3f}",
                 nodeId, ftScore, vecScore, typeBoost, propertyBoost, distancePenalty, finalScore);
        
        return Math.max(0.0, finalScore);
    }

    /**
     * Calculates shortest distances from start nodes using BFS
     */
    private Map<String, Integer> calculateDistancesFromStart(SubGraph subGraph, List<String> startNodeIds) {
        Map<String, Integer> distances = new HashMap<>();
        Queue<String> queue = new LinkedList<>();
        
        // Initialize start nodes with distance 0
        for (String startId : startNodeIds) {
            if (subGraph.getNodes().containsKey(startId)) {
                distances.put(startId, 0);
                queue.offer(startId);
            }
        }
        
        // Build adjacency list for efficient traversal
        Map<String, Set<String>> adjacency = buildAdjacencyList(subGraph);
        
        // BFS to calculate distances
        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            int currentDistance = distances.get(currentId);
            
            Set<String> neighbors = adjacency.getOrDefault(currentId, Collections.emptySet());
            for (String neighborId : neighbors) {
                if (!distances.containsKey(neighborId)) {
                    distances.put(neighborId, currentDistance + 1);
                    queue.offer(neighborId);
                }
            }
        }
        
        return distances;
    }

    /**
     * Builds adjacency list from subgraph relationships
     */
    private Map<String, Set<String>> buildAdjacencyList(SubGraph subGraph) {
        Map<String, Set<String>> adjacency = new HashMap<>();
        
        for (GraphExpander.GraphRelationship rel : subGraph.getRelationships()) {
            adjacency.computeIfAbsent(rel.getStartNodeId(), k -> new HashSet<>())
                     .add(rel.getEndNodeId());
            adjacency.computeIfAbsent(rel.getEndNodeId(), k -> new HashSet<>())
                     .add(rel.getStartNodeId());
        }
        
        return adjacency;
    }

    /**
     * Calculates boost based on node type
     */
    private double calculateNodeTypeBoost(GraphNode node) {
        String nodeType = node.getType().toLowerCase();
        
        return switch (nodeType) {
            case "method" -> nodeTypeBoost * 1.0;      // Methods are primary
            case "class" -> nodeTypeBoost * 0.8;       // Classes are important
            case "interface" -> nodeTypeBoost * 0.7;   // Interfaces are relevant
            case "enum" -> nodeTypeBoost * 0.6;        // Enums are relevant
            case "description" -> nodeTypeBoost * 0.9; // Descriptions are valuable
            case "filedoc" -> nodeTypeBoost * 0.6;     // File docs provide context
            default -> 0.0;
        };
    }

    /**
     * Calculates boost based on node properties
     */
    private double calculatePropertyBoost(GraphNode node) {
        double boost = 0.0;
        Map<String, Object> props = node.getProperties();
        
        if (props == null) return boost;
        
        // Boost for public methods/classes (more likely to be important)
        if (Boolean.TRUE.equals(props.get("isPublic"))) {
            boost += 0.1;
        }
        
        // Boost for methods with annotations (often important)
        if (props.containsKey("annotations") && props.get("annotations") != null) {
            Object annotations = props.get("annotations");
            if (annotations instanceof List && !((List<?>) annotations).isEmpty()) {
                boost += 0.15;
            }
        }
        
        // Boost for methods with business tags
        if (props.containsKey("businessTags") && props.get("businessTags") != null) {
            Object businessTags = props.get("businessTags");
            if (businessTags instanceof List && !((List<?>) businessTags).isEmpty()) {
                boost += 0.2;
            }
        }
        
        // Penalty for high complexity
        if (props.containsKey("complexity")) {
            String complexity = (String) props.get("complexity");
            if ("high".equalsIgnoreCase(complexity)) {
                boost -= 0.1;
            }
        }
        
        return boost;
    }

    /**
     * Filters and ranks nodes by score
     */
    public List<ScoredNode> rankNodesByScore(SubGraph subGraph, Map<String, Double> nodeScores) {
        return subGraph.getNodesList().stream()
                .filter(node -> nodeScores.containsKey(node.getId()))
                .map(node -> ScoredNode.builder()
                        .node(node)
                        .score(nodeScores.get(node.getId()))
                        .build())
                .sorted(Comparator.comparing(ScoredNode::getScore).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Applies score-based filtering to subgraph
     */
    public SubGraph filterByScore(SubGraph subGraph, Map<String, Double> nodeScores, double threshold) {
        Set<String> acceptedNodeIds = nodeScores.entrySet().stream()
                .filter(entry -> entry.getValue() >= threshold)
                .map(Map.Entry::getKey)
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
                        "scoreThreshold", threshold,
                        "originalNodeCount", subGraph.getNodeCount(),
                        "filteredNodeCount", filteredNodes.size()
                ))
                .build();
    }

    /**
     * Gets top N nodes by score
     */
    public List<String> getTopNodeIds(Map<String, Double> nodeScores, int n) {
        return nodeScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(n)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Data class for scored nodes
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoredNode {
        private GraphNode node;
        private double score;
        
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