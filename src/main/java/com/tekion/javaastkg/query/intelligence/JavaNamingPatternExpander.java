package com.tekion.javaastkg.query.intelligence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Expands query terms based on Java naming conventions and common patterns.
 * This helps find code elements that follow standard naming conventions.
 */
@Service
@Slf4j
public class JavaNamingPatternExpander {

    @Value("${query_optimization.pattern_expansion.java_patterns_enabled:true}")
    private boolean javaPatternsEnabled;
    
    @Value("${query_optimization.pattern_expansion.max_pattern_expansions:15}")
    private int maxPatternExpansions;

    // Common Java class suffixes
    private static final List<String> COMMON_CLASS_SUFFIXES = Arrays.asList(
        "Service", "Controller", "Manager", "Processor", "Handler",
        "Engine", "Builder", "Factory", "Repository", "DAO",
        "Validator", "Converter", "Mapper", "Adapter", "Facade",
        "Helper", "Util", "Utils", "Component", "Module",
        "Provider", "Consumer", "Producer", "Listener", "Observer",
        "Strategy", "Decorator", "Proxy", "Wrapper", "Filter",
        "Interceptor", "Resolver", "Generator", "Parser", "Serializer",
        "Deserializer", "Transformer", "Translator", "Executor", "Worker",
        "Task", "Job", "Command", "Query", "Request",
        "Response", "Result", "Output", "Input", "Context",
        "Config", "Configuration", "Settings", "Options", "Properties"
    );
    
    // Common Java class prefixes
    private static final List<String> COMMON_CLASS_PREFIXES = Arrays.asList(
        "Data", "Processing", "Pipeline", "Workflow", "Execution",
        "Task", "Job", "Event", "Message", "Request",
        "Response", "Api", "Web", "Rest", "Http",
        "Database", "Cache", "Queue", "Stream", "Batch",
        "Async", "Sync", "Concurrent", "Parallel", "Distributed",
        "Remote", "Local", "Internal", "External", "Custom",
        "Default", "Base", "Abstract", "Simple", "Complex",
        "Generic", "Specific", "Common", "Shared", "Global"
    );
    
    // Common method prefixes
    private static final List<String> COMMON_METHOD_PREFIXES = Arrays.asList(
        "get", "set", "is", "has", "can",
        "should", "create", "build", "make", "generate",
        "process", "handle", "execute", "run", "perform",
        "validate", "verify", "check", "ensure", "assert",
        "find", "search", "lookup", "fetch", "retrieve",
        "save", "store", "persist", "update", "delete",
        "remove", "add", "insert", "append", "prepend",
        "convert", "transform", "map", "translate", "parse",
        "serialize", "deserialize", "encode", "decode", "format",
        "init", "initialize", "setup", "configure", "prepare",
        "start", "stop", "pause", "resume", "restart",
        "open", "close", "connect", "disconnect", "bind",
        "register", "unregister", "subscribe", "unsubscribe", "publish"
    );
    
    // Common variable/field suffixes
    private static final List<String> COMMON_FIELD_SUFFIXES = Arrays.asList(
        "Id", "Name", "Type", "Status", "State",
        "Count", "Size", "Length", "Index", "Offset",
        "Time", "Date", "Timestamp", "Duration", "Timeout",
        "Path", "Url", "Uri", "Location", "Address",
        "Key", "Value", "Entry", "Item", "Element",
        "List", "Set", "Map", "Collection", "Array",
        "Queue", "Stack", "Tree", "Graph", "Node",
        "Config", "Settings", "Options", "Parameters", "Properties",
        "Result", "Response", "Request", "Message", "Event"
    );

    /**
     * Expands a single term with Java naming patterns
     */
    public List<String> expandWithPatterns(String term) {
        if (!javaPatternsEnabled || term == null || term.trim().isEmpty()) {
            return Collections.singletonList(term);
        }
        
        Set<String> expansions = new LinkedHashSet<>();
        String normalizedTerm = term.trim();
        
        // Add original term
        expansions.add(normalizedTerm);
        
        // Apply different expansion strategies
        expansions.addAll(expandWithClassPatterns(normalizedTerm));
        expansions.addAll(expandWithMethodPatterns(normalizedTerm));
        expansions.addAll(expandWithFieldPatterns(normalizedTerm));
        expansions.addAll(expandWithCamelCaseVariations(normalizedTerm));
        
        // Limit expansions
        List<String> limitedExpansions = expansions.stream()
            .limit(maxPatternExpansions)
            .collect(Collectors.toList());
        
        log.debug("Expanded '{}' to {} patterns", term, limitedExpansions.size());
        return limitedExpansions;
    }
    
    /**
     * Expands multiple terms with patterns
     */
    public List<String> expandTerms(List<String> terms) {
        Set<String> allExpansions = new LinkedHashSet<>();
        
        for (String term : terms) {
            allExpansions.addAll(expandWithPatterns(term));
        }
        
        return new ArrayList<>(allExpansions);
    }
    
    /**
     * Expands with class naming patterns
     */
    private Set<String> expandWithClassPatterns(String term) {
        Set<String> expansions = new LinkedHashSet<>();
        
        // Try suffix combinations
        for (String suffix : COMMON_CLASS_SUFFIXES) {
            expansions.add(term + suffix);
            expansions.add(capitalize(term) + suffix);
            
            // Handle compound words
            if (term.contains("_")) {
                expansions.add(toCamelCase(term) + suffix);
            }
        }
        
        // Try prefix combinations
        for (String prefix : COMMON_CLASS_PREFIXES) {
            expansions.add(prefix + capitalize(term));
            expansions.add(prefix + term);
            
            // Handle compound words
            if (term.contains("_")) {
                expansions.add(prefix + toCamelCase(term));
            }
        }
        
        return expansions;
    }
    
