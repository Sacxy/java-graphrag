package com.tekion.javaastkg.adk.tools;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import com.tekion.javaastkg.service.Neo4jService;
import com.tekion.javaastkg.agents.entity.registry.LuceneEntityIndex;
import com.tekion.javaastkg.dto.CodeEntityDto;
import com.tekion.javaastkg.model.NodeType;
import com.tekion.javaastkg.query.services.ParallelSearchService;
import com.tekion.javaastkg.query.services.ParallelSearchService.SearchResult;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 🔍 Semantic Code Hunter - Multi-Modal Code Entity Discovery Tool
 * 
 * Finds relevant code entities using multiple search strategies:
 * - Full-text search using Neo4j Lucene index
 * - Relationship traversal for discovery
 * - Entity pattern matching
 * - Semantic similarity scoring
 * 
 * Designed for AI agents to make intelligent decisions about code exploration.
 * Provides rich, structured responses with confidence scores and alternatives.
 */
@Slf4j
public class SemanticCodeHunter {
    
    private static Neo4jService neo4jService;
    private static LuceneEntityIndex luceneIndex;
    private static ParallelSearchService parallelSearchService;
    private static EmbeddingModel embeddingModel;
    
    /**
     * Initialize the tool with step-by-step semantic capabilities
     * Called by Spring context during tool registration
     */
    public static void initialize(Neo4jService service, 
                                 LuceneEntityIndex luceneEntityIndex,
                                 ParallelSearchService searchService,
                                 EmbeddingModel embedding) {
        neo4jService = service;
        luceneIndex = luceneEntityIndex;
        parallelSearchService = searchService;
        embeddingModel = embedding;
        log.info("✅ SemanticCodeHunter initialized with semantic search capabilities");
        log.info("🔍 Available capabilities: Lucene text search, Vector embeddings, Parallel search");
    }
    
