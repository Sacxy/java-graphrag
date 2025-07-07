package com.tekion.javaastkg.query.intelligence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates compound terms by intelligently combining query terms.
 * This helps find classes and methods that use compound naming conventions.
 */
@Service
@Slf4j
public class CompoundTermGenerator {

    @Value("${query_optimization.pattern_expansion.compound_generation_enabled:true}")
    private boolean compoundGenerationEnabled;
    
    @Value("${query_optimization.pattern_expansion.max_compound_terms:20}")
    private int maxCompoundTerms;

    // Semantic clusters for intelligent term combination
    private static final Map<String, List<String>> SEMANTIC_CLUSTERS = Map.of(
        "pipeline", Arrays.asList("workflow", "orchestration", "sequence", "chain", "flow", "process"),
        "processing", Arrays.asList("execution", "handling", "transformation", "computation", "operation"),
        "data", Arrays.asList("information", "payload", "content", "entity", "model", "object", "record"),
        "status", Arrays.asList("state", "condition", "phase", "stage", "progress", "result"),
        "service", Arrays.asList("component", "module", "handler", "processor", "engine", "manager"),
        "config", Arrays.asList("configuration", "settings", "properties", "options", "parameters"),
        "task", Arrays.asList("job", "work", "operation", "action", "activity", "process"),
        "event", Arrays.asList("message", "notification", "signal", "trigger", "action"),
        "request", Arrays.asList("query", "call", "invocation", "command", "operation"),
        "response", Arrays.asList("result", "reply", "answer", "output", "return")
    );
    
    // Common connector words to ignore when creating compounds
    private static final Set<String> CONNECTOR_WORDS = Set.of(
        "and", "or", "the", "a", "an", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "as", "is", "are", "was", "were",
        "been", "being", "have", "has", "had", "do", "does", "did",
        "will", "would", "could", "should", "may", "might", "must"
    );
    
    // Domain-specific compound patterns
    private static final Map<List<String>, List<String>> COMPOUND_PATTERNS = new HashMap<>();
    
    static {
        // Initialize common compound patterns
        COMPOUND_PATTERNS.put(Arrays.asList("data", "processing"), 
            Arrays.asList("DataProcessor", "DataProcessingEngine", "ProcessingDataService"));
            
        COMPOUND_PATTERNS.put(Arrays.asList("event", "handler"), 
            Arrays.asList("EventHandler", "EventHandlerService", "EventHandlingManager"));
            
        COMPOUND_PATTERNS.put(Arrays.asList("pipeline", "processor"), 
            Arrays.asList("PipelineProcessor", "ProcessingPipeline", "PipelineProcessingEngine"));
            
        COMPOUND_PATTERNS.put(Arrays.asList("task", "executor"), 
            Arrays.asList("TaskExecutor", "TaskExecutionService", "ExecutorTaskManager"));
            
        COMPOUND_PATTERNS.put(Arrays.asList("message", "queue"), 
            Arrays.asList("MessageQueue", "QueueMessageHandler", "MessageQueueProcessor"));
    }

    /**
     * Generates compound terms from a list of query terms
     */
    public List<String> generateCompounds(List<String> terms) {
        if (!compoundGenerationEnabled || terms == null || terms.size() < 2) {
            return new ArrayList<>();
        }
        
        // Filter out connector words
        List<String> meaningfulTerms = terms.stream()
            .filter(term -> !CONNECTOR_WORDS.contains(term.toLowerCase()))
            .collect(Collectors.toList());
        
        Set<String> compounds = new LinkedHashSet<>();
        
        // Generate 2-term combinations
        generateTwoTermCompounds(meaningfulTerms, compounds);
        
        // Generate semantic expansions
        generateSemanticCompounds(meaningfulTerms, compounds);
        
        // Generate pattern-based compounds
        generatePatternBasedCompounds(meaningfulTerms, compounds);
        
        // Generate multi-term compounds (3+ terms)
        if (meaningfulTerms.size() >= 3) {
            generateMultiTermCompounds(meaningfulTerms, compounds);
        }
        
        // Limit the number of compounds
        List<String> limitedCompounds = compounds.stream()
            .limit(maxCompoundTerms)
            .collect(Collectors.toList());
        
        log.debug("Generated {} compound terms from {} input terms", limitedCompounds.size(), terms.size());
        return limitedCompounds;
    }
    