    /**
     * Expands with method naming patterns
     */
    private Set<String> expandWithMethodPatterns(String term) {
        Set<String> expansions = new LinkedHashSet<>();
        
        // Try method prefix combinations
        for (String prefix : COMMON_METHOD_PREFIXES) {
            expansions.add(prefix + capitalize(term));
            expansions.add(prefix + term);
            
            // Add variations for common getter/setter patterns
            if (prefix.equals("get") || prefix.equals("set")) {
                expansions.add(prefix + capitalize(term) + "()");
            }
            
            // Boolean patterns
            if (prefix.equals("is") || prefix.equals("has")) {
                expansions.add(prefix + capitalize(term));
                expansions.add(prefix + capitalize(term) + "()");
            }
        }
        
        // Add method-like patterns
        expansions.add(uncapitalize(term) + "()");
        expansions.add(term + "()");
        
        return expansions;
    }
    
    /**
     * Expands with field/variable naming patterns
     */
    private Set<String> expandWithFieldPatterns(String term) {
        Set<String> expansions = new LinkedHashSet<>();
        
        // Try field suffix combinations
        for (String suffix : COMMON_FIELD_SUFFIXES) {
            expansions.add(uncapitalize(term) + suffix);
            expansions.add(term + suffix);
            expansions.add(term.toLowerCase() + suffix);
        }
        
        // Common field naming patterns
        expansions.add(uncapitalize(term));
        expansions.add(term.toLowerCase());
        expansions.add(term.toUpperCase()); // For constants
        expansions.add(toSnakeCase(term).toUpperCase()); // CONSTANT_STYLE
        
        return expansions;
    }
    
    /**
     * Generates camel case variations
     */
    private Set<String> expandWithCamelCaseVariations(String term) {
        Set<String> variations = new LinkedHashSet<>();
        
        // Handle different case styles
        if (term.contains("_")) {
            // snake_case to CamelCase
            variations.add(toCamelCase(term));
            variations.add(uncapitalize(toCamelCase(term)));
        } else if (term.contains("-")) {
            // kebab-case to CamelCase
            variations.add(toCamelCase(term.replace("-", "_")));
            variations.add(uncapitalize(toCamelCase(term.replace("-", "_"))));
        } else if (hasUpperCase(term) && !term.equals(term.toUpperCase())) {
            // CamelCase to snake_case
            variations.add(toSnakeCase(term));
            variations.add(toSnakeCase(term).toUpperCase());
        }
        
        // Add basic variations
        variations.add(capitalize(term));
        variations.add(uncapitalize(term));
        
        return variations;
    }
    
    /**
     * Capitalizes first letter
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
    
    /**
     * Uncapitalizes first letter
     */
    private String uncapitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }
    
    /**
     * Converts snake_case to CamelCase
     */
    private String toCamelCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        String[] parts = str.split("_");
        StringBuilder camelCase = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                if (i == 0) {
                    camelCase.append(capitalize(parts[i]));
                } else {
                    camelCase.append(capitalize(parts[i]));
                }
            }
        }
        
        return camelCase.toString();
    }
    
    /**
     * Converts CamelCase to snake_case
     */
    private String toSnakeCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        StringBuilder snakeCase = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                snakeCase.append("_");
            }
            snakeCase.append(Character.toLowerCase(ch));
        }
        
        return snakeCase.toString();
    }
    
    /**
     * Checks if string contains uppercase letters
     */
    private boolean hasUpperCase(String str) {
        return str.chars().anyMatch(Character::isUpperCase);
    }
    
    /**
     * Gets common patterns for a specific domain term
     */
    public List<String> getDomainSpecificPatterns(String term) {
        List<String> patterns = new ArrayList<>();
        
        // Special handling for common domain terms
        switch (term.toLowerCase()) {
            case "pipeline":
                patterns.addAll(Arrays.asList(
                    "PipelineProcessor", "PipelineManager", "PipelineExecutor",
                    "PipelineOrchestrator", "PipelineEngine", "PipelineService",
                    "DataPipeline", "ProcessingPipeline", "WorkflowPipeline"
                ));
                break;
                
            case "processing":
                patterns.addAll(Arrays.asList(
                    "ProcessingEngine", "ProcessingService", "DataProcessor",
                    "EventProcessor", "MessageProcessor", "BatchProcessor",
                    "StreamProcessor", "RequestProcessor", "TaskProcessor"
                ));
                break;
                
            case "data":
                patterns.addAll(Arrays.asList(
                    "DataService", "DataManager", "DataRepository",
                    "DataProvider", "DataHandler", "DataProcessor",
                    "DataTransformer", "DataValidator", "DataMapper"
                ));
                break;
                
            case "status":
                patterns.addAll(Arrays.asList(
                    "StatusManager", "StatusService", "StatusHandler",
                    "ExecutionStatus", "ProcessingStatus", "WorkflowStatus",
                    "TaskStatus", "JobStatus", "SystemStatus"
                ));
                break;
                
            case "config":
            case "configuration":
                patterns.addAll(Arrays.asList(
                    "ConfigurationService", "ConfigManager", "ConfigProvider",
                    "AppConfig", "SystemConfig", "ServiceConfig",
                    "ConfigurationProperties", "ConfigurationBuilder"
                ));
                break;
        }
        
        return patterns;
    }
}