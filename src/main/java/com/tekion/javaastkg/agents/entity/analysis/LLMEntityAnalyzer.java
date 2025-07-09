package com.tekion.javaastkg.agents.entity.analysis;

import com.tekion.javaastkg.agents.entity.models.ClassEntity;
import com.tekion.javaastkg.agents.entity.models.MethodEntity;
import com.tekion.javaastkg.agents.entity.registry.CodebaseEntityRegistry;
import com.tekion.javaastkg.model.ExtractedEntities;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * LLM Entity Analyzer - Uses LLM to analyze ALL classes and methods with descriptions
 * to identify entities relevant to a query. This implements the user's requested workflow:
 * 1. Get ALL class names and methods with descriptions
 * 2. LLM finds classes/methods related to query
 * 3. Extract those entities for subsequent processing
 */
@Component
@Slf4j
public class LLMEntityAnalyzer {

    private final CodebaseEntityRegistry registry;
    private final ChatLanguageModel llm;

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
                           @Qualifier("contextDistillerModel") ChatLanguageModel llm) {
        this.registry = registry;
        this.llm = llm;
    }

    /**
     * Analyzes all available entities using LLM to find ones relevant to the query
     */
    public ExtractedEntities analyzeEntities(String query) {
        log.info("LLM_ENTITY_ANALYZER: Starting LLM-based entity analysis for query: {}", query);
        
        try {
            // Step 1: Load ALL classes and methods with descriptions
            List<ClassEntity> allClasses = new ArrayList<>(registry.getAllClasses());
            List<MethodEntity> allMethods = new ArrayList<>(registry.getAllMethods());
            
            log.info("LLM_ENTITY_ANALYZER: Loaded {} classes and {} methods for analysis", 
                    allClasses.size(), allMethods.size());
            
            // Step 2: Format entities for LLM analysis
            String classesText = formatClassesForLLM(allClasses);
            String methodsText = formatMethodsForLLM(allMethods);
            
            log.info("LLM_ENTITY_ANALYZER: Formatted entities for LLM analysis");
            
            // Step 3: Send to LLM for analysis
            String llmResponse = performLLMAnalysis(query, classesText, methodsText);
            
            log.info("LLM_ENTITY_ANALYZER: Received LLM analysis response");
            
            // Step 4: Parse LLM response and extract entity names
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
                .limit(100) // Limit to prevent token overflow
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
                .limit(200) // Limit to prevent token overflow
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
}