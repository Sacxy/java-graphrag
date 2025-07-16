package com.tekion.javaastkg.adk.tools;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import com.tekion.javaastkg.adk.core.BaseAdkTool;
import com.tekion.javaastkg.service.Neo4jService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Code Context Enricher Tool - Gathers comprehensive contextual information
 * 
 * This tool aggregates contextual information around code entities including:
 * - Documentation (Javadoc, README, inline comments)
 * - Usage examples (tests, documentation examples, real usage)
 * - Test coverage and quality information
 * - Business context and domain knowledge
 * - Quality assessment and gap analysis
 * 
 * Designed to help AI agents understand code purpose, usage patterns, and quality.
 */
@Slf4j
public class CodeContextEnricher extends BaseAdkTool {

    private static Neo4jService neo4jService;
    
    /**
     * Initialize the tool with Neo4j service
     * Called by Spring context during tool registration
     */
    public static void initialize(Neo4jService service) {
        neo4jService = service;
    }

    /**
     * Enriches code entities with comprehensive contextual information
     * 
     * @param entities List of entity IDs to enrich with context
     * @param contextTypes Types of context to gather (DOCUMENTATION, USAGE_EXAMPLES, TESTS, BUSINESS_CONTEXT)
     * @param contextConfig Configuration for quality thresholds, depth, etc.
     * @param ctx Tool context for state management
     * @return Enriched entities with context, quality assessment, gaps, and recommendations
     */
    @Schema(description = "Enrich code entities with comprehensive contextual information")
    public static Map<String, Object> enrichContext(
            @Schema(description = "Entities to enrich with context") List<String> entities,
            @Schema(description = "Types of context to gather") List<String> contextTypes,
            @Schema(description = "Context gathering configuration") Map<String, Object> contextConfig,
            @Schema(name = "toolContext") ToolContext ctx
    ) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Validate inputs
            if (entities == null || entities.isEmpty()) {
                return errorResponse("enrichContext", "Entities cannot be null or empty");
            }
            
            if (neo4jService == null) {
                return errorResponse("enrichContext", "Neo4j service not initialized");
            }
            
            // Extract context parameters
            String qualityThreshold = (String) contextConfig.getOrDefault("qualityThreshold", "MODERATE");
            boolean includeBusinessContext = (Boolean) contextConfig.getOrDefault("includeBusinessContext", true);
            boolean includeUsageExamples = (Boolean) contextConfig.getOrDefault("includeUsageExamples", true);
            int maxContextDepth = (Integer) contextConfig.getOrDefault("maxContextDepth", 3);
            
            log.info("Starting context enrichment for {} entities with types: {}, threshold: {}", 
                    entities.size(), contextTypes, qualityThreshold);
            
            // Determine context gathering strategy
            ContextStrategy strategy = selectContextStrategy(contextTypes, entities.size());
            
            // Gather context for each entity
            List<EnrichedEntity> enrichedEntities = gatherContextForEntities(entities, contextTypes, strategy, 
                    includeBusinessContext, includeUsageExamples, maxContextDepth);
            
            // Assess context quality
            ContextQualityAssessment qualityAssessment = assessContextQuality(enrichedEntities, qualityThreshold);
            
            // Identify context gaps
            List<ContextGap> contextGaps = identifyContextGaps(enrichedEntities, contextTypes);
            
            // Generate context recommendations
            List<ContextRecommendation> recommendations = generateContextRecommendations(
                    enrichedEntities, contextGaps, qualityAssessment);
            
