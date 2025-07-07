package com.tekion.javaastkg.query.intelligence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Expands query terms using domain-specific synonyms and semantic relationships.
 * This helps find related code elements that use different terminology for similar concepts.
 */
@Service
@Slf4j
public class SemanticExpander {

    @Value("${query_optimization.semantic_expansion.domain_synonyms_enabled:true}")
    private boolean domainSynonymsEnabled;
    
    @Value("${query_optimization.semantic_expansion.max_semantic_expansions:10}")
    private int maxSemanticExpansions;

    // Domain-specific synonyms and related terms
    private static final Map<String, Set<String>> DOMAIN_SYNONYMS = new HashMap<>();
    
    static {
        // Pipeline and workflow related terms
        DOMAIN_SYNONYMS.put("pipeline", Set.of(
            "workflow", "orchestration", "chain", "sequence", "flow", 
            "process", "stream", "channel", "path", "route"
        ));
        
        // Processing and execution related terms
        DOMAIN_SYNONYMS.put("processing", Set.of(
            "execution", "handling", "transformation", "computation", 
            "operation", "manipulation", "treatment", "conversion", "action"
        ));
        
        // Manager and controller related terms
        DOMAIN_SYNONYMS.put("manager", Set.of(
            "coordinator", "orchestrator", "controller", "supervisor", 
            "handler", "administrator", "director", "governor", "organizer"
        ));
        
        // Data and information related terms
        DOMAIN_SYNONYMS.put("data", Set.of(
            "information", "payload", "content", "entity", "model", 
            "object", "record", "item", "element", "resource"
        ));
        
        // Service and component related terms
        DOMAIN_SYNONYMS.put("service", Set.of(
            "component", "module", "handler", "processor", "engine", 
            "provider", "facility", "utility", "system", "unit"
        ));
        
        // Status and state related terms
        DOMAIN_SYNONYMS.put("status", Set.of(
            "state", "condition", "phase", "stage", "progress", 
            "result", "outcome", "situation", "position", "standing"
        ));
        
        // Configuration related terms
        DOMAIN_SYNONYMS.put("config", Set.of(
            "configuration", "settings", "properties", "options", 
            "parameters", "preferences", "setup", "arrangement", "profile"
        ));
        
        DOMAIN_SYNONYMS.put("configuration", Set.of(
            "config", "settings", "properties", "options", 
            "parameters", "preferences", "setup", "arrangement", "profile"
        ));
        
        // Task and job related terms
        DOMAIN_SYNONYMS.put("task", Set.of(
            "job", "work", "operation", "action", "activity", 
            "process", "duty", "assignment", "function", "responsibility"
        ));
        
        // Event and message related terms
        DOMAIN_SYNONYMS.put("event", Set.of(
            "message", "notification", "signal", "trigger", 
            "action", "occurrence", "incident", "happening", "alert"
        ));
        
        // Request and query related terms
        DOMAIN_SYNONYMS.put("request", Set.of(
            "query", "call", "invocation", "command", "operation", 
            "demand", "petition", "solicitation", "appeal", "ask"
        ));
        
        // Response and result related terms
        DOMAIN_SYNONYMS.put("response", Set.of(
            "result", "reply", "answer", "output", "return", 
            "feedback", "reaction", "acknowledgment", "echo", "outcome"
        ));
        
        // Error and exception related terms
        DOMAIN_SYNONYMS.put("error", Set.of(
            "exception", "fault", "failure", "mistake", "problem", 
            "issue", "defect", "bug", "glitch", "anomaly"
        ));
        
        // Validation related terms
        DOMAIN_SYNONYMS.put("validate", Set.of(
            "verify", "check", "confirm", "ensure", "test", 
            "examine", "inspect", "audit", "assess", "evaluate"
        ));
        
        // Create/build related terms
        DOMAIN_SYNONYMS.put("create", Set.of(
            "build", "generate", "make", "construct", "produce", 
            "form", "establish", "instantiate", "initialize", "spawn"
        ));
        
        // Update/modify related terms
        DOMAIN_SYNONYMS.put("update", Set.of(
            "modify", "change", "alter", "edit", "revise", 
            "adjust", "amend", "refresh", "renew", "upgrade"
        ));
        
        // Delete/remove related terms
        DOMAIN_SYNONYMS.put("delete", Set.of(
            "remove", "destroy", "eliminate", "erase", "clear", 
            "purge", "drop", "discard", "terminate", "abolish"
        ));
        
        // Get/retrieve related terms
        DOMAIN_SYNONYMS.put("get", Set.of(
            "retrieve", "fetch", "obtain", "acquire", "access", 
            "find", "lookup", "search", "query", "read"
        ));
        
        // Set/assign related terms
        DOMAIN_SYNONYMS.put("set", Set.of(
            "assign", "configure", "define", "establish", "specify", 
            "designate", "appoint", "allocate", "apply", "put"
        ));
        
        // Start/begin related terms
        DOMAIN_SYNONYMS.put("start", Set.of(
            "begin", "initiate", "launch", "commence", "open", 
            "activate", "trigger", "boot", "kickoff", "startup"
        ));
        
        // Stop/end related terms
        DOMAIN_SYNONYMS.put("stop", Set.of(
            "end", "terminate", "halt", "cease", "finish", 
            "close", "shutdown", "abort", "cancel", "discontinue"
        ));
        
        // Cache related terms
        DOMAIN_SYNONYMS.put("cache", Set.of(
            "store", "buffer", "memory", "storage", "repository", 
            "vault", "reserve", "stash", "pool", "bank"
        ));
        
        // Queue related terms
        DOMAIN_SYNONYMS.put("queue", Set.of(
            "buffer", "line", "sequence", "list", "array", 
            "stack", "pipeline", "channel", "stream", "fifo"
        ));
        
        // Database related terms
        DOMAIN_SYNONYMS.put("database", Set.of(
            "db", "datastore", "repository", "storage", "persistence", 
            "store", "warehouse", "archive", "vault", "collection"
        ));
    }
    
