package com.tekion.javaastkg.agents.entity.registry;

import com.tekion.javaastkg.agents.entity.models.ClassEntity;
import com.tekion.javaastkg.agents.entity.models.MethodEntity;
import com.tekion.javaastkg.agents.entity.models.EntityMatch;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fast, in-memory registry of all codebase entities.
 * Provides multiple indexes for different search strategies following Factor 12 (stateless reducer).
 * 
 * Applied 12-Factor Principles:
 * - Factor 1: Structured outputs (EntityMatch objects)
 * - Factor 4: Tools as structured outputs (search methods return EntityMatch)
 * - Factor 10: Small focused responsibility (just entity indexing)
 * - Factor 12: Stateless (pure functions for search)
 */
@Component
@Slf4j
public class CodebaseEntityRegistry {
    
    private final Driver neo4jDriver;
    private final SessionConfig sessionConfig;
    
    // Core entity storage
    private final Map<String, ClassEntity> classIndex = new ConcurrentHashMap<>();
    private final Map<String, MethodEntity> methodIndex = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> packageIndex = new ConcurrentHashMap<>();

    // Name-based indexes for O(1) lookups
    private final Map<String, ClassEntity> classNameIndex = new ConcurrentHashMap<>();
    private final Map<String, List<MethodEntity>> methodNameIndex = new ConcurrentHashMap<>();

    // Pattern-based indexes for fast lookup
    private final Map<String, List<String>> classSuffixIndex = new ConcurrentHashMap<>(); // Service -> [UserService, PaymentService]
    private final Map<String, List<String>> classPrefixIndex = new ConcurrentHashMap<>(); // User -> [UserService, UserManager]
    private final Map<String, List<String>> methodPrefixIndex = new ConcurrentHashMap<>(); // get -> [getUser, getPayment]
    private final Map<String, List<String>> methodSuffixIndex = new ConcurrentHashMap<>(); // Method -> [getMethod, setMethod]
    private final Map<String, List<String>> methodActionIndex = new ConcurrentHashMap<>(); // User -> [getUser, saveUser]
    
    // Fuzzy search structures
    private BKTree classNameTree;
    private BKTree methodNameTree;
    private TrieNode classNameTrie;
    private TrieNode methodNameTrie;
    
    // Usage statistics
    private final Map<String, Integer> entityUsageCount = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> entityLastAccessed = new ConcurrentHashMap<>();
    
    // Cache refresh tracking
    private LocalDateTime lastRefresh;
    private boolean isInitialized = false;
    
    public CodebaseEntityRegistry(Driver neo4jDriver, SessionConfig sessionConfig) {
        this.neo4jDriver = neo4jDriver;
        this.sessionConfig = sessionConfig;
    }
    
    @PostConstruct
    public void initialize() {
        log.info("REGISTRY: Initializing CodebaseEntityRegistry...");
        refreshFromDatabase();
        log.info("REGISTRY: CodebaseEntityRegistry initialized with {} classes, {} methods", 
                classIndex.size(), methodIndex.size());
        log.info("REGISTRY: Registry state - isInitialized: {}, classCount: {}, methodCount: {}", 
                isInitialized, getClassCount(), getMethodCount());
    }
    