    /**
     * Hunt for relevant code entities using multi-modal search strategies
     * 
     * @param entities List of entities to search for (e.g., ["PaymentService", "processPayment"])
     * @param searchCriteria Search configuration from intent classifier
     * @param filters Optional filters to apply (exclude patterns, scope limits, etc.)
     * @param ctx ADK ToolContext for state management
     * @return Structured response with matches, confidence, and alternatives
     */
    @Schema(description = "Hunt for relevant code entities using multi-modal search strategies")
    public static Map<String, Object> huntCode(
        @Schema(description = "Entities to search for (e.g., class names, method names, concepts)") List<String> entities,
        @Schema(description = "Search criteria from intent classification (entityTypes, scope, maxResults)") Map<String, Object> searchCriteria,
        @Schema(description = "Optional filters to apply (exclude patterns, scope limits, etc.)") Map<String, Object> filters,
        @Schema(name = "toolContext") ToolContext ctx
    ) {
        
        log.info("🔍 Starting semantic code hunt for entities: {}", entities);
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Input validation
            if (entities == null || entities.isEmpty()) {
                return errorResponse("huntCode", "Entities list cannot be empty");
            }
            
            if (neo4jService == null) {
                return errorResponse("huntCode", "Neo4j service not initialized");
            }
            
            // Extract and validate search parameters
            SearchParameters params = extractSearchParameters(entities, searchCriteria, filters);
            
            // Select optimal search strategy based on query characteristics
            SearchStrategy strategy = selectSearchStrategy(params);
            
            // Execute multi-modal search using actual Neo4j service
            List<EntityMatch> matches = executeRealSearch(params, strategy);
            
            // Filter and rank results
            List<EntityMatch> filteredMatches = filterAndRank(matches, params.filters);
            
            // Generate search alternatives if results are poor
            List<String> alternatives = generateAlternatives(params.entities, filteredMatches);
            
            // Update ADK context state
            updateContextState(ctx, params.entities, filteredMatches);
            
            // Build comprehensive response
            Map<String, Object> response = buildSuccessResponse(
                filteredMatches, strategy, matches.size(), 
                alternatives, System.currentTimeMillis() - startTime, params);
            
            log.info("✅ Code hunt completed: {} matches in {}ms", 
                filteredMatches.size(), System.currentTimeMillis() - startTime);
            
            return response;
            
        } catch (Exception e) {
            log.error("❌ Code hunting failed for entities: {}", entities, e);
            return errorResponse("huntCode", "Code hunting failed: " + e.getMessage());
        }
    }
    
    /**
     * Extract and validate search parameters from inputs
     */
    private static SearchParameters extractSearchParameters(List<String> entities, 
                                                          Map<String, Object> searchCriteria, 
                                                          Map<String, Object> filters) {
        // Default search criteria
        Map<String, Object> criteria = searchCriteria != null ? searchCriteria : new HashMap<>();
        Map<String, Object> filterMap = filters != null ? filters : new HashMap<>();
        
        return SearchParameters.builder()
            .entities(entities.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()))
            .entityTypes(extractEntityTypes(criteria))
            .scope(extractScope(criteria))
            .maxResults(extractMaxResults(criteria))
            .filters(filterMap)
            .build();
    }
    
    /**
     * Extract entity types from search criteria, mapping to actual NodeType enum
     */
    private static List<NodeType> extractEntityTypes(Map<String, Object> criteria) {
        Object types = criteria.get("entityTypes");
        if (types instanceof List) {
            List<String> typeStrings = (List<String>) types;
            return typeStrings.stream()
                .map(type -> {
                    try {
                        return NodeType.valueOf(type.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown entity type: {}, using CLASS as default", type);
                        return NodeType.CLASS;
                    }
                })
                .collect(Collectors.toList());
        }
        return Arrays.asList(NodeType.CLASS, NodeType.METHOD, NodeType.INTERFACE);
    }
    
    /**
     * Extract search scope from criteria
     */
    private static String extractScope(Map<String, Object> criteria) {
        return (String) criteria.getOrDefault("scope", "MODERATE");
    }
    
    /**
     * Extract maximum results limit
     */
    private static Integer extractMaxResults(Map<String, Object> criteria) {
        Object maxResults = criteria.get("maxResults");
        if (maxResults instanceof Integer) {
            return (Integer) maxResults;
        }
        return 20;
    }
    
    /**
     * Select optimal search strategy based on query characteristics
     */
    private static SearchStrategy selectSearchStrategy(SearchParameters params) {
        // Analyze query characteristics
        boolean hasSpecificEntities = params.entities.stream()
            .anyMatch(e -> e.length() > 3 && Character.isUpperCase(e.charAt(0)));
        
        boolean hasConceptualTerms = params.entities.stream()
            .anyMatch(e -> e.contains(" ") || e.length() > 15);
        
        String scope = params.scope;
        
        // Strategy selection logic
        if (hasSpecificEntities && "FOCUSED".equals(scope)) {
            return SearchStrategy.PRECISE;
        } else if (hasConceptualTerms || "BROAD".equals(scope)) {
            return SearchStrategy.SEMANTIC;
        } else if ("EXPLORATORY".equals(scope)) {
            return SearchStrategy.EXPLORATORY;
        } else {
            return SearchStrategy.HYBRID; // Default balanced approach
        }
    }
    
    /**
     * Execute search using real Neo4j service
     */
    private static List<EntityMatch> executeRealSearch(SearchParameters params, SearchStrategy strategy) {
        log.info("📊 Executing {} search for entities: {}", strategy, params.entities);
        
        List<EntityMatch> allMatches = new ArrayList<>();
        
        for (String entity : params.entities) {
            // Execute different search strategies
            switch (strategy) {
                case PRECISE:
                    allMatches.addAll(executePreciseSearch(entity, params));
                    break;
                case SEMANTIC:
                    allMatches.addAll(executeSemanticSearch(entity, params));
                    break;
                case EXPLORATORY:
                    allMatches.addAll(executeExploratorySearch(entity, params));
                    break;
                case HYBRID:
                default:
                    allMatches.addAll(executeHybridSearch(entity, params));
                    break;
            }
        }
        
        return allMatches;
    }
    
    /**
     * Execute precise search for exact entity matches using Lucene
     */
    private static List<EntityMatch> executePreciseSearch(String entity, SearchParameters params) {
        log.info("🎯 Starting PRECISE search for entity: '{}' with types: {}", entity, params.entityTypes);
        List<EntityMatch> matches = new ArrayList<>();
        
        try {
            // Use Lucene index for precise search
            log.info("🔍 Using LuceneEntityIndex for precise search of '{}'", entity);
            List<CodeEntityDto> results = luceneIndex.search(
                entity, 
                LuceneEntityIndex.SearchType.STANDARD, 
                params.maxResults
            );
            log.info("📊 Lucene returned {} results for precise search of '{}'", results.size(), entity);
            
            for (CodeEntityDto codeEntity : results) {
                // Filter by entity types if specified
                if (isEntityTypeMatch(codeEntity, params.entityTypes)) {
                    EntityMatch match = createEntityMatchFromCodeEntity(codeEntity, "PRECISE");
                    matches.add(match);
                    log.info("✅ Created precise match: {} (confidence: {})", match.name, match.confidence);
                } else {
                    log.info("🔍 Skipping entity {} - type {} not in filter {}", 
                        codeEntity.getName(), codeEntity.getType(), params.entityTypes);
                }
            }
        } catch (Exception e) {
            log.error("❌ PRECISE search failed for entity '{}': {}", entity, e.getMessage(), e);
            // Fallback to Neo4j if Lucene fails
            log.info("🔄 Falling back to Neo4j for precise search");
            for (NodeType nodeType : params.entityTypes) {
                List<Map<String, Object>> results = neo4jService.executeFullTextSearch(
                    nodeType.name(), entity, params.maxResults);
                for (Map<String, Object> result : results) {
                    EntityMatch match = createEntityMatchFromNeo4jResult(result, nodeType, "PRECISE");
                    matches.add(match);
                }
            }
        }
        
        log.info("🎯 PRECISE search completed: {} total matches for '{}'", matches.size(), entity);
        return matches;
    }
    
    /**
     * Execute semantic search for conceptual matches using Lucene + Neo4j
     */
    private static List<EntityMatch> executeSemanticSearch(String entity, SearchParameters params) {
        log.info("🧠 Starting SEMANTIC search for entity: '{}' with types: {}", entity, params.entityTypes);
        List<EntityMatch> matches = new ArrayList<>();
        
        try {
            // Step 1: Try vector similarity search first (most semantic)
            if (embeddingModel != null && parallelSearchService != null) {
                log.info("🧠 SEMANTIC: Using vector embeddings for semantic similarity search of '{}'", entity);
                try {
                    // Generate query embedding
                    Embedding queryEmbedding = embeddingModel.embed(entity).content();
                    float[] queryVector = queryEmbedding.vector();
                    log.info("🔢 Generated embedding vector for '{}' (dimension: {})", entity, queryVector.length);
                    
                    // Perform vector similarity search
                    List<SearchResult> vectorResults = 
                        parallelSearchService.unifiedVectorSearch(queryVector).get();
                    log.info("📊 SEMANTIC: Vector search returned {} results", vectorResults.size());
                    
                    // Convert vector results to EntityMatch
                    for (SearchResult result : vectorResults) {
                        EntityMatch match = createEntityMatchFromVectorResult(result, "SEMANTIC_VECTOR");
                        matches.add(match);
                        log.info("✅ SEMANTIC: Created vector match: {} (confidence: {})", match.name, match.confidence);
                    }
                    
                } catch (Exception e) {
                    log.info("⚠️ Vector search failed, falling back to Lucene: {}", e.getMessage());
                }
            }
            
            // Step 2: Fallback to Lucene fuzzy search if vector search failed or unavailable
            if (matches.isEmpty()) {
                log.info("🔍 SEMANTIC: Using LuceneEntityIndex for fuzzy search of '{}'", entity);
                List<CodeEntityDto> fuzzyResults = luceneIndex.fuzzySearch(entity, params.maxResults);
                log.info("📊 SEMANTIC: Lucene fuzzy search returned {} results", fuzzyResults.size());
                
                for (CodeEntityDto codeEntity : fuzzyResults) {
                    if (isEntityTypeMatch(codeEntity, params.entityTypes)) {
                        EntityMatch match = createEntityMatchFromCodeEntity(codeEntity, "SEMANTIC");
                        matches.add(match);
                        log.info("✅ SEMANTIC: Created fuzzy match: {} (confidence: {})", match.name, match.confidence);
                    }
                }
            }
            
            // Step 2: Use Lucene for wildcard search for broader coverage
            log.info("🔍 SEMANTIC: Using LuceneEntityIndex for wildcard search of '{}*'", entity);
            List<CodeEntityDto> wildcardResults = luceneIndex.search(
                entity + "*", 
                LuceneEntityIndex.SearchType.WILDCARD, 
                Math.max(5, params.maxResults / 2)
            );
            log.info("📊 SEMANTIC: Lucene wildcard search returned {} results", wildcardResults.size());
            
            for (CodeEntityDto codeEntity : wildcardResults) {
                if (isEntityTypeMatch(codeEntity, params.entityTypes) && !isDuplicate(codeEntity, matches)) {
                    EntityMatch match = createEntityMatchFromCodeEntity(codeEntity, "SEMANTIC_WILDCARD");
                    matches.add(match);
                    log.info("✅ SEMANTIC: Created wildcard match: {} (confidence: {})", match.name, match.confidence);
                }
            }
            
        } catch (Exception e) {
            log.error("❌ SEMANTIC Lucene search failed for entity '{}': {}", entity, e.getMessage(), e);
        }
        
        // Step 3: Fallback to Neo4j for relationship traversal
        try {
            log.info("🔗 SEMANTIC: Using Neo4j for relationship traversal of '{}'", entity);
            List<Map<String, Object>> relatedResults = neo4jService.findRelatedEntities(entity, 2);
            log.info("📊 SEMANTIC: Neo4j relationship search returned {} related entities", relatedResults.size());
            for (Map<String, Object> result : relatedResults) {
                EntityMatch match = createEntityMatchFromNeo4jResult(result, NodeType.CLASS, "SEMANTIC_RELATED");
                matches.add(match);
                log.info("✅ SEMANTIC: Created related match: {} (confidence: {})", match.name, match.confidence);
            }
        } catch (Exception e) {
            log.error("❌ SEMANTIC relationship search failed: {}", e.getMessage(), e);
        }
        
        log.info("🧠 SEMANTIC search completed: {} total matches for '{}'", matches.size(), entity);
        return matches;
    }
    
    /**
     * Execute exploratory search for broad discovery using Lucene
     */
    private static List<EntityMatch> executeExploratorySearch(String entity, SearchParameters params) {
        log.info("🌐 Starting EXPLORATORY search for entity: '{}'", entity);
        List<EntityMatch> matches = new ArrayList<>();
        
        // Combine all search methods for maximum coverage
        matches.addAll(executePreciseSearch(entity, params));
        matches.addAll(executeSemanticSearch(entity, params));
        
        try {
            // Add broader wildcard searches using Lucene
            String wildcardEntity = entity.length() > 3 ? entity.substring(0, entity.length() - 1) + "*" : entity + "*";
            log.info("🔍 EXPLORATORY: Using LuceneEntityIndex for broad wildcard search of '{}'", wildcardEntity);
            
            List<CodeEntityDto> wildcardResults = luceneIndex.search(
                wildcardEntity, 
                LuceneEntityIndex.SearchType.WILDCARD, 
                params.maxResults
            );
            log.info("📊 EXPLORATORY: Lucene wildcard search returned {} results", wildcardResults.size());
            
            for (CodeEntityDto codeEntity : wildcardResults) {
                if (isEntityTypeMatch(codeEntity, params.entityTypes) && !isDuplicate(codeEntity, matches)) {
                    EntityMatch match = createEntityMatchFromCodeEntity(codeEntity, "EXPLORATORY");
                    matches.add(match);
                    log.info("✅ EXPLORATORY: Created wildcard match: {} (confidence: {})", match.name, match.confidence);
                }
            }
            
        } catch (Exception e) {
            log.error("❌ EXPLORATORY Lucene search failed: {}", e.getMessage(), e);
            // Fallback to Neo4j for exploratory search
            log.info("🔄 Falling back to Neo4j for exploratory search");
            String wildcardEntity = entity.length() > 3 ? entity.substring(0, entity.length() - 1) + "*" : entity;
            for (NodeType nodeType : params.entityTypes) {
                List<Map<String, Object>> wildcardResults = neo4jService.executeFullTextSearch(
                    nodeType.name(), wildcardEntity, params.maxResults);
                for (Map<String, Object> result : wildcardResults) {
                    EntityMatch match = createEntityMatchFromNeo4jResult(result, nodeType, "EXPLORATORY");
                    matches.add(match);
                }
            }
        }
        
        log.info("🌐 EXPLORATORY search completed: {} total matches for '{}'", matches.size(), entity);
        return matches;
    }
    
    /**
     * Execute hybrid search combining multiple strategies
     */
    private static List<EntityMatch> executeHybridSearch(String entity, SearchParameters params) {
        List<EntityMatch> matches = new ArrayList<>();
        
        // Start with precise search
        List<EntityMatch> preciseMatches = executePreciseSearch(entity, params);
        matches.addAll(preciseMatches);
        
        // If precise search yields few results, add semantic search
        if (preciseMatches.size() < params.maxResults / 2) {
            List<EntityMatch> semanticMatches = executeSemanticSearch(entity, params);
            matches.addAll(semanticMatches);
        }
        
        return matches;
    }
    
    /**
     * Check if CodeEntityDto matches the requested entity types
     */
    private static boolean isEntityTypeMatch(CodeEntityDto codeEntity, List<NodeType> entityTypes) {
        if (entityTypes == null || entityTypes.isEmpty()) {
            return true; // No filter, accept all
        }
        
        if (codeEntity.getType() == null) {
            return entityTypes.contains(NodeType.CLASS); // Default to CLASS if type is unknown
        }
        
        // Convert CodeEntityDto.EntityType to NodeType for comparison
        NodeType entityNodeType;
        switch (codeEntity.getType()) {
            case CLASS:
                entityNodeType = NodeType.CLASS;
                break;
            case METHOD:
                entityNodeType = NodeType.METHOD;
                break;
            case PACKAGE:
                entityNodeType = NodeType.CLASS; // Map PACKAGE to CLASS in NodeType
                break;
            case INTERFACE:
                entityNodeType = NodeType.INTERFACE;
                break;
            default:
                entityNodeType = NodeType.CLASS;
                break;
        }
        
        return entityTypes.contains(entityNodeType);
    }
    
    /**
     * Check if CodeEntityDto is already in the matches list
     */
    private static boolean isDuplicate(CodeEntityDto codeEntity, List<EntityMatch> matches) {
        String entityId = codeEntity.getId();
        return matches.stream()
            .anyMatch(match -> match.entityId.equals(entityId));
    }
    
    /**
     * Create EntityMatch from vector search result (semantic similarity)
     */
    private static EntityMatch createEntityMatchFromVectorResult(
            SearchResult result, String source) {
        
        String entityId = result.getNodeId();
        String name = result.getName(); // Use label as name for now
        String entityType = result.getType() != null ? result.getType() : "UNKNOWN";
        
        // Vector similarity provides high-quality confidence scores
        double confidence = Math.min(0.95, result.getScore() * 0.9); // Scale and cap confidence
        
        String matchReason = String.format("Vector similarity match with score %.3f", result.getScore());
        
        return EntityMatch.builder()
            .entityId(entityId != null ? entityId : "unknown")
            .entityType(entityType)
            .name(name != null ? name : "unknown")
            .signature(null) // Vector results don't have signatures
            .filePath(null)  // Vector results don't have file paths
            .startLine(null) // Vector results don't have line numbers
            .confidence(confidence)
            .matchReason(matchReason)
            .source("Vector " + source.toLowerCase() + " search")
            .metadata(Map.of(
                "searchSource", source,
                "originalScore", result.getScore(),
                "entityType", entityType,
                "vectorSearch", true,
                "nodeType", result.getType() != null ? result.getType() : "unknown"
            ))
            .build();
    }
    
    /**
     * Create EntityMatch from CodeEntityDto (Lucene result)
     */
    private static EntityMatch createEntityMatchFromCodeEntity(CodeEntityDto codeEntity, String source) {
        String entityId = codeEntity.getId();
        String name = codeEntity.getName();
        String signature = buildSignatureFromCodeEntity(codeEntity);
        String filePath = codeEntity.getFilePath();
        Integer startLine = codeEntity.getLineNumber();
        Double score = codeEntity.getScore() != null ? codeEntity.getScore().doubleValue() : null;
        
        // Determine entity type
        String entityType = codeEntity.getType() != null ? 
            codeEntity.getType().name() : "CLASS";
        
        // Calculate confidence based on score and source
        double confidence = calculateConfidenceFromCodeEntity(score, source);
        
        // Build match reason
        String matchReason = buildMatchReasonFromCodeEntity(name, source, score);
        
        return EntityMatch.builder()
            .entityId(entityId != null ? entityId : "unknown")
            .entityType(entityType)
            .name(name != null ? name : "unknown")
            .signature(signature)
            .filePath(filePath)
            .startLine(startLine)
            .confidence(confidence)
            .matchReason(matchReason)
            .source("Lucene " + source.toLowerCase() + " search")
            .metadata(Map.of(
                "searchSource", source,
                "originalScore", score != null ? score : 0.0,
                "entityType", entityType,
                "luceneSearch", true
            ))
            .build();
    }
    
    /**
     * Build signature from CodeEntityDto
     */
    private static String buildSignatureFromCodeEntity(CodeEntityDto codeEntity) {
        if (codeEntity.getType() == CodeEntityDto.EntityType.METHOD && codeEntity.getMethodName() != null) {
            StringBuilder sig = new StringBuilder();
            if (codeEntity.getClassName() != null) {
                sig.append(codeEntity.getClassName()).append(".");
            }
            sig.append(codeEntity.getMethodName());
            if (codeEntity.getParameters() != null && !codeEntity.getParameters().isEmpty()) {
                sig.append("(");
                sig.append(String.join(", ", codeEntity.getParameters()));
                sig.append(")");
            }
            if (codeEntity.getReturnType() != null) {
                sig.append(": ").append(codeEntity.getReturnType());
            }
            return sig.toString();
        }
        return codeEntity.getName();
    }
    
    /**
     * Calculate confidence based on CodeEntityDto score and search source
     */
    private static double calculateConfidenceFromCodeEntity(Double score, String source) {
        if (score == null) score = 5.0; // Default Lucene score
        
        // Normalize Lucene score (typically 0-10+) to 0-1 range
        double baseConfidence = Math.min(1.0, Math.max(0.0, score / 10.0));
        
        // Adjust confidence based on search strategy
        switch (source) {
            case "PRECISE":
                return baseConfidence * 0.95; // High confidence for precise matches
            case "SEMANTIC":
                return baseConfidence * 0.8; // Medium confidence for semantic matches  
            case "SEMANTIC_WILDCARD":
                return baseConfidence * 0.75; // Medium-low confidence for wildcard semantic
            case "EXPLORATORY":
                return baseConfidence * 0.7; // Lower confidence for exploratory results
            default:
                return baseConfidence * 0.8;
        }
    }
    
    /**
     * Build human-readable match reason from CodeEntityDto
     */
    private static String buildMatchReasonFromCodeEntity(String name, String source, Double score) {
        String confidenceDesc = score != null && score > 8.0 ? "high" : score != null && score > 5.0 ? "good" : "partial";
        
        switch (source) {
            case "PRECISE":
                return String.format("Lucene exact match for '%s' with %s relevance", name, confidenceDesc);
            case "SEMANTIC":
                return String.format("Lucene fuzzy match for '%s' with %s similarity", name, confidenceDesc);
            case "SEMANTIC_WILDCARD":
                return String.format("Lucene wildcard match for '%s' with %s pattern matching", name, confidenceDesc);
            case "EXPLORATORY":
                return String.format("Lucene exploratory match for '%s' with %s pattern matching", name, confidenceDesc);
            default:
                return String.format("Lucene match for '%s' with %s confidence", name, confidenceDesc);
        }
    }
    
    /**
     * Create EntityMatch from Neo4j result
     */
    private static EntityMatch createEntityMatchFromNeo4jResult(Map<String, Object> result, NodeType nodeType, String source) {
        String entityId = (String) result.get("id");
        String name = (String) result.get("name");
        String signature = (String) result.get("signature");
        String filePath = (String) result.get("filePath");
        Integer startLine = (Integer) result.get("startLine");
        Double score = (Double) result.get("score");
        
        // Calculate confidence based on score and source
        double confidence = calculateConfidence(score, source);
        
        // Build match reason
        String matchReason = buildMatchReason(name, source, score);
        
        return EntityMatch.builder()
            .entityId(entityId != null ? entityId : "unknown")
            .entityType(nodeType.name())
            .name(name != null ? name : "unknown")
            .signature(signature)
            .filePath(filePath)
            .startLine(startLine)
            .confidence(confidence)
            .matchReason(matchReason)
            .source("Neo4j " + source.toLowerCase() + " search")
            .metadata(Map.of(
                "searchSource", source,
                "originalScore", score != null ? score : 0.0,
                "nodeType", nodeType.name()
            ))
            .build();
    }
    
    /**
     * Calculate confidence based on Neo4j score and search source
     */
    private static double calculateConfidence(Double score, String source) {
        if (score == null) score = 0.5;
        
        // Adjust confidence based on search strategy
        double baseConfidence = Math.min(1.0, Math.max(0.0, score));
        
        switch (source) {
            case "PRECISE":
                return baseConfidence * 0.95; // High confidence for precise matches
            case "SEMANTIC":
                return baseConfidence * 0.8; // Medium confidence for semantic matches
            case "SEMANTIC_RELATED":
                return baseConfidence * 0.7; // Lower confidence for related entities
            case "EXPLORATORY":
                return baseConfidence * 0.6; // Lower confidence for exploratory results
            default:
                return baseConfidence * 0.8;
        }
    }
    
    /**
     * Build human-readable match reason
     */
    private static String buildMatchReason(String name, String source, Double score) {
        String confidenceDesc = score != null && score > 0.8 ? "exact" : score != null && score > 0.6 ? "good" : "partial";
        
        switch (source) {
            case "PRECISE":
                return String.format("Exact match for '%s' with %s confidence", name, confidenceDesc);
            case "SEMANTIC":
                return String.format("Semantic match for '%s' with %s similarity", name, confidenceDesc);
            case "SEMANTIC_RELATED":
                return String.format("Related entity '%s' found through graph traversal", name);
            case "EXPLORATORY":
                return String.format("Exploratory match for '%s' with %s pattern matching", name, confidenceDesc);
            default:
                return String.format("Match for '%s' with %s confidence", name, confidenceDesc);
        }
    }
    
    /**
     * Filter and rank search results
     */
    private static List<EntityMatch> filterAndRank(List<EntityMatch> matches, Map<String, Object> filters) {
        List<EntityMatch> filtered = new ArrayList<>(matches);
        
        // Apply filters
        if (filters.containsKey("minConfidence")) {
            double minConfidence = (Double) filters.get("minConfidence");
            filtered = filtered.stream()
                .filter(match -> match.confidence >= minConfidence)
                .collect(Collectors.toList());
        }
        
        if (filters.containsKey("excludePatterns")) {
            List<String> excludePatterns = (List<String>) filters.get("excludePatterns");
            filtered = filtered.stream()
                .filter(match -> excludePatterns.stream()
                    .noneMatch(pattern -> match.name.toLowerCase().contains(pattern.toLowerCase())))
                .collect(Collectors.toList());
        }
        
        // Remove duplicates based on entityId
        filtered = filtered.stream()
            .collect(Collectors.toMap(
                match -> match.entityId,
                match -> match,
                (existing, replacement) -> existing.confidence > replacement.confidence ? existing : replacement
            ))
            .values()
            .stream()
            .collect(Collectors.toList());
        
        // Rank by confidence (descending)
        filtered.sort((a, b) -> Double.compare(b.confidence, a.confidence));
        
        return filtered;
    }
    
    /**
     * Generate search alternatives for poor results
     */
    private static List<String> generateAlternatives(List<String> originalEntities, List<EntityMatch> matches) {
        List<String> alternatives = new ArrayList<>();
        
        // Calculate average confidence
        double avgConfidence = matches.stream()
            .mapToDouble(m -> m.confidence)
            .average()
            .orElse(0.0);
        
        // If results are poor, suggest variations
        if (matches.isEmpty() || avgConfidence < 0.5) {
            
            for (String entity : originalEntities) {
                // Suggest wildcards
                alternatives.add(entity + "*");
                
                // Suggest case variations
                alternatives.add(entity.toLowerCase());
                alternatives.add(entity.toUpperCase());
                
                // Suggest partial matches
                if (entity.length() > 5) {
                    alternatives.add(entity.substring(0, entity.length() - 2) + "*");
                }
                
                // Suggest related terms
                if (entity.toLowerCase().contains("service")) {
                    alternatives.add(entity.replace("service", "controller").replace("Service", "Controller"));
                }
                if (entity.toLowerCase().contains("controller")) {
                    alternatives.add(entity.replace("controller", "service").replace("Controller", "Service"));
                }
            }
        }
        
        return alternatives.stream().distinct().collect(Collectors.toList());
    }
    
    /**
     * Update ADK context state with search results
     */
    private static void updateContextState(ToolContext ctx, List<String> entities, List<EntityMatch> matches) {
        if (ctx != null && ctx.state() != null) {
            try {
                ctx.state().put("app:hunt_entities", entities);
                ctx.state().put("app:hunt_matches", matches.stream()
                    .map(EntityMatch::getEntityId)
                    .limit(10)
                    .collect(Collectors.toList()));
                ctx.state().put("app:hunt_timestamp", System.currentTimeMillis());
                ctx.state().put("app:hunt_total_matches", matches.size());
            } catch (UnsupportedOperationException e) {
                log.info("Context state is immutable, skipping state updates");
            }
        }
    }
    
    /**
     * Build success response with comprehensive metadata
     */
    private static Map<String, Object> buildSuccessResponse(List<EntityMatch> matches, 
                                                          SearchStrategy strategy, 
                                                          int totalCandidates,
                                                          List<String> alternatives, 
                                                          long executionTime,
                                                          SearchParameters params) {
        
        double overallConfidence = calculateOverallConfidence(matches);
        List<String> nextActions = determineNextActions(matches, params.entityTypes);
        
        return Map.of(
            "status", "success",
            "matches", matches.stream().map(EntityMatch::toMap).collect(Collectors.toList()),
            "searchStrategy", strategy.name(),
            "searchMetadata", Map.of(
                "totalCandidates", totalCandidates,
                "filteredResults", matches.size(),
                "executionTimeMs", executionTime,
                "strategy", strategy.name(),
                "scope", params.scope,
                "entityTypes", params.entityTypes.stream().map(NodeType::name).collect(Collectors.toList()),
                "neo4jHealthy", neo4jService.isHealthy()
            ),
            "alternatives", alternatives,
            "confidence", overallConfidence,
            "nextActions", nextActions,
            "explanation", buildExplanation(matches, strategy, overallConfidence)
        );
    }
    
    /**
     * Calculate overall confidence from matches
     */
    private static double calculateOverallConfidence(List<EntityMatch> matches) {
        if (matches.isEmpty()) return 0.0;
        
        return matches.stream()
            .mapToDouble(m -> m.confidence)
            .average()
            .orElse(0.0);
    }
    
    /**
     * Determine recommended next actions based on results
     */
    private static List<String> determineNextActions(List<EntityMatch> matches, List<NodeType> entityTypes) {
        List<String> actions = new ArrayList<>();
        
        if (matches.isEmpty()) {
            actions.add("BROADEN_SEARCH");
            actions.add("TRY_ALTERNATIVES");
        } else if (matches.size() == 1) {
            actions.add("EXPLORE_STRUCTURE");
            actions.add("ENRICH_CONTEXT");
        } else {
            actions.add("FILTER_RESULTS");
            actions.add("EXPLORE_STRUCTURE");
            actions.add("ENRICH_CONTEXT");
        }
        
        return actions;
    }
    
    /**
     * Build explanation for agent understanding
     */
    private static String buildExplanation(List<EntityMatch> matches, SearchStrategy strategy, double confidence) {
        if (matches.isEmpty()) {
            return String.format("No matches found using %s strategy against Neo4j graph. Consider broadening search or trying alternatives.", 
                strategy.name().toLowerCase());
        }
        
        return String.format("Found %d matches using %s strategy with %.1f%% average confidence from Neo4j graph traversal. " +
            "Results are %s for further exploration.",
            matches.size(), strategy.name().toLowerCase(), confidence * 100,
            confidence > 0.8 ? "excellent" : confidence > 0.6 ? "good" : "moderate");
    }
    
    /**
     * Build error response
     */
    private static Map<String, Object> errorResponse(String operation, String error) {
        return Map.of(
            "status", "error",
            "operation", operation,
            "error", error,
            "timestamp", System.currentTimeMillis()
        );
    }
    
    /**
     * Search parameters data structure
     */
    private static class SearchParameters {
        final List<String> entities;
        final List<NodeType> entityTypes;
        final String scope;
        final Integer maxResults;
        final Map<String, Object> filters;
        
        private SearchParameters(List<String> entities, List<NodeType> entityTypes, String scope, 
                               Integer maxResults, Map<String, Object> filters) {
            this.entities = entities;
            this.entityTypes = entityTypes;
            this.scope = scope;
            this.maxResults = maxResults;
            this.filters = filters;
        }
        
        static SearchParametersBuilder builder() {
            return new SearchParametersBuilder();
        }
        
        static class SearchParametersBuilder {
            private List<String> entities;
            private List<NodeType> entityTypes;
            private String scope;
            private Integer maxResults;
            private Map<String, Object> filters;
            
            SearchParametersBuilder entities(List<String> entities) {
                this.entities = entities;
                return this;
            }
            
            SearchParametersBuilder entityTypes(List<NodeType> entityTypes) {
                this.entityTypes = entityTypes;
                return this;
            }
            
            SearchParametersBuilder scope(String scope) {
                this.scope = scope;
                return this;
            }
            
            SearchParametersBuilder maxResults(Integer maxResults) {
                this.maxResults = maxResults;
                return this;
            }
            
            SearchParametersBuilder filters(Map<String, Object> filters) {
                this.filters = filters;
                return this;
            }
            
            SearchParameters build() {
                return new SearchParameters(entities, entityTypes, scope, maxResults, filters);
            }
        }
    }
    
    /**
     * Entity match data structure
     */
    public static class EntityMatch {
        public final String entityId;
        public final String entityType;
        public final String name;
        public final String signature;
        public final String filePath;
        public final Integer startLine;
        public final double confidence;
        public final String matchReason;
        public final String source;
        public final Map<String, Object> metadata;
        
        private EntityMatch(String entityId, String entityType, String name, String signature,
                          String filePath, Integer startLine, double confidence, 
                          String matchReason, String source, Map<String, Object> metadata) {
            this.entityId = entityId;
            this.entityType = entityType;
            this.name = name;
            this.signature = signature;
            this.filePath = filePath;
            this.startLine = startLine;
            this.confidence = confidence;
            this.matchReason = matchReason;
            this.source = source;
            this.metadata = metadata;
        }
        
        public String getEntityId() { return entityId; }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("entityId", entityId);
            map.put("entityType", entityType);
            map.put("name", name);
            map.put("confidence", confidence);
            map.put("matchReason", matchReason);
            map.put("source", source);
            map.put("metadata", metadata);
            
            // Add optional fields if present
            if (signature != null) map.put("signature", signature);
            if (filePath != null) map.put("filePath", filePath);
            if (startLine != null) map.put("startLine", startLine);
            
            return map;
        }
        
        static EntityMatchBuilder builder() {
            return new EntityMatchBuilder();
        }
        
        static class EntityMatchBuilder {
            private String entityId;
            private String entityType;
            private String name;
            private String signature;
            private String filePath;
            private Integer startLine;
            private double confidence;
            private String matchReason;
            private String source;
            private Map<String, Object> metadata = new HashMap<>();
            
            EntityMatchBuilder entityId(String entityId) {
                this.entityId = entityId;
                return this;
            }
            
            EntityMatchBuilder entityType(String entityType) {
                this.entityType = entityType;
                return this;
            }
            
            EntityMatchBuilder name(String name) {
                this.name = name;
                return this;
            }
            
            EntityMatchBuilder signature(String signature) {
                this.signature = signature;
                return this;
            }
            
            EntityMatchBuilder filePath(String filePath) {
                this.filePath = filePath;
                return this;
            }
            
            EntityMatchBuilder startLine(Integer startLine) {
                this.startLine = startLine;
                return this;
            }
            
            EntityMatchBuilder confidence(double confidence) {
                this.confidence = confidence;
                return this;
            }
            
            EntityMatchBuilder matchReason(String matchReason) {
                this.matchReason = matchReason;
                return this;
            }
            
            EntityMatchBuilder source(String source) {
                this.source = source;
                return this;
            }
            
            EntityMatchBuilder metadata(Map<String, Object> metadata) {
                this.metadata = metadata;
                return this;
            }
            
            EntityMatch build() {
                return new EntityMatch(entityId, entityType, name, signature, filePath, startLine, 
                                     confidence, matchReason, source, metadata);
            }
        }
    }
    
    /**
     * Search strategy enumeration
     */
    public enum SearchStrategy {
        PRECISE,     // Exact matching for well-defined entities
        SEMANTIC,    // Full-text + relationship traversal for conceptual searches
        EXPLORATORY, // Broad discovery with wildcard and relationship traversal
        HYBRID       // Balanced approach combining multiple methods
    }
}