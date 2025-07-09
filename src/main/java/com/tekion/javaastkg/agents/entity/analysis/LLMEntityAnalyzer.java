package com.tekion.javaastkg.agents.entity.analysis;

import com.tekion.javaastkg.agents.entity.models.ClassEntity;
import com.tekion.javaastkg.agents.entity.models.MethodEntity;
import com.tekion.javaastkg.agents.entity.registry.CodebaseEntityRegistry;
import com.tekion.javaastkg.model.ExtractedEntities;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * LLM Entity Analyzer - Uses embedding-based pre-filtering followed by LLM analysis
 * to identify entities relevant to a query. Phase 2 Architectural Fix:
 * 1. Pre-filter entities using embedding similarity (top 30 from 590 total)
 * 2. LLM analyzes only the most relevant pre-filtered entities
 * 3. Extract those entities for subsequent processing
 *
 * Expected Impact: 85% reduction in LLM prompt size, faster processing, better focus
 */
@Component
@Slf4j
public class LLMEntityAnalyzer {

    private final CodebaseEntityRegistry registry;
    private final ChatLanguageModel llm;
    private final Driver neo4jDriver;
    private final SessionConfig sessionConfig;
    private final EmbeddingModel queryEmbeddingModel;

    // Configuration properties for pre-filtering
    @Value("${llm.entity.analysis.prefiltering.enabled:true}")
    private boolean preFilteringEnabled;

    @Value("${llm.entity.analysis.prefiltering.similarity_threshold:0.65}")
    private float preFilteringSimilarityThreshold;

    @Value("${llm.entity.analysis.prefiltering.max_entities:30}")
    private int maxPreFilteredEntities;

    @Value("${llm.entity.analysis.prefiltering.search_limit:50}")
    private int preFilteringSearchLimit;

    private static final String ENTITY_ANALYSIS_PROMPT = """
            You are analyzing a Java codebase to find entities relevant to a user query.
            
            Query: {{query}}
            
            Available Classes:
            {{classes}}
            
            Available Methods:
            {{methods}}
            
            Task: Analyze the query and identify the most relevant classes and methods.
            Return ONLY the names of relevant entities, one per line, in this format:
            CLASS: ClassName
            METHOD: methodName
            
            Focus on:
            1. Direct name matches and semantic similarity
            2. Methods that implement functionality described in the query
            3. Classes that contain related business logic
            4. Entities whose descriptions relate to the query intent
            
            Return the top 20 most relevant entities.
            """;

    public LLMEntityAnalyzer(CodebaseEntityRegistry registry,
                           @Qualifier("contextDistillerModel") ChatLanguageModel llm,
                           Driver neo4jDriver,
                           SessionConfig sessionConfig,
                           @Qualifier("queryEmbeddingModel") EmbeddingModel queryEmbeddingModel) {
        this.registry = registry;
        this.llm = llm;
        this.neo4jDriver = neo4jDriver;
        this.sessionConfig = sessionConfig;
        this.queryEmbeddingModel = queryEmbeddingModel;
    }