    /**
     * Generates all 2-term combinations
     */
    private void generateTwoTermCompounds(List<String> terms, Set<String> compounds) {
        for (int i = 0; i < terms.size(); i++) {
            for (int j = i + 1; j < terms.size(); j++) {
                String term1 = terms.get(i);
                String term2 = terms.get(j);
                
                // Direct combinations
                compounds.add(capitalize(term1) + capitalize(term2));
                compounds.add(capitalize(term2) + capitalize(term1));
                
                // With common suffixes
                compounds.add(capitalize(term1) + capitalize(term2) + "Service");
                compounds.add(capitalize(term2) + capitalize(term1) + "Service");
                compounds.add(capitalize(term1) + capitalize(term2) + "Manager");
                compounds.add(capitalize(term2) + capitalize(term1) + "Manager");
                
                // Underscore combinations
                compounds.add(term1.toLowerCase() + "_" + term2.toLowerCase());
                compounds.add(term2.toLowerCase() + "_" + term1.toLowerCase());
                
                // Method-like combinations
                compounds.add("get" + capitalize(term1) + capitalize(term2));
                compounds.add("process" + capitalize(term1) + capitalize(term2));
                compounds.add("handle" + capitalize(term1) + capitalize(term2));
            }
        }
    }
    
    /**
     * Generates compounds using semantic relationships
     */
    private void generateSemanticCompounds(List<String> terms, Set<String> compounds) {
        for (String term : terms) {
            List<String> semanticAlternatives = getSemanticAlternatives(term);
            
            // Combine with other terms
            for (String otherTerm : terms) {
                if (!term.equals(otherTerm)) {
                    for (String alternative : semanticAlternatives) {
                        compounds.add(capitalize(alternative) + capitalize(otherTerm));
                        compounds.add(capitalize(otherTerm) + capitalize(alternative));
                        
                        // With common patterns
                        compounds.add(capitalize(alternative) + capitalize(otherTerm) + "Handler");
                        compounds.add(capitalize(otherTerm) + capitalize(alternative) + "Processor");
                    }
                }
            }
        }
    }
    
    /**
     * Gets semantic alternatives for a term
     */
    private List<String> getSemanticAlternatives(String term) {
        String lowerTerm = term.toLowerCase();
        
        // Check if term is in semantic clusters
        for (Map.Entry<String, List<String>> entry : SEMANTIC_CLUSTERS.entrySet()) {
            if (entry.getKey().equals(lowerTerm)) {
                return entry.getValue();
            }
            if (entry.getValue().contains(lowerTerm)) {
                List<String> alternatives = new ArrayList<>(entry.getValue());
                alternatives.add(entry.getKey());
                return alternatives;
            }
        }
        
        return Collections.emptyList();
    }
    
    /**
     * Generates compounds based on known patterns
     */
    private void generatePatternBasedCompounds(List<String> terms, Set<String> compounds) {
        List<String> lowerTerms = terms.stream()
            .map(String::toLowerCase)
            .collect(Collectors.toList());
        
        for (Map.Entry<List<String>, List<String>> entry : COMPOUND_PATTERNS.entrySet()) {
            List<String> pattern = entry.getKey();
            
            // Check if all pattern terms are present in the query
            if (containsAllTerms(lowerTerms, pattern)) {
                compounds.addAll(entry.getValue());
                
                // Generate variations
                for (String compound : entry.getValue()) {
                    compounds.add(compound + "Impl");
                    compounds.add("Default" + compound);
                    compounds.add(compound + "Factory");
                }
            }
        }
    }
    
    /**
     * Generates compounds with 3 or more terms
     */
    private void generateMultiTermCompounds(List<String> terms, Set<String> compounds) {
        if (terms.size() > 4) {
            // Limit to prevent explosion of combinations
            return;
        }
        
        // Generate 3-term compounds
        if (terms.size() >= 3) {
            for (int i = 0; i < terms.size(); i++) {
                for (int j = i + 1; j < terms.size(); j++) {
                    for (int k = j + 1; k < terms.size(); k++) {
                        String term1 = capitalize(terms.get(i));
                        String term2 = capitalize(terms.get(j));
                        String term3 = capitalize(terms.get(k));
                        
                        // Different ordering patterns
                        compounds.add(term1 + term2 + term3);
                        compounds.add(term1 + term3 + term2);
                        compounds.add(term2 + term1 + term3);
                        
                        // With common suffixes
                        compounds.add(term1 + term2 + term3 + "Service");
                        compounds.add(term1 + term2 + term3 + "Manager");
                    }
                }
            }
        }
    }
    
