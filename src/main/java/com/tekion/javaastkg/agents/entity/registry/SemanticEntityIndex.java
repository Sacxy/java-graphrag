package com.tekion.javaastkg.agents.entity.registry;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Semantic index for entity embeddings and domain relationships.
 * Provides semantic similarity search and domain-specific term expansion.
 * 
 * Applied 12-Factor Principles:
 * - Factor 1: Structured outputs (SimilarEntity objects)
 * - Factor 4: Tools as structured outputs
 * - Factor 10: Small focused responsibility (semantic indexing only)
 * - Factor 12: Stateless search functions
 */
@Component
@Slf4j
public class SemanticEntityIndex {
    
    // Entity embeddings storage
    private final Map<String, float[]> entityEmbeddings = new ConcurrentHashMap<>();
    
    // Semantic clusters (entities grouped by similarity)
    private final Map<String, Set<String>> semanticClusters = new ConcurrentHashMap<>();
    
    // Domain-specific term mappings
    private final Map<String, Set<String>> domainTerms = new ConcurrentHashMap<>();
    
    // Business context mappings
    private final Map<String, Set<String>> businessContexts = new ConcurrentHashMap<>();
    
    // Abbreviation expansions
    private final Map<String, Set<String>> abbreviationMappings = new ConcurrentHashMap<>();
    
