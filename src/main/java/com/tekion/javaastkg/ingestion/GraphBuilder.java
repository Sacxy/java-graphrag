package com.tekion.javaastkg.ingestion;

import com.tekion.javaastkg.model.AnalysisResult;
import com.tekion.javaastkg.model.GraphNode;
import com.tekion.javaastkg.model.GraphEdge;
import com.tekion.javaastkg.model.GraphMetadata;
import com.tekion.javaastkg.model.NodeType;
import com.tekion.javaastkg.model.EdgeType;
import org.neo4j.driver.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for constructing the knowledge graph in Neo4j.
 * Handles node creation and relationship establishment.
 */
@Service
@Slf4j
public class GraphBuilder {

    private final Driver neo4jDriver;
    private final SessionConfig sessionConfig;
    private final CypherQueryLoader queryLoader;

    @Autowired
    public GraphBuilder(Driver neo4jDriver, SessionConfig sessionConfig, CypherQueryLoader queryLoader) {
        this.neo4jDriver = neo4jDriver;
        this.sessionConfig = sessionConfig;
        this.queryLoader = queryLoader;
    }

    /**
     * Builds the complete graph from the AST data.
     * Creates nodes first, then establishes relationships.
     */
    public void buildGraph(AnalysisResult analysisResult) {
        log.info("Starting graph construction with {} nodes and {} edges",
                analysisResult.getNodes() != null ? analysisResult.getNodes().size() : 0,
                analysisResult.getEdges() != null ? analysisResult.getEdges().size() : 0);
        
        // Log metadata insights before building
        if (analysisResult.getMetadata() != null) {
            GraphMetadata metadata = analysisResult.getMetadata();
//            log.info("Graph Metadata Overview:");
//            log.info("  - Total Nodes: {} (expected: {})",
//                analysisResult.getNodes() != null ? analysisResult.getNodes().size() : 0,
//                metadata.getTotalNodes());
//            log.info("  - Total Edges: {} (expected: {})",
//                analysisResult.getEdges() != null ? analysisResult.getEdges().size() : 0,
//                metadata.getTotalEdges());
//
//            if (metadata.getNodeTypeCount() != null && !metadata.getNodeTypeCount().isEmpty()) {
//                log.info("  - Node Types: {}", metadata.getNodeTypeCount().keySet());
//            }
//            if (metadata.getEdgeTypeCount() != null && !metadata.getEdgeTypeCount().isEmpty()) {
//                log.info("  - Edge Types: {}", metadata.getEdgeTypeCount().keySet());
//            }
        }

        long startTime = System.currentTimeMillis();

        try (Session session = neo4jDriver.session(sessionConfig)) {
            // Clear existing data (for POC - in production, use incremental updates)
            clearGraph(session);

            // Create constraints and indexes
            createConstraintsAndIndexes(session);

            // Step 1: Create all nodes
            createNodes(session, analysisResult.getNodes());

            // Step 2: Create relationships
            createEdges(session, analysisResult.getEdges());

            long duration = System.currentTimeMillis() - startTime;
            log.info("Graph construction completed in {} ms", duration);

        } catch (Exception e) {
            log.error("Failed to build graph", e);
            throw new RuntimeException("Graph construction failed", e);
        }
    }

    /**
     * Clears existing graph data - use with caution!
     */
    private void clearGraph(Session session) {
        log.warn("Clearing existing graph data and embeddings");
        
        // Clear all nodes and relationships
        session.run("MATCH (n) DETACH DELETE n").consume();
        
        // Clear vector index if it exists
        try {
            session.run("DROP INDEX method_embeddings IF EXISTS").consume();
            log.info("Dropped existing vector index");
        } catch (Exception e) {
            log.debug("No existing vector index to drop");
        }
    }