    /**
     * Expands compound terms with semantic variations
     */
    public void expandWithSemantics(String term1, String term2, List<String> compounds) {
        List<String> term1Alternatives = getSemanticAlternatives(term1);
        List<String> term2Alternatives = getSemanticAlternatives(term2);
        
        // Add original term if no alternatives found
        if (term1Alternatives.isEmpty()) {
            term1Alternatives = Collections.singletonList(term1);
        }
        if (term2Alternatives.isEmpty()) {
            term2Alternatives = Collections.singletonList(term2);
        }
        
        // Generate all combinations
        for (String alt1 : term1Alternatives) {
            for (String alt2 : term2Alternatives) {
                compounds.add(capitalize(alt1) + capitalize(alt2));
                compounds.add(capitalize(alt2) + capitalize(alt1));
                
                // With common patterns
                compounds.add(capitalize(alt1) + capitalize(alt2) + "Service");
                compounds.add(capitalize(alt1) + capitalize(alt2) + "Handler");
                compounds.add(capitalize(alt1) + capitalize(alt2) + "Manager");
            }
        }
    }
    
    /**
     * Generates domain-specific compound suggestions
     */
    public List<String> getDomainCompounds(String domain, List<String> terms) {
        List<String> compounds = new ArrayList<>();
        
        switch (domain.toLowerCase()) {
            case "pipeline":
                compounds.addAll(generatePipelineCompounds(terms));
                break;
            case "data":
                compounds.addAll(generateDataCompounds(terms));
                break;
            case "event":
                compounds.addAll(generateEventCompounds(terms));
                break;
            case "service":
                compounds.addAll(generateServiceCompounds(terms));
                break;
        }
        
        return compounds;
    }
    
    /**
     * Generates pipeline-specific compounds
     */
    private List<String> generatePipelineCompounds(List<String> terms) {
        List<String> compounds = new ArrayList<>();
        
        for (String term : terms) {
            String capitalized = capitalize(term);
            compounds.add("Pipeline" + capitalized + "Processor");
            compounds.add(capitalized + "PipelineStep");
            compounds.add(capitalized + "PipelineStage");
            compounds.add("Pipeline" + capitalized + "Handler");
            compounds.add(capitalized + "ProcessingPipeline");
        }
        
        return compounds;
    }
    
    /**
     * Generates data-specific compounds
     */
    private List<String> generateDataCompounds(List<String> terms) {
        List<String> compounds = new ArrayList<>();
        
        for (String term : terms) {
            String capitalized = capitalize(term);
            compounds.add("Data" + capitalized + "Service");
            compounds.add(capitalized + "DataProcessor");
            compounds.add(capitalized + "DataHandler");
            compounds.add("Data" + capitalized + "Manager");
            compounds.add(capitalized + "DataTransformer");
        }
        
        return compounds;
    }
    
    /**
     * Generates event-specific compounds
     */
    private List<String> generateEventCompounds(List<String> terms) {
        List<String> compounds = new ArrayList<>();
        
        for (String term : terms) {
            String capitalized = capitalize(term);
            compounds.add("Event" + capitalized + "Handler");
            compounds.add(capitalized + "EventProcessor");
            compounds.add(capitalized + "EventListener");
            compounds.add("Event" + capitalized + "Publisher");
            compounds.add(capitalized + "EventConsumer");
        }
        
        return compounds;
    }
    
    /**
     * Generates service-specific compounds
     */
    private List<String> generateServiceCompounds(List<String> terms) {
        List<String> compounds = new ArrayList<>();
        
        for (String term : terms) {
            String capitalized = capitalize(term);
            compounds.add(capitalized + "Service");
            compounds.add(capitalized + "ServiceImpl");
            compounds.add("Default" + capitalized + "Service");
            compounds.add(capitalized + "ServiceManager");
            compounds.add(capitalized + "ServiceProvider");
        }
        
        return compounds;
    }
    
    /**
     * Checks if a list contains all terms from another list
     */
    private boolean containsAllTerms(List<String> terms, List<String> pattern) {
        return terms.containsAll(pattern);
    }
    
    /**
     * Capitalizes first letter of a string
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1).toLowerCase();
    }
}