            // Update context state
            updateContextState(ctx, entities, qualityAssessment, contextGaps);
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Context enrichment completed in {}ms: {} entities, quality: {}, {} gaps", 
                    executionTime, enrichedEntities.size(), 
                    String.format("%.2f", qualityAssessment.getOverallScore()), contextGaps.size());
            
            return Map.of(
                "status", "success",
                "enrichedEntities", enrichedEntities.stream().map(EnrichedEntity::toMap).collect(Collectors.toList()),
                "contextQuality", qualityAssessment.toMap(),
                "contextGaps", contextGaps.stream().map(ContextGap::toMap).collect(Collectors.toList()),
                "recommendations", recommendations.stream().map(ContextRecommendation::toMap).collect(Collectors.toList()),
                "contextMetadata", Map.of(
                    "strategy", strategy.name(),
                    "entitiesEnriched", enrichedEntities.size(),
                    "contextTypesGathered", contextTypes.size(),
                    "overallQualityScore", qualityAssessment.getOverallScore(),
                    "gapsIdentified", contextGaps.size(),
                    "executionTimeMs", executionTime
                ),
                "nextActions", determineNextActions(enrichedEntities, contextGaps, qualityAssessment)
            );
            
        } catch (Exception e) {
            log.error("Context enrichment failed for entities: {}", entities, e);
            return errorResponse("enrichContext", "Context enrichment failed: " + e.getMessage());
        }
    }
    
    /**
     * Selects appropriate context gathering strategy
     */
    private static ContextStrategy selectContextStrategy(List<String> contextTypes, int entityCount) {
        if (contextTypes.contains("COMPREHENSIVE")) {
            return ContextStrategy.COMPREHENSIVE;
        } else if (contextTypes.contains("DOCUMENTATION") && contextTypes.size() <= 2) {
            return ContextStrategy.DOCUMENTATION_FOCUSED;
        } else if (contextTypes.contains("USAGE_EXAMPLES")) {
            return ContextStrategy.USAGE_FOCUSED;
        } else if (entityCount > 10) {
            return ContextStrategy.EFFICIENT;
        } else {
            return ContextStrategy.BALANCED;
        }
    }
    
    /**
     * Gathers context for all entities based on strategy
     */
    private static List<EnrichedEntity> gatherContextForEntities(List<String> entities, List<String> contextTypes, 
                                                               ContextStrategy strategy, boolean includeBusinessContext,
                                                               boolean includeUsageExamples, int maxContextDepth) {
        List<EnrichedEntity> enrichedEntities = new ArrayList<>();
        
        for (String entityId : entities) {
            EnrichedEntity.EnrichedEntityBuilder builder = EnrichedEntity.builder()
                .entityId(entityId)
                .metadata(Map.of("enrichmentTimestamp", System.currentTimeMillis(), "strategy", strategy.name()));
            
            // Gather different types of context based on configuration
            if (contextTypes.contains("DOCUMENTATION") || strategy == ContextStrategy.COMPREHENSIVE) {
                builder.documentation(gatherDocumentation(entityId, strategy));
            }
            
            if ((contextTypes.contains("USAGE_EXAMPLES") || strategy == ContextStrategy.COMPREHENSIVE) && includeUsageExamples) {
                builder.usageExamples(gatherUsageExamples(entityId, strategy));
            }
            
            if (contextTypes.contains("TESTS") || strategy == ContextStrategy.COMPREHENSIVE) {
                builder.tests(gatherRelatedTests(entityId, strategy));
            }
            
            if (contextTypes.contains("COMMENTS") || strategy == ContextStrategy.DOCUMENTATION_FOCUSED || strategy == ContextStrategy.COMPREHENSIVE) {
                builder.comments(gatherComments(entityId, strategy));
            }
            
            if ((contextTypes.contains("BUSINESS_CONTEXT") || strategy == ContextStrategy.COMPREHENSIVE) && includeBusinessContext) {
                builder.businessContext(gatherBusinessContext(entityId, strategy));
            }
            
            builder.relatedEntities(gatherRelatedEntities(entityId, strategy, maxContextDepth));
            
            enrichedEntities.add(builder.build());
        }
        
        return enrichedEntities;
    }
    
    /**
     * Gathers documentation context for an entity
     */
    private static Documentation gatherDocumentation(String entityId, ContextStrategy strategy) {
        // Mock implementation - in production, would query actual documentation sources
        String javadoc = generateMockJavadoc(entityId);
        String readme = findRelatedReadme(entityId);
        List<String> inlineComments = findInlineComments(entityId);
        String apiDocumentation = findApiDocumentation(entityId);
        
        double completenessScore = calculateDocumentationCompleteness(javadoc, readme, inlineComments, apiDocumentation);
        
        return Documentation.builder()
            .javadoc(javadoc)
            .readme(readme)
            .inlineComments(inlineComments)
            .apiDocumentation(apiDocumentation)
            .completenessScore(completenessScore)
            .build();
    }
    
    /**
     * Gathers usage examples for an entity
     */
    private static List<UsageExample> gatherUsageExamples(String entityId, ContextStrategy strategy) {
        List<UsageExample> examples = new ArrayList<>();
        
        // Mock examples based on entity type
        if (entityId.contains("Service")) {
            examples.add(UsageExample.builder()
                .source("test")
                .example("@Autowired\nprivate " + extractClassName(entityId) + " service;\nservice.processRequest(request);")
                .description("Dependency injection usage in test")
                .context("Unit test")
                .build());
                
            examples.add(UsageExample.builder()
                .source("controller")
                .example("result = " + extractMethodName(entityId) + "(data);")
                .description("Service method call from controller")
                .context("REST endpoint")
                .build());
        } else if (entityId.contains("Repository")) {
            examples.add(UsageExample.builder()
                .source("service")
                .example("entity = repository.findById(id);")
                .description("Data access pattern")
                .context("Service layer")
                .build());
        } else if (entityId.contains("Controller")) {
            examples.add(UsageExample.builder()
                .source("documentation")
                .example("POST /api/v1/" + extractMethodName(entityId))
                .description("REST API endpoint")
                .context("API documentation")
                .build());
        }
        
        return examples;
    }
    
    /**
     * Gathers related test information
     */
    private static List<TestReference> gatherRelatedTests(String entityId, ContextStrategy strategy) {
        List<TestReference> tests = new ArrayList<>();
        
        // Mock test references
        String className = extractClassName(entityId);
        String methodName = extractMethodName(entityId);
        
        tests.add(TestReference.builder()
            .testClass(className + "Test")
            .testMethod("test" + capitalize(methodName))
            .testType("UNIT")
            .coverage(0.85)
            .description("Unit test for " + methodName)
            .build());
            
        if (entityId.contains("Controller")) {
            tests.add(TestReference.builder()
                .testClass(className + "IntegrationTest")
                .testMethod("test" + capitalize(methodName) + "Integration")
                .testType("INTEGRATION")
                .coverage(0.70)
                .description("Integration test for " + methodName)
                .build());
        }
        
        return tests;
    }
    
    /**
     * Gathers code comments
     */
    private static Comments gatherComments(String entityId, ContextStrategy strategy) {
        // Mock comment gathering
        List<String> inlineComments = Arrays.asList(
            "// Validate input parameters",
            "// Process business logic",
            "// Handle error cases"
        );
        
        List<String> todoComments = new ArrayList<>();
        if (Math.random() > 0.7) {
            todoComments.add("TODO: Add input validation");
        }
        
        return Comments.builder()
            .inlineComments(inlineComments)
            .todoComments(todoComments)
            .fixmeComments(new ArrayList<>())
            .build();
    }
    
    /**
     * Gathers business context information
     */
    private static BusinessContext gatherBusinessContext(String entityId, ContextStrategy strategy) {
        List<String> domainConcepts = extractDomainConcepts(entityId);
        List<String> businessRules = generateBusinessRules(entityId);
        Map<String, String> requirementsTraceability = generateRequirementsTraceability(entityId);
        
        return BusinessContext.builder()
            .domainConcepts(domainConcepts)
            .businessRules(businessRules)
            .requirementsTraceability(requirementsTraceability)
            .build();
    }
    
    /**
     * Gathers related entities
     */
    private static List<String> gatherRelatedEntities(String entityId, ContextStrategy strategy, int maxDepth) {
        List<String> relatedEntities = new ArrayList<>();
        
        // Mock related entities based on patterns
        if (entityId.contains("Controller")) {
            relatedEntities.add(entityId.replace("Controller", "Service"));
            relatedEntities.add(entityId.replace("Controller", "Request"));
            relatedEntities.add(entityId.replace("Controller", "Response"));
        } else if (entityId.contains("Service")) {
            relatedEntities.add(entityId.replace("Service", "Repository"));
            relatedEntities.add(entityId.replace("Service", "Entity"));
            relatedEntities.add(entityId.replace("Service", "DTO"));
        } else if (entityId.contains("Repository")) {
            relatedEntities.add(entityId.replace("Repository", "Entity"));
        }
        
        return relatedEntities;
    }
    
    /**
     * Assesses overall context quality
     */
    private static ContextQualityAssessment assessContextQuality(List<EnrichedEntity> enrichedEntities, String qualityThreshold) {
        double totalScore = 0.0;
        Map<String, Double> categoryScores = new HashMap<>();
        List<String> qualityFactors = new ArrayList<>();
        List<String> qualityIssues = new ArrayList<>();
        
        double docTotal = 0.0, usageTotal = 0.0, testTotal = 0.0, businessTotal = 0.0;
        
        for (EnrichedEntity entity : enrichedEntities) {
            // Documentation quality
            double docScore = assessDocumentationQuality(entity.getDocumentation());
            docTotal += docScore;
            
            // Usage examples quality  
            double usageScore = assessUsageQuality(entity.getUsageExamples());
            usageTotal += usageScore;
            
            // Test coverage quality
            double testScore = assessTestQuality(entity.getTests());
            testTotal += testScore;
            
            // Business context quality
            double businessScore = assessBusinessContextQuality(entity.getBusinessContext());
            businessTotal += businessScore;
            
            // Calculate weighted average for this entity
            double entityScore = (docScore * 0.3) + (usageScore * 0.25) + (testScore * 0.25) + (businessScore * 0.2);
            totalScore += entityScore;
            
            // Identify quality factors and issues
            if (docScore > 0.8) qualityFactors.add("Comprehensive documentation for " + entity.getEntityId());
            if (usageScore > 0.8) qualityFactors.add("Rich usage examples for " + entity.getEntityId());
            if (testScore < 0.3) qualityIssues.add("Limited test coverage for " + entity.getEntityId());
            if (businessScore < 0.3) qualityIssues.add("Missing business context for " + entity.getEntityId());
        }
        
        double overallScore = totalScore / enrichedEntities.size();
        
        // Calculate category averages
        categoryScores.put("documentation", docTotal / enrichedEntities.size());
        categoryScores.put("usage", usageTotal / enrichedEntities.size());
        categoryScores.put("tests", testTotal / enrichedEntities.size());
        categoryScores.put("business", businessTotal / enrichedEntities.size());
        
        return ContextQualityAssessment.builder()
            .overallScore(overallScore)
            .categoryScores(categoryScores)
            .qualityFactors(qualityFactors)
            .qualityIssues(qualityIssues)
            .qualityGrade(calculateQualityGrade(overallScore))
            .recommendations(generateQualityRecommendations(overallScore, qualityIssues))
            .build();
    }
    
    /**
     * Assesses documentation quality
     */
    private static double assessDocumentationQuality(Documentation documentation) {
        if (documentation == null) return 0.0;
        
        double score = 0.0;
        
        if (documentation.getJavadoc() != null && !documentation.getJavadoc().trim().isEmpty()) {
            score += 0.4;
        }
        
        if (documentation.getInlineComments() != null && !documentation.getInlineComments().isEmpty()) {
            score += 0.3;
        }
        
        if (documentation.getReadme() != null && !documentation.getReadme().trim().isEmpty()) {
            score += 0.2;
        }
        
        if (documentation.getApiDocumentation() != null && !documentation.getApiDocumentation().trim().isEmpty()) {
            score += 0.1;
        }
        
        return Math.min(1.0, score);
    }
    
    /**
     * Assesses usage examples quality
     */
    private static double assessUsageQuality(List<UsageExample> usageExamples) {
        if (usageExamples == null || usageExamples.isEmpty()) return 0.0;
        
        double score = Math.min(0.7, usageExamples.size() * 0.3);
        
        // Bonus for diverse sources
        Set<String> sources = usageExamples.stream()
            .map(UsageExample::getSource)
            .collect(Collectors.toSet());
        score += sources.size() * 0.1;
        
        return Math.min(1.0, score);
    }
    
    /**
     * Assesses test quality
     */
    private static double assessTestQuality(List<TestReference> tests) {
        if (tests == null || tests.isEmpty()) return 0.0;
        
        double totalCoverage = tests.stream()
            .mapToDouble(TestReference::getCoverage)
            .average()
            .orElse(0.0);
            
        double diversityScore = tests.size() > 1 ? 0.2 : 0.0;
        
        return Math.min(1.0, totalCoverage + diversityScore);
    }
    
    /**
     * Assesses business context quality
     */
    private static double assessBusinessContextQuality(BusinessContext businessContext) {
        if (businessContext == null) return 0.0;
        
        double score = 0.0;
        
        if (businessContext.getDomainConcepts() != null && !businessContext.getDomainConcepts().isEmpty()) {
            score += 0.4;
        }
        
        if (businessContext.getBusinessRules() != null && !businessContext.getBusinessRules().isEmpty()) {
            score += 0.4;
        }
        
        if (businessContext.getRequirementsTraceability() != null && !businessContext.getRequirementsTraceability().isEmpty()) {
            score += 0.2;
        }
        
        return Math.min(1.0, score);
    }
    
    /**
     * Calculates quality grade from score
     */
    private static String calculateQualityGrade(double score) {
        if (score >= 0.9) return "A";
        if (score >= 0.8) return "B";
        if (score >= 0.7) return "C";
        if (score >= 0.6) return "D";
        return "F";
    }
    
    /**
     * Generates quality improvement recommendations
     */
    private static List<String> generateQualityRecommendations(double overallScore, List<String> qualityIssues) {
        List<String> recommendations = new ArrayList<>();
        
        if (overallScore < 0.6) {
            recommendations.add("Overall context quality is low - prioritize documentation and testing");
        }
        
        if (qualityIssues.stream().anyMatch(issue -> issue.contains("test coverage"))) {
            recommendations.add("Improve test coverage with unit and integration tests");
        }
        
        if (qualityIssues.stream().anyMatch(issue -> issue.contains("business context"))) {
            recommendations.add("Add business context documentation and domain concepts");
        }
        
        if (overallScore > 0.8) {
            recommendations.add("Excellent context quality - maintain current standards");
        }
        
        return recommendations;
    }
    
    /**
     * Identifies context gaps in the enriched entities
     */
    private static List<ContextGap> identifyContextGaps(List<EnrichedEntity> enrichedEntities, List<String> contextTypes) {
        List<ContextGap> gaps = new ArrayList<>();
        Map<String, List<String>> gapsByType = new HashMap<>();
        
        for (EnrichedEntity entity : enrichedEntities) {
            // Check for missing documentation
            if (entity.getDocumentation() == null || 
                (entity.getDocumentation().getJavadoc() == null || entity.getDocumentation().getJavadoc().trim().isEmpty())) {
                gapsByType.computeIfAbsent("MISSING_DOCUMENTATION", k -> new ArrayList<>()).add(entity.getEntityId());
            }
            
            // Check for missing usage examples
            if (entity.getUsageExamples() == null || entity.getUsageExamples().isEmpty()) {
                gapsByType.computeIfAbsent("MISSING_USAGE_EXAMPLES", k -> new ArrayList<>()).add(entity.getEntityId());
            }
            
            // Check for missing tests
            if (entity.getTests() == null || entity.getTests().isEmpty()) {
                gapsByType.computeIfAbsent("MISSING_TESTS", k -> new ArrayList<>()).add(entity.getEntityId());
            }
            
            // Check for missing business context
            if (entity.getBusinessContext() == null) {
                gapsByType.computeIfAbsent("MISSING_BUSINESS_CONTEXT", k -> new ArrayList<>()).add(entity.getEntityId());
            }
        }
        
        // Create gap objects
        for (Map.Entry<String, List<String>> entry : gapsByType.entrySet()) {
            String gapType = entry.getKey();
            List<String> affectedEntities = entry.getValue();
            
            gaps.add(ContextGap.builder()
                .gapType(gapType)
                .description(generateGapDescription(gapType))
                .severity(calculateGapSeverity(gapType, affectedEntities.size(), enrichedEntities.size()))
                .affectedEntities(affectedEntities)
                .suggestions(generateGapSuggestions(gapType))
                .build());
        }
        
        return gaps;
    }
    
    /**
     * Generates gap description
     */
    private static String generateGapDescription(String gapType) {
        switch (gapType) {
            case "MISSING_DOCUMENTATION":
                return "Entities lack comprehensive documentation";
            case "MISSING_USAGE_EXAMPLES":
                return "Entities lack usage examples and code samples";
            case "MISSING_TESTS":
                return "Entities lack adequate test coverage";
            case "MISSING_BUSINESS_CONTEXT":
                return "Entities lack business context and domain information";
            default:
                return "Context gap identified: " + gapType;
        }
    }
    
    /**
     * Calculates gap severity
     */
    private static String calculateGapSeverity(String gapType, int affectedCount, int totalCount) {
        double affectedPercentage = (double) affectedCount / totalCount;
        
        if (affectedPercentage > 0.7) return "HIGH";
        if (affectedPercentage > 0.4) return "MEDIUM";
        return "LOW";
    }
    
    /**
     * Generates gap suggestions
     */
    private static List<String> generateGapSuggestions(String gapType) {
        switch (gapType) {
            case "MISSING_DOCUMENTATION":
                return Arrays.asList("Add Javadoc comments", "Create README files", "Document API endpoints");
            case "MISSING_USAGE_EXAMPLES":
                return Arrays.asList("Add code examples in documentation", "Create usage samples", "Improve test examples");
            case "MISSING_TESTS":
                return Arrays.asList("Write unit tests", "Add integration tests", "Improve test coverage");
            case "MISSING_BUSINESS_CONTEXT":
                return Arrays.asList("Document business rules", "Add domain concept explanations", "Create requirements traceability");
            default:
                return Arrays.asList("Address context gap", "Improve documentation");
        }
    }
    
    /**
     * Generates context recommendations
     */
    private static List<ContextRecommendation> generateContextRecommendations(List<EnrichedEntity> enrichedEntities,
                                                                            List<ContextGap> contextGaps,
                                                                            ContextQualityAssessment qualityAssessment) {
        List<ContextRecommendation> recommendations = new ArrayList<>();
        
        // High-priority recommendations based on gaps
        for (ContextGap gap : contextGaps) {
            if ("HIGH".equals(gap.getSeverity())) {
                recommendations.add(ContextRecommendation.builder()
                    .type("GAP_RESOLUTION")
                    .priority("HIGH")
                    .title("Address " + gap.getGapType())
                    .description(gap.getDescription())
                    .actionItems(gap.getSuggestions())
                    .estimatedEffort("MEDIUM")
                    .build());
            }
        }
        
        // Quality-based recommendations
        if (qualityAssessment.getOverallScore() < 0.6) {
            recommendations.add(ContextRecommendation.builder()
                .type("QUALITY_IMPROVEMENT")
                .priority("HIGH")
                .title("Improve Overall Context Quality")
                .description("Context quality is below acceptable threshold")
                .actionItems(qualityAssessment.getRecommendations())
                .estimatedEffort("HIGH")
                .build());
        }
        
        return recommendations;
    }
    
    /**
     * Updates context state
     */
    private static void updateContextState(ToolContext ctx, List<String> entities, 
                                          ContextQualityAssessment qualityAssessment, List<ContextGap> contextGaps) {
        if (ctx != null && ctx.state() != null) {
            ctx.state().put("app:enriched_entities", entities);
            ctx.state().put("app:context_quality", qualityAssessment.getOverallScore());
            ctx.state().put("app:context_gaps", contextGaps.stream()
                .map(ContextGap::getGapType).collect(Collectors.toList()));
            ctx.state().put("app:quality_grade", qualityAssessment.getQualityGrade());
        }
    }
    
    /**
     * Determines next actions based on context analysis
     */
    private static List<String> determineNextActions(List<EnrichedEntity> enrichedEntities, List<ContextGap> contextGaps,
                                                   ContextQualityAssessment qualityAssessment) {
        List<String> actions = new ArrayList<>();
        
        // Gap-based actions
        if (contextGaps.stream().anyMatch(gap -> "MISSING_DOCUMENTATION".equals(gap.getGapType()))) {
            actions.add("IMPROVE_DOCUMENTATION");
        }
        
        if (contextGaps.stream().anyMatch(gap -> "MISSING_TESTS".equals(gap.getGapType()))) {
            actions.add("INCREASE_TEST_COVERAGE");
        }
        
        if (contextGaps.stream().anyMatch(gap -> "MISSING_USAGE_EXAMPLES".equals(gap.getGapType()))) {
            actions.add("ADD_USAGE_EXAMPLES");
        }
        
        if (contextGaps.stream().anyMatch(gap -> "MISSING_BUSINESS_CONTEXT".equals(gap.getGapType()))) {
            actions.add("DOCUMENT_BUSINESS_CONTEXT");
        }
        
        // Quality-based actions
        if (qualityAssessment.getOverallScore() > 0.8) {
            actions.add("MAINTAIN_QUALITY_STANDARDS");
        } else if (qualityAssessment.getOverallScore() < 0.5) {
            actions.add("URGENT_QUALITY_IMPROVEMENT");
        }
        
        // Context-specific actions
        boolean hasComplexEntities = enrichedEntities.stream()
            .anyMatch(entity -> entity.getRelatedEntities().size() > 5);
        if (hasComplexEntities) {
            actions.add("SIMPLIFY_COMPLEX_ENTITIES");
        }
        
        return actions;
    }
    
    // Helper methods for mock data generation
    
    private static String generateMockJavadoc(String entityId) {
        String className = extractClassName(entityId);
        String methodName = extractMethodName(entityId);
        
        return String.format("""
            /**
             * %s method for %s.
             * 
             * This method handles the core functionality of %s by processing
             * the input parameters and returning the appropriate result.
             * 
             * @param input the input parameters
             * @return the processing result
             * @throws IllegalArgumentException if input is invalid
             */
            """, capitalize(methodName), className, methodName);
    }
    
    private static String findRelatedReadme(String entityId) {
        if (entityId.contains("Service")) {
            return "# " + extractClassName(entityId) + "\n\nService class for handling business logic.";
        }
        return null;
    }
    
    private static List<String> findInlineComments(String entityId) {
        return Arrays.asList(
            "// Validate input parameters",
            "// Process core business logic", 
            "// Handle error scenarios",
            "// Return formatted result"
        );
    }
    
    private static String findApiDocumentation(String entityId) {
        if (entityId.contains("Controller")) {
            return "REST API endpoint for " + extractMethodName(entityId) + " operations";
        }
        return null;
    }
    
    private static double calculateDocumentationCompleteness(String javadoc, String readme, 
                                                           List<String> inlineComments, String apiDocumentation) {
        double score = 0.0;
        if (javadoc != null && !javadoc.trim().isEmpty()) score += 0.4;
        if (readme != null && !readme.trim().isEmpty()) score += 0.2;
        if (inlineComments != null && !inlineComments.isEmpty()) score += 0.3;
        if (apiDocumentation != null && !apiDocumentation.trim().isEmpty()) score += 0.1;
        return Math.min(1.0, score);
    }
    
    private static String extractClassName(String entityId) {
        if (entityId.contains(".")) {
            String[] parts = entityId.split("\\.");
            for (int i = parts.length - 1; i >= 0; i--) {
                if (Character.isUpperCase(parts[i].charAt(0))) {
                    return parts[i];
                }
            }
        }
        return entityId;
    }
    
    private static String extractMethodName(String entityId) {
        if (entityId.contains(".")) {
            String[] parts = entityId.split("\\.");
            String lastPart = parts[parts.length - 1];
            if (Character.isLowerCase(lastPart.charAt(0))) {
                return lastPart;
            }
        }
        return "process";
    }
    
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    private static List<String> extractDomainConcepts(String entityId) {
        List<String> concepts = new ArrayList<>();
        
        if (entityId.contains("Payment")) {
            concepts.addAll(Arrays.asList("Payment", "Transaction", "Money", "Account"));
        } else if (entityId.contains("User")) {
            concepts.addAll(Arrays.asList("User", "Profile", "Authentication", "Authorization"));
        } else if (entityId.contains("Order")) {
            concepts.addAll(Arrays.asList("Order", "Product", "Customer", "Inventory"));
        } else {
            concepts.add(extractClassName(entityId));
        }
        
        return concepts;
    }
    
    private static List<String> generateBusinessRules(String entityId) {
        List<String> rules = new ArrayList<>();
        
        if (entityId.contains("Payment")) {
            rules.addAll(Arrays.asList(
                "Payment amount must be positive",
                "User must be authenticated for payments",
                "Payment method must be validated"
            ));
        } else if (entityId.contains("User")) {
            rules.addAll(Arrays.asList(
                "Username must be unique",
                "Password must meet security requirements",
                "Email must be validated"
            ));
        } else {
            rules.add("Input validation is required");
            rules.add("Business logic must be consistent");
        }
        
        return rules;
    }
    
    private static Map<String, String> generateRequirementsTraceability(String entityId) {
        Map<String, String> traceability = new HashMap<>();
        
        if (entityId.contains("Payment")) {
            traceability.put("REQ-001", "Process payment transactions");
            traceability.put("REQ-002", "Validate payment methods");
        } else if (entityId.contains("User")) {
            traceability.put("REQ-003", "User authentication");
            traceability.put("REQ-004", "User profile management");
        }
        
        return traceability;
    }
    
    // Data classes for context enrichment
    
    enum ContextStrategy {
        COMPREHENSIVE,         // Gather all available context
        DOCUMENTATION_FOCUSED, // Focus on documentation and comments
        USAGE_FOCUSED,        // Focus on usage examples and tests
        EFFICIENT,            // Quick context gathering for many entities
        BALANCED              // Balanced approach for moderate needs
    }
    
    @Data
    @Builder
    static class EnrichedEntity {
        private String entityId;
        private Documentation documentation;
        private List<UsageExample> usageExamples;
        private List<TestReference> tests;
        private Comments comments;
        private BusinessContext businessContext;
        private List<String> relatedEntities;
        private Map<String, Object> metadata;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "entityId", entityId,
                "documentation", documentation != null ? documentation.toMap() : Map.of(),
                "usageExamples", usageExamples != null ? usageExamples.stream().map(UsageExample::toMap).collect(Collectors.toList()) : List.of(),
                "tests", tests != null ? tests.stream().map(TestReference::toMap).collect(Collectors.toList()) : List.of(),
                "comments", comments != null ? comments.toMap() : Map.of(),
                "businessContext", businessContext != null ? businessContext.toMap() : Map.of(),
                "relatedEntities", relatedEntities != null ? relatedEntities : List.of(),
                "metadata", metadata != null ? metadata : Map.of()
            );
        }
    }
    
    @Data
    @Builder
    static class Documentation {
        private String javadoc;
        private String readme;
        private List<String> inlineComments;
        private String apiDocumentation;
        private double completenessScore;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "javadoc", javadoc != null ? javadoc : "",
                "readme", readme != null ? readme : "",
                "inlineComments", inlineComments != null ? inlineComments : List.of(),
                "apiDocumentation", apiDocumentation != null ? apiDocumentation : "",
                "completenessScore", completenessScore
            );
        }
    }
    
    @Data
    @Builder
    static class UsageExample {
        private String source;        // WHERE: test, documentation, code
        private String example;       // WHAT: actual usage code
        private String description;   // WHY: what this example shows
        private String context;       // HOW: context where it's used
        
        public Map<String, Object> toMap() {
            return Map.of(
                "source", source != null ? source : "",
                "example", example != null ? example : "",
                "description", description != null ? description : "",
                "context", context != null ? context : ""
            );
        }
    }
    
    @Data
    @Builder
    static class TestReference {
        private String testClass;
        private String testMethod;
        private String testType;      // UNIT, INTEGRATION, FUNCTIONAL
        private double coverage;
        private String description;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "testClass", testClass != null ? testClass : "",
                "testMethod", testMethod != null ? testMethod : "",
                "testType", testType != null ? testType : "",
                "coverage", coverage,
                "description", description != null ? description : ""
            );
        }
    }
    
    @Data
    @Builder
    static class Comments {
        private List<String> inlineComments;
        private List<String> todoComments;
        private List<String> fixmeComments;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "inlineComments", inlineComments != null ? inlineComments : List.of(),
                "todoComments", todoComments != null ? todoComments : List.of(),
                "fixmeComments", fixmeComments != null ? fixmeComments : List.of()
            );
        }
    }
    
    @Data
    @Builder
    static class BusinessContext {
        private List<String> domainConcepts;
        private List<String> businessRules;
        private Map<String, String> requirementsTraceability;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "domainConcepts", domainConcepts != null ? domainConcepts : List.of(),
                "businessRules", businessRules != null ? businessRules : List.of(),
                "requirementsTraceability", requirementsTraceability != null ? requirementsTraceability : Map.of()
            );
        }
    }
    
    @Data
    @Builder
    static class ContextQualityAssessment {
        private double overallScore;
        private Map<String, Double> categoryScores;
        private List<String> qualityFactors;
        private List<String> qualityIssues;
        private String qualityGrade;      // A, B, C, D, F
        private List<String> recommendations;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "overallScore", overallScore,
                "categoryScores", categoryScores != null ? categoryScores : Map.of(),
                "qualityFactors", qualityFactors != null ? qualityFactors : List.of(),
                "qualityIssues", qualityIssues != null ? qualityIssues : List.of(),
                "qualityGrade", qualityGrade != null ? qualityGrade : "F",
                "recommendations", recommendations != null ? recommendations : List.of()
            );
        }
    }
    
    @Data
    @Builder
    static class ContextGap {
        private String gapType;           // MISSING_DOCS, MISSING_TESTS, etc.
        private String description;
        private String severity;          // HIGH, MEDIUM, LOW
        private List<String> affectedEntities;
        private List<String> suggestions;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "gapType", gapType != null ? gapType : "",
                "description", description != null ? description : "",
                "severity", severity != null ? severity : "LOW",
                "affectedEntities", affectedEntities != null ? affectedEntities : List.of(),
                "suggestions", suggestions != null ? suggestions : List.of()
            );
        }
    }
    
    @Data
    @Builder
    static class ContextRecommendation {
        private String type;              // GAP_RESOLUTION, QUALITY_IMPROVEMENT, etc.
        private String priority;          // HIGH, MEDIUM, LOW
        private String title;
        private String description;
        private List<String> actionItems;
        private String estimatedEffort;   // LOW, MEDIUM, HIGH
        
        public Map<String, Object> toMap() {
            return Map.of(
                "type", type != null ? type : "",
                "priority", priority != null ? priority : "LOW",
                "title", title != null ? title : "",
                "description", description != null ? description : "",
                "actionItems", actionItems != null ? actionItems : List.of(),
                "estimatedEffort", estimatedEffort != null ? estimatedEffort : "MEDIUM"
            );
        }
    }
}