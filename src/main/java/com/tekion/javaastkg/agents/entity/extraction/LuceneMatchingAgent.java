package com.tekion.javaastkg.agents.entity.extraction;

import com.tekion.javaastkg.agents.entity.models.EntityMatch;
import com.tekion.javaastkg.agents.entity.models.QueryContext;
import com.tekion.javaastkg.agents.entity.registry.LuceneEntityIndex;
import com.tekion.javaastkg.dto.CodeEntityDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LuceneMatchingAgent implements ExtractionAgent {
    
    private final LuceneEntityIndex luceneIndex;
    
    private static final Set<String> LUCENE_INDICATORS = Set.of(
        "AND", "OR", "NOT", "+", "-", "~", "*", "?", ":", "^", "\"", "(", ")"
    );
    
    @Override
    public List<EntityMatch> extract(QueryContext context) {
        String query = context.getOriginalQuery();
        log.info("LUCENE_AGENT: Starting extraction for query: '{}' with intent: {}", query, context.getIntent());
        
        List<EntityMatch> matches = new ArrayList<>();
        
        try {
            // Step 1: Standard search
            log.info("LUCENE_AGENT: Step 1 - Standard search for query: '{}'", query);
            List<CodeEntityDto> standardResults = luceneIndex.search(
                query, 
                LuceneEntityIndex.SearchType.STANDARD, 
                20
            );
            log.info("LUCENE_AGENT: Standard search returned {} results", standardResults.size());
            
            for (CodeEntityDto entity : standardResults) {
                double confidence = calculateConfidence(entity, query);
                Map<String, String> highlights = luceneIndex.getHighlightedSnippets(query, entity);
                
                EntityMatch match = createEntityMatch(
                    entity, 
                    EntityMatch.MatchType.PATTERN, 
                    confidence, 
                    buildExplanation(entity, highlights)
                );
                    
                matches.add(match);
            }
            log.info("LUCENE_AGENT: Created {} standard matches", matches.size());
            
            // Step 2: Fuzzy search if needed
            boolean needsFuzzy = matches.isEmpty() || getMaxConfidence(matches) < 0.6;
            log.info("LUCENE_AGENT: Need fuzzy search? {} (matches: {}, maxConfidence: {})", 
                needsFuzzy, matches.size(), matches.isEmpty() ? 0.0 : getMaxConfidence(matches));
            
            if (needsFuzzy) {
                log.info("LUCENE_AGENT: Step 2 - Fuzzy search for query: '{}'", query);
                List<CodeEntityDto> fuzzyResults = luceneIndex.fuzzySearch(query, 10);
                log.info("LUCENE_AGENT: Fuzzy search returned {} results", fuzzyResults.size());
                
                int fuzzyAdded = 0;
                for (CodeEntityDto entity : fuzzyResults) {
                    if (isAlreadyMatched(entity, matches)) {
                        log.info("LUCENE_AGENT: Skipping already matched entity: {}", entity.getName());
                        continue;
                    }
                    
                    double confidence = calculateFuzzyConfidence(entity, query);
                    Map<String, String> highlights = luceneIndex.getHighlightedSnippets(query, entity);
                    
                    EntityMatch match = createEntityMatch(
                        entity,
                        EntityMatch.MatchType.FUZZY,
                        confidence * 0.8,
                        buildFuzzyExplanation(entity, highlights)
                    );
                        
                    matches.add(match);
                    fuzzyAdded++;
                }
                log.info("LUCENE_AGENT: Added {} new fuzzy matches", fuzzyAdded);
            }
            
            // Step 3: Wildcard search if applicable
            if (containsWildcard(query)) {
                log.info("LUCENE_AGENT: Step 3 - Wildcard search for query: '{}'", query);
                String wildcardQuery = convertToWildcardQuery(query);
                log.info("LUCENE_AGENT: Converted to wildcard query: '{}'", wildcardQuery);
                
                List<CodeEntityDto> wildcardResults = luceneIndex.search(
                    wildcardQuery,
                    LuceneEntityIndex.SearchType.WILDCARD,
                    10
                );
                log.info("LUCENE_AGENT: Wildcard search returned {} results", wildcardResults.size());
                
                int wildcardAdded = 0;
                for (CodeEntityDto entity : wildcardResults) {
                    if (isAlreadyMatched(entity, matches)) continue;
                    
                    double confidence = calculateWildcardConfidence(entity, query);
                    
                    EntityMatch match = createEntityMatch(
                        entity,
                        EntityMatch.MatchType.PATTERN,
                        confidence * 0.9,
                        "Lucene wildcard pattern match: " + query
                    );
                        
                    matches.add(match);
                    wildcardAdded++;
                }
                log.info("LUCENE_AGENT: Added {} new wildcard matches", wildcardAdded);
            }
            
        } catch (Exception e) {
            log.info("LUCENE_AGENT: FAILED extraction for query: '{}' - error: {} - message: {}", 
                query, e.getClass().getSimpleName(), e.getMessage());
            log.error("LUCENE_AGENT: Full error stack trace:", e);
        }
        
        List<EntityMatch> sortedMatches = matches.stream()
            .sorted((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()))
            .limit(15)
            .collect(Collectors.toList());
        
        log.info("LUCENE_AGENT: Final result - returning {} matches for query: '{}'", sortedMatches.size(), query);
        
        // Log top matches for debugging
        for (int i = 0; i < Math.min(3, sortedMatches.size()); i++) {
            EntityMatch match = sortedMatches.get(i);
            log.info("LUCENE_AGENT: Top match[{}] - entity: {}, type: {}, confidence: {}, reason: {}", 
                i+1, match.getEntityName(), match.getMatchType(), match.getConfidence(), match.getMatchReason());
        }
        
        return sortedMatches;
    }
    
    @Override
    public boolean canHandle(QueryContext context) {
        String query = context.getOriginalQuery();
        
        boolean hasOperators = hasLuceneOperators(query);
        boolean hasQuotes = query.contains("\"");
        boolean isLongEnough = query.length() > 3;
        boolean hasCompatibleIntent = context.getIntent() == QueryContext.QueryIntent.FIND_CLASS ||
                                     context.getIntent() == QueryContext.QueryIntent.FIND_METHOD ||
                                     context.getIntent() == QueryContext.QueryIntent.FIND_PACKAGE;
        
        boolean canHandle = hasOperators || hasQuotes || isLongEnough || hasCompatibleIntent;
        
        log.info("LUCENE_AGENT: canHandle query '{}' = {} (operators: {}, quotes: {}, length: {}, intent: {})", 
            query, canHandle, hasOperators, hasQuotes, isLongEnough, context.getIntent());
        
        return canHandle;
    }
    
    @Override
    public double getHandlingConfidence(QueryContext context) {
        String query = context.getOriginalQuery();
        double confidence = 0.5;
        
        if (hasLuceneOperators(query)) {
            confidence = 0.9;
        } else if (query.contains("\"")) {
            confidence = 0.85;
        } else if (context.getIntent() == QueryContext.QueryIntent.FIND_CLASS ||
                  context.getIntent() == QueryContext.QueryIntent.FIND_METHOD ||
                  context.getIntent() == QueryContext.QueryIntent.FIND_PACKAGE) {
            confidence = 0.8;
        } else if (containsWildcard(query)) {
            confidence = 0.75;
        }
        
        if (query.split("\\s+").length > 2) {
            confidence += 0.1;
        }
        
        return Math.min(confidence, 1.0);
    }
    
    private EntityMatch createEntityMatch(CodeEntityDto entity, EntityMatch.MatchType matchType,
                                         double confidence, String reason) {
        EntityMatch.EntityType entityType = determineEntityType(entity);
        
        return EntityMatch.builder()
            .entityId(entity.getId())
            .entityName(entity.getName())
            .entityType(entityType)
            .source(getAgentName())
            .confidence(confidence)
            .matchType(matchType)
            .matchReason(reason)
            .className(entity.getClassName())
            .packageName(entity.getPackageName())
            .signature(getSignature(entity))
            .build();
    }
    
    private EntityMatch.EntityType determineEntityType(CodeEntityDto entity) {
        if (entity.getType() == null) {
            return EntityMatch.EntityType.CLASS;
        }
        
        switch (entity.getType()) {
            case CLASS:
                return EntityMatch.EntityType.CLASS;
            case METHOD:
                return EntityMatch.EntityType.METHOD;
            case PACKAGE:
                return EntityMatch.EntityType.PACKAGE;
            default:
                return EntityMatch.EntityType.CLASS;
        }
    }
    
    private String getSignature(CodeEntityDto entity) {
        if (entity.getType() == CodeEntityDto.EntityType.METHOD && entity.getMethodName() != null) {
            StringBuilder sig = new StringBuilder();
            if (entity.getClassName() != null) {
                sig.append(entity.getClassName()).append(".");
            }
            sig.append(entity.getMethodName());
            if (entity.getParameters() != null && !entity.getParameters().isEmpty()) {
                sig.append("(");
                sig.append(String.join(", ", entity.getParameters()));
                sig.append(")");
            }
            if (entity.getReturnType() != null) {
                sig.append(": ").append(entity.getReturnType());
            }
            return sig.toString();
        }
        return entity.getName();
    }
    
    private boolean hasLuceneOperators(String query) {
        return LUCENE_INDICATORS.stream().anyMatch(query::contains);
    }
    
    private boolean containsWildcard(String query) {
        return query.contains("*") || query.contains("?");
    }
    
    private String convertToWildcardQuery(String query) {
        if (!query.contains("*") && !query.contains("?")) {
            return "*" + query + "*";
        }
        return query;
    }
    
    private double calculateConfidence(CodeEntityDto entity, String query) {
        double baseConfidence = entity.getScore() != null ? 
            Math.min(entity.getScore() / 10.0, 1.0) : 0.5;
        
        String lowerQuery = query.toLowerCase();
        String entityName = entity.getName().toLowerCase();
        
        if (entityName.equals(lowerQuery)) {
            return Math.min(baseConfidence + 0.3, 1.0);
        } else if (entityName.contains(lowerQuery)) {
            return Math.min(baseConfidence + 0.2, 1.0);
        }
        
        return baseConfidence;
    }
    
    private double calculateFuzzyConfidence(CodeEntityDto entity, String query) {
        double baseConfidence = entity.getScore() != null ? 
            Math.min(entity.getScore() / 15.0, 0.8) : 0.4;
        
        return baseConfidence;
    }
    
    private double calculateWildcardConfidence(CodeEntityDto entity, String query) {
        String pattern = query.replace("*", ".*").replace("?", ".");
        boolean matches = entity.getName().toLowerCase().matches(pattern.toLowerCase());
        
        return matches ? 0.85 : 0.6;
    }
    
    private boolean isAlreadyMatched(CodeEntityDto entity, List<EntityMatch> matches) {
        return matches.stream()
            .anyMatch(m -> m.getEntityId().equals(entity.getId()));
    }
    
    private double getMaxConfidence(List<EntityMatch> matches) {
        return matches.stream()
            .mapToDouble(EntityMatch::getConfidence)
            .max()
            .orElse(0.0);
    }
    
    private String buildExplanation(CodeEntityDto entity, Map<String, String> highlights) {
        if (!highlights.isEmpty()) {
            return "Lucene match with highlights in: " + String.join(", ", highlights.keySet());
        }
        return "Lucene text search match for: " + entity.getName();
    }
    
    private String buildFuzzyExplanation(CodeEntityDto entity, Map<String, String> highlights) {
        if (!highlights.isEmpty()) {
            return "Fuzzy match with highlights in: " + String.join(", ", highlights.keySet());
        }
        return "Fuzzy search match (edit distance ≤ 2) for: " + entity.getName();
    }
}