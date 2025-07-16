package com.tekion.javaastkg.adk.tools;

import java.util.*;

/**
 * Constants and enums for Code Context Enricher
 * 
 * Centralizes all hardcoded values to improve maintainability and extensibility.
 */
public class CodeContextConstants {

    /**
     * Context gap types - centralized enum to avoid hardcoded strings
     */
    public enum GapType {
        MISSING_DOCUMENTATION("Documentation gap", "Entity lacks adequate documentation"),
        INSUFFICIENT_TESTING("Testing gap", "Entity has inadequate test coverage"), 
        MISSING_USAGE_EXAMPLES("Usage gap", "Entity lacks clear usage examples"),
        MISSING_BUSINESS_CONTEXT("Business context gap", "Entity lacks business context and domain knowledge");
        
        private final String displayName;
        private final String description;
        
        GapType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    /**
     * Recommendation types for different improvement categories
     */
    public enum RecommendationType {
        DOCUMENTATION_IMPROVEMENT("Documentation improvement"),
        TEST_IMPROVEMENT("Test improvement"),
        USAGE_IMPROVEMENT("Usage improvement"), 
        BUSINESS_CONTEXT_IMPROVEMENT("Business context improvement"),
        ENTITY_DOCUMENTATION("Entity-specific documentation"),
        ENTITY_TESTING("Entity-specific testing"),
        SYSTEMATIC_IMPROVEMENT("Systematic improvement"),
        MAINTENANCE("Maintenance");
        
        private final String displayName;
        
        RecommendationType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    /**
     * Priority levels for consistent priority handling
     */
    public enum Priority {
        HIGH("HIGH", 3),
        MEDIUM("MEDIUM", 2), 
        LOW("LOW", 1);
        
        private final String value;
        private final int numericValue;
        
        Priority(String value, int numericValue) {
            this.value = value;
            this.numericValue = numericValue;
        }
        
        public String getValue() { return value; }
        public int getNumericValue() { return numericValue; }
    }
    
    /**
     * Effort estimates for recommendation planning
     */
    public enum EffortEstimate {
        LOW("LOW", 1),
        MEDIUM("MEDIUM", 2),
        HIGH("HIGH", 3);
        
        private final String value;
        private final int numericValue;
        
        EffortEstimate(String value, int numericValue) {
            this.value = value;
            this.numericValue = numericValue;
        }
        
        public String getValue() { return value; }
        public int getNumericValue() { return numericValue; }
    }
    
    /**
     * Configurable domain concept mappings
     * Can be loaded from configuration files for different domains
     */
    public static class DomainConceptExtractor {
        
        private final Map<String, Set<String>> domainMappings;
        
        public DomainConceptExtractor() {
            this.domainMappings = createDefaultMappings();
        }
        
        public DomainConceptExtractor(Map<String, Set<String>> customMappings) {
            this.domainMappings = customMappings;
        }
        
        /**
         * Extract domain concepts using configurable mappings
         */
        public Set<String> extractConcepts(List<String> descriptions, String entityId) {
            Set<String> concepts = new HashSet<>();
            
            // Add entity class name as a concept
            concepts.add(extractClassName(entityId));
            
            // Extract concepts using configurable mappings
            for (String description : descriptions) {
                String lowerDesc = description.toLowerCase();
                
                for (Map.Entry<String, Set<String>> mapping : domainMappings.entrySet()) {
                    String keyword = mapping.getKey();
                    Set<String> conceptSet = mapping.getValue();
                    
                    if (lowerDesc.contains(keyword.toLowerCase())) {
                        concepts.addAll(conceptSet);
                    }
                }
            }
            
            return concepts;
        }
        
        /**
         * Default domain mappings - can be overridden per domain
         */
        private static Map<String, Set<String>> createDefaultMappings() {
            Map<String, Set<String>> mappings = new HashMap<>();
            
            // Financial domain
            mappings.put("payment", Set.of("Payment", "Transaction"));
            mappings.put("transaction", Set.of("Transaction", "Payment"));
            mappings.put("account", Set.of("Account", "User"));
            mappings.put("balance", Set.of("Balance", "Account"));
            mappings.put("invoice", Set.of("Invoice", "Billing"));
            
            // E-commerce domain
            mappings.put("order", Set.of("Order", "Transaction"));
            mappings.put("product", Set.of("Product", "Inventory"));
            mappings.put("customer", Set.of("Customer", "User"));
            mappings.put("cart", Set.of("Cart", "Order"));
            mappings.put("inventory", Set.of("Inventory", "Product"));
            
            // User management domain
            mappings.put("user", Set.of("User", "Authentication"));
            mappings.put("authentication", Set.of("Authentication", "Security"));
            mappings.put("authorization", Set.of("Authorization", "Security"));
            mappings.put("profile", Set.of("Profile", "User"));
            mappings.put("session", Set.of("Session", "Authentication"));
            
            // General business concepts
            mappings.put("workflow", Set.of("Workflow", "Process"));
            mappings.put("process", Set.of("Process", "Business"));
            mappings.put("validation", Set.of("Validation", "Quality"));
            mappings.put("notification", Set.of("Notification", "Communication"));
            mappings.put("audit", Set.of("Audit", "Compliance"));
            
            return mappings;
        }
        
        private static String extractClassName(String entityId) {
            if (entityId.contains(".")) {
                String[] parts = entityId.split("\\.");
                for (String part : parts) {
                    if (Character.isUpperCase(part.charAt(0))) {
                        return part;
                    }
                }
            }
            return entityId;
        }
        
        /**
         * Load domain mappings from configuration
         * Future enhancement: load from JSON/YAML config files
         */
        public static DomainConceptExtractor fromConfig(String domainType) {
            // Future: load from configuration files based on domain type
            // For now, return default mappings
            return new DomainConceptExtractor();
        }
    }
    
    /**
     * Quality grade thresholds
     */
    public static final double GRADE_A_THRESHOLD = 0.9;
    public static final double GRADE_B_THRESHOLD = 0.8;
    public static final double GRADE_C_THRESHOLD = 0.7;
    public static final double GRADE_D_THRESHOLD = 0.6;
    
    /**
     * Default quality scoring weights
     */
    public static final double DEFAULT_DOCUMENTATION_WEIGHT = 0.3;
    public static final double DEFAULT_USAGE_WEIGHT = 0.25;
    public static final double DEFAULT_TEST_WEIGHT = 0.25;
    public static final double DEFAULT_BUSINESS_WEIGHT = 0.2;
    
    /**
     * Severity determination thresholds
     */
    public static final double HIGH_SEVERITY_THRESHOLD = 0.7;
    public static final double MEDIUM_SEVERITY_THRESHOLD = 0.4;
}