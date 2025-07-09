package com.tekion.javaastkg.query;

import com.tekion.javaastkg.agents.entity.IntelligentEntityExtractor;
import com.tekion.javaastkg.model.ExtractedEntities;
import com.tekion.javaastkg.model.GraphEntities;
import com.tekion.javaastkg.model.QueryModels;
import com.tekion.javaastkg.query.services.*;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Implements hybrid retrieval combining parallel full-text and vector similarity search with graph traversal.
 * This provides both semantic similarity, exact matching, and structural context.
 */
@Service
@Slf4j
public class HybridRetriever {

    private final Driver neo4jDriver;
    private final SessionConfig sessionConfig;
    private final EmbeddingModel embeddingModel;
    private final EnhancedEntityExtractor enhancedEntityExtractor;
    private final ParallelSearchService parallelSearchService;
    private final SearchResultCombiner searchResultCombiner;
    private final GraphExpander graphExpander;
    private final NodeScorer nodeScorer;
    private final ReRankingService reRankingService;

    @org.springframework.beans.factory.annotation.Value("${query.retrieval.score-threshold:0.1}")
    private double scoreThreshold;

    @org.springframework.beans.factory.annotation.Value("${query.retrieval.initial-limit:100}")
    private int initialLimit;

    @org.springframework.beans.factory.annotation.Value("${query.retrieval.graph-expansion-depth:3}")
    private int graphExpansionDepth;

    @Autowired
    public HybridRetriever(Driver neo4jDriver,
                           SessionConfig sessionConfig,
                           @Qualifier("queryEmbeddingModel") EmbeddingModel embeddingModel,
                           EnhancedEntityExtractor enhancedEntityExtractor,
                           ParallelSearchService parallelSearchService,
                           SearchResultCombiner searchResultCombiner,
                           GraphExpander graphExpander,
                           NodeScorer nodeScorer,
                           ReRankingService reRankingService) {
        this.neo4jDriver = neo4jDriver;
        this.sessionConfig = sessionConfig;
        this.embeddingModel = embeddingModel;
        this.enhancedEntityExtractor = enhancedEntityExtractor;
        this.parallelSearchService = parallelSearchService;
        this.searchResultCombiner = searchResultCombiner;
        this.graphExpander = graphExpander;
        this.nodeScorer = nodeScorer;
        this.reRankingService = reRankingService;
    }