    // Technical action synonyms (for method names)
    private static final Map<String, Set<String>> ACTION_SYNONYMS = new HashMap<>();
    
    static {
        ACTION_SYNONYMS.put("process", Set.of(
            "handle", "execute", "run", "perform", "operate", 
            "treat", "manage", "conduct", "carry", "accomplish"
        ));
        
        ACTION_SYNONYMS.put("handle", Set.of(
            "process", "manage", "deal", "treat", "address", 
            "tackle", "cope", "control", "direct", "administer"
        ));
        
        ACTION_SYNONYMS.put("execute", Set.of(
            "run", "perform", "carry", "implement", "accomplish", 
            "complete", "fulfill", "achieve", "realize", "effect"
        ));
    }

    /**
     * Expands a single term with semantic alternatives
     */
    public List<String> expandSemantics(String term) {
        if (!domainSynonymsEnabled || term == null || term.trim().isEmpty()) {
            return Collections.singletonList(term);
        }
        
        Set<String> expansions = new LinkedHashSet<>();
        String normalizedTerm = term.toLowerCase().trim();
        
        // Add original term
        expansions.add(term);
        
        // Get direct synonyms
        Set<String> synonyms = DOMAIN_SYNONYMS.get(normalizedTerm);
        if (synonyms != null) {
            expansions.addAll(synonyms);
        }
        
        // Check if term is a synonym of another term (reverse lookup)
        for (Map.Entry<String, Set<String>> entry : DOMAIN_SYNONYMS.entrySet()) {
            if (entry.getValue().contains(normalizedTerm)) {
                expansions.add(entry.getKey());
                expansions.addAll(entry.getValue());
            }
        }
        
        // Add action synonyms if applicable
        Set<String> actionSynonyms = ACTION_SYNONYMS.get(normalizedTerm);
        if (actionSynonyms != null) {
            expansions.addAll(actionSynonyms);
        }
        
        // Apply case variations
        Set<String> casedExpansions = new LinkedHashSet<>();
        for (String expansion : expansions) {
            casedExpansions.add(expansion);
            casedExpansions.add(capitalize(expansion));
            casedExpansions.add(expansion.toUpperCase());
        }
        
        // Limit expansions
        List<String> limitedExpansions = casedExpansions.stream()
            .limit(maxSemanticExpansions)
            .collect(Collectors.toList());
        
        log.debug("Expanded '{}' to {} semantic alternatives", term, limitedExpansions.size());
        return limitedExpansions;
    }
    
    /**
     * Expands multiple terms with semantic alternatives
     */
    public List<String> expandWithSemantics(List<String> terms) {
        Set<String> allExpansions = new LinkedHashSet<>();
        
        for (String term : terms) {
            allExpansions.addAll(expandSemantics(term));
        }
        
        return new ArrayList<>(allExpansions);
    }
    