    /**
     * Analyzes entities using embedding-based pre-filtering followed by LLM analysis
     * Phase 2 Architectural Fix: Reduces LLM prompt size by 85%
     */
    public ExtractedEntities analyzeEntities(String query) {
        log.info("LLM_ENTITY_ANALYZER: Starting entity analysis for query: {}", query);

        try {
            List<ClassEntity> classesToAnalyze;
            List<MethodEntity> methodsToAnalyze;

            if (preFilteringEnabled) {
                // Phase 2 Fix: Pre-filter entities using embedding similarity
                log.info("LLM_ENTITY_ANALYZER: Pre-filtering enabled - using embedding similarity");

                // Step 1: Load ALL entities for pre-filtering
                List<ClassEntity> allClasses = new ArrayList<>(registry.getAllClasses());
                List<MethodEntity> allMethods = new ArrayList<>(registry.getAllMethods());

                log.info("LLM_ENTITY_ANALYZER: Loaded {} classes and {} methods for pre-filtering",
                        allClasses.size(), allMethods.size());

                // Step 2: Pre-filter using embedding similarity
                PreFilteringResult preFilterResult = preFilterEntitiesWithEmbeddings(query, allClasses, allMethods);
                classesToAnalyze = preFilterResult.getFilteredClasses();
                methodsToAnalyze = preFilterResult.getFilteredMethods();

                log.info("LLM_ENTITY_ANALYZER: Pre-filtering reduced entities from {}/{} to {}/{} ({}% reduction)",
                        allClasses.size(), allMethods.size(),
                        classesToAnalyze.size(), methodsToAnalyze.size(),
                        Math.round(100.0 * (1.0 - (double)(classesToAnalyze.size() + methodsToAnalyze.size()) /
                                                  (allClasses.size() + allMethods.size()))));
            } else {
                // Fallback: Use all entities (original behavior)
                log.info("LLM_ENTITY_ANALYZER: Pre-filtering disabled - using all entities");
                classesToAnalyze = new ArrayList<>(registry.getAllClasses());
                methodsToAnalyze = new ArrayList<>(registry.getAllMethods());

                log.info("LLM_ENTITY_ANALYZER: Loaded {} classes and {} methods for analysis",
                        classesToAnalyze.size(), methodsToAnalyze.size());
            }

            // Step 3: Format entities for LLM analysis
            String classesText = formatClassesForLLM(classesToAnalyze);
            String methodsText = formatMethodsForLLM(methodsToAnalyze);

            log.info("LLM_ENTITY_ANALYZER: Formatted {} classes and {} methods for LLM analysis",
                    classesToAnalyze.size(), methodsToAnalyze.size());

            // Step 4: Send to LLM for analysis
            String llmResponse = performLLMAnalysis(query, classesText, methodsText);

            log.info("LLM_ENTITY_ANALYZER: Received LLM analysis response");

            // Step 5: Parse LLM response and extract entity names
            ExtractedEntities result = parseLLMResponse(llmResponse);

            log.info("LLM_ENTITY_ANALYZER: Parsed LLM response - {} classes, {} methods, {} packages, {} terms",
                    result.getClasses().size(), result.getMethods().size(),
                    result.getPackages().size(), result.getTerms().size());

            return result;
            
        } catch (Exception e) {
            log.error("LLM_ENTITY_ANALYZER: Analysis failed for query: {}", query, e);
            return ExtractedEntities.builder()
                    .classes(Collections.emptyList())
                    .methods(Collections.emptyList())
                    .packages(Collections.emptyList())
                    .terms(Collections.emptyList())
                    .build();
        }
    }