    /**
     * Performs hybrid retrieval for a user query using parallel full-text and vector search
     */
    public QueryModels.RetrievalResult retrieve(String query) {
        log.info("Performing hybrid retrieval for query: {}", query);

        try {
            // Step 1: Extract and expand entities from query using enhanced extractor
            ExtractedEntities entities = enhancedEntityExtractor.extractAndExpand(query);
            log.info("Enhanced extraction: classes={}, methods={}, packages={}, terms={}",
                     entities.getClasses().size(), entities.getMethods().size(), 
                     entities.getPackages().size(), entities.getTerms().size());
            
            log.info("HYBRID_RETRIEVER: Using CLASSES for search: {}", entities.getClasses());
            log.info("HYBRID_RETRIEVER: Using METHODS for search: {}", entities.getMethods());

            // Step 2: Generate query embedding
            float[] queryVector = embeddingModel.embed(query).content().vector();
            log.info("Generated query vector with length: {}", queryVector.length);

            // Step 3: Parallel search execution
            CompletableFuture<List<ParallelSearchService.SearchResult>> fullTextFuture = 
                parallelSearchService.fullTextSearch(entities);
            
            // Use unified vector search that includes method, class, and description embeddings
            CompletableFuture<List<ParallelSearchService.SearchResult>> vectorFuture = 
                parallelSearchService.unifiedVectorSearch(queryVector);

            // Step 4: Wait for both searches to complete
            List<ParallelSearchService.SearchResult> fullTextResults = fullTextFuture.join();
            List<ParallelSearchService.SearchResult> vectorResults = vectorFuture.join();
            
            log.info("Search completed: {} full-text results, {} vector results", 
                    fullTextResults.size(), vectorResults.size());

            // Step 5: Combine and rank results
            List<SearchResultCombiner.RankedResult> combinedResults = 
                searchResultCombiner.combine(fullTextResults, vectorResults);
            
            log.info("Combined and ranked {} unique results", combinedResults.size());

            // Step 6: Extract top node IDs for graph expansion
            List<String> topNodeIds = combinedResults.stream()
                    .filter(result -> result.getCombinedScore() >= scoreThreshold)
                    .limit(initialLimit)
                    .map(SearchResultCombiner.RankedResult::getNodeId)
                    .collect(Collectors.toList());
            
            log.info("HYBRID_RETRIEVER: Top {} nodes for graph expansion (score >= {}): {}", 
                    topNodeIds.size(), scoreThreshold, topNodeIds.subList(0, Math.min(5, topNodeIds.size())));
            
            // Log top scoring results
            combinedResults.stream()
                    .limit(5)
                    .forEach(result -> log.info("HYBRID_RETRIEVER: Top result - nodeId: {}, combinedScore: {}, fullTextScore: {}, vectorScore: {}", 
                            result.getNodeId(), result.getCombinedScore(), result.getFullTextScore(), result.getVectorScore()));

            // Step 7: Expand graph using configurable n-hop traversal
            GraphExpander.SubGraph expandedGraph = graphExpander.expandNHop(topNodeIds, graphExpansionDepth, initialLimit);
            log.debug("Graph expansion completed: {} nodes, {} relationships", 
                     expandedGraph.getNodeCount(), expandedGraph.getRelationshipCount());

            // Step 8: Score nodes based on multiple criteria
            Map<String, Double> fullTextScores = combinedResults.stream()
                    .collect(Collectors.toMap(
                            SearchResultCombiner.RankedResult::getNodeId,
                            SearchResultCombiner.RankedResult::getFullTextScore,
                            (existing, replacement) -> existing
                    ));
            
            Map<String, Double> vectorScores = combinedResults.stream()
                    .collect(Collectors.toMap(
                            SearchResultCombiner.RankedResult::getNodeId,
                            SearchResultCombiner.RankedResult::getVectorScore,
                            (existing, replacement) -> existing
                    ));

            Map<String, Double> nodeScores = nodeScorer.calculateNodeScores(
                    expandedGraph, fullTextScores, vectorScores, topNodeIds);
            
            // Step 9: Apply re-ranking based on embedding similarity
            log.info("HYBRID_RETRIEVER: Before re-ranking: {} nodes", expandedGraph.getNodeCount());
            GraphExpander.SubGraph reRankedGraph = reRankingService.applyReRanking(expandedGraph, query);
            log.info("HYBRID_RETRIEVER: After re-ranking: {} nodes remaining", reRankedGraph.getNodeCount());

            // Step 10: Convert to compatible GraphContext format
            GraphEntities.GraphContext graphContext = convertToGraphContext(reRankedGraph);

            // Step 11: Build final score map combining all scoring methods
            Map<String, Double> finalScoreMap = buildFinalScoreMap(combinedResults, nodeScores, reRankedGraph);

            return QueryModels.RetrievalResult.builder()
                    .topMethodIds(topNodeIds)
                    .graphContext(graphContext)
                    .scoreMap(finalScoreMap)
                    .metadata(Map.of(
                            "fullTextResultCount", fullTextResults.size(),
                            "vectorResultCount", vectorResults.size(),
                            "combinedResultCount", combinedResults.size(),
                            "expandedNodeCount", expandedGraph.getNodeCount(),
                            "reRankedNodeCount", reRankedGraph.getNodeCount(),
                            "scoreThreshold", scoreThreshold,
                            "expansionDepth", graphExpansionDepth,
                            "queryProcessingTime", System.currentTimeMillis()
                    ))
                    .build();

        } catch (Exception e) {
            log.error("Hybrid retrieval failed", e);
            throw new RuntimeException("Failed to perform hybrid retrieval", e);
        }
    }


    /**
     * Converts SubGraph to GraphContext for compatibility with existing APIs
     */
    private GraphEntities.GraphContext convertToGraphContext(GraphExpander.SubGraph subGraph) {
        List<GraphEntities.MethodNode> methods = new ArrayList<>();
        List<GraphEntities.ClassNode> classes = new ArrayList<>();
        List<GraphEntities.Relationship> relationships = new ArrayList<>();

        log.info("HYBRID_RETRIEVER: Converting subgraph with {} nodes to GraphContext", subGraph.getNodeCount());

        // Convert nodes
        for (GraphExpander.GraphNode node : subGraph.getNodesList()) {
            String nodeType = node.getType();
            log.debug("HYBRID_RETRIEVER: Processing node type: '{}', labels: {}", nodeType, node.getLabels());
            
            if ("Method".equals(nodeType)) {
                methods.add(convertToMethodNode(node));
            } else if ("Class".equals(nodeType)) {
                classes.add(convertToClassNode(node));
            } else {
                log.debug("HYBRID_RETRIEVER: Skipping node with unknown type: '{}'", nodeType);
            }
        }
        
        log.info("HYBRID_RETRIEVER: Converted {} methods and {} classes from subgraph", methods.size(), classes.size());

        // Convert relationships
        for (GraphExpander.GraphRelationship rel : subGraph.getRelationships()) {
            relationships.add(convertToRelationship(rel));
        }

        return GraphEntities.GraphContext.builder()
                .methods(methods)
                .classes(classes)
                .relationships(relationships)
                .metadata(subGraph.getMetadata())
                .build();
    }