    /**
     * Refreshes the registry from Neo4j database
     * Factor 12: Pure function that rebuilds state from source of truth
     */
    public void refreshFromDatabase() {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            log.info("Refreshing entity registry from database...");

            // Clear existing indexes
            clearIndexes();

            // Load data with error recovery
            boolean classesLoaded = loadClasses(session);
            boolean methodsLoaded = loadMethods(session);
            boolean packagesLoaded = loadPackages(session);

            // Only build indexes if we have some data
            if (classesLoaded || methodsLoaded) {
                buildSearchIndexes();
                lastRefresh = LocalDateTime.now();
                isInitialized = true;

                log.info("Registry refresh completed. Loaded {} classes, {} methods, {} packages",
                        classIndex.size(), methodIndex.size(), packageIndex.size());
            } else {
                log.warn("Failed to load any data from database. Registry remains uninitialized.");
            }

        } catch (Exception e) {
            log.error("Failed to refresh entity registry", e);
            // Don't mark as initialized if refresh failed completely
            if (!isInitialized) {
                log.warn("Registry initialization failed. Some operations may not work correctly.");
            }
        }
    }
    
    /**
     * Loads all classes from Neo4j with optimized query and error handling
     */
    private boolean loadClasses(Session session) {
        try {
            // Optimized query - load basic class info first
            String query = """
                MATCH (c:Class)
                OPTIONAL MATCH (c)-[:EXTENDS]->(super:Class)
                OPTIONAL MATCH (c)-[]->(d:Description)
                RETURN c.id as id,
                       c.name as name,
                       c.fullName as fullName,
                       c.packageName as packageName,
                       c.filePath as filePath,
                       labels(c) as labels,
                       super.name as superClass,
                       d.content as description
                LIMIT 10000
                """;

            int loadedCount = 0;
            var records = session.run(query).list();

            for (var record : records) {
                try {
                    String id = record.get("id").asString("");
                    String name = record.get("name").asString("");

                    // Log first few records for debugging
                    if (loadedCount < 5) {
                        String description = record.get("description").asString("");
                        log.info("REGISTRY: Processing class record[{}] - id: '{}', name: '{}', package: '{}', description: '{}'", 
                            loadedCount + 1, id, name, record.get("packageName").asString(""), 
                            description.length() > 50 ? description.substring(0, 50) + "..." : description);
                    }

                    // Skip invalid records
                    if (id.isEmpty() || name.isEmpty()) {
                        log.warn("Skipping class with empty id or name");
                        continue;
                    }

                    ClassEntity entity = ClassEntity.builder()
                            .id(id)
                            .name(name)
                            .fullName(record.get("fullName").asString(""))
                            .packageName(record.get("packageName").asString(""))
                            .filePath(record.get("filePath").asString(""))
                            .superClass(record.get("superClass").asString(null))
                            .description(record.get("description").asString(""))
                            .interfaces(new HashSet<>()) // Load separately if needed
                            .methodIds(new ArrayList<>()) // Load separately if needed
                            .methodNames(new ArrayList<>()) // Load separately if needed
                            .nameTokens(tokenizeName(name))
                            .lastModified(LocalDateTime.now())
                            .build();

                    // Determine class type from labels
                    List<String> labels = record.get("labels").asList(String::valueOf);
                    entity.setType(determineClassType(labels));

                    classIndex.put(entity.getId(), entity);
                    classNameIndex.put(entity.getName(), entity);
                    loadedCount++;

                } catch (Exception e) {
                    log.warn("Error processing class record: {}", e.getMessage());
                }
            }

            log.info("Loaded {} classes", loadedCount);
            return loadedCount > 0;

        } catch (Exception e) {
            log.error("Failed to load classes from database", e);
            return false;
        }
    }
    
    /**
     * Loads all methods from Neo4j with error handling
     */
    private boolean loadMethods(Session session) {
        log.info("REGISTRY: Starting loadMethods() - attempting to load methods from Neo4j");
        
        try {
            // First, let's discover what relationships actually exist
            String relationshipQuery = """
                MATCH (m:Method)-[r]->(c:Class)
                RETURN type(r) as relationshipType, count(*) as count
                LIMIT 10
                """;
            
            log.info("REGISTRY: Discovering Method->Class relationships in Neo4j...");
            var relationshipRecords = session.run(relationshipQuery).list();
            
            for (var record : relationshipRecords) {
                String relType = record.get("relationshipType").asString();
                int count = record.get("count").asInt();
                log.info("REGISTRY: Found relationship: Method-[{}]->Class with {} occurrences", relType, count);
            }
            
            // Use the CONTAINS relationship: Class-[CONTAINS]->Method
            log.info("REGISTRY: Using CONTAINS relationship: Class-[CONTAINS]->Method");
            
            String workingQuery = """
                MATCH (c:Class)-[:CONTAINS]->(m:Method)
                OPTIONAL MATCH (m)-[]->(d:Description)
                RETURN m.id as id,
                       m.name as name,
                       m.signature as signature,
                       c.name as className,
                       c.packageName as packageName,
                       m.returnType as returnType,
                       m.modifiers as modifiers,
                       m.isConstructor as isConstructor,
                       d.content as description
                LIMIT 50000
                """;
            String workingRelationship = "CONTAINS";
            
            log.info("REGISTRY: Using relationship [{}] for method loading", workingRelationship);
            log.info("REGISTRY: Executing Neo4j query for methods: {}", workingQuery.trim());

            int loadedCount = 0;
            var records = session.run(workingQuery).list();
            
            log.info("REGISTRY: Neo4j query returned {} method records", records.size());
            
            if (records.isEmpty()) {
                log.info("REGISTRY: WARNING - No method records returned from Neo4j query!");
                log.info("REGISTRY: This suggests either no methods exist or the query/relationship structure is wrong");
                return false;
            }

            for (int i = 0; i < records.size(); i++) {
                var record = records.get(i);
                try {
                    String id = record.get("id").asString("");
                    String name = record.get("name").asString("");

                    // Log first few records for debugging
                    if (i < 5) {
                        String description = record.get("description").asString("");
                        log.info("REGISTRY: Processing method record[{}] - id: '{}', name: '{}', className: '{}', signature: '{}', description: '{}'", 
                            i + 1, id, name, record.get("className").asString(""), record.get("signature").asString(""), 
                            description.length() > 50 ? description.substring(0, 50) + "..." : description);
                    }

                    // Skip invalid records
                    if (id.isEmpty() || name.isEmpty()) {
                        log.info("REGISTRY: Skipping method record[{}] with empty id ('{}') or name ('{}')", i + 1, id, name);
                        continue;
                    }

                    MethodEntity entity = MethodEntity.builder()
                            .id(id)
                            .name(name)
                            .signature(record.get("signature").asString(""))
                            .className(record.get("className").asString(""))
                            .packageName(record.get("packageName").asString(""))
                            .returnType(record.get("returnType").asString(""))
                            .isConstructor(record.get("isConstructor").asBoolean(false))
                            .description(record.get("description").asString(""))
                            .nameTokens(tokenizeName(name))
                            .lastModified(LocalDateTime.now())
                            .build();

                    methodIndex.put(entity.getId(), entity);

                    // Add to name-based index
                    methodNameIndex.computeIfAbsent(entity.getName(), k -> new ArrayList<>()).add(entity);
                    loadedCount++;

                } catch (Exception e) {
                    log.info("REGISTRY: Error processing method record[{}]: {} - {}", i + 1, e.getClass().getSimpleName(), e.getMessage());
                }
            }

            log.info("REGISTRY: Successfully loaded {} methods out of {} records", loadedCount, records.size());
            log.info("REGISTRY: Method index now contains {} entries", methodIndex.size());
            log.info("REGISTRY: Method name index now contains {} unique names", methodNameIndex.size());
            
            return loadedCount > 0;

        } catch (Exception e) {
            log.info("REGISTRY: FAILED to load methods from database - error: {} - message: {}", 
                e.getClass().getSimpleName(), e.getMessage());
            log.error("REGISTRY: Full error stack trace:", e);
            return false;
        }
    }
    
    /**
     * Loads all packages from Neo4j with error handling
     */
    private boolean loadPackages(Session session) {
        try {
            String query = """
                MATCH (c:Class)
                WHERE c.packageName IS NOT NULL AND c.packageName <> ''
                RETURN DISTINCT c.packageName as packageName,
                       collect(c.name) as classes
                """;

            int loadedCount = 0;
            var records = session.run(query).list();

            for (var record : records) {
                try {
                    String packageName = record.get("packageName").asString("");

                    if (!packageName.isEmpty()) {
                        Set<String> classes = new HashSet<>(record.get("classes").asList(String::valueOf));
                        packageIndex.put(packageName, classes);
                        loadedCount++;
                    }

                } catch (Exception e) {
                    log.warn("Error processing package record: {}", e.getMessage());
                }
            }

            log.info("Loaded {} packages", loadedCount);
            return loadedCount > 0;

        } catch (Exception e) {
            log.error("Failed to load packages from database", e);
            return false;
        }
    }
    
    /**
     * Builds all search indexes for fast lookup
     * Factor 12: Pure function that builds deterministic indexes
     */
    private void buildSearchIndexes() {
        log.debug("Building search indexes...");

        try {
            // Build class pattern indexes
            for (ClassEntity entity : classIndex.values()) {
                if (entity == null || entity.getName() == null) continue;

                String name = entity.getName();

                // Suffix index (Service, Controller, etc.)
                String suffix = entity.getSuffix();
                if (suffix != null && !suffix.isEmpty()) {
                    classSuffixIndex.computeIfAbsent(suffix, k -> new ArrayList<>()).add(name);
                }

                // Prefix index
                String prefix = entity.getPrefix();
                if (prefix != null && !prefix.isEmpty()) {
                    classPrefixIndex.computeIfAbsent(prefix, k -> new ArrayList<>()).add(name);
                }
            }

            // Build method pattern indexes
            for (MethodEntity entity : methodIndex.values()) {
                if (entity == null || entity.getName() == null) continue;

                String name = entity.getName();

                // Prefix index (get, set, process, etc.)
                String prefix = extractMethodPrefix(name);
                if (prefix != null && !prefix.isEmpty()) {
                    methodPrefixIndex.computeIfAbsent(prefix, k -> new ArrayList<>()).add(name);
                }

                // Suffix index for methods
                String suffix = extractMethodSuffix(name);
                if (suffix != null && !suffix.isEmpty()) {
                    methodSuffixIndex.computeIfAbsent(suffix, k -> new ArrayList<>()).add(name);
                }

                // Action index (User, Payment, etc.)
                String action = extractMethodAction(name);
                if (action != null && !action.isEmpty()) {
                    methodActionIndex.computeIfAbsent(action, k -> new ArrayList<>()).add(name);
                }
            }

            // Build fuzzy search structures
            buildFuzzySearchStructures();

            log.debug("Search indexes built successfully");

        } catch (Exception e) {
            log.error("Error building search indexes", e);
        }
    }
    
    /**
     * Builds BK-trees and Tries for fuzzy search
     */
    private void buildFuzzySearchStructures() {
        try {
            // Build BK-trees for edit distance search
            classNameTree = new BKTree();
            methodNameTree = new BKTree();

            // Build Tries for prefix search
            classNameTrie = new TrieNode();
            methodNameTrie = new TrieNode();

            // Add class names to structures
            for (ClassEntity entity : classIndex.values()) {
                if (entity != null && entity.getName() != null && !entity.getName().isEmpty()) {
                    String className = entity.getName();
                    classNameTree.add(className);
                    classNameTrie.insert(className.toLowerCase());
                }
            }

            // Add method names to structures
            for (MethodEntity entity : methodIndex.values()) {
                if (entity != null && entity.getName() != null && !entity.getName().isEmpty()) {
                    String methodName = entity.getName();
                    methodNameTree.add(methodName);
                    methodNameTrie.insert(methodName.toLowerCase());
                }
            }

            log.debug("Built fuzzy search structures: {} classes, {} methods in BK-trees",
                     classNameTree.size(), methodNameTree.size());

        } catch (Exception e) {
            log.error("Error building fuzzy search structures", e);
            // Initialize empty structures to prevent null pointer exceptions
            classNameTree = new BKTree();
            methodNameTree = new BKTree();
            classNameTrie = new TrieNode();
            methodNameTrie = new TrieNode();
        }
    }
    
    // ========== SEARCH METHODS (Factor 12: Stateless functions) ==========
    
    /**
     * Factor 12: Pure function - same input always produces same output
     */
    public List<EntityMatch> findExactMatches(String term) {
        if (term == null || term.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<EntityMatch> matches = new ArrayList<>();
        String trimmedTerm = term.trim();

        try {
            // Check class names using O(1) lookup
            ClassEntity classEntity = classNameIndex.get(trimmedTerm);
            if (classEntity != null) {
                matches.add(createClassMatch(classEntity, EntityMatch.MatchType.EXACT, 1.0, "exact name match"));
            }

            // Check method names using O(1) lookup
            List<MethodEntity> methodEntities = methodNameIndex.get(trimmedTerm);
            if (methodEntities != null) {
                for (MethodEntity entity : methodEntities) {
                    if (entity != null) {
                        matches.add(createMethodMatch(entity, EntityMatch.MatchType.EXACT, 1.0, "exact name match"));
                    }
                }
            }

            recordUsage(trimmedTerm);

        } catch (Exception e) {
            log.warn("Error finding exact matches for term: {}", trimmedTerm, e);
        }

        return matches;
    }
    
    /**
     * Factor 12: Pure function for pattern-based search
     */
    public List<EntityMatch> findByPattern(String pattern) {
        List<EntityMatch> matches = new ArrayList<>();
        
        if (pattern.endsWith("*")) {
            // Prefix search
            String prefix = pattern.substring(0, pattern.length() - 1);
            matches.addAll(findByPrefix(prefix));
        } else if (pattern.startsWith("*")) {
            // Suffix search
            String suffix = pattern.substring(1);
            matches.addAll(findBySuffix(suffix));
        }
        
        recordUsage(pattern);
        return matches;
    }

    /**
     * Finds entities that start with the given prefix (missing method referenced by FuzzyMatchingAgent)
     */
    public List<EntityMatch> findPrefixMatches(String prefix) {
        return findByPrefix(prefix);
    }

    /**
     * Finds entities that end with the given suffix (missing method referenced by FuzzyMatchingAgent)
     */
    public List<EntityMatch> findSuffixMatches(String suffix) {
        return findBySuffix(suffix);
    }

    /**
     * Factor 12: Pure function for prefix search
     */
    public List<EntityMatch> findByPrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<EntityMatch> matches = new ArrayList<>();
        String trimmedPrefix = prefix.trim();

        try {
            // Use Trie for efficient prefix search
            List<String> trieMatches = classNameTrie.findWordsWithPrefix(trimmedPrefix.toLowerCase());
            for (String className : trieMatches) {
                ClassEntity entity = classNameIndex.get(className);
                if (entity != null && entity.getName().toLowerCase().startsWith(trimmedPrefix.toLowerCase())) {
                    double confidence = calculatePrefixConfidence(trimmedPrefix, entity.getName());
                    matches.add(createClassMatch(entity, EntityMatch.MatchType.PREFIX, confidence, "prefix match: " + trimmedPrefix));
                }
            }

            // Method prefix matches using Trie
            List<String> methodTrieMatches = methodNameTrie.findWordsWithPrefix(trimmedPrefix.toLowerCase());
            for (String methodName : methodTrieMatches) {
                List<MethodEntity> entities = methodNameIndex.get(methodName);
                if (entities != null) {
                    for (MethodEntity entity : entities) {
                        if (entity != null && entity.getName().toLowerCase().startsWith(trimmedPrefix.toLowerCase())) {
                            double confidence = calculatePrefixConfidence(trimmedPrefix, entity.getName());
                            matches.add(createMethodMatch(entity, EntityMatch.MatchType.PREFIX, confidence, "prefix match: " + trimmedPrefix));
                        }
                    }
                }
            }

            // Fallback to pattern-based indexes
            List<String> classMatches = classPrefixIndex.getOrDefault(trimmedPrefix, Collections.emptyList());
            for (String className : classMatches) {
                ClassEntity entity = classNameIndex.get(className);
                if (entity != null && !containsClassMatch(matches, entity)) {
                    double confidence = calculatePrefixConfidence(trimmedPrefix, entity.getName());
                    matches.add(createClassMatch(entity, EntityMatch.MatchType.PREFIX, confidence, "prefix pattern match: " + trimmedPrefix));
                }
            }

            List<String> methodMatches = methodPrefixIndex.getOrDefault(trimmedPrefix, Collections.emptyList());
            for (String methodName : methodMatches) {
                List<MethodEntity> entities = methodNameIndex.get(methodName);
                if (entities != null) {
                    for (MethodEntity entity : entities) {
                        if (entity != null && !containsMethodMatch(matches, entity)) {
                            double confidence = calculatePrefixConfidence(trimmedPrefix, entity.getName());
                            matches.add(createMethodMatch(entity, EntityMatch.MatchType.PREFIX, confidence, "prefix pattern match: " + trimmedPrefix));
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.warn("Error finding prefix matches for: {}", trimmedPrefix, e);
        }

        return matches;
    }
    
    /**
     * Factor 12: Pure function for suffix search
     */
    public List<EntityMatch> findBySuffix(String suffix) {
        if (suffix == null || suffix.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<EntityMatch> matches = new ArrayList<>();
        String trimmedSuffix = suffix.trim();

        try {
            // Class suffix matches
            List<String> classMatches = classSuffixIndex.getOrDefault(trimmedSuffix, Collections.emptyList());
            for (String className : classMatches) {
                ClassEntity entity = classNameIndex.get(className);
                if (entity != null) {
                    double confidence = calculateSuffixConfidence(trimmedSuffix, entity.getName());
                    matches.add(createClassMatch(entity, EntityMatch.MatchType.SUFFIX, confidence, "suffix match: " + trimmedSuffix));
                }
            }

            // Method suffix matches
            List<String> methodMatches = methodSuffixIndex.getOrDefault(trimmedSuffix, Collections.emptyList());
            for (String methodName : methodMatches) {
                List<MethodEntity> entities = methodNameIndex.get(methodName);
                if (entities != null) {
                    for (MethodEntity entity : entities) {
                        if (entity != null) {
                            double confidence = calculateSuffixConfidence(trimmedSuffix, entity.getName());
                            matches.add(createMethodMatch(entity, EntityMatch.MatchType.SUFFIX, confidence, "suffix match: " + trimmedSuffix));
                        }
                    }
                }
            }

            // Also search for entities that end with the suffix (not just pattern-based)
            for (ClassEntity entity : classIndex.values()) {
                if (entity != null && entity.getName() != null &&
                    entity.getName().toLowerCase().endsWith(trimmedSuffix.toLowerCase()) &&
                    !containsClassMatch(matches, entity)) {
                    double confidence = calculateSuffixConfidence(trimmedSuffix, entity.getName());
                    matches.add(createClassMatch(entity, EntityMatch.MatchType.SUFFIX, confidence, "suffix name match: " + trimmedSuffix));
                }
            }

        } catch (Exception e) {
            log.warn("Error finding suffix matches for: {}", trimmedSuffix, e);
        }

        return matches;
    }
    
    /**
     * Factor 12: Pure function for fuzzy search using BK-tree
     */
    public List<EntityMatch> findSimilar(String term, int maxEditDistance) {
        if (term == null || term.trim().isEmpty() || maxEditDistance < 0) {
            return new ArrayList<>();
        }

        List<EntityMatch> matches = new ArrayList<>();
        String trimmedTerm = term.trim();

        try {
            // Find similar class names
            if (classNameTree != null && !classNameTree.isEmpty()) {
                List<String> similarClasses = classNameTree.search(trimmedTerm, maxEditDistance);
                for (String className : similarClasses) {
                    if (className != null) {
                        ClassEntity entity = classNameIndex.get(className);
                        if (entity != null) {
                            double confidence = calculateImprovedFuzzyConfidence(trimmedTerm, className, maxEditDistance);
                            matches.add(createClassMatch(entity, EntityMatch.MatchType.FUZZY, confidence, "fuzzy match"));
                        }
                    }
                }
            }

            // Find similar method names
            if (methodNameTree != null && !methodNameTree.isEmpty()) {
                List<String> similarMethods = methodNameTree.search(trimmedTerm, maxEditDistance);
                for (String methodName : similarMethods) {
                    if (methodName != null) {
                        List<MethodEntity> entities = methodNameIndex.get(methodName);
                        if (entities != null) {
                            for (MethodEntity entity : entities) {
                                if (entity != null) {
                                    double confidence = calculateImprovedFuzzyConfidence(trimmedTerm, methodName, maxEditDistance);
                                    matches.add(createMethodMatch(entity, EntityMatch.MatchType.FUZZY, confidence, "fuzzy match"));
                                }
                            }
                        }
                    }
                }
            }

            recordUsage(trimmedTerm);

        } catch (Exception e) {
            log.warn("Error finding similar matches for term: {}", trimmedTerm, e);
        }

        return matches;
    }
    
    /**
     * Factor 12: Pure function for compound term matching
     */
    public List<EntityMatch> findByCompound(String... terms) {
        if (terms == null || terms.length == 0) {
            return new ArrayList<>();
        }

        List<EntityMatch> matches = new ArrayList<>();

        try {
            // Filter out null and empty terms
            List<String> validTerms = Arrays.stream(terms)
                    .filter(term -> term != null && !term.trim().isEmpty())
                    .map(String::trim)
                    .collect(Collectors.toList());

            if (validTerms.isEmpty()) {
                return matches;
            }

            // Generate compound combinations
            List<String> compounds = generateCompounds(validTerms);

            for (String compound : compounds) {
                if (compound != null && !compound.isEmpty()) {
                    matches.addAll(findExactMatches(compound));
                }
            }

        } catch (Exception e) {
            log.warn("Error finding compound matches for terms: {}", Arrays.toString(terms), e);
        }

        return matches;
    }
    
    // ========== UTILITY METHODS ==========

    /**
     * Optimized O(1) class lookup by name
     */
    private ClassEntity findClassByName(String name) {
        if (name == null) return null;
        return classNameIndex.get(name);
    }

    /**
     * Optimized O(1) method lookup by name (returns first match)
     */
    private MethodEntity findMethodByName(String name) {
        if (name == null) return null;
        List<MethodEntity> entities = methodNameIndex.get(name);
        return (entities != null && !entities.isEmpty()) ? entities.get(0) : null;
    }

    /**
     * Checks if matches already contains a class match with the same entity
     */
    private boolean containsClassMatch(List<EntityMatch> matches, ClassEntity entity) {
        return matches.stream()
                .anyMatch(match -> match.getEntityType() == EntityMatch.EntityType.CLASS &&
                                 match.getEntityId().equals(entity.getId()));
    }

    /**
     * Checks if matches already contains a method match with the same entity
     */
    private boolean containsMethodMatch(List<EntityMatch> matches, MethodEntity entity) {
        return matches.stream()
                .anyMatch(match -> match.getEntityType() == EntityMatch.EntityType.METHOD &&
                                 match.getEntityId().equals(entity.getId()));
    }
    
    private EntityMatch createClassMatch(ClassEntity entity, EntityMatch.MatchType matchType, 
                                       double confidence, String reason) {
        return EntityMatch.builder()
                .entityId(entity.getId())
                .entityName(entity.getName())
                .entityType(EntityMatch.EntityType.CLASS)
                .source("CodebaseEntityRegistry")
                .confidence(confidence)
                .matchType(matchType)
                .matchReason(reason)
                .packageName(entity.getPackageName())
                .signature(entity.getFullName())
                .build();
    }
    
    private EntityMatch createMethodMatch(MethodEntity entity, EntityMatch.MatchType matchType, 
                                        double confidence, String reason) {
        return EntityMatch.builder()
                .entityId(entity.getId())
                .entityName(entity.getName())
                .entityType(EntityMatch.EntityType.METHOD)
                .source("CodebaseEntityRegistry")
                .confidence(confidence)
                .matchType(matchType)
                .matchReason(reason)
                .className(entity.getClassName())
                .packageName(entity.getPackageName())
                .signature(entity.getSignature())
                .build();
    }
    
    private List<String> tokenizeName(String name) {
        // Split CamelCase: getUserInfo -> [get, User, Info]
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        
        for (char c : name.toCharArray()) {
            if (Character.isUpperCase(c) && current.length() > 0) {
                tokens.add(current.toString());
                current = new StringBuilder();
            }
            current.append(Character.toLowerCase(c));
        }
        
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        
        return tokens;
    }
    
    private ClassEntity.ClassType determineClassType(List<String> labels) {
        if (labels.contains("Interface")) return ClassEntity.ClassType.INTERFACE;
        if (labels.contains("Enum")) return ClassEntity.ClassType.ENUM;
        if (labels.contains("Annotation")) return ClassEntity.ClassType.ANNOTATION;
        if (labels.contains("Record")) return ClassEntity.ClassType.RECORD;
        return ClassEntity.ClassType.CLASS;
    }
    
    private List<String> generateCompounds(List<String> terms) {
        List<String> compounds = new ArrayList<>();

        if (terms == null || terms.isEmpty()) {
            return compounds;
        }

        try {
            // Filter out empty terms and validate
            List<String> validTerms = terms.stream()
                    .filter(term -> term != null && !term.trim().isEmpty())
                    .map(String::trim)
                    .collect(Collectors.toList());

            if (validTerms.isEmpty()) {
                return compounds;
            }

            // Simple concatenation with different cases
            String camelCase = validTerms.stream()
                    .map(term -> {
                        if (term.isEmpty()) return "";
                        return Character.toUpperCase(term.charAt(0)) + term.substring(1).toLowerCase();
                    })
                    .collect(Collectors.joining());
            if (!camelCase.isEmpty()) {
                compounds.add(camelCase);
            }

            // First word lowercase (camelCase style)
            if (validTerms.size() > 0) {
                String firstTerm = validTerms.get(0);
                if (!firstTerm.isEmpty()) {
                    String firstLower = firstTerm.toLowerCase() +
                            validTerms.stream().skip(1)
                                    .map(term -> {
                                        if (term.isEmpty()) return "";
                                        return Character.toUpperCase(term.charAt(0)) + term.substring(1).toLowerCase();
                                    })
                                    .collect(Collectors.joining());
                    if (!firstLower.isEmpty()) {
                        compounds.add(firstLower);
                    }
                }
            }

            // All lowercase with underscores
            String snakeCase = validTerms.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.joining("_"));
            if (!snakeCase.isEmpty()) {
                compounds.add(snakeCase);
            }

        } catch (Exception e) {
            log.warn("Error generating compounds for terms: {}", terms, e);
        }

        return compounds;
    }
    
    /**
     * Improved fuzzy confidence calculation
     */
    private double calculateImprovedFuzzyConfidence(String query, String match, int maxEditDistance) {
        if (query == null || match == null) return 0.0;
        if (query.equals(match)) return 1.0;

        int editDistance = calculateEditDistance(query, match);
        int maxLength = Math.max(query.length(), match.length());

        if (maxLength == 0) return 0.0;

        // Base confidence based on edit distance relative to string length
        double confidence = 1.0 - (double) editDistance / maxLength;

        // Boost for exact matches within edit distance
        if (editDistance == 0) {
            confidence = 1.0;
        } else if (editDistance == 1) {
            confidence = Math.max(confidence, 0.8);
        } else if (editDistance == 2) {
            confidence = Math.max(confidence, 0.6);
        }

        // Penalty for very different lengths
        int lengthDiff = Math.abs(query.length() - match.length());
        if (lengthDiff > maxLength / 2) {
            confidence *= 0.7;
        }

        return Math.max(0.0, Math.min(1.0, confidence));
    }

    /**
     * Legacy method for backward compatibility
     */
    private double calculateFuzzyConfidence(String query, String match, int maxEditDistance) {
        return calculateImprovedFuzzyConfidence(query, match, maxEditDistance);
    }

    /**
     * Calculates confidence for prefix matches
     */
    private double calculatePrefixConfidence(String prefix, String fullName) {
        if (prefix == null || fullName == null) return 0.0;
        if (fullName.toLowerCase().startsWith(prefix.toLowerCase())) {
            double ratio = (double) prefix.length() / fullName.length();
            return Math.min(1.0, 0.7 + ratio * 0.3); // Base 0.7, up to 1.0 for longer prefixes
        }
        return 0.0;
    }

    /**
     * Calculates confidence for suffix matches
     */
    private double calculateSuffixConfidence(String suffix, String fullName) {
        if (suffix == null || fullName == null) return 0.0;
        if (fullName.toLowerCase().endsWith(suffix.toLowerCase())) {
            double ratio = (double) suffix.length() / fullName.length();
            return Math.min(1.0, 0.7 + ratio * 0.3); // Base 0.7, up to 1.0 for longer suffixes
        }
        return 0.0;
    }
    
    private int calculateEditDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]);
                }
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    /**
     * Extracts method prefix (get, set, is, has, etc.)
     */
    private String extractMethodPrefix(String methodName) {
        if (methodName == null || methodName.isEmpty()) return null;

        String[] commonPrefixes = {"get", "set", "is", "has", "can", "should", "will", "create", "build",
                                  "make", "find", "search", "process", "handle", "execute", "run", "start",
                                  "stop", "load", "save", "delete", "remove", "add", "update", "validate",
                                  "check", "verify", "parse", "format", "convert", "transform"};

        String lowerName = methodName.toLowerCase();
        for (String prefix : commonPrefixes) {
            if (lowerName.startsWith(prefix.toLowerCase()) && methodName.length() > prefix.length()) {
                return prefix;
            }
        }

        return null;
    }

    /**
     * Extracts method suffix (common method endings)
     */
    private String extractMethodSuffix(String methodName) {
        if (methodName == null || methodName.isEmpty()) return null;

        String[] commonSuffixes = {"Method", "Function", "Handler", "Processor", "Builder", "Factory",
                                  "Manager", "Service", "Helper", "Util", "Utils", "Tool", "Tools"};

        for (String suffix : commonSuffixes) {
            if (methodName.endsWith(suffix) && methodName.length() > suffix.length()) {
                return suffix;
            }
        }

        return null;
    }

    /**
     * Extracts action/domain from method name (User from getUserInfo)
     */
    private String extractMethodAction(String methodName) {
        if (methodName == null || methodName.isEmpty()) return null;

        // Remove common prefixes first
        String prefix = extractMethodPrefix(methodName);
        String remaining = methodName;

        if (prefix != null) {
            remaining = methodName.substring(prefix.length());
        }

        // Extract first significant word (usually the domain object)
        if (remaining.length() > 0) {
            // Find first uppercase letter or end of first word
            StringBuilder action = new StringBuilder();
            for (int i = 0; i < remaining.length(); i++) {
                char c = remaining.charAt(i);
                if (i > 0 && Character.isUpperCase(c)) {
                    break;
                }
                action.append(Character.toLowerCase(c));
            }

            String result = action.toString();
            return result.length() > 1 ? result : null;
        }

        return null;
    }

    private void recordUsage(String term) {
        if (term != null && !term.isEmpty()) {
            entityUsageCount.merge(term, 1, Integer::sum);
            entityLastAccessed.put(term, LocalDateTime.now());
        }
    }
    
    private void clearIndexes() {
        classIndex.clear();
        methodIndex.clear();
        packageIndex.clear();
        classNameIndex.clear();
        methodNameIndex.clear();
        classSuffixIndex.clear();
        classPrefixIndex.clear();
        methodPrefixIndex.clear();
        methodSuffixIndex.clear();
        methodActionIndex.clear();

        // Clear fuzzy search structures
        if (classNameTree != null) {
            classNameTree.clear();
        }
        if (methodNameTree != null) {
            methodNameTree.clear();
        }
        if (classNameTrie != null) {
            classNameTrie.clear();
        }
        if (methodNameTrie != null) {
            methodNameTrie.clear();
        }
    }
    
    // ========== SCHEDULED REFRESH ==========
    
    @Scheduled(fixedDelay = 3600000) // Every hour
    public void scheduledRefresh() {
        if (lastRefresh != null && lastRefresh.isBefore(LocalDateTime.now().minusHours(1))) {
            log.info("Performing scheduled registry refresh");
            refreshFromDatabase();
        }
    }
    
    // ========== PUBLIC API ==========
    
    public boolean isInitialized() {
        return isInitialized;
    }
    
    public int getClassCount() {
        return classIndex.size();
    }
    
    public int getMethodCount() {
        return methodIndex.size();
    }
    
    public int getPackageCount() {
        return packageIndex.size();
    }
    
    public Set<String> getCommonPrefixes() {
        return methodPrefixIndex.keySet();
    }
    
    public Set<String> getCommonSuffixes() {
        return classSuffixIndex.keySet();
    }
    
    public boolean hasClass(String className) {
        if (className == null || className.trim().isEmpty()) {
            return false;
        }
        return classNameIndex.containsKey(className.trim());
    }

    public boolean hasMethods(String methodName) {
        if (methodName == null || methodName.trim().isEmpty()) {
            return false;
        }
        return methodNameIndex.containsKey(methodName.trim());
    }

    public boolean exists(String entityName) {
        if (entityName == null || entityName.trim().isEmpty()) {
            return false;
        }
        String trimmed = entityName.trim();
        return hasClass(trimmed) || hasMethods(trimmed);
    }
    
    /**
     * Returns all class entities in the registry
     * Used by LuceneIndexPopulator for indexing
     */
    public Collection<ClassEntity> getAllClasses() {
        log.info("REGISTRY: getAllClasses called - returning {} class entities", classIndex.size());
        if (classIndex.isEmpty()) {
            log.info("REGISTRY: WARNING - Class index is empty! Registry initialized: {}", isInitialized);
        }
        return classIndex.values();
    }
    
    /**
     * Returns all method entities in the registry
     * Used by LuceneIndexPopulator for indexing
     */
    public Collection<MethodEntity> getAllMethods() {
        log.info("REGISTRY: getAllMethods called - returning {} method entities", methodIndex.size());
        if (methodIndex.isEmpty()) {
            log.info("REGISTRY: WARNING - Method index is empty! Registry initialized: {}", isInitialized);
        }
        return methodIndex.values();
    }
}