    /**
     * Formats classes for LLM analysis
     */
    private String formatClassesForLLM(List<ClassEntity> classes) {
        if (classes.isEmpty()) {
            return "No classes available.";
        }

        return classes.stream()
                .limit(50) // EMERGENCY FIX: Reduced from 100 to 50 to prevent token overflow
                .map(cls -> {
                    String description = cls.getDescription() != null ? cls.getDescription() : "No description";
                    return String.format("- %s (%s.%s): %s",
                            cls.getName(), cls.getPackageName(), cls.getName(), description);
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Formats methods for LLM analysis
     */
    private String formatMethodsForLLM(List<MethodEntity> methods) {
        if (methods.isEmpty()) {
            return "No methods available.";
        }

        return methods.stream()
                .limit(100) // EMERGENCY FIX: Reduced from 200 to 100 to prevent token overflow
                .map(method -> {
                    String description = method.getDescription() != null ? method.getDescription() : "No description";
                    return String.format("- %s (%s): %s",
                            method.getName(), method.getSignature(), description);
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Performs LLM analysis with the formatted entities
     */
    private String performLLMAnalysis(String query, String classesText, String methodsText) {
        try {
            PromptTemplate promptTemplate = PromptTemplate.from(ENTITY_ANALYSIS_PROMPT);
            
            Prompt prompt = promptTemplate.apply(Map.of(
                    "query", query,
                    "classes", classesText,
                    "methods", methodsText
            ));
            
            log.info("LLM_ENTITY_ANALYZER: Sending analysis request to LLM");
            
            String response = llm.generate(prompt.text());
            
            log.info("LLM_ENTITY_ANALYZER: LLM analysis completed");
            log.info("LLM_ENTITY_ANALYZER: Raw LLM response: {}", response);
            
            return response;
            
        } catch (Exception e) {
            log.error("LLM_ENTITY_ANALYZER: LLM analysis failed", e);
            throw new RuntimeException("LLM analysis failed", e);
        }
    }

    /**
     * Parses LLM response to extract entity names
     */
    private ExtractedEntities parseLLMResponse(String llmResponse) {
        List<String> classes = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        List<String> packages = new ArrayList<>();
        List<String> terms = new ArrayList<>();
        
        if (llmResponse == null || llmResponse.trim().isEmpty()) {
            log.warn("LLM_ENTITY_ANALYZER: Empty LLM response received");
            return ExtractedEntities.builder()
                    .classes(classes)
                    .methods(methods)
                    .packages(packages)
                    .terms(terms)
                    .build();
        }
        
        String[] lines = llmResponse.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            if (line.startsWith("CLASS:")) {
                String className = line.substring(6).trim();
                if (!className.isEmpty()) {
                    classes.add(className);
                }
            } else if (line.startsWith("METHOD:")) {
                String methodName = line.substring(7).trim();
                if (!methodName.isEmpty()) {
                    methods.add(methodName);
                }
            }
        }
        
        log.info("LLM_ENTITY_ANALYZER: Extracted {} classes and {} methods from LLM response", 
                classes.size(), methods.size());
        
        // Log the specific entities identified by LLM
        if (!classes.isEmpty()) {
            log.info("LLM_ENTITY_ANALYZER: LLM-identified CLASSES: {}", classes);
        }
        if (!methods.isEmpty()) {
            log.info("LLM_ENTITY_ANALYZER: LLM-identified METHODS: {}", methods);
        }
        
        return ExtractedEntities.builder()
                .classes(classes)
                .methods(methods)
                .packages(packages)
                .terms(terms)
                .build();
    }

    /**
     * Pre-filters entities using embedding similarity to reduce LLM prompt size
     * Phase 2 Architectural Fix: 85% reduction in entities sent to LLM
     */
    private PreFilteringResult preFilterEntitiesWithEmbeddings(String query,
                                                               List<ClassEntity> allClasses,
                                                               List<MethodEntity> allMethods) {
        log.info("LLM_ENTITY_ANALYZER: Starting embedding-based pre-filtering for query: {}", query);

        try {
            // Step 1: Generate query embedding
            float[] queryEmbedding = queryEmbeddingModel.embed(query).content().vector();
            log.debug("LLM_ENTITY_ANALYZER: Generated query embedding with {} dimensions", queryEmbedding.length);

            // Step 2: Search for similar classes and methods using Neo4j vector indexes
            List<SimilarEntity> similarClasses = searchSimilarClasses(queryEmbedding);
            List<SimilarEntity> similarMethods = searchSimilarMethods(queryEmbedding);

            log.info("LLM_ENTITY_ANALYZER: Found {} similar classes and {} similar methods above threshold {}",
                    similarClasses.size(), similarMethods.size(), preFilteringSimilarityThreshold);

            // Step 3: Convert similar entities back to ClassEntity and MethodEntity objects
            List<ClassEntity> filteredClasses = mapSimilarEntitiesToClasses(similarClasses, allClasses);
            List<MethodEntity> filteredMethods = mapSimilarEntitiesToMethods(similarMethods, allMethods);

            // Step 4: Ensure we don't exceed the maximum limit
            if (filteredClasses.size() + filteredMethods.size() > maxPreFilteredEntities) {
                // Combine and sort by similarity score, then take top N
                List<SimilarEntity> allSimilar = new ArrayList<>();
                allSimilar.addAll(similarClasses);
                allSimilar.addAll(similarMethods);

                allSimilar.sort(Comparator.comparing(SimilarEntity::getScore).reversed());
                List<SimilarEntity> topEntities = allSimilar.stream()
                        .limit(maxPreFilteredEntities)
                        .collect(Collectors.toList());

                // Separate back into classes and methods
                List<SimilarEntity> topClasses = topEntities.stream()
                        .filter(e -> "class".equals(e.getType()))
                        .collect(Collectors.toList());
                List<SimilarEntity> topMethods = topEntities.stream()
                        .filter(e -> "method".equals(e.getType()))
                        .collect(Collectors.toList());

                filteredClasses = mapSimilarEntitiesToClasses(topClasses, allClasses);
                filteredMethods = mapSimilarEntitiesToMethods(topMethods, allMethods);

                log.info("LLM_ENTITY_ANALYZER: Limited to top {} entities: {} classes, {} methods",
                        maxPreFilteredEntities, filteredClasses.size(), filteredMethods.size());
            }

            return PreFilteringResult.builder()
                    .filteredClasses(filteredClasses)
                    .filteredMethods(filteredMethods)
                    .originalClassCount(allClasses.size())
                    .originalMethodCount(allMethods.size())
                    .reductionPercentage(calculateReductionPercentage(
                            allClasses.size() + allMethods.size(),
                            filteredClasses.size() + filteredMethods.size()))
                    .build();

        } catch (Exception e) {
            log.error("LLM_ENTITY_ANALYZER: Pre-filtering failed, falling back to all entities", e);
            // Fallback: return all entities if pre-filtering fails
            return PreFilteringResult.builder()
                    .filteredClasses(allClasses)
                    .filteredMethods(allMethods)
                    .originalClassCount(allClasses.size())
                    .originalMethodCount(allMethods.size())
                    .reductionPercentage(0.0)
                    .build();
        }
    }

    /**
     * Searches for similar classes using Neo4j vector index
     */
    private List<SimilarEntity> searchSimilarClasses(float[] queryEmbedding) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                CALL db.index.vector.queryNodes('class_embeddings', $topK, $queryVector)
                YIELD node, score
                WHERE score >= $threshold
                RETURN DISTINCT node.name as name,
                       node.fullName as fullName,
                       node.packageName as packageName,
                       score,
                       'class' as type
                ORDER BY score DESC
                """;

            Map<String, Object> params = Map.of(
                "topK", preFilteringSearchLimit,
                "queryVector", queryEmbedding,
                "threshold", preFilteringSimilarityThreshold
            );

            return session.run(query, params)
                .list(record -> SimilarEntity.builder()
                    .name(record.get("name").asString())
                    .fullName(record.get("fullName").asString())
                    .packageName(record.get("packageName").asString())
                    .score(record.get("score").asFloat())
                    .type(record.get("type").asString())
                    .build());

        } catch (Exception e) {
            log.error("LLM_ENTITY_ANALYZER: Failed to search similar classes", e);
            return Collections.emptyList();
        }
    }

    /**
     * Searches for similar methods using Neo4j vector index
     */
    private List<SimilarEntity> searchSimilarMethods(float[] queryEmbedding) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                CALL db.index.vector.queryNodes('method_embeddings', $topK, $queryVector)
                YIELD node, score
                WHERE score >= $threshold
                RETURN DISTINCT node.name as name,
                       node.signature as signature,
                       node.className as className,
                       score,
                       'method' as type
                ORDER BY score DESC
                """;

            Map<String, Object> params = Map.of(
                "topK", preFilteringSearchLimit,
                "queryVector", queryEmbedding,
                "threshold", preFilteringSimilarityThreshold
            );

            return session.run(query, params)
                .list(record -> SimilarEntity.builder()
                    .name(record.get("name").asString())
                    .signature(record.get("signature").asString())
                    .className(record.get("className").asString())
                    .score(record.get("score").asFloat())
                    .type(record.get("type").asString())
                    .build());

        } catch (Exception e) {
            log.error("LLM_ENTITY_ANALYZER: Failed to search similar methods", e);
            return Collections.emptyList();
        }
    }

    /**
     * Maps similar entities back to ClassEntity objects
     */
    private List<ClassEntity> mapSimilarEntitiesToClasses(List<SimilarEntity> similarEntities,
                                                          List<ClassEntity> allClasses) {
        Map<String, ClassEntity> classMap = allClasses.stream()
                .collect(Collectors.toMap(ClassEntity::getName, c -> c, (existing, replacement) -> existing));

        return similarEntities.stream()
                .map(similar -> classMap.get(similar.getName()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Maps similar entities back to MethodEntity objects
     */
    private List<MethodEntity> mapSimilarEntitiesToMethods(List<SimilarEntity> similarEntities,
                                                           List<MethodEntity> allMethods) {
        Map<String, MethodEntity> methodMap = allMethods.stream()
                .collect(Collectors.toMap(MethodEntity::getName, m -> m, (existing, replacement) -> existing));

        return similarEntities.stream()
                .map(similar -> methodMap.get(similar.getName()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Calculates the reduction percentage
     */
    private double calculateReductionPercentage(int originalCount, int filteredCount) {
        if (originalCount == 0) return 0.0;
        return Math.round(100.0 * (1.0 - (double) filteredCount / originalCount));
    }

    /**
     * Data class for similar entities found through embedding search
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarEntity {
        private String name;
        private String fullName;
        private String packageName;
        private String signature;
        private String className;
        private float score;
        private String type;
    }

    /**
     * Data class for pre-filtering results
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreFilteringResult {
        private List<ClassEntity> filteredClasses;
        private List<MethodEntity> filteredMethods;
        private int originalClassCount;
        private int originalMethodCount;
        private double reductionPercentage;
    }
}