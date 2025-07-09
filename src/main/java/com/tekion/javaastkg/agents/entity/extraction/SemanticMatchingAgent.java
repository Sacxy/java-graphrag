package com.tekion.javaastkg.agents.entity.extraction;

import com.tekion.javaastkg.agents.entity.models.EntityMatch;
import com.tekion.javaastkg.agents.entity.models.QueryContext;
import com.tekion.javaastkg.agents.entity.registry.CodebaseEntityRegistry;
import com.tekion.javaastkg.agents.entity.registry.SemanticEntityIndex;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Semantic Matching Agent - Uses embeddings and domain knowledge for intelligent matching.
 * Implements Factor 10 (Small Focused Agent) and Factor 12 (Stateless Reducer).
 * 
 * Applied 12-Factor Principles:
 * - Factor 1: Structured outputs (EntityMatch objects)
 * - Factor 4: Tools as structured outputs
 * - Factor 10: Small focused agent (5-8 steps: embed, similarity, domain, context, cluster)
 * - Factor 12: Stateless pure function
 */
@Component
@Slf4j
public class SemanticMatchingAgent implements ExtractionAgent {
    
    private final CodebaseEntityRegistry registry;
    private final SemanticEntityIndex semanticIndex;
    private final EmbeddingModel embeddingModel;
    
    // Configuration
    private static final double MIN_SIMILARITY_THRESHOLD = 0.6;
    private static final double HIGH_SIMILARITY_THRESHOLD = 0.8;
    private static final int MAX_SEMANTIC_RESULTS = 15;
    
    public SemanticMatchingAgent(CodebaseEntityRegistry registry, 
                               SemanticEntityIndex semanticIndex,
                               @Qualifier("queryEmbeddingModel") EmbeddingModel embeddingModel) {
        this.registry = registry;
        this.semanticIndex = semanticIndex;
        this.embeddingModel = embeddingModel;
    }
    
    /**
     * Factor 12: Pure function for semantic entity extraction
     * Factor 1: Converts QueryContext to structured EntityMatch decisions
     */
    @Override
    public List<EntityMatch> extract(QueryContext context) {
        log.debug("Starting semantic matching for query: {}", context.getOriginalQuery());
        
        List<EntityMatch> allMatches = new ArrayList<>();
        
        // Step 1: Embed query for semantic similarity
        allMatches.addAll(findEmbeddingSimilarityMatches(context));
        
        // Step 2: Domain-specific term expansion
        allMatches.addAll(findDomainTermMatches(context));
        
        // Step 3: Business context expansion
        allMatches.addAll(findBusinessContextMatches(context));
        
        // Step 4: Semantic cluster matches
        allMatches.addAll(findSemanticClusterMatches(context));
        
        // Step 5: Contextual relationship matches
        allMatches.addAll(findContextualMatches(context));
        
        // Step 6: Intent-based semantic expansion (handled in contextual matches)
        
        // Step 7: Filter and rank results
        List<EntityMatch> rankedMatches = filterAndRankMatches(allMatches);
        
        log.debug("Semantic matching completed. Found {} matches", rankedMatches.size());
        return rankedMatches;
    }
    