    // Phonetic mappings (Soundex-like)
    private final Map<String, Set<String>> phoneticMappings = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing SemanticEntityIndex...");
        buildDomainMappings();
        buildAbbreviationMappings();
        buildBusinessContexts();
        log.info("SemanticEntityIndex initialized");
    }
    
    /**
     * Builds domain-specific term mappings for the codebase
     */
    private void buildDomainMappings() {
        // Payment domain
        domainTerms.put("payment", Set.of("transaction", "charge", "billing", "invoice", 
                "payment", "pay", "money", "financial", "cost", "price", "amount"));
        
        // User domain
        domainTerms.put("user", Set.of("customer", "client", "account", "person", "member", 
                "profile", "identity", "authentication", "auth", "login"));
        
        // Order domain
        domainTerms.put("order", Set.of("purchase", "order", "cart", "checkout", "item", 
                "product", "inventory", "stock", "catalog"));
        
        // Service domain
        domainTerms.put("service", Set.of("api", "endpoint", "handler", "controller", 
                "processor", "manager", "engine", "worker"));
        
        // Data domain
        domainTerms.put("data", Set.of("entity", "model", "repository", "dao", "database", 
                "storage", "persistence", "record", "document"));
        
        // Validation domain
        domainTerms.put("validation", Set.of("validate", "verify", "check", "confirm", 
                "ensure", "assert", "test", "audit", "compliance"));
        
        // Security domain
        domainTerms.put("security", Set.of("auth", "authentication", "authorization", 
                "permission", "role", "access", "token", "credential", "encrypt"));
        
        // Communication domain
        domainTerms.put("communication", Set.of("message", "notification", "email", "sms", 
                "alert", "broadcast", "publish", "subscribe", "event"));
    }
    
    /**
     * Builds common abbreviation mappings
     */
    private void buildAbbreviationMappings() {
        abbreviationMappings.put("svc", Set.of("service"));
        abbreviationMappings.put("mgr", Set.of("manager"));
        abbreviationMappings.put("ctrl", Set.of("controller"));
        abbreviationMappings.put("repo", Set.of("repository"));
        abbreviationMappings.put("impl", Set.of("implementation", "implement"));
        abbreviationMappings.put("util", Set.of("utility", "utilities"));
        abbreviationMappings.put("config", Set.of("configuration"));
        abbreviationMappings.put("proc", Set.of("processor", "process"));
        abbreviationMappings.put("auth", Set.of("authentication", "authorization"));
        abbreviationMappings.put("admin", Set.of("administrator", "administration"));
        abbreviationMappings.put("info", Set.of("information"));
        abbreviationMappings.put("ref", Set.of("reference"));
        abbreviationMappings.put("temp", Set.of("temporary", "template"));
        abbreviationMappings.put("calc", Set.of("calculator", "calculate"));
        abbreviationMappings.put("gen", Set.of("generator", "generate"));
        abbreviationMappings.put("val", Set.of("validator", "validate", "value"));
        abbreviationMappings.put("trans", Set.of("transaction", "transformation", "translate"));
        abbreviationMappings.put("log", Set.of("logger", "logging"));
    }
    
    /**
     * Builds business context mappings
     */
    private void buildBusinessContexts() {
        // E-commerce contexts
        businessContexts.put("ecommerce", Set.of("order", "payment", "product", "cart", 
                "checkout", "shipping", "inventory", "catalog", "customer"));
        
        // Financial contexts
        businessContexts.put("financial", Set.of("payment", "transaction", "billing", 
                "invoice", "accounting", "ledger", "balance", "credit", "debit"));
        
        // User management contexts
        businessContexts.put("user_management", Set.of("user", "profile", "account", 
                "authentication", "authorization", "permission", "role"));
        
        // Content management contexts
        businessContexts.put("content", Set.of("document", "file", "media", "asset", 
                "content", "template", "layout", "theme"));
        
        // Analytics contexts
        businessContexts.put("analytics", Set.of("metric", "report", "dashboard", 
                "statistic", "analysis", "insight", "tracking", "measurement"));
    }
    
    /**
     * Factor 12: Pure function for semantic similarity search
     */
    public List<SimilarEntity> findSimilar(float[] queryEmbedding, double threshold) {
        List<SimilarEntity> similarities = new ArrayList<>();
        
        for (Map.Entry<String, float[]> entry : entityEmbeddings.entrySet()) {
            double similarity = calculateCosineSimilarity(queryEmbedding, entry.getValue());
            
            if (similarity >= threshold) {
                similarities.add(SimilarEntity.builder()
                        .entityName(entry.getKey())
                        .similarity(similarity)
                        .build());
            }
        }
        
        // Sort by similarity (highest first)
        similarities.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        
        return similarities;
    }
    
    /**
     * Factor 12: Pure function for domain term expansion
     */
    public Set<String> getDomainTerms(String term) {
        Set<String> relatedTerms = new HashSet<>();
        String lowerTerm = term.toLowerCase();
        
        // Direct domain mapping
        Set<String> directTerms = domainTerms.get(lowerTerm);
        if (directTerms != null) {
            relatedTerms.addAll(directTerms);
        }
        
        // Check if term appears in any domain
        for (Map.Entry<String, Set<String>> entry : domainTerms.entrySet()) {
            if (entry.getValue().contains(lowerTerm)) {
                relatedTerms.addAll(entry.getValue());
                relatedTerms.add(entry.getKey()); // Add the domain key itself
            }
        }
        
        // Remove the original term
        relatedTerms.remove(lowerTerm);
        
        return relatedTerms;
    }
    
    /**
     * Factor 12: Pure function for abbreviation expansion
     */
    public Set<String> expandAbbreviation(String abbreviation) {
        String lowerAbbrev = abbreviation.toLowerCase();
        return abbreviationMappings.getOrDefault(lowerAbbrev, Collections.emptySet());
    }
    
    /**
     * Factor 12: Pure function for phonetic matching
     */
    public Set<String> findPhoneticMatches(String term) {
        String phoneticCode = calculateSoundex(term);
        return phoneticMappings.getOrDefault(phoneticCode, Collections.emptySet());
    }
    
    /**
     * Factor 12: Pure function for business context expansion
     */
    public Set<String> getBusinessContextTerms(String term) {
        Set<String> contextTerms = new HashSet<>();
        String lowerTerm = term.toLowerCase();
        
        for (Map.Entry<String, Set<String>> entry : businessContexts.entrySet()) {
            if (entry.getValue().contains(lowerTerm)) {
                contextTerms.addAll(entry.getValue());
            }
        }
        
        contextTerms.remove(lowerTerm);
        return contextTerms;
    }
    
    /**
     * Factor 12: Pure function for semantic clustering
     */
    public Set<String> findSemanticCluster(String entityName) {
        return semanticClusters.getOrDefault(entityName.toLowerCase(), Collections.emptySet());
    }
    
    /**
     * Adds or updates an entity embedding
     */
    public void addEntityEmbedding(String entityName, float[] embedding) {
        entityEmbeddings.put(entityName.toLowerCase(), embedding);
        updatePhoneticMappings(entityName);
    }
    
    /**
     * Updates phonetic mappings when new entity is added
     */
    private void updatePhoneticMappings(String entityName) {
        String phoneticCode = calculateSoundex(entityName);
        phoneticMappings.computeIfAbsent(phoneticCode, k -> new HashSet<>()).add(entityName);
    }
    
    /**
     * Builds semantic clusters based on embedding similarities
     */
    public void buildSemanticClusters() {
        log.info("Building semantic clusters...");
        
        List<String> entities = new ArrayList<>(entityEmbeddings.keySet());
        double clusterThreshold = 0.8; // High similarity threshold for clustering
        
        for (int i = 0; i < entities.size(); i++) {
            String entity1 = entities.get(i);
            Set<String> cluster = new HashSet<>();
            cluster.add(entity1);
            
            for (int j = i + 1; j < entities.size(); j++) {
                String entity2 = entities.get(j);
                
                double similarity = calculateCosineSimilarity(
                    entityEmbeddings.get(entity1), 
                    entityEmbeddings.get(entity2)
                );
                
                if (similarity >= clusterThreshold) {
                    cluster.add(entity2);
                }
            }
            
            if (cluster.size() > 1) {
                semanticClusters.put(entity1, cluster);
            }
        }
        
        log.info("Built {} semantic clusters", semanticClusters.size());
    }
    
    /**
     * Updates domain term mappings based on co-occurrence analysis
     */
    public void updateDomainTerms(Map<String, Map<String, Integer>> coOccurrences) {
        for (Map.Entry<String, Map<String, Integer>> entry : coOccurrences.entrySet()) {
            String term = entry.getKey();
            Map<String, Integer> coOccurs = entry.getValue();
            
            // Find highly co-occurring terms (above threshold)
            Set<String> relatedTerms = coOccurs.entrySet().stream()
                    .filter(e -> e.getValue() >= 5) // Threshold for co-occurrence
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            
            if (!relatedTerms.isEmpty()) {
                domainTerms.merge(term, relatedTerms, (existing, newTerms) -> {
                    Set<String> merged = new HashSet<>(existing);
                    merged.addAll(newTerms);
                    return merged;
                });
            }
        }
    }
    
    // ========== UTILITY METHODS ==========
    
    /**
     * Calculates cosine similarity between two vectors
     */
    private double calculateCosineSimilarity(float[] vector1, float[] vector2) {
        if (vector1.length != vector2.length) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2[i] * vector2[i];
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * Calculates Soundex code for phonetic matching
     */
    private String calculateSoundex(String word) {
        if (word == null || word.isEmpty()) {
            return "0000";
        }
        
        word = word.toUpperCase();
        StringBuilder soundex = new StringBuilder();
        
        // First character
        soundex.append(word.charAt(0));
        
        // Mapping for consonants
        Map<Character, Character> mapping = new HashMap<>();
        mapping.put('B', '1'); mapping.put('F', '1'); mapping.put('P', '1'); mapping.put('V', '1');
        mapping.put('C', '2'); mapping.put('G', '2'); mapping.put('J', '2'); mapping.put('K', '2');
        mapping.put('Q', '2'); mapping.put('S', '2'); mapping.put('X', '2'); mapping.put('Z', '2');
        mapping.put('D', '3'); mapping.put('T', '3');
        mapping.put('L', '4');
        mapping.put('M', '5'); mapping.put('N', '5');
        mapping.put('R', '6');
        
        char prevCode = getCode(word.charAt(0), mapping);
        
        for (int i = 1; i < word.length() && soundex.length() < 4; i++) {
            char currentCode = getCode(word.charAt(i), mapping);
            
            if (currentCode != '0' && currentCode != prevCode) {
                soundex.append(currentCode);
            }
            
            prevCode = currentCode;
        }
        
        // Pad with zeros
        while (soundex.length() < 4) {
            soundex.append('0');
        }
        
        return soundex.toString();
    }
    
    private char getCode(char c, Map<Character, Character> mapping) {
        return mapping.getOrDefault(c, '0');
    }
    
    // ========== DATA CLASSES ==========
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarEntity {
        private String entityName;
        private double similarity;
        private String reason;
    }
    
    // ========== PUBLIC API ==========
    
    public int getEntityCount() {
        return entityEmbeddings.size();
    }
    
    public int getDomainTermCount() {
        return domainTerms.values().stream().mapToInt(Set::size).sum();
    }
    
    public int getClusterCount() {
        return semanticClusters.size();
    }
    
    public boolean hasEmbedding(String entityName) {
        return entityEmbeddings.containsKey(entityName.toLowerCase());
    }
    
    public Set<String> getAllDomains() {
        return domainTerms.keySet();
    }
    
    public Set<String> getAllBusinessContexts() {
        return businessContexts.keySet();
    }
}