    /**
     * Gets conceptually related terms for a given term
     */
    public List<String> getConceptuallyRelatedTerms(String term) {
        Set<String> related = new LinkedHashSet<>();
        String normalizedTerm = term.toLowerCase();
        
        // Get direct semantic relations
        related.addAll(expandSemantics(term));
        
        // Add conceptual relationships based on domain knowledge
        switch (normalizedTerm) {
            case "pipeline":
                related.addAll(Arrays.asList(
                    "stage", "step", "phase", "node", "task",
                    "executor", "runner", "scheduler", "coordinator"
                ));
                break;
                
            case "service":
                related.addAll(Arrays.asList(
                    "endpoint", "api", "interface", "contract",
                    "client", "provider", "implementation", "facade"
                ));
                break;
                
            case "data":
                related.addAll(Arrays.asList(
                    "schema", "structure", "format", "type",
                    "validation", "transformation", "serialization"
                ));
                break;
                
            case "event":
                related.addAll(Arrays.asList(
                    "listener", "publisher", "subscriber", "emitter",
                    "broadcaster", "dispatcher", "propagator"
                ));
                break;
                
            case "cache":
                related.addAll(Arrays.asList(
                    "eviction", "ttl", "expiry", "refresh",
                    "invalidation", "warming", "preload"
                ));
                break;
                
            case "queue":
                related.addAll(Arrays.asList(
                    "consumer", "producer", "broker", "topic",
                    "partition", "offset", "acknowledgment"
                ));
                break;
                
            case "transaction":
                related.addAll(Arrays.asList(
                    "commit", "rollback", "isolation", "consistency",
                    "atomicity", "durability", "saga"
                ));
                break;
        }
        
        return new ArrayList<>(related);
    }
    
    /**
     * Expands terms based on semantic context
     */
    public List<String> expandWithContext(String term, List<String> contextTerms) {
        Set<String> expansions = new LinkedHashSet<>();
        
        // Get base expansions
        expansions.addAll(expandSemantics(term));
        
        // Add context-aware expansions
        if (contextTerms.contains("async") || contextTerms.contains("asynchronous")) {
            expansions.addAll(getAsyncRelatedExpansions(term));
        }
        
        if (contextTerms.contains("batch") || contextTerms.contains("bulk")) {
            expansions.addAll(getBatchRelatedExpansions(term));
        }
        
        if (contextTerms.contains("stream") || contextTerms.contains("streaming")) {
            expansions.addAll(getStreamRelatedExpansions(term));
        }
        
        if (contextTerms.contains("distributed") || contextTerms.contains("cluster")) {
            expansions.addAll(getDistributedRelatedExpansions(term));
        }
        
        return new ArrayList<>(expansions);
    }
    
    /**
     * Gets async-related expansions
     */
    private List<String> getAsyncRelatedExpansions(String term) {
        List<String> expansions = new ArrayList<>();
        String capitalized = capitalize(term);
        
        expansions.add("Async" + capitalized);
        expansions.add(capitalized + "Async");
        expansions.add("Asynchronous" + capitalized);
        expansions.add(capitalized + "Future");
        expansions.add(capitalized + "Promise");
        expansions.add("Reactive" + capitalized);
        
        return expansions;
    }
    
    /**
     * Gets batch-related expansions
     */
    private List<String> getBatchRelatedExpansions(String term) {
        List<String> expansions = new ArrayList<>();
        String capitalized = capitalize(term);
        
        expansions.add("Batch" + capitalized);
        expansions.add(capitalized + "Batch");
        expansions.add("Bulk" + capitalized);
        expansions.add(capitalized + "Bulk");
        expansions.add(capitalized + "BatchProcessor");
        
        return expansions;
    }
    
    /**
     * Gets stream-related expansions
     */
    private List<String> getStreamRelatedExpansions(String term) {
        List<String> expansions = new ArrayList<>();
        String capitalized = capitalize(term);
        
        expansions.add("Stream" + capitalized);
        expansions.add(capitalized + "Stream");
        expansions.add("Streaming" + capitalized);
        expansions.add(capitalized + "StreamProcessor");
        expansions.add("Reactive" + capitalized + "Stream");
        
        return expansions;
    }
    
    /**
     * Gets distributed-related expansions
     */
    private List<String> getDistributedRelatedExpansions(String term) {
        List<String> expansions = new ArrayList<>();
        String capitalized = capitalize(term);
        
        expansions.add("Distributed" + capitalized);
        expansions.add(capitalized + "Cluster");
        expansions.add("Remote" + capitalized);
        expansions.add(capitalized + "Node");
        expansions.add("Clustered" + capitalized);
        
        return expansions;
    }
    
    /**
     * Checks if two terms are semantically related
     */
    public boolean areSemanticallySimilar(String term1, String term2) {
        if (term1.equalsIgnoreCase(term2)) {
            return true;
        }
        
        List<String> term1Expansions = expandSemantics(term1.toLowerCase());
        List<String> term2Expansions = expandSemantics(term2.toLowerCase());
        
        // Check if there's any overlap in expansions
        for (String expansion : term1Expansions) {
            if (term2Expansions.contains(expansion.toLowerCase())) {
                return true;
            }
        }
        
        return false;
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