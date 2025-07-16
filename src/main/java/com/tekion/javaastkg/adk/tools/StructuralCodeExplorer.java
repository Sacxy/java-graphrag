package com.tekion.javaastkg.adk.tools;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import com.tekion.javaastkg.adk.core.BaseAdkTool;
import com.tekion.javaastkg.service.Neo4jService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Structural Code Explorer Tool - Analyzes architectural patterns and relationships
 * 
 * This tool performs graph traversal and pattern detection to understand:
 * - Architectural relationships between code entities
 * - Structural patterns (layered architecture, dependency injection, etc.)
 * - Code organization and coupling metrics
 * - Architectural insights and recommendations
 */
@Slf4j
public class StructuralCodeExplorer extends BaseAdkTool {

    private static Neo4jService neo4jService;
    
    /**
     * Initialize the tool with Neo4j service
     * Called by Spring context during tool registration
     */
    public static void initialize(Neo4jService service) {
        neo4jService = service;
    }

    /**
     * Explores structural relationships and patterns around code entities
     * 
     * @param seedEntities Starting points for exploration (class/method IDs)
     * @param explorationScope Configuration for exploration depth and breadth
     * @param focusAreas Optional areas to emphasize during exploration
     * @param ctx Tool context for state management
     * @return Structural analysis results with graph, patterns, and insights
     */
    @Schema(description = "Explore structural relationships and patterns around code entities")
    public static Map<String, Object> exploreStructure(
            @Schema(description = "Seed entities to start exploration from") List<String> seedEntities,
            @Schema(description = "Exploration scope and configuration") Map<String, Object> explorationScope,
            @Schema(description = "Optional focus areas") Map<String, Object> focusAreas,
            @Schema(name = "toolContext") ToolContext ctx
    ) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Validate inputs
            if (seedEntities == null || seedEntities.isEmpty()) {
                return errorResponse("exploreStructure", "Seed entities cannot be null or empty");
            }
            
            // Extract exploration parameters
            String scope = (String) explorationScope.getOrDefault("scope", "MODERATE");
            int maxDepth = (Integer) explorationScope.getOrDefault("maxDepth", 3);
            int maxNodes = (Integer) explorationScope.getOrDefault("maxNodes", 100);
            
            log.info("Starting structural exploration for {} entities with scope: {}, maxDepth: {}, maxNodes: {}", 
                    seedEntities.size(), scope, maxDepth, maxNodes);
            
            // Select exploration strategy
            ExplorationStrategy strategy = selectExplorationStrategy(scope, seedEntities.size());
            
            // Build structural graph
            StructuralGraph graph = buildStructuralGraph(seedEntities, strategy, maxDepth, maxNodes);
            
            // Detect architectural patterns
            List<ArchitecturalPattern> patterns = detectArchitecturalPatterns(graph);
            
            // Generate architectural insights
            List<ArchitecturalInsight> insights = generateArchitecturalInsights(graph, patterns);
            
            // Update context state
            updateContextState(ctx, graph, patterns);
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Structural exploration completed in {}ms: {} nodes, {} edges, {} patterns", 
                    executionTime, graph.getNodeCount(), graph.getEdgeCount(), patterns.size());
            
            return Map.of(
                "status", "success",
                "graph", graph.toMap(),
                "patterns", patterns.stream().map(ArchitecturalPattern::toMap).collect(Collectors.toList()),
                "insights", insights.stream().map(ArchitecturalInsight::toMap).collect(Collectors.toList()),
                "explorationMetadata", Map.of(
                    "strategy", strategy.name(),
                    "nodesExplored", graph.getNodeCount(),
                    "relationshipsFound", graph.getEdgeCount(),
                    "patternsDetected", patterns.size(),
                    "executionTimeMs", executionTime
                ),
                "nextActions", determineNextActions(graph, patterns, scope)
            );
            
        } catch (Exception e) {
            log.error("Structural exploration failed for entities: {}", seedEntities, e);
            return errorResponse("exploreStructure", "Structural exploration failed: " + e.getMessage());
        }
    }
    
    /**
     * Selects the appropriate exploration strategy based on scope and entity count
     */
    private static ExplorationStrategy selectExplorationStrategy(String scope, int entityCount) {
        switch (scope.toUpperCase()) {
            case "FOCUSED":
                return ExplorationStrategy.FOCUSED;
            case "COMPREHENSIVE":
                return ExplorationStrategy.COMPREHENSIVE;
            case "MODERATE":
            default:
                return ExplorationStrategy.LAYERED;
        }
    }
    
    /**
     * Builds the structural graph by traversing relationships from seed entities
     */
    private static StructuralGraph buildStructuralGraph(List<String> seedEntities, ExplorationStrategy strategy, 
                                                       int maxDepth, int maxNodes) {
        
        StructuralGraph graph = new StructuralGraph();
        Set<String> visitedNodes = new HashSet<>();
        Queue<String> nodesToExplore = new LinkedList<>(seedEntities);
        
        // Initialize Neo4j service if not already done
        if (neo4jService == null) {
            throw new RuntimeException("Neo4j service not initialized");
        }
        
        int currentDepth = 0;
        
        while (!nodesToExplore.isEmpty() && currentDepth < maxDepth && graph.getNodeCount() < maxNodes) {
            
            int nodesAtCurrentDepth = nodesToExplore.size();
            
            for (int i = 0; i < nodesAtCurrentDepth && !nodesToExplore.isEmpty(); i++) {
                String nodeId = nodesToExplore.poll();
                
                if (visitedNodes.contains(nodeId)) continue;
                visitedNodes.add(nodeId);
                
                // Get node details
                NodeDetails nodeDetails = getNodeDetails(nodeId);
                if (nodeDetails != null) {
                    graph.addNode(nodeDetails);
                    
                    // Get relationships based on strategy
                    List<RelationshipDetails> relationships = getRelationships(nodeId, strategy);
                    
                    for (RelationshipDetails rel : relationships) {
                        graph.addEdge(rel);
                        
                        // Add target node to exploration queue if not visited
                        if (!visitedNodes.contains(rel.getTargetId()) && graph.getNodeCount() < maxNodes) {
                            nodesToExplore.offer(rel.getTargetId());
                        }
                    }
                }
            }
            
            currentDepth++;
        }
        
        return graph;
    }
    
    /**
     * Gets detailed information about a node from Neo4j
     */
    private static NodeDetails getNodeDetails(String nodeId) {
        String query = """
            MATCH (n {id: $nodeId})
            RETURN n.id as id, 
                   labels(n) as labels,
                   n.name as name,
                   n.type as type,
                   n.className as className,
                   n.packageName as packageName,
                   n.fullName as fullName,
                   n.signature as signature,
                   n.sourceFile as sourceFile,
                   n.lineNumber as lineNumber
            """;
        
        try {
            List<Map<String, Object>> results = neo4jService.executeCypherQuery(query, Map.of("nodeId", nodeId));
            
            if (!results.isEmpty()) {
                Map<String, Object> record = results.get(0);
                return new NodeDetails(
                    (String) record.get("id"),
                    (List<String>) record.getOrDefault("labels", List.of()),
                    (String) record.getOrDefault("name", ""),
                    (String) record.getOrDefault("type", ""),
                    (String) record.getOrDefault("className", ""),
                    (String) record.getOrDefault("packageName", ""),
                    (String) record.getOrDefault("fullName", ""),
                    (String) record.getOrDefault("signature", ""),
                    (String) record.getOrDefault("sourceFile", ""),
                    (Integer) record.getOrDefault("lineNumber", 0)
                );
            }
        } catch (Exception e) {
            log.warn("Failed to get node details for: {}", nodeId, e);
        }
        return null;
    }
    
    /**
     * Gets relationships for a node based on exploration strategy
     */
    private static List<RelationshipDetails> getRelationships(String nodeId, ExplorationStrategy strategy) {
        String query = buildRelationshipQuery(strategy);
        
        try {
            List<Map<String, Object>> results = neo4jService.executeCypherQuery(query, Map.of("nodeId", nodeId));
            
            return results.stream().map(record -> new RelationshipDetails(
                (String) record.get("sourceId"),
                (String) record.get("targetId"),
                (String) record.get("relationshipType"),
                (Double) record.getOrDefault("weight", 1.0),
                (Map<String, Object>) record.getOrDefault("properties", Map.of())
            )).collect(Collectors.toList());
            
        } catch (Exception e) {
            log.warn("Failed to get relationships for node: {}", nodeId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Builds the appropriate Cypher query based on exploration strategy
     */
    private static String buildRelationshipQuery(ExplorationStrategy strategy) {
        switch (strategy) {
            case FOCUSED:
                return """
                    MATCH (n {id: $nodeId})-[r:CONTAINS|EXTENDS|IMPLEMENTS]->(target)
                    RETURN n.id as sourceId, target.id as targetId, type(r) as relationshipType,
                           r.weight as weight, properties(r) as properties
                    LIMIT 10
                    """;
                    
            case COMPREHENSIVE:
                return """
                    MATCH (n {id: $nodeId})-[r]->(target)
                    WHERE type(r) IN ['CONTAINS', 'EXTENDS', 'IMPLEMENTS', 'CALLS', 'DEPENDS_ON', 
                                     'HAS_PARAMETER', 'HAS_FIELD', 'USES_FIELD', 'RETURNS', 'THROWS',
                                     'ANNOTATED_BY', 'OVERRIDES', 'INSTANTIATES']
                    RETURN n.id as sourceId, target.id as targetId, type(r) as relationshipType,
                           r.weight as weight, properties(r) as properties
                    LIMIT 50
                    """;
                    
            case LAYERED:
            default:
                return """
                    MATCH (n {id: $nodeId})-[r]->(target)
                    WHERE type(r) IN ['CONTAINS', 'EXTENDS', 'IMPLEMENTS', 'CALLS', 'DEPENDS_ON', 'HAS_FIELD', 'USES_FIELD']
                    RETURN n.id as sourceId, target.id as targetId, type(r) as relationshipType,
                           r.weight as weight, properties(r) as properties
                    LIMIT 25
                    """;
        }
    }
    
    /**
     * Detects architectural patterns in the structural graph
     */
    private static List<ArchitecturalPattern> detectArchitecturalPatterns(StructuralGraph graph) {
        List<ArchitecturalPattern> patterns = new ArrayList<>();
        
        // Pattern 1: Layered Architecture (Service-Repository pattern)
        ArchitecturalPattern layeredPattern = detectLayeredArchitecture(graph);
        if (layeredPattern != null) patterns.add(layeredPattern);
        
        // Pattern 2: Dependency Injection
        ArchitecturalPattern diPattern = detectDependencyInjection(graph);
        if (diPattern != null) patterns.add(diPattern);
        
        // Pattern 3: Circular Dependencies
        ArchitecturalPattern circularPattern = detectCircularDependencies(graph);
        if (circularPattern != null) patterns.add(circularPattern);
        
        // Pattern 4: High Coupling
        ArchitecturalPattern couplingPattern = detectHighCoupling(graph);
        if (couplingPattern != null) patterns.add(couplingPattern);
        
        return patterns;
    }
    
    /**
     * Detects layered architecture patterns
     */
    private static ArchitecturalPattern detectLayeredArchitecture(StructuralGraph graph) {
        // Look for Service -> Repository pattern
        int serviceRepoConnections = 0;
        int totalServices = 0;
        
        for (NodeDetails node : graph.getNodes()) {
            if (node.getName().contains("Service")) {
                totalServices++;
                
                // Check if this service calls repositories
                for (RelationshipDetails edge : graph.getEdges()) {
                    if (edge.getSourceId().equals(node.getId()) && 
                        edge.getType().equals("CALLS") &&
                        graph.getNode(edge.getTargetId()) != null &&
                        graph.getNode(edge.getTargetId()).getName().contains("Repository")) {
                        serviceRepoConnections++;
                        break;
                    }
                }
            }
        }
        
        if (totalServices > 0) {
            double confidence = (double) serviceRepoConnections / totalServices;
            if (confidence > 0.5) {
                return new ArchitecturalPattern(
                    "LAYERED_ARCHITECTURE",
                    "Service-Repository pattern detected",
                    confidence,
                    Map.of("serviceCount", totalServices, "repoConnections", serviceRepoConnections)
                );
            }
        }
        
        return null;
    }
    
    /**
     * Detects dependency injection patterns
     */
    private static ArchitecturalPattern detectDependencyInjection(StructuralGraph graph) {
        int diConnections = 0;
        int totalClasses = 0;
        
        for (NodeDetails node : graph.getNodes()) {
            if (node.getLabels().contains("Class")) {
                totalClasses++;
                
                // Check for field injection patterns
                for (RelationshipDetails edge : graph.getEdges()) {
                    if (edge.getSourceId().equals(node.getId()) && 
                        (edge.getType().equals("HAS_FIELD") || edge.getType().equals("USES_FIELD"))) {
                        diConnections++;
                        break;
                    }
                }
            }
        }
        
        if (totalClasses > 0) {
            double confidence = Math.min(1.0, (double) diConnections / totalClasses);
            if (confidence > 0.3) {
                return new ArchitecturalPattern(
                    "DEPENDENCY_INJECTION",
                    "Dependency injection pattern detected",
                    confidence,
                    Map.of("classCount", totalClasses, "diConnections", diConnections)
                );
            }
        }
        
        return null;
    }
    
    /**
     * Detects circular dependencies
     */
    private static ArchitecturalPattern detectCircularDependencies(StructuralGraph graph) {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        int circularCount = 0;
        
        for (NodeDetails node : graph.getNodes()) {
            if (!visited.contains(node.getId())) {
                if (hasCycleDFS(node.getId(), visited, recursionStack, graph)) {
                    circularCount++;
                }
            }
        }
        
        if (circularCount > 0) {
            return new ArchitecturalPattern(
                "CIRCULAR_DEPENDENCY",
                "Circular dependencies detected",
                1.0,
                Map.of("circularCount", circularCount)
            );
        }
        
        return null;
    }
    
    /**
     * DFS helper for cycle detection
     */
    private static boolean hasCycleDFS(String nodeId, Set<String> visited, Set<String> recursionStack, StructuralGraph graph) {
        visited.add(nodeId);
        recursionStack.add(nodeId);
        
        for (RelationshipDetails edge : graph.getEdges()) {
            if (edge.getSourceId().equals(nodeId)) {
                String targetId = edge.getTargetId();
                
                if (!visited.contains(targetId)) {
                    if (hasCycleDFS(targetId, visited, recursionStack, graph)) {
                        return true;
                    }
                } else if (recursionStack.contains(targetId)) {
                    return true;
                }
            }
        }
        
        recursionStack.remove(nodeId);
        return false;
    }
    
    /**
     * Detects high coupling patterns
     */
    private static ArchitecturalPattern detectHighCoupling(StructuralGraph graph) {
        Map<String, Integer> connectionCounts = new HashMap<>();
        
        for (RelationshipDetails edge : graph.getEdges()) {
            connectionCounts.merge(edge.getSourceId(), 1, Integer::sum);
        }
        
        int highCouplingNodes = 0;
        int totalNodes = graph.getNodeCount();
        
        for (Integer count : connectionCounts.values()) {
            if (count > 5) { // Threshold for high coupling
                highCouplingNodes++;
            }
        }
        
        if (totalNodes > 0) {
            double confidence = (double) highCouplingNodes / totalNodes;
            if (confidence > 0.2) {
                return new ArchitecturalPattern(
                    "HIGH_COUPLING",
                    "High coupling detected",
                    confidence,
                    Map.of("highCouplingNodes", highCouplingNodes, "totalNodes", totalNodes)
                );
            }
        }
        
        return null;
    }
    
    /**
     * Generates architectural insights based on graph and patterns
     */
    private static List<ArchitecturalInsight> generateArchitecturalInsights(StructuralGraph graph, List<ArchitecturalPattern> patterns) {
        List<ArchitecturalInsight> insights = new ArrayList<>();
        
        // Insight 1: Coupling analysis
        insights.add(analyzeCoupling(graph));
        
        // Insight 2: Pattern-based insights
        for (ArchitecturalPattern pattern : patterns) {
            insights.add(generatePatternInsight(pattern));
        }
        
        // Insight 3: Complexity analysis
        insights.add(analyzeComplexity(graph));
        
        return insights.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }
    
    /**
     * Analyzes coupling in the graph
     */
    private static ArchitecturalInsight analyzeCoupling(StructuralGraph graph) {
        double avgConnections = graph.getEdgeCount() / (double) Math.max(1, graph.getNodeCount());
        
        String severity = avgConnections > 5 ? "HIGH" : (avgConnections > 3 ? "MEDIUM" : "LOW");
        
        return new ArchitecturalInsight(
            "Coupling Analysis",
            "COUPLING",
            severity,
            String.format("Average connections per node: %.2f", avgConnections),
            Map.of("avgConnections", avgConnections, "totalNodes", graph.getNodeCount())
        );
    }
    
    /**
     * Generates insight from architectural pattern
     */
    private static ArchitecturalInsight generatePatternInsight(ArchitecturalPattern pattern) {
        String severity = pattern.getConfidence() > 0.8 ? "HIGH" : (pattern.getConfidence() > 0.5 ? "MEDIUM" : "LOW");
        
        return new ArchitecturalInsight(
            pattern.getType() + " Pattern",
            "PATTERN",
            severity,
            pattern.getDescription(),
            pattern.getMetadata()
        );
    }
    
    /**
     * Analyzes complexity in the graph
     */
    private static ArchitecturalInsight analyzeComplexity(StructuralGraph graph) {
        int nodeCount = graph.getNodeCount();
        int edgeCount = graph.getEdgeCount();
        
        // Complexity metric: edges per node ratio
        double complexity = edgeCount / (double) Math.max(1, nodeCount);
        String severity = complexity > 3 ? "HIGH" : (complexity > 2 ? "MEDIUM" : "LOW");
        
        return new ArchitecturalInsight(
            "Complexity Analysis",
            "COMPLEXITY",
            severity,
            String.format("Graph complexity ratio: %.2f", complexity),
            Map.of("complexity", complexity, "nodeCount", nodeCount, "edgeCount", edgeCount)
        );
    }
    
    /**
     * Updates context state with exploration results
     */
    private static void updateContextState(ToolContext ctx, StructuralGraph graph, List<ArchitecturalPattern> patterns) {
        if (ctx != null && ctx.state() != null) {
            ctx.state().put("app:explored_entities", graph.getNodeIds());
            ctx.state().put("app:key_patterns", patterns.stream()
                .map(ArchitecturalPattern::getType).collect(Collectors.toList()));
            ctx.state().put("app:graph_size", graph.getNodeCount());
        }
    }
    
    /**
     * Determines next actions based on analysis results
     */
    private static List<String> determineNextActions(StructuralGraph graph, List<ArchitecturalPattern> patterns, String scope) {
        List<String> actions = new ArrayList<>();
        
        // If circular dependencies found, suggest deeper analysis
        if (patterns.stream().anyMatch(p -> p.getType().equals("CIRCULAR_DEPENDENCY"))) {
            actions.add("ANALYZE_CIRCULAR_DEPENDENCIES");
        }
        
        // If high coupling detected, suggest refactoring
        if (patterns.stream().anyMatch(p -> p.getType().equals("HIGH_COUPLING"))) {
            actions.add("SUGGEST_REFACTORING");
        }
        
        // If focused scope, suggest expanding
        if ("FOCUSED".equals(scope)) {
            actions.add("EXPAND_EXPLORATION");
        }
        
        // If many patterns found, suggest documentation
        if (patterns.size() > 2) {
            actions.add("GENERATE_DOCUMENTATION");
        }
        
        return actions;
    }
    
    // Data classes for structural analysis
    
    enum ExplorationStrategy {
        FOCUSED,    // Direct relationships only
        LAYERED,    // Layer-by-layer expansion
        COMPREHENSIVE // Deep exploration
    }
    
    @Data
    @AllArgsConstructor
    static class NodeDetails {
        private String id;
        private List<String> labels;
        private String name;
        private String type;
        private String className;
        private String packageName;
        private String fullName;
        private String signature;
        private String sourceFile;
        private int lineNumber;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "id", id,
                "labels", labels,
                "name", name,
                "type", type,
                "className", className,
                "packageName", packageName,
                "fullName", fullName,
                "signature", signature,
                "sourceFile", sourceFile,
                "lineNumber", lineNumber
            );
        }
    }
    
    @Data
    @AllArgsConstructor
    static class RelationshipDetails {
        private String sourceId;
        private String targetId;
        private String type;
        private double weight;
        private Map<String, Object> properties;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "sourceId", sourceId,
                "targetId", targetId,
                "type", type,
                "weight", weight,
                "properties", properties != null ? properties : Map.of()
            );
        }
    }
    
    @Data
    @AllArgsConstructor
    static class ArchitecturalPattern {
        private String type;
        private String description;
        private double confidence;
        private Map<String, Object> metadata;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "type", type,
                "description", description,
                "confidence", confidence,
                "metadata", metadata != null ? metadata : Map.of()
            );
        }
    }
    
    @Data
    @AllArgsConstructor
    static class ArchitecturalInsight {
        private String title;
        private String category;
        private String severity;
        private String description;
        private Map<String, Object> metadata;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "title", title,
                "category", category,
                "severity", severity,
                "description", description,
                "metadata", metadata != null ? metadata : Map.of()
            );
        }
    }
    
    /**
     * Structural graph representation for analysis
     */
    static class StructuralGraph {
        private Map<String, NodeDetails> nodes = new HashMap<>();
        private List<RelationshipDetails> edges = new ArrayList<>();
        
        public void addNode(NodeDetails node) {
            nodes.put(node.getId(), node);
        }
        
        public void addEdge(RelationshipDetails edge) {
            edges.add(edge);
        }
        
        public NodeDetails getNode(String id) {
            return nodes.get(id);
        }
        
        public List<NodeDetails> getNodes() {
            return new ArrayList<>(nodes.values());
        }
        
        public List<RelationshipDetails> getEdges() {
            return new ArrayList<>(edges);
        }
        
        public Set<String> getNodeIds() {
            return nodes.keySet();
        }
        
        public int getNodeCount() {
            return nodes.size();
        }
        
        public int getEdgeCount() {
            return edges.size();
        }
        
        public Map<String, Object> toMap() {
            return Map.of(
                "nodes", nodes.values().stream().map(NodeDetails::toMap).collect(Collectors.toList()),
                "edges", edges.stream().map(RelationshipDetails::toMap).collect(Collectors.toList()),
                "nodeCount", getNodeCount(),
                "edgeCount", getEdgeCount()
            );
        }
    }
}