    /**
     * Creates database constraints and indexes for performance
     */
    private void createConstraintsAndIndexes(Session session) {
        log.info("Creating constraints and indexes");
        
        // First, drop any old constraints that might conflict
        try {
            session.run("DROP CONSTRAINT method_unique IF EXISTS").consume();
            log.info("Dropped old method_unique constraint");
        } catch (Exception e) {
            log.debug("No method_unique constraint to drop");
        }
        
        // Drop any other potentially conflicting constraints
        try {
            session.run("DROP CONSTRAINT class_unique IF EXISTS").consume();
            session.run("DROP CONSTRAINT field_unique IF EXISTS").consume();
            session.run("DROP CONSTRAINT package_unique IF EXISTS").consume();
            log.info("Dropped other old uniqueness constraints");
        } catch (Exception e) {
            log.debug("No other old constraints to drop");
        }

        // Create new constraints based on id
        session.run("CREATE CONSTRAINT IF NOT EXISTS FOR (n:GraphNode) REQUIRE n.id IS UNIQUE").consume();
        session.run("CREATE CONSTRAINT IF NOT EXISTS FOR (m:Method) REQUIRE m.id IS UNIQUE").consume();
        session.run("CREATE CONSTRAINT IF NOT EXISTS FOR (c:Class) REQUIRE c.id IS UNIQUE").consume();
        session.run("CREATE CONSTRAINT IF NOT EXISTS FOR (i:Interface) REQUIRE i.id IS UNIQUE").consume();
        session.run("CREATE CONSTRAINT IF NOT EXISTS FOR (f:Field) REQUIRE f.id IS UNIQUE").consume();
        session.run("CREATE CONSTRAINT IF NOT EXISTS FOR (p:Package) REQUIRE p.id IS UNIQUE").consume();

        // Indexes for common query patterns
        session.run("CREATE INDEX IF NOT EXISTS FOR (n:GraphNode) ON (n.type)").consume();
        session.run("CREATE INDEX IF NOT EXISTS FOR (n:GraphNode) ON (n.label)").consume();
        session.run("CREATE INDEX IF NOT EXISTS FOR (n:GraphNode) ON (n.sourceFile)").consume();
        
        // Specific indexes for common node types
        session.run("CREATE INDEX IF NOT EXISTS FOR (c:Class) ON (c.name)").consume();
        session.run("CREATE INDEX IF NOT EXISTS FOR (m:Method) ON (m.name)").consume();
        session.run("CREATE INDEX IF NOT EXISTS FOR (m:Method) ON (m.signature)").consume();
        session.run("CREATE INDEX IF NOT EXISTS FOR (f:Field) ON (f.name)").consume();
        session.run("CREATE INDEX IF NOT EXISTS FOR (p:Package) ON (p.name)").consume();
    }

    /**
     * Creates all nodes from the graph structure
     */
    private void createNodes(Session session, List<GraphNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        
        log.info("Creating {} graph nodes", nodes.size());
        
        // Group nodes by type for efficient batch processing
        Map<NodeType, List<GraphNode>> nodesByType = nodes.stream()
                .filter(node -> node.getType() != null)
                .collect(Collectors.groupingBy(GraphNode::getType));
        
        log.info("Processing nodes grouped by {} different node types", nodesByType.size());
        
        // Process each node type with its specific label
        nodesByType.forEach((nodeType, nodeList) -> {
            createNodesOfType(session, nodeType, nodeList);
        });
        
        // Log summary of created nodes
        log.info("Node creation summary by type:");
        nodesByType.forEach((type, list) -> log.info("  - {}: {} nodes", type, list.size()));
    }
    
    /**
     * Creates nodes of a specific type using the proper label in Neo4j
     */
    private void createNodesOfType(Session session, NodeType nodeType, List<GraphNode> nodes) {
        log.info("Creating {} nodes of type {}", nodes.size(), nodeType);
        
        String query;
        
        // Use switch-case to get the appropriate query for each node type
        switch (nodeType) {
            case PACKAGE:
                query = queryLoader.getNodeQuery("package");
                break;
            case CLASS:
                query = queryLoader.getNodeQuery("class");
                break;
            case INTERFACE:
                query = queryLoader.getNodeQuery("interface");
                break;
            case ENUM:
                query = """
                    UNWIND $nodes AS node
                    MERGE (n:Enum {id: node.id})
                    SET n += {
                        type: node.type,
                        label: node.label,
                        sourceFile: node.sourceFile,
                        lineNumber: node.lineNumber,
                        columnNumber: node.columnNumber,
                        createdAt: datetime()
                    }
                    SET n += node.properties
                    """;
                break;
            case ANNOTATION_TYPE:
                query = """
                    UNWIND $nodes AS node
                    MERGE (n:AnnotationType {id: node.id})
                    SET n += {
                        type: node.type,
                        label: node.label,
                        sourceFile: node.sourceFile,
                        lineNumber: node.lineNumber,
                        columnNumber: node.columnNumber,
                        createdAt: datetime()
                    }
                    SET n += node.properties
                    """;
                break;
            case METHOD:
                query = queryLoader.getNodeQuery("method");
                break;
            case FIELD:
                query = queryLoader.getNodeQuery("field");
                break;
            case PARAMETER:
                query = """
                    UNWIND $nodes AS node
                    MERGE (n:Parameter {id: node.id})
                    SET n += {
                        type: node.type,
                        label: node.label,
                        sourceFile: node.sourceFile,
                        lineNumber: node.lineNumber,
                        columnNumber: node.columnNumber,
                        createdAt: datetime()
                    }
                    SET n += node.properties
                    """;
                break;
            case ANNOTATION:
                query = """
                    UNWIND $nodes AS node
                    MERGE (n:Annotation {id: node.id})
                    SET n += {
                        type: node.type,
                        label: node.label,
                        sourceFile: node.sourceFile,
                        lineNumber: node.lineNumber,
                        columnNumber: node.columnNumber,
                        createdAt: datetime()
                    }
                    SET n += node.properties
                    """;
                break;
            case LAMBDA:
                query = """
                    UNWIND $nodes AS node
                    MERGE (n:Lambda {id: node.id})
                    SET n += {
                        type: node.type,
                        label: node.label,
                        sourceFile: node.sourceFile,
                        lineNumber: node.lineNumber,
                        columnNumber: node.columnNumber,
                        createdAt: datetime()
                    }
                    SET n += node.properties
                    """;
                break;
            case ANONYMOUS_CLASS:
                query = """
                    UNWIND $nodes AS node
                    MERGE (n:AnonymousClass {id: node.id})
                    SET n += {
                        type: node.type,
                        label: node.label,
                        sourceFile: node.sourceFile,
                        lineNumber: node.lineNumber,
                        columnNumber: node.columnNumber,
                        createdAt: datetime()
                    }
                    SET n += node.properties
                    """;
                break;
            case GENERIC_TYPE:
                query = """
                    UNWIND $nodes AS node
                    MERGE (n:GenericType {id: node.id})
                    SET n += {
                        type: node.type,
                        label: node.label,
                        sourceFile: node.sourceFile,
                        lineNumber: node.lineNumber,
                        columnNumber: node.columnNumber,
                        createdAt: datetime()
                    }
                    SET n += node.properties
                    """;
                break;
            case LOCAL_VARIABLE:
                query = """
                    UNWIND $nodes AS node
                    MERGE (n:LocalVariable {id: node.id})
                    SET n += {
                        type: node.type,
                        label: node.label,
                        sourceFile: node.sourceFile,
                        lineNumber: node.lineNumber,
                        columnNumber: node.columnNumber,
                        createdAt: datetime()
                    }
                    SET n += node.properties
                    """;
                break;
            default:
                log.warn("Unknown node type: {}, using generic GraphNode", nodeType);
                query = """
                    UNWIND $nodes AS node
                    MERGE (n:GraphNode {id: node.id})
                    SET n += {
                        type: node.type,
                        label: node.label,
                        sourceFile: node.sourceFile,
                        lineNumber: node.lineNumber,
                        columnNumber: node.columnNumber,
                        createdAt: datetime()
                    }
                    SET n += node.properties
                    """;
        }

        int batchSize = 100;
        
        for (int i = 0; i < nodes.size(); i += batchSize) {
            List<Map<String, Object>> batch = nodes
                    .subList(i, Math.min(i + batchSize, nodes.size()))
                    .stream()
                    .map(this::nodeToMap)
                    .collect(Collectors.toList());

            try {
                session.run(query, Map.of("nodes", batch)).consume();
                log.debug("Created {} {} nodes", batch.size(), nodeType);
            } catch (Exception e) {
                log.error("Failed to create {} nodes: {}", nodeType, e.getMessage());
            }
        }
    }

    /**
     * Creates all edges from the graph structure
     */
    private void createEdges(Session session, List<GraphEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return;
        }
        
        log.info("Creating {} graph edges", edges.size());
        
        // Group edges by type for efficient batch processing
        Map<EdgeType, List<GraphEdge>> edgesByType = edges.stream()
                .filter(edge -> edge.getType() != null)
                .collect(Collectors.groupingBy(GraphEdge::getType));
        
        log.info("Processing edges grouped by {} different relationship types", edgesByType.size());
        
        // Process each edge type with its specific Cypher query
        edgesByType.forEach((edgeType, edgeList) -> {
            createEdgesOfType(session, edgeType, edgeList);
        });
    }
    
    /**
     * Creates edges of a specific type using the proper relationship type in Neo4j
     */
    private void createEdgesOfType(Session session, EdgeType edgeType, List<GraphEdge> edges) {
        log.info("Creating {} edges of type {}", edges.size(), edgeType);
        
        String query;
        
        // Use switch-case to handle each edge type
        switch (edgeType) {
            case CONTAINS:
                query = queryLoader.getEdgeQuery("contains");
                break;
            case CONTAINS_INNER_CLASS:
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:CONTAINS_INNER_CLASS {
                        id: edge.id,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
                break;
            case CONTAINS_LAMBDA:
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:CONTAINS_LAMBDA {
                        id: edge.id,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
                break;
            case EXTENDS:
                query = queryLoader.getEdgeQuery("extends");
                break;
            case IMPLEMENTS:
                query = queryLoader.getEdgeQuery("implements");
                break;
            case CALLS:
                query = queryLoader.getEdgeQuery("calls");
                break;
            case OVERRIDES:
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:OVERRIDES {
                        id: edge.id,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
                break;
            case METHOD_REFERENCE:
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:METHOD_REFERENCE {
                        id: edge.id,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
                break;
            case HAS_PARAMETER:
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:HAS_PARAMETER {
                        id: edge.id,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
                break;
            case HAS_FIELD:
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:HAS_FIELD {
                        id: edge.id,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
                break;
            case USES_FIELD:
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:USES_FIELD {
                        id: edge.id,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
                break;
            case RETURNS:
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:RETURNS {
                        id: edge.id,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
                break;
            case THROWS:
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:THROWS {
                        id: edge.id,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
                break;
            case ANNOTATED_BY:
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:ANNOTATED_BY {
                        id: edge.id,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
                break;
            case DEPENDS_ON:
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:DEPENDS_ON {
                        id: edge.id,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
                break;
            case PARAMETERIZES:
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:PARAMETERIZES {
                        id: edge.id,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
                break;
            case HAS_TYPE_ARGUMENT:
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:HAS_TYPE_ARGUMENT {
                        id: edge.id,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
                break;
            case CAPTURES:
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:CAPTURES {
                        id: edge.id,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
                break;
            case DECLARES:
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:DECLARES {
                        id: edge.id,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
                break;
            case ACCESSES:
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:ACCESSES {
                        id: edge.id,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
                break;
            case CIRCULAR_DEPENDENCY:
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:CIRCULAR_DEPENDENCY {
                        id: edge.id,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
                break;
            case INSTANTIATES:
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:INSTANTIATES {
                        id: edge.id,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
                break;
            default:
                log.warn("Unknown edge type: {}, using generic RELATIONSHIP", edgeType);
                query = """
                    UNWIND $edges AS edge
                    MATCH (source {id: edge.source})
                    MATCH (target {id: edge.target})
                    CREATE (source)-[r:RELATIONSHIP {
                        id: edge.id,
                        type: edge.type,
                        weight: edge.weight,
                        directed: edge.directed,
                        createdAt: datetime()
                    }]->(target)
                    SET r += edge.properties
                    """;
        }

        int batchSize = 100;
        
        for (int i = 0; i < edges.size(); i += batchSize) {
            List<Map<String, Object>> batch = edges
                    .subList(i, Math.min(i + batchSize, edges.size()))
                    .stream()
                    .map(this::edgeToMap)
                    .collect(Collectors.toList());

            try {
                session.run(query, Map.of("edges", batch)).consume();
                log.debug("Created {} {} relationships", batch.size(), edgeType);
            } catch (Exception e) {
                log.error("Failed to create {} relationships: {}", edgeType, e.getMessage());
            }
        }
    }


    // Helper methods to convert objects to maps for Neo4j

    private Map<String, Object> nodeToMap(GraphNode node) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", node.getId());
        map.put("type", node.getType() != null ? node.getType().toString() : null);
        map.put("label", node.getLabel());
        map.put("sourceFile", node.getSourceFile());
        map.put("lineNumber", node.getLineNumber());
        map.put("columnNumber", node.getColumnNumber());
        
        // Include all properties - this is where class/method/field details are stored
        if (node.getProperties() != null) {
            map.put("properties", node.getProperties());
        }
        
        return map;
    }

    private Map<String, Object> edgeToMap(GraphEdge edge) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", edge.getId());
        map.put("source", edge.getSource());
        map.put("target", edge.getTarget());
        map.put("type", edge.getType() != null ? edge.getType().toString() : null);
        map.put("weight", edge.getWeight());
        map.put("directed", edge.getDirected());
        
        // Include edge properties
        if (edge.getProperties() != null) {
            map.put("properties", edge.getProperties());
        }
        
        return map;
    }
}