    /**
     * Converts GraphNode to MethodNode
     */
    private GraphEntities.MethodNode convertToMethodNode(GraphExpander.GraphNode node) {
        Map<String, Object> props = node.getProperties();
        return GraphEntities.MethodNode.builder()
                .id(node.getId())
                .signature((String) props.get("signature"))
                .name((String) props.get("name"))
                .className((String) props.get("className"))
                .businessTags(extractStringList(props, "businessTags"))
                .metadata(props)
                .build();
    }

    /**
     * Converts GraphNode to ClassNode
     */
    private GraphEntities.ClassNode convertToClassNode(GraphExpander.GraphNode node) {
        Map<String, Object> props = node.getProperties();
        return GraphEntities.ClassNode.builder()
                .id(node.getId())
                .fullName((String) props.get("fullName"))
                .name((String) props.get("name"))
                .packageName((String) props.get("package"))
                .type((String) props.get("type"))
                .isInterface((Boolean) props.getOrDefault("isInterface", false))
                .isAbstract((Boolean) props.getOrDefault("isAbstract", false))
                .metadata(props)
                .build();
    }

    /**
     * Converts GraphRelationship to Relationship
     */
    private GraphEntities.Relationship convertToRelationship(GraphExpander.GraphRelationship rel) {
        return GraphEntities.Relationship.builder()
                .fromId(rel.getStartNodeId())
                .toId(rel.getEndNodeId())
                .type(rel.getType())
                .properties(rel.getProperties())
                .build();
    }

    /**
     * Builds final score map combining all scoring methods
     */
    private Map<String, Double> buildFinalScoreMap(List<SearchResultCombiner.RankedResult> combinedResults,
                                                  Map<String, Double> nodeScores,
                                                  GraphExpander.SubGraph reRankedGraph) {
        Map<String, Double> finalScores = new LinkedHashMap<>();
        
        // Start with nodes that exist in the re-ranked graph
        Set<String> acceptedNodeIds = reRankedGraph.getNodes().keySet();
        
        for (SearchResultCombiner.RankedResult result : combinedResults) {
            if (acceptedNodeIds.contains(result.getNodeId())) {
                double baseScore = result.getCombinedScore();
                double nodeScore = nodeScores.getOrDefault(result.getNodeId(), 0.0);
                
                // Combine scores with weights
                double finalScore = (baseScore * 0.6) + (nodeScore * 0.4);
                finalScores.put(result.getNodeId(), finalScore);
            }
        }
        
        // Add any additional nodes that were discovered during expansion
        for (String nodeId : acceptedNodeIds) {
            if (!finalScores.containsKey(nodeId)) {
                double nodeScore = nodeScores.getOrDefault(nodeId, 0.0);
                finalScores.put(nodeId, nodeScore * 0.3); // Lower weight for expansion-only nodes
            }
        }
        
        return finalScores;
    }

    /**
     * Extracts string list from properties map
     */
    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> props, String key) {
        Object value = props.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return new ArrayList<>();
    }

    /**
     * Expands the graph context around the top search results (legacy method for compatibility)
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
            // Handle mixed node types (string and numeric IDs)
            List<String> validNodeIds = nodeIds.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (validNodeIds.isEmpty()) {
                return GraphEntities.GraphContext.builder()
                        .methods(new ArrayList<>())
                        .classes(new ArrayList<>())
                        .relationships(new ArrayList<>())
                        .build();
            }

            // Expand graph to include related methods and classes
            // Handle both numeric IDs and string IDs from different node types
            String expansionQuery = String.format("""
                // Start from nodes that are either Method or Class
                MATCH (startNode)
                WHERE (startNode:Method OR startNode:Class OR startNode:Interface) 
                  AND startNode.id IN $nodeIds
                CALL {
                    WITH startNode
                    MATCH path = (startNode)-[*0..%d]-(connected)
                    WHERE connected:Method OR connected:Class OR connected:Interface
                    RETURN connected, relationships(path) as rels
                }
                WITH collect(DISTINCT connected) as nodes,
                     collect(DISTINCT rels) as allRels
                UNWIND allRels as relList
                UNWIND relList as rel
                WITH nodes, collect(DISTINCT rel) as relationships
                RETURN nodes, relationships
                """, graphExpansionDepth);

            try {
                Record result = session.run(expansionQuery,
                        Map.of("nodeIds", validNodeIds)).single();

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
                    // Note: We could add Interface nodes if needed in the future
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
                                "startNodeCount", validNodeIds.size()
                        ))
                        .build();
                        
            } catch (Exception e) {
                log.warn("Graph expansion failed, returning empty context: {}", e.getMessage());
                return GraphEntities.GraphContext.builder()
                        .methods(new ArrayList<>())
                        .classes(new ArrayList<>())
                        .relationships(new ArrayList<>())
                        .build();
            }
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