    /**
     * Step 1: Find matches using embedding similarity
     */
    private List<EntityMatch> findEmbeddingSimilarityMatches(QueryContext context) {
        if (context == null || context.getNormalizedQuery() == null || context.getNormalizedQuery().trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<EntityMatch> matches = new ArrayList<>();

        try {
            // Create embedding for the normalized query
            String queryText = context.getNormalizedQuery();
            float[] queryEmbedding = embeddingModel.embed(queryText).content().vector();

            if (queryEmbedding == null || queryEmbedding.length == 0) {
                log.warn("Failed to generate embedding for query: {}", queryText);
                return matches;
            }

            // Find semantically similar entities
            List<SemanticEntityIndex.SimilarEntity> similarEntities =
                semanticIndex.findSimilar(queryEmbedding, MIN_SIMILARITY_THRESHOLD);

            for (SemanticEntityIndex.SimilarEntity similarEntity : similarEntities) {
                if (similarEntity == null || similarEntity.getEntityName() == null) {
                    continue;
                }

                List<EntityMatch> entityMatches = registry.findExactMatches(similarEntity.getEntityName());

                for (EntityMatch originalMatch : entityMatches) {
                    if (originalMatch == null) continue;

                    // Create new match to avoid mutating shared objects
                    EntityMatch match = createSemanticMatch(originalMatch, similarEntity.getSimilarity(),
                        "embedding similarity: " + String.format("%.2f", similarEntity.getSimilarity()));
                    matches.add(match);
                }
            }

        } catch (RuntimeException e) {
            log.warn("Failed to perform embedding similarity search for query: {}", context.getNormalizedQuery(), e);
        } catch (Exception e) {
            log.error("Unexpected error in embedding similarity search", e);
        }

        return matches;
    }
    
    /**
     * Step 2: Find matches using domain-specific term expansion
     */
    private List<EntityMatch> findDomainTermMatches(QueryContext context) {
        if (context == null || context.getDomainTerms() == null) {
            return Collections.emptyList();
        }

        List<EntityMatch> matches = new ArrayList<>();

        for (QueryContext.Token token : context.getDomainTerms()) {
            if (token == null || token.getValue() == null || token.getValue().trim().isEmpty()) {
                continue;
            }

            String term = token.getValue();

            try {
                // Get related domain terms
                Set<String> relatedTerms = semanticIndex.getDomainTerms(term);

                if (relatedTerms == null || relatedTerms.isEmpty()) {
                    continue;
                }

                for (String relatedTerm : relatedTerms) {
                    if (relatedTerm == null || relatedTerm.trim().isEmpty()) {
                        continue;
                    }

                    List<EntityMatch> termMatches = findEntitiesWithTerm(relatedTerm);

                    for (EntityMatch originalMatch : termMatches) {
                        if (originalMatch == null) continue;

                        // Create new match to avoid mutating shared objects
                        EntityMatch match = createSemanticMatch(originalMatch, 0.65,
                            "domain term expansion: " + term + " -> " + relatedTerm);
                        matches.add(match);
                    }
                }
            } catch (Exception e) {
                log.warn("Error processing domain term '{}': {}", term, e.getMessage());
            }
        }

        return matches;
    }
    
    /**
     * Step 3: Find matches using business context expansion
     */
    private List<EntityMatch> findBusinessContextMatches(QueryContext context) {
        if (context == null || context.getDomainTerms() == null) {
            return Collections.emptyList();
        }

        List<EntityMatch> matches = new ArrayList<>();

        for (QueryContext.Token token : context.getDomainTerms()) {
            if (token == null || token.getValue() == null || token.getValue().trim().isEmpty()) {
                continue;
            }

            String term = token.getValue();

            try {
                // Get business context terms
                Set<String> contextTerms = semanticIndex.getBusinessContextTerms(term);

                if (contextTerms == null || contextTerms.isEmpty()) {
                    continue;
                }

                for (String contextTerm : contextTerms) {
                    if (contextTerm == null || contextTerm.trim().isEmpty()) {
                        continue;
                    }

                    List<EntityMatch> contextMatches = findEntitiesWithTerm(contextTerm);

                    for (EntityMatch originalMatch : contextMatches) {
                        if (originalMatch == null) continue;

                        // Create new match to avoid mutating shared objects
                        EntityMatch match = createSemanticMatch(originalMatch, 0.6,
                            "business context: " + term + " -> " + contextTerm);
                        matches.add(match);
                    }
                }
            } catch (Exception e) {
                log.warn("Error processing business context for term '{}': {}", term, e.getMessage());
            }
        }

        return matches;
    }
    
    /**
     * Step 4: Find matches using semantic clusters
     */
    private List<EntityMatch> findSemanticClusterMatches(QueryContext context) {
        if (context == null || context.getIdentifierTokens() == null) {
            return Collections.emptyList();
        }

        List<EntityMatch> matches = new ArrayList<>();

        for (QueryContext.Token token : context.getIdentifierTokens()) {
            if (token == null || token.getValue() == null || token.getValue().trim().isEmpty()) {
                continue;
            }

            String term = token.getValue();

            try {
                // Find semantic cluster for this term
                Set<String> clusterMembers = semanticIndex.findSemanticCluster(term);

                if (clusterMembers == null || clusterMembers.isEmpty()) {
                    continue;
                }

                for (String clusterMember : clusterMembers) {
                    if (clusterMember == null || clusterMember.trim().isEmpty() || clusterMember.equals(term)) {
                        continue; // Don't include the original term or null/empty members
                    }

                    List<EntityMatch> clusterMatches = registry.findExactMatches(clusterMember);

                    for (EntityMatch originalMatch : clusterMatches) {
                        if (originalMatch == null) continue;

                        // Create new match to avoid mutating shared objects
                        EntityMatch match = createSemanticMatch(originalMatch, 0.7,
                            "semantic cluster member: " + term + " -> " + clusterMember);
                        matches.add(match);
                    }
                }
            } catch (Exception e) {
                log.warn("Error processing semantic cluster for term '{}': {}", term, e.getMessage());
            }
        }

        return matches;
    }
    
    /**
     * Step 5: Find contextual relationship matches
     */
    private List<EntityMatch> findContextualMatches(QueryContext context) {
        List<EntityMatch> matches = new ArrayList<>();
        
        // Use query intent to guide semantic expansion
        if (context.getIntent() != null) {
            matches.addAll(findIntentGuidedMatches(context));
        }
        
        // Use query type for contextual expansion
        if (context.getQueryType() != null) {
            matches.addAll(findTypeGuidedMatches(context));
        }
        
        return matches;
    }
    
    /**
     * Find matches guided by query intent
     */
    private List<EntityMatch> findIntentGuidedMatches(QueryContext context) {
        List<EntityMatch> matches = new ArrayList<>();
        
        switch (context.getIntent()) {
            case FIND_CLASS:
                matches.addAll(findClassSemanticMatches(context));
                break;
            case FIND_METHOD:
                matches.addAll(findMethodSemanticMatches(context));
                break;
            case FIND_IMPLEMENTATION:
                matches.addAll(findImplementationSemanticMatches(context));
                break;
            case FIND_RELATED:
                matches.addAll(findRelatedSemanticMatches(context));
                break;
        }
        
        return matches;
    }
    
    /**
     * Find class-specific semantic matches
     */
    private List<EntityMatch> findClassSemanticMatches(QueryContext context) {
        if (context == null || context.getDomainTerms() == null) {
            return Collections.emptyList();
        }

        List<EntityMatch> matches = new ArrayList<>();
        String[] classSuffixes = {"Service", "Controller", "Manager", "Handler", "Repository", "Entity"};

        // Look for common class patterns with domain terms
        for (QueryContext.Token token : context.getDomainTerms()) {
            if (token == null || token.getValue() == null || token.getValue().trim().isEmpty()) {
                continue;
            }

            String term = token.getValue();

            for (String suffix : classSuffixes) {
                try {
                    String classPattern = capitalize(term) + suffix;
                    List<EntityMatch> classMatches = registry.findExactMatches(classPattern);

                    for (EntityMatch originalMatch : classMatches) {
                        if (originalMatch == null || originalMatch.getEntityType() != EntityMatch.EntityType.CLASS) {
                            continue;
                        }

                        // Create new match to avoid mutating shared objects
                        EntityMatch match = createSemanticMatch(originalMatch, 0.75,
                            "class pattern: " + term + " + " + suffix);
                        matches.add(match);
                    }
                } catch (Exception e) {
                    log.warn("Error processing class pattern for term '{}' with suffix '{}': {}",
                        term, suffix, e.getMessage());
                }
            }
        }

        return matches;
    }
    
    /**
     * Find method-specific semantic matches
     */
    private List<EntityMatch> findMethodSemanticMatches(QueryContext context) {
        if (context == null || context.getTokens() == null) {
            return Collections.emptyList();
        }

        List<EntityMatch> matches = new ArrayList<>();

        // Look for common method patterns with action words
        for (QueryContext.Token token : context.getTokens()) {
            if (token == null || token.getType() != QueryContext.Token.TokenType.ACTION_WORD ||
                token.getValue() == null || token.getValue().trim().isEmpty()) {
                continue;
            }

            String action = token.getValue();

            try {
                // Find methods that start with this action
                List<EntityMatch> actionMatches = registry.findByPrefix(action);

                for (EntityMatch originalMatch : actionMatches) {
                    if (originalMatch == null || originalMatch.getEntityType() != EntityMatch.EntityType.METHOD) {
                        continue;
                    }

                    // Create new match to avoid mutating shared objects
                    EntityMatch match = createSemanticMatch(originalMatch, 0.7,
                        "action pattern: " + action + "*");
                    matches.add(match);
                }
            } catch (Exception e) {
                log.warn("Error processing action pattern for '{}': {}", action, e.getMessage());
            }
        }

        return matches;
    }
    
    /**
     * Find implementation-specific semantic matches
     */
    private List<EntityMatch> findImplementationSemanticMatches(QueryContext context) {
        if (context == null || context.getDomainTerms() == null) {
            return Collections.emptyList();
        }

        List<EntityMatch> matches = new ArrayList<>();
        String[] implSuffixes = {"Impl", "Implementation", "Concrete"};

        // Look for implementation patterns
        for (QueryContext.Token token : context.getDomainTerms()) {
            if (token == null || token.getValue() == null || token.getValue().trim().isEmpty()) {
                continue;
            }

            String term = token.getValue();

            for (String suffix : implSuffixes) {
                try {
                    String implPattern = capitalize(term) + suffix;
                    List<EntityMatch> implMatches = registry.findExactMatches(implPattern);

                    for (EntityMatch originalMatch : implMatches) {
                        if (originalMatch == null) continue;

                        // Create new match to avoid mutating shared objects
                        EntityMatch match = createSemanticMatch(originalMatch, 0.8,
                            "implementation pattern: " + term + " + " + suffix);
                        matches.add(match);
                    }
                } catch (Exception e) {
                    log.warn("Error processing implementation pattern for term '{}' with suffix '{}': {}",
                        term, suffix, e.getMessage());
                }
            }
        }

        return matches;
    }
    
    /**
     * Find related semantic matches
     */
    private List<EntityMatch> findRelatedSemanticMatches(QueryContext context) {
        if (context == null || context.getDomainTerms() == null) {
            return Collections.emptyList();
        }

        List<EntityMatch> matches = new ArrayList<>();

        // Find entities that share domain terms
        Set<String> allDomainTerms = new HashSet<>();
        for (QueryContext.Token token : context.getDomainTerms()) {
            if (token == null || token.getValue() == null || token.getValue().trim().isEmpty()) {
                continue;
            }

            String tokenValue = token.getValue();
            allDomainTerms.add(tokenValue);

            try {
                Set<String> relatedTerms = semanticIndex.getDomainTerms(tokenValue);
                if (relatedTerms != null) {
                    allDomainTerms.addAll(relatedTerms);
                }
            } catch (Exception e) {
                log.warn("Error getting domain terms for '{}': {}", tokenValue, e.getMessage());
            }
        }

        for (String domainTerm : allDomainTerms) {
            if (domainTerm == null || domainTerm.trim().isEmpty()) {
                continue;
            }

            try {
                List<EntityMatch> relatedMatches = findEntitiesWithTerm(domainTerm);

                for (EntityMatch originalMatch : relatedMatches) {
                    if (originalMatch == null) continue;

                    // Create new match to avoid mutating shared objects
                    EntityMatch match = createSemanticMatch(originalMatch, 0.55,
                        "related domain term: " + domainTerm);
                    matches.add(match);
                }
            } catch (Exception e) {
                log.warn("Error finding entities for domain term '{}': {}", domainTerm, e.getMessage());
            }
        }

        return matches;
    }
    
    /**
     * Find matches guided by query type
     */
    private List<EntityMatch> findTypeGuidedMatches(QueryContext context) {
        List<EntityMatch> matches = new ArrayList<>();
        
        switch (context.getQueryType()) {
            case FUNCTIONALITY:
                matches.addAll(findFunctionalityMatches(context));
                break;
            case RELATIONSHIP:
                matches.addAll(findRelationshipMatches(context));
                break;
            case PATTERN_SEARCH:
                matches.addAll(findPatternSearchMatches(context));
                break;
        }
        
        return matches;
    }
    
    /**
     * Step 6: Find intent-based matches (removed - functionality moved to findContextualMatches)
     */
    
    /**
     * Step 7: Filter and rank results by semantic relevance
     */
    private List<EntityMatch> filterAndRankMatches(List<EntityMatch> matches) {
        if (matches == null || matches.isEmpty()) {
            return Collections.emptyList();
        }

        // Group by entity ID and keep highest confidence match for each entity
        Map<String, EntityMatch> bestMatches = new HashMap<>();

        for (EntityMatch match : matches) {
            if (match == null || match.getEntityId() == null || match.getConfidence() < MIN_SIMILARITY_THRESHOLD) {
                continue;
            }

            String entityId = match.getEntityId();
            EntityMatch existing = bestMatches.get(entityId);

            if (existing == null || match.getConfidence() > existing.getConfidence()) {
                bestMatches.put(entityId, match);
            }
        }

        // Sort by confidence (descending) and then by match type priority
        return bestMatches.values().stream()
                .sorted(this::compareMatches)
                .limit(MAX_SEMANTIC_RESULTS)
                .collect(Collectors.toList());
    }

    /**
     * Compares two EntityMatch objects for sorting
     */
    private int compareMatches(EntityMatch a, EntityMatch b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;

        // Primary sort: confidence (higher first)
        int confidenceCompare = Double.compare(b.getConfidence(), a.getConfidence());
        if (confidenceCompare != 0) {
            return confidenceCompare;
        }

        // Secondary sort: match type preference (lower priority number = higher preference)
        int aPriority = getMatchTypePriority(a.getMatchType());
        int bPriority = getMatchTypePriority(b.getMatchType());
        return Integer.compare(aPriority, bPriority);
    }
    
    // ========== UTILITY METHODS ==========

    /**
     * Creates a new semantic match to avoid mutating shared objects
     */
    private EntityMatch createSemanticMatch(EntityMatch originalMatch, double confidence, String matchReason) {
        if (originalMatch == null) {
            return null;
        }

        return EntityMatch.builder()
                .entityId(originalMatch.getEntityId())
                .entityName(originalMatch.getEntityName())
                .entityType(originalMatch.getEntityType())
                .className(originalMatch.getClassName())
                .packageName(originalMatch.getPackageName())
                .signature(originalMatch.getSignature())
                .source("SemanticMatchingAgent")
                .matchType(EntityMatch.MatchType.SEMANTIC)
                .matchReason(matchReason)
                .confidence(confidence)
                .build();
    }

    /**
     * Finds entities that contain the given term (simplified implementation)
     */
    private List<EntityMatch> findEntitiesWithTerm(String term) {
        if (term == null || term.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<EntityMatch> matches = new ArrayList<>();

        try {
            // Try exact match first
            matches.addAll(registry.findExactMatches(term));

            // Try as prefix
            matches.addAll(registry.findByPrefix(term));

            // Try as suffix
            matches.addAll(registry.findBySuffix(term));
        } catch (Exception e) {
            log.warn("Error finding entities with term '{}': {}", term, e.getMessage());
        }

        return matches;
    }
    
    /**
     * Find entities related to functionality queries
     */
    private List<EntityMatch> findFunctionalityMatches(QueryContext context) {
        if (context == null || context.getTokens() == null) {
            return Collections.emptyList();
        }

        List<EntityMatch> matches = new ArrayList<>();

        // Look for functional keywords and expand to related entities
        for (QueryContext.Token token : context.getTokens()) {
            if (token == null || token.getValue() == null) continue;

            String tokenValue = token.getValue().toLowerCase();

            // Common functionality patterns
            if (tokenValue.contains("process") || tokenValue.contains("handle") ||
                tokenValue.contains("execute") || tokenValue.contains("perform")) {

                try {
                    // Find methods with these action words
                    List<EntityMatch> actionMatches = registry.findByPrefix(tokenValue);
                    for (EntityMatch originalMatch : actionMatches) {
                        if (originalMatch != null && originalMatch.getEntityType() == EntityMatch.EntityType.METHOD) {
                            EntityMatch match = createSemanticMatch(originalMatch, 0.6,
                                "functionality pattern: " + tokenValue);
                            matches.add(match);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error processing functionality pattern for '{}': {}", tokenValue, e.getMessage());
                }
            }
        }

        return matches;
    }

    /**
     * Find entities related to relationship queries
     */
    private List<EntityMatch> findRelationshipMatches(QueryContext context) {
        if (context == null || context.getTokens() == null) {
            return Collections.emptyList();
        }

        List<EntityMatch> matches = new ArrayList<>();

        // Look for relationship keywords
        for (QueryContext.Token token : context.getTokens()) {
            if (token == null || token.getValue() == null) continue;

            String tokenValue = token.getValue().toLowerCase();

            // Common relationship patterns
            if (tokenValue.contains("call") || tokenValue.contains("use") ||
                tokenValue.contains("depend") || tokenValue.contains("extend")) {

                try {
                    // Find related entities through domain terms
                    Set<String> relatedTerms = semanticIndex.getDomainTerms(tokenValue);
                    if (relatedTerms != null) {
                        for (String relatedTerm : relatedTerms) {
                            List<EntityMatch> relatedMatches = findEntitiesWithTerm(relatedTerm);
                            for (EntityMatch originalMatch : relatedMatches) {
                                if (originalMatch != null) {
                                    EntityMatch match = createSemanticMatch(originalMatch, 0.5,
                                        "relationship pattern: " + tokenValue + " -> " + relatedTerm);
                                    matches.add(match);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error processing relationship pattern for '{}': {}", tokenValue, e.getMessage());
                }
            }
        }

        return matches;
    }

    /**
     * Find entities for pattern search queries
     */
    private List<EntityMatch> findPatternSearchMatches(QueryContext context) {
        if (context == null || context.getTokens() == null) {
            return Collections.emptyList();
        }

        List<EntityMatch> matches = new ArrayList<>();

        // Look for pattern keywords
        for (QueryContext.Token token : context.getTokens()) {
            if (token == null || token.getValue() == null) continue;

            String tokenValue = token.getValue().toLowerCase();

            // Common pattern search terms
            if (tokenValue.contains("all") || tokenValue.contains("list") ||
                tokenValue.contains("find") || tokenValue.contains("search")) {

                try {
                    // Use domain terms to find broader matches
                    for (QueryContext.Token domainToken : context.getDomainTerms()) {
                        if (domainToken != null && domainToken.getValue() != null) {
                            List<EntityMatch> patternMatches = findEntitiesWithTerm(domainToken.getValue());
                            for (EntityMatch originalMatch : patternMatches) {
                                if (originalMatch != null) {
                                    EntityMatch match = createSemanticMatch(originalMatch, 0.45,
                                        "pattern search: " + tokenValue + " + " + domainToken.getValue());
                                    matches.add(match);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error processing pattern search for '{}': {}", tokenValue, e.getMessage());
                }
            }
        }

        return matches;
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }

        String trimmed = str.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        return Character.toUpperCase(trimmed.charAt(0)) +
               (trimmed.length() > 1 ? trimmed.substring(1).toLowerCase() : "");
    }
    
    private int getMatchTypePriority(EntityMatch.MatchType matchType) {
        if (matchType == null) {
            return 10; // Lowest priority for null match types
        }

        switch (matchType) {
            case EXACT: return 1;
            case SEMANTIC: return 2;
            case PREFIX: return 3;
            case SUFFIX: return 4;
            case PATTERN: return 5;
            case FUZZY: return 6;
            case ABBREVIATION: return 7;
            case PHONETIC: return 8;
            case LLM_SUGGESTED: return 9;
            default: return 10;
        }
    }
    
    @Override
    public boolean canHandle(QueryContext context) {
        if (context == null) {
            return false;
        }

        // Can handle if there are domain terms or the query has semantic intent
        return (context.getDomainTerms() != null && !context.getDomainTerms().isEmpty()) ||
               context.getIntent() != null ||
               context.getQueryType() == QueryContext.QueryType.FUNCTIONALITY ||
               context.getQueryType() == QueryContext.QueryType.EXPLORATORY;
    }

    @Override
    public double getHandlingConfidence(QueryContext context) {
        if (context == null) {
            return 0.0;
        }

        double confidence = 0.4; // Base confidence

        // Boost for domain terms
        if (context.getDomainTerms() != null) {
            confidence += Math.min(0.3, context.getDomainTerms().size() * 0.1);
        }

        // Boost for clear intent
        if (context.getIntent() != null) {
            confidence += 0.2;
        }

        // Boost for exploratory queries
        if (context.getQueryType() == QueryContext.QueryType.EXPLORATORY ||
            context.getQueryType() == QueryContext.QueryType.FUNCTIONALITY) {
            confidence += 0.2;
        }

        return Math.min(1.0, confidence);
    }
}