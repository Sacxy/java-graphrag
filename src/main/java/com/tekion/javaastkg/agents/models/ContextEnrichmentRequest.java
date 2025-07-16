package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 📚 Context Enrichment Request - Input for code context enrichment
 * 
 * Represents different types of context enrichment requests:
 * - Adding semantic meaning and business context
 * - Enriching with domain knowledge and relationships
 * - Providing usage patterns and examples
 * - Enhancing understanding with documentation and metadata
 * 
 * Domain Focus: ENRICHING code entities with contextual understanding
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextEnrichmentRequest {
    
    /**
     * 🎯 Target entities to enrich with context
     */
    private List<String> targetEntities;
    
    /**
     * 🔍 Type of context enrichment to perform
     */
    private EnrichmentType enrichmentType;
    
    /**
     * 🏢 Domain or business area focus
     */
    private String domainFocus;
    
    /**
     * 📊 Enrichment scope and depth
     */
    @Builder.Default
    private EnrichmentScope scope = EnrichmentScope.MODERATE;
    
    /**
     * 🎭 Specific enrichment areas to focus on
     */
    private List<EnrichmentArea> focusAreas;
    
    /**
     * 📚 Include related entities in enrichment
     */
    @Builder.Default
    private boolean includeRelatedEntities = true;
    
    /**
     * 🔗 Include usage patterns and examples
     */
    @Builder.Default
    private boolean includeUsagePatterns = true;
    
    /**
     * 💡 Include recommendations and best practices
     */
    @Builder.Default
    private boolean includeRecommendations = true;
    
    /**
     * ⚙️ Additional enrichment parameters
     */
    private Map<String, Object> enrichmentParameters;
    
    public enum EnrichmentType {
        /**
         * 📝 Add semantic meaning and purpose
         */
        SEMANTIC_ENRICHMENT("Add semantic meaning and business purpose"),
        
        /**
         * 🏢 Add business and domain context
         */
        BUSINESS_CONTEXT("Add business domain knowledge and context"),
        
        /**
         * 🔗 Add relationship and dependency context
         */
        RELATIONSHIP_CONTEXT("Add relationship and dependency information"),
        
        /**
         * 📖 Add usage patterns and examples
         */
        USAGE_ENRICHMENT("Add usage patterns and practical examples"),
        
        /**
         * 🎨 Add architectural and design context
         */
        ARCHITECTURAL_CONTEXT("Add architectural patterns and design context"),
        
        /**
         * 🔄 Complete context enrichment
         */
        COMPREHENSIVE("Comprehensive context enrichment across all areas");
        
        private final String description;
        
        EnrichmentType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum EnrichmentScope {
        /**
         * 🎯 Focus only on the target entities
         */
        FOCUSED("Focus only on specified entities"),
        
        /**
         * ⚖️ Moderate scope including immediate relationships
         */
        MODERATE("Include immediate relationships and context"),
        
        /**
         * 🌐 Comprehensive scope including extended context
         */
        COMPREHENSIVE("Include extended relationships and comprehensive context");
        
        private final String description;
        
        EnrichmentScope(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum EnrichmentArea {
        BUSINESS_PURPOSE,
        TECHNICAL_IMPLEMENTATION,
        USAGE_PATTERNS,
        PERFORMANCE_CHARACTERISTICS,
        SECURITY_CONSIDERATIONS,
        ERROR_HANDLING,
        DEPENDENCIES,
        EVOLUTION_HISTORY,
        TESTING_APPROACH,
        DOCUMENTATION_STATUS
    }
    
    /**
     * 📝 Create request for semantic enrichment
     */
    public static ContextEnrichmentRequest semanticEnrichment(List<String> entities) {
        return ContextEnrichmentRequest.builder()
            .targetEntities(entities)
            .enrichmentType(EnrichmentType.SEMANTIC_ENRICHMENT)
            .scope(EnrichmentScope.MODERATE)
            .focusAreas(List.of(
                EnrichmentArea.BUSINESS_PURPOSE,
                EnrichmentArea.TECHNICAL_IMPLEMENTATION,
                EnrichmentArea.USAGE_PATTERNS
            ))
            .build();
    }
    
    /**
     * 🏢 Create request for business context enrichment
     */
    public static ContextEnrichmentRequest businessContext(List<String> entities, String domain) {
        return ContextEnrichmentRequest.builder()
            .targetEntities(entities)
            .enrichmentType(EnrichmentType.BUSINESS_CONTEXT)
            .domainFocus(domain)
            .scope(EnrichmentScope.COMPREHENSIVE)
            .focusAreas(List.of(
                EnrichmentArea.BUSINESS_PURPOSE,
                EnrichmentArea.USAGE_PATTERNS,
                EnrichmentArea.DEPENDENCIES
            ))
            .build();
    }
    
    /**
     * 🔗 Create request for relationship context enrichment
     */
    public static ContextEnrichmentRequest relationshipContext(List<String> entities) {
        return ContextEnrichmentRequest.builder()
            .targetEntities(entities)
            .enrichmentType(EnrichmentType.RELATIONSHIP_CONTEXT)
            .scope(EnrichmentScope.MODERATE)
            .focusAreas(List.of(
                EnrichmentArea.DEPENDENCIES,
                EnrichmentArea.TECHNICAL_IMPLEMENTATION,
                EnrichmentArea.USAGE_PATTERNS
            ))
            .includeRelatedEntities(true)
            .build();
    }
    
    /**
     * 📖 Create request for usage pattern enrichment
     */
    public static ContextEnrichmentRequest usageEnrichment(List<String> entities) {
        return ContextEnrichmentRequest.builder()
            .targetEntities(entities)
            .enrichmentType(EnrichmentType.USAGE_ENRICHMENT)
            .scope(EnrichmentScope.MODERATE)
            .focusAreas(List.of(
                EnrichmentArea.USAGE_PATTERNS,
                EnrichmentArea.PERFORMANCE_CHARACTERISTICS,
                EnrichmentArea.TESTING_APPROACH
            ))
            .includeUsagePatterns(true)
            .includeRecommendations(true)
            .build();
    }
    
    /**
     * 🔄 Create request for comprehensive enrichment
     */
    public static ContextEnrichmentRequest comprehensive(List<String> entities, String domain) {
        return ContextEnrichmentRequest.builder()
            .targetEntities(entities)
            .enrichmentType(EnrichmentType.COMPREHENSIVE)
            .domainFocus(domain)
            .scope(EnrichmentScope.COMPREHENSIVE)
            .focusAreas(List.of(EnrichmentArea.values()))
            .includeRelatedEntities(true)
            .includeUsagePatterns(true)
            .includeRecommendations(true)
            .build();
    }
}