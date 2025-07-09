package com.tekion.javaastkg.query.services;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for configurable graph expansion using n-hop traversal.
 * Supports different expansion strategies and relationship filtering.
 */
@Service
@Slf4j
public class GraphExpander {

    private final Driver neo4jDriver;
    private final SessionConfig sessionConfig;

    @org.springframework.beans.factory.annotation.Value("${query.retrieval.expansion.depth:2}")
    private int expansionDepth;

    @org.springframework.beans.factory.annotation.Value("${query.retrieval.expansion.max-nodes-per-hop:50}")
    private int maxNodesPerHop;

    @org.springframework.beans.factory.annotation.Value("${query.retrieval.expansion.include-all-relationships:true}")
    private boolean includeAllRelationships;

    public GraphExpander(Driver neo4jDriver, SessionConfig sessionConfig) {
        this.neo4jDriver = neo4jDriver;
        this.sessionConfig = sessionConfig;
    }

    /**
     * Expands graph using n-hop traversal with default configuration
     */
    public SubGraph expandNHop(List<String> startNodeIds) {
        return expandNHop(startNodeIds, expansionDepth, maxNodesPerHop);
    }

    /**
     * Expands graph using n-hop traversal with custom parameters
     */
    public SubGraph expandNHop(List<String> startNodeIds, int depth, int maxNodes) {
        log.info("GRAPH_EXPANDER: Expanding graph from {} nodes with depth={}, maxNodes={}", 
                 startNodeIds.size(), depth, maxNodes);
        
        // Log some sample node IDs
        startNodeIds.stream()
                .limit(3)
                .forEach(nodeId -> log.info("GRAPH_EXPANDER: Sample start node ID: {}", nodeId));

        if (startNodeIds.isEmpty()) {
            return SubGraph.builder()
                    .nodes(new HashMap<>())
                    .relationships(new ArrayList<>())
                    .metadata(Map.of("reason", "empty_start_nodes"))
                    .build();
        }

        try (Session session = neo4jDriver.session(sessionConfig)) {
            // Generic n-hop traversal query
            String query = buildExpansionQuery(depth, maxNodes);
            log.info("GRAPH_EXPANDER: Using query: {}", query);
            
            Map<String, Object> params = Map.of(
                "nodeIds", startNodeIds,
                "depth", depth,
                "maxNodes", maxNodes
            );

            Result result = session.run(query, params);
            return buildSubGraph(result, startNodeIds);

        } catch (Exception e) {
            log.error("Graph expansion failed for nodes: {}", startNodeIds, e);
            return SubGraph.builder()
                    .nodes(new HashMap<>())
                    .relationships(new ArrayList<>())
                    .metadata(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Expands graph with specific relationship types
     */
    public SubGraph expandWithRelationshipTypes(List<String> startNodeIds, 
                                               List<String> relationshipTypes,
                                               int depth) {
        log.debug("Expanding graph with specific relationships: {}", relationshipTypes);

        if (startNodeIds.isEmpty()) {
            return SubGraph.builder().nodes(new HashMap<>()).relationships(new ArrayList<>()).build();
        }

        try (Session session = neo4jDriver.session(sessionConfig)) {
            String relationshipFilter = relationshipTypes.stream()
                    .map(type -> "'" + type + "'")
                    .collect(Collectors.joining(", "));

            String query = String.format("""
                MATCH (start)
                WHERE start.id IN $nodeIds
                CALL {
                    WITH start
                    MATCH path = (start)-[r*1..%d]-(connected)
                    WHERE type(r) IN [%s]
                      AND (connected:Method OR connected:Class OR connected:Interface)
                    RETURN connected, relationships(path) as rels, length(path) as distance
                    ORDER BY distance
                    LIMIT $maxNodes
                }
                WITH collect(DISTINCT connected) as nodes,
                     collect(DISTINCT rels) as allRels
                UNWIND allRels as relList
                UNWIND relList as rel
                WITH nodes, collect(DISTINCT rel) as relationships
                RETURN nodes, relationships
                """, depth, relationshipFilter);

            Map<String, Object> params = Map.of(
                "nodeIds", startNodeIds,
                "maxNodes", maxNodesPerHop
            );

            Result result = session.run(query, params);
            return buildSubGraph(result, startNodeIds);

        } catch (Exception e) {
            log.error("Relationship-filtered expansion failed", e);
            return SubGraph.builder().nodes(new HashMap<>()).relationships(new ArrayList<>()).build();
        }
    }

    /**
     * Builds the expansion query based on configuration
     */
    private String buildExpansionQuery(int depth, int maxNodes) {
        if (includeAllRelationships) {
            return String.format("""
                MATCH (start)
                WHERE start.id IN $nodeIds
                CALL {
                    WITH start
                    MATCH path = (start)-[*1..%d]-(connected)
                    WHERE connected:Method OR connected:Class OR connected:Interface
                    RETURN connected, relationships(path) as rels, length(path) as distance
                    ORDER BY distance
                    LIMIT %d
                }
                WITH collect(DISTINCT connected) as nodes,
                     collect(DISTINCT rels) as allRels
                UNWIND allRels as relList
                UNWIND relList as rel
                WITH nodes, collect(DISTINCT rel) as relationships
                RETURN nodes, relationships
                """, depth, maxNodes);
        } else {
            // Only include specific important relationships
            return String.format("""
                MATCH (start)
                WHERE start.id IN $nodeIds
                CALL {
                    WITH start
                    MATCH path = (start)-[r*1..%d]-(connected)
                    WHERE type(r) IN ['CALLS', 'CONTAINS', 'EXTENDS', 'IMPLEMENTS', 'HAS_DESCRIPTION']
                      AND (connected:Method OR connected:Class OR connected:Interface)
                    RETURN connected, relationships(path) as rels, length(path) as distance
                    ORDER BY distance
                    LIMIT %d
                }
                WITH collect(DISTINCT connected) as nodes,
                     collect(DISTINCT rels) as allRels
                UNWIND allRels as relList
                UNWIND relList as rel
                WITH nodes, collect(DISTINCT rel) as relationships
                RETURN nodes, relationships
                """, depth, maxNodes);
        }
    }

    /**
     * Builds SubGraph from Neo4j query result
     */
    private SubGraph buildSubGraph(Result result, List<String> startNodeIds) {
        Map<String, GraphNode> nodes = new HashMap<>();
        List<GraphRelationship> relationships = new ArrayList<>();

        try {
            if (result.hasNext()) {
                Record record = result.single();
                
                // Debug: log the structure of what we got back
                log.info("GRAPH_EXPANDER: Query returned record with keys: {}", record.keys());
                log.info("GRAPH_EXPANDER: Nodes array size: {}", record.get("nodes").size());
                log.info("GRAPH_EXPANDER: Relationships array size: {}", record.get("relationships").size());

                // Process nodes
                for (Value nodeValue : record.get("nodes").values()) {
                    org.neo4j.driver.types.Node neo4jNode = nodeValue.asNode();
                    GraphNode graphNode = convertToGraphNode(neo4jNode);
                    nodes.put(graphNode.getId(), graphNode);
                    
                    // Log details about found nodes
//                    log.info("GRAPH_EXPANDER: Found {} node - id: {}, signature: {}",
//                            graphNode.getType(), graphNode.getId(),
//                            graphNode.getProperties().get("signature"));
                }

                // Process relationships
                for (Value relValue : record.get("relationships").values()) {
                    org.neo4j.driver.types.Relationship neo4jRel = relValue.asRelationship();
                    GraphRelationship graphRel = convertToGraphRelationship(neo4jRel);
                    relationships.add(graphRel);
                }
            }

            log.info("GRAPH_EXPANDER: Built subgraph with {} nodes and {} relationships", 
                     nodes.size(), relationships.size());

            return SubGraph.builder()
                    .nodes(nodes)
                    .relationships(relationships)
                    .metadata(Map.of(
                            "startNodeCount", startNodeIds.size(),
                            "expansionDepth", expansionDepth,
                            "totalNodes", nodes.size(),
                            "totalRelationships", relationships.size()
                    ))
                    .build();

        } catch (Exception e) {
            log.error("Failed to build subgraph from result", e);
            return SubGraph.builder()
                    .nodes(new HashMap<>())
                    .relationships(new ArrayList<>())
                    .metadata(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Converts Neo4j node to GraphNode
     */
    private GraphNode convertToGraphNode(org.neo4j.driver.types.Node node) {
        Map<String, Object> properties = new HashMap<>(node.asMap());

        return GraphNode.builder()
                .id(node.containsKey("id") ? node.get("id").asString() : String.valueOf(node.id()))
                .labels(new ArrayList<>((Collection) node.labels()))
                .properties(properties)
                .build();
    }

    /**
     * Converts Neo4j relationship to GraphRelationship
     */
    private GraphRelationship convertToGraphRelationship(org.neo4j.driver.types.Relationship rel) {
        return GraphRelationship.builder()
                .id(String.valueOf(rel.id()))
                .type(rel.type())
                .startNodeId(String.valueOf(rel.startNodeId()))
                .endNodeId(String.valueOf(rel.endNodeId()))
                .properties(new HashMap<>(rel.asMap()))
                .build();
    }

    /**
     * Filters subgraph by node types
     */
    public SubGraph filterByNodeTypes(SubGraph subGraph, List<String> nodeTypes) {
        Map<String, GraphNode> filteredNodes = subGraph.getNodes().entrySet().stream()
                .filter(entry -> entry.getValue().getLabels().stream()
                        .anyMatch(nodeTypes::contains))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Set<String> filteredNodeIds = filteredNodes.keySet();
        List<GraphRelationship> filteredRels = subGraph.getRelationships().stream()
                .filter(rel -> filteredNodeIds.contains(rel.getStartNodeId()) && 
                              filteredNodeIds.contains(rel.getEndNodeId()))
                .collect(Collectors.toList());

        return SubGraph.builder()
                .nodes(filteredNodes)
                .relationships(filteredRels)
                .metadata(Map.of("filtered", true, "originalSize", subGraph.getNodes().size()))
                .build();
    }

    /**
     * SubGraph data structure
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubGraph {
        private Map<String, GraphNode> nodes;
        private List<GraphRelationship> relationships;
        private Map<String, Object> metadata;

        public int getNodeCount() {
            return nodes != null ? nodes.size() : 0;
        }

        public int getRelationshipCount() {
            return relationships != null ? relationships.size() : 0;
        }

        public boolean isEmpty() {
            return getNodeCount() == 0 && getRelationshipCount() == 0;
        }

        public List<GraphNode> getNodesList() {
            return nodes != null ? new ArrayList<>(nodes.values()) : new ArrayList<>();
        }
    }

    /**
     * Graph node representation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphNode {
        private String id;
        private List<String> labels;
        private Map<String, Object> properties;

        public String getType() {
            return labels != null && !labels.isEmpty() ? labels.get(0) : "Unknown";
        }

        public String getName() {
            if (properties == null) return "Unknown";
            return (String) properties.getOrDefault("name", 
                   properties.getOrDefault("fileName", "Unknown"));
        }
    }

    /**
     * Graph relationship representation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphRelationship {
        private String id;
        private String type;
        private String startNodeId;
        private String endNodeId;
        private Map<String, Object> properties;
    }
}