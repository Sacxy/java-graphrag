package com.tekion.javaastkg.adk.tools;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import com.tekion.javaastkg.adk.core.BaseAdkTool;
import com.tekion.javaastkg.adk.tools.CodeContextEnricherDataModels.*;
import com.tekion.javaastkg.service.Neo4jService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Code Context Enricher - Intelligent Context Analysis and Improvement Tool
 * 
 * This tool provides comprehensive context analysis for code entities by:
 * - Gathering documentation, usage examples, tests, and business context from multiple sources
 * - Intelligently assessing context quality using multi-dimensional scoring
 * - Identifying specific gaps and improvement opportunities
 * - Generating prioritized, actionable recommendations with effort estimates
 * 
 * Designed for AI agents to help developers understand and improve code context quality.
 */
@Slf4j
public class CodeContextEnricherRedesigned extends BaseAdkTool {

    private static Neo4jService neo4jService;
    
    /**
     * Initialize the tool with Neo4j service for data access
     */
    public static void initialize(Neo4jService service) {
        neo4jService = service;
    }

    /**
     * Analyzes code context quality and provides intelligent improvement recommendations
     * 
     * This tool gathers comprehensive context information from multiple sources, assesses
     * quality using intelligent scoring algorithms, identifies specific gaps, and generates
     * actionable recommendations prioritized by impact and effort.
     * 
     * @param entityIds List of entity IDs to analyze (e.g., "com.example.PaymentService.processPayment")
     * @param analysisConfig Configuration for analysis depth, quality thresholds, and focus areas
     * @param qualityCriteria Criteria for assessing context quality (documentation, tests, usage examples)
     * @param ctx Tool context for state management and data flow
     * @return Comprehensive context analysis with quality scores, gaps, and prioritized recommendations
     */
    @Schema(description = "Analyze code context quality and generate intelligent improvement recommendations")
    public static Map<String, Object> analyzeContextQuality(
            @Schema(description = "Entity IDs to analyze for context quality") List<String> entityIds,
            @Schema(description = "Analysis configuration (depth, thresholds, focus areas)") Map<String, Object> analysisConfig,
            @Schema(description = "Quality criteria and scoring weights") Map<String, Object> qualityCriteria,
            @Schema(name = "toolContext") ToolContext ctx
    ) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Input validation
            if (entityIds == null || entityIds.isEmpty()) {
                return errorResponse("analyzeContextQuality", "Entity IDs cannot be null or empty");
            }
            
            if (neo4jService == null) {
                return errorResponse("analyzeContextQuality", "Neo4j service not initialized");
            }
            
            log.info("Starting context quality analysis for {} entities with config: {}", 
                    entityIds.size(), analysisConfig);
            
            // Extract configuration parameters
            AnalysisConfiguration config = AnalysisConfiguration.fromMap(analysisConfig);
            QualityScoring scoring = QualityScoring.fromMap(qualityCriteria != null ? qualityCriteria : Map.of());
            
            // Select optimal analysis strategy based on entity count and configuration
            ContextAnalysisStrategy strategy = selectAnalysisStrategy(entityIds.size(), config);
            
            // Gather comprehensive context for all entities
            List<EntityContextAnalysis> entityAnalyses = gatherEntityContexts(entityIds, config, strategy);
            
            // Assess overall context quality using intelligent scoring
            OverallQualityAssessment qualityAssessment = assessOverallQuality(entityAnalyses, scoring);
            
            // Identify specific context gaps and improvement opportunities
            List<ContextGap> contextGaps = identifyContextGaps(entityAnalyses, config, scoring);
            
            // Generate prioritized recommendations with effort estimates
            List<ContextRecommendation> recommendations = generateRecommendations(entityAnalyses, contextGaps, config);
            
            // Update context state for agent reasoning
            updateContextState(ctx, entityIds, qualityAssessment, contextGaps);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.info("Context analysis completed in {}ms: {} entities, overall score: {:.2f}, {} gaps, {} recommendations",
                    executionTime, entityAnalyses.size(), qualityAssessment.getOverallScore(), 
                    contextGaps.size(), recommendations.size());
            
            return Map.of(
                "status", "success",
                "entityAnalyses", entityAnalyses.stream()
                    .map(EntityContextAnalysis::toMap)
                    .collect(Collectors.toList()),
                "qualityAssessment", qualityAssessment.toMap(),
                "contextGaps", contextGaps.stream()
                    .map(ContextGap::toMap)
                    .collect(Collectors.toList()),
                "recommendations", recommendations.stream()
                    .map(ContextRecommendation::toMap)
                    .collect(Collectors.toList()),
                "analysisMetadata", Map.of(
                    "strategy", strategy.name(),
                    "entitiesAnalyzed", entityAnalyses.size(),
                    "overallScore", qualityAssessment.getOverallScore(),
                    "overallGrade", qualityAssessment.getOverallGrade(),
                    "gapsIdentified", contextGaps.size(),
                    "recommendationsGenerated", recommendations.size(),
                    "executionTimeMs", executionTime,
                    "analysisDepth", config.getAnalysisDepth()
                ),
                "nextActions", determineNextActions(qualityAssessment, contextGaps, recommendations)
            );
            
        } catch (Exception e) {
            log.error("Context quality analysis failed for entities: {}", entityIds, e);
            return errorResponse("analyzeContextQuality", "Context analysis failed: " + e.getMessage());
        }
    }
    
    /**
     * Selects optimal analysis strategy based on entity count and configuration
     */
    private static ContextAnalysisStrategy selectAnalysisStrategy(int entityCount, AnalysisConfiguration config) {
        if (config.getAnalysisDepth().equals("COMPREHENSIVE")) {
            return ContextAnalysisStrategy.COMPREHENSIVE;
        } else if (entityCount > 20) {
            return ContextAnalysisStrategy.EFFICIENT;
        } else if (config.getFocusAreas().contains("DOCUMENTATION")) {
            return ContextAnalysisStrategy.DOCUMENTATION_FOCUSED;
        } else if (config.getFocusAreas().contains("TESTS")) {
            return ContextAnalysisStrategy.TEST_FOCUSED;
        } else {
            return ContextAnalysisStrategy.BALANCED;
        }
    }
    
    /**
     * Gathers comprehensive context information for all entities
     */
    private static List<EntityContextAnalysis> gatherEntityContexts(
            List<String> entityIds, 
            AnalysisConfiguration config, 
            ContextAnalysisStrategy strategy) {
        
        List<EntityContextAnalysis> analyses = new ArrayList<>();
        
        for (String entityId : entityIds) {
            log.debug("Gathering context for entity: {}", entityId);
            
            // Gather context from multiple sources based on strategy
            DocumentationContext documentation = gatherDocumentationContext(entityId, strategy);
            UsageContext usage = gatherUsageContext(entityId, strategy);
            TestContext tests = gatherTestContext(entityId, strategy);
            BusinessContext business = gatherBusinessContext(entityId, strategy);
            
            // Assess entity-specific quality
            EntityQualityScores scores = calculateEntityQualityScores(documentation, usage, tests, business);
            
            EntityContextAnalysis analysis = EntityContextAnalysis.builder()
                .entityId(entityId)
                .documentation(documentation)
                .usage(usage)
                .tests(tests)
                .business(business)
                .qualityScores(scores)
                .analysisTimestamp(System.currentTimeMillis())
                .build();
            
            analyses.add(analysis);
        }
        
        return analyses;
    }
    
    /**
     * Gathers documentation context from multiple sources
     */
    private static DocumentationContext gatherDocumentationContext(String entityId, ContextAnalysisStrategy strategy) {
        // Query for LLM-generated descriptions
        String descriptionQuery = """
            MATCH (entity {id: $entityId})-[:HAS_DESCRIPTION]->(desc:Description)
            RETURN desc.content as content, desc.type as type, desc.createdAt as createdAt
            ORDER BY desc.createdAt DESC
            """;
        
        List<Map<String, Object>> descriptions = neo4jService.executeCypherQuery(
            descriptionQuery, Map.of("entityId", entityId));
        
        // Query for file documentation
        String fileDocQuery = """
            MATCH (entity {id: $entityId})
            MATCH (fileDoc:FileDoc)
            WHERE entity.packageName = fileDoc.packageName 
               OR fileDoc.fileName CONTAINS entity.className
            RETURN fileDoc.content as content, fileDoc.fileName as fileName
            LIMIT 5
            """;
        
        List<Map<String, Object>> fileDocs = neo4jService.executeCypherQuery(
            fileDocQuery, Map.of("entityId", entityId));
        
        // Process and structure documentation data
        List<String> javadocContent = descriptions.stream()
            .filter(desc -> "llm_generated".equals(desc.get("type")))
            .map(desc -> (String) desc.get("content"))
            .collect(Collectors.toList());
        
        List<String> inlineComments = descriptions.stream()
            .filter(desc -> !"llm_generated".equals(desc.get("type")))
            .map(desc -> (String) desc.get("content"))
            .collect(Collectors.toList());
        
        List<String> readmeContent = fileDocs.stream()
            .filter(doc -> {
                String fileName = (String) doc.get("fileName");
                return fileName != null && fileName.toLowerCase().contains("readme");
            })
            .map(doc -> (String) doc.get("content"))
            .collect(Collectors.toList());
        
        double completenessScore = calculateDocumentationCompleteness(javadocContent, inlineComments, readmeContent);
        double qualityScore = calculateDocumentationQuality(javadocContent, inlineComments);
        
        return DocumentationContext.builder()
            .javadocContent(javadocContent)
            .inlineComments(inlineComments)
            .readmeContent(readmeContent)
            .completenessScore(completenessScore)
            .qualityScore(qualityScore)
            .hasDocumentation(!javadocContent.isEmpty() || !inlineComments.isEmpty())
            .build();
    }
    
    /**
     * Gathers usage context from method calls and relationships
     */
    private static UsageContext gatherUsageContext(String entityId, ContextAnalysisStrategy strategy) {
        // Find callers (methods that use this entity)
        String callersQuery = """
            MATCH (caller)-[:CALLS]->(entity {id: $entityId})
            RETURN caller.id as callerId, caller.name as callerName, 
                   caller.className as callerClass, caller.packageName as callerPackage
            LIMIT 15
            """;
        
        List<Map<String, Object>> callers = neo4jService.executeCypherQuery(
            callersQuery, Map.of("entityId", entityId));
        
        // Find callees (methods this entity calls)
        String calleesQuery = """
            MATCH (entity {id: $entityId})-[:CALLS]->(callee)
            RETURN callee.id as calleeId, callee.name as calleeName,
                   callee.className as calleeClass, callee.packageName as calleePackage
            LIMIT 15
            """;
        
        List<Map<String, Object>> callees = neo4jService.executeCypherQuery(
            calleesQuery, Map.of("entityId", entityId));
        
        // Find field usage
        String fieldUsageQuery = """
            MATCH (entity {id: $entityId})-[:USES_FIELD]->(field)
            RETURN field.id as fieldId, field.name as fieldName, field.type as fieldType
            LIMIT 10
            """;
        
        List<Map<String, Object>> fieldUsage = neo4jService.executeCypherQuery(
            fieldUsageQuery, Map.of("entityId", entityId));
        
        // Create usage examples from the data
        List<UsageExample> usageExamples = new ArrayList<>();
        
        // Add caller examples
        for (Map<String, Object> caller : callers) {
            usageExamples.add(UsageExample.builder()
                .source("method_call")
                .example(caller.get("callerName") + "() calls " + extractMethodName(entityId) + "()")
                .context("Called by " + caller.get("callerClass"))
                .packageContext((String) caller.get("callerPackage"))
                .build());
        }
        
        // Add callee examples  
        for (Map<String, Object> callee : callees) {
            usageExamples.add(UsageExample.builder()
                .source("calls_method")
                .example(extractMethodName(entityId) + "() calls " + callee.get("calleeName") + "()")
                .context("Calls " + callee.get("calleeClass"))
                .packageContext((String) callee.get("calleePackage"))
                .build());
        }
        
        // Add field usage examples
        for (Map<String, Object> field : fieldUsage) {
            usageExamples.add(UsageExample.builder()
                .source("field_access")
                .example("Uses field: " + field.get("fieldName"))
                .context("Field of type: " + field.get("fieldType"))
                .packageContext("field_usage")
                .build());
        }
        
        double usageRichness = calculateUsageRichness(usageExamples);
        double usageDiversity = calculateUsageDiversity(usageExamples);
        
        return UsageContext.builder()
            .usageExamples(usageExamples)
            .callerCount(callers.size())
            .calleeCount(callees.size())
            .fieldUsageCount(fieldUsage.size())
            .usageRichness(usageRichness)
            .usageDiversity(usageDiversity)
            .hasUsageExamples(!usageExamples.isEmpty())
            .build();
    }
    
    /**
     * Gathers test context from test classes and methods
     */
    private static TestContext gatherTestContext(String entityId, ContextAnalysisStrategy strategy) {
        String className = extractClassName(entityId);
        String methodName = extractMethodName(entityId);
        String packageName = extractPackageName(entityId);
        
        // Find test classes
        String testClassQuery = """
            MATCH (testClass:Class)
            WHERE testClass.name CONTAINS 'Test' 
            AND (testClass.name CONTAINS $className OR testClass.packageName = $packageName)
            RETURN testClass.name as testClassName, testClass.packageName as packageName
            LIMIT 10
            """;
        
        List<Map<String, Object>> testClasses = neo4jService.executeCypherQuery(
            testClassQuery, Map.of("className", className, "packageName", packageName));
        
        // Find test methods
        String testMethodQuery = """
            MATCH (testMethod:Method)
            WHERE testMethod.name CONTAINS 'test'
            AND (testMethod.name CONTAINS $methodName OR testMethod.className CONTAINS $className)
            RETURN testMethod.name as testMethodName, testMethod.className as testClassName
            LIMIT 15
            """;
        
        List<Map<String, Object>> testMethods = neo4jService.executeCypherQuery(
            testMethodQuery, Map.of("methodName", methodName, "className", className));
        
        List<TestReference> testReferences = new ArrayList<>();
        
        // Process test classes
        for (Map<String, Object> testClass : testClasses) {
            testReferences.add(TestReference.builder()
                .testType("class")
                .testName((String) testClass.get("testClassName"))
                .testCategory("unit")
                .packageName((String) testClass.get("packageName"))
                .build());
        }
        
        // Process test methods
        for (Map<String, Object> testMethod : testMethods) {
            String testMethodName = (String) testMethod.get("testMethodName");
            testReferences.add(TestReference.builder()
                .testType("method")
                .testName(testMethodName)
                .testClass((String) testMethod.get("testClassName"))
                .testCategory(determineTestCategory(testMethodName))
                .build());
        }
        
        double estimatedCoverage = calculateEstimatedCoverage(testReferences);
        double testQuality = calculateTestQuality(testReferences);
        
        return TestContext.builder()
            .testReferences(testReferences)
            .testClassCount(testClasses.size())
            .testMethodCount(testMethods.size())
            .estimatedCoverage(estimatedCoverage)
            .testQuality(testQuality)
            .hasTests(!testReferences.isEmpty())
            .build();
    }
    
    /**
     * Gathers business context from LLM-generated descriptions
     */
    private static BusinessContext gatherBusinessContext(String entityId, ContextAnalysisStrategy strategy) {
        // Get business-oriented descriptions
        String businessQuery = """
            MATCH (entity {id: $entityId})-[:HAS_DESCRIPTION]->(desc:Description)
            WHERE desc.type = 'llm_generated'
            RETURN desc.content as content
            """;
        
        List<Map<String, Object>> descriptions = neo4jService.executeCypherQuery(
            businessQuery, Map.of("entityId", entityId));
        
        List<String> businessDescriptions = descriptions.stream()
            .map(desc -> (String) desc.get("content"))
            .collect(Collectors.toList());
        
        // Extract domain concepts and business rules
        Set<String> domainConcepts = extractDomainConcepts(businessDescriptions, entityId);
        List<String> businessRules = extractBusinessRules(businessDescriptions);
        
        double businessContextRichness = calculateBusinessContextRichness(businessDescriptions, domainConcepts, businessRules);
        
        return BusinessContext.builder()
            .businessDescriptions(businessDescriptions)
            .domainConcepts(new ArrayList<>(domainConcepts))
            .businessRules(businessRules)
            .businessContextRichness(businessContextRichness)
            .hasBusinessContext(!businessDescriptions.isEmpty())
            .build();
    }
    
    // Supporting classes and calculation methods follow...
    
    /**
     * Analysis strategy enum for different approaches
     */
    enum ContextAnalysisStrategy {
        COMPREHENSIVE,      // Deep analysis of all context types
        EFFICIENT,         // Quick analysis for large entity sets
        DOCUMENTATION_FOCUSED, // Focus on documentation quality
        TEST_FOCUSED,      // Focus on test coverage and quality
        BALANCED          // Balanced approach across all areas
    }
    
    /**
     * Configuration for analysis parameters
     */
    @Data
    @Builder
    static class AnalysisConfiguration {
        private String analysisDepth;        // SHALLOW, MODERATE, COMPREHENSIVE
        private List<String> focusAreas;     // DOCUMENTATION, TESTS, USAGE, BUSINESS
        private Map<String, Double> qualityThresholds;
        private int maxEntityDepth;
        
        static AnalysisConfiguration fromMap(Map<String, Object> configMap) {
            @SuppressWarnings("unchecked")
            List<String> focusAreas = (List<String>) configMap.getOrDefault("focusAreas", 
                List.of("DOCUMENTATION", "TESTS", "USAGE", "BUSINESS"));
            
            @SuppressWarnings("unchecked")
            Map<String, Double> thresholds = (Map<String, Double>) configMap.getOrDefault("qualityThresholds",
                Map.of("documentation", 0.6, "tests", 0.5, "usage", 0.4, "business", 0.3));
            
            return AnalysisConfiguration.builder()
                .analysisDepth((String) configMap.getOrDefault("analysisDepth", "MODERATE"))
                .focusAreas(focusAreas)
                .qualityThresholds(thresholds)
                .maxEntityDepth((Integer) configMap.getOrDefault("maxEntityDepth", 5))
                .build();
        }
    }
    
    /**
     * Quality scoring configuration
     */
    @Data
    @Builder  
    static class QualityScoring {
        private double documentationWeight;
        private double testWeight;
        private double usageWeight;
        private double businessWeight;
        
        static QualityScoring fromMap(Map<String, Object> criteriaMap) {
            return QualityScoring.builder()
                .documentationWeight((Double) criteriaMap.getOrDefault("documentationWeight", 0.3))
                .testWeight((Double) criteriaMap.getOrDefault("testWeight", 0.25))
                .usageWeight((Double) criteriaMap.getOrDefault("usageWeight", 0.25))
                .businessWeight((Double) criteriaMap.getOrDefault("businessWeight", 0.2))
                .build();
        }
    }
    
    /**
     * Assesses overall quality using intelligent algorithms
     */
    private static OverallQualityAssessment assessOverallQuality(
            List<EntityContextAnalysis> entityAnalyses, 
            QualityScoring scoring) {
        return CodeContextEnricherCalculations.assessOverallQuality(entityAnalyses, scoring);
    }
    
    /**
     * Identifies context gaps across all entities
     */
    private static List<ContextGap> identifyContextGaps(
            List<EntityContextAnalysis> entityAnalyses,
            AnalysisConfiguration config,
            QualityScoring scoring) {
        return CodeContextEnricherCalculations.identifyContextGaps(entityAnalyses, config, scoring);
    }
    
    /**
     * Generates prioritized recommendations for context improvement
     */
    private static List<ContextRecommendation> generateRecommendations(
            List<EntityContextAnalysis> entityAnalyses,
            List<ContextGap> contextGaps,
            AnalysisConfiguration config) {
        
        List<ContextRecommendation> recommendations = new ArrayList<>();
        
        // Generate gap-specific recommendations
        for (ContextGap gap : contextGaps) {
            ContextRecommendation gapRec = generateGapRecommendation(gap, config);
            if (gapRec != null) {
                recommendations.add(gapRec);
            }
        }
        
        // Generate entity-specific recommendations
        for (EntityContextAnalysis entity : entityAnalyses) {
            recommendations.addAll(generateEntityRecommendations(entity, config));
        }
        
        // Generate overall improvement recommendations
        recommendations.addAll(generateOverallRecommendations(entityAnalyses, contextGaps, config));
        
        // Prioritize and deduplicate
        return prioritizeAndDeduplicateRecommendations(recommendations);
    }
    
    /**
     * Generates recommendation for a specific gap
     */
    private static ContextRecommendation generateGapRecommendation(ContextGap gap, AnalysisConfiguration config) {
        return switch (gap.getGapType()) {
            case "MISSING_DOCUMENTATION" -> ContextRecommendation.builder()
                .recommendationType(CodeContextConstants.RecommendationType.DOCUMENTATION_IMPROVEMENT.name())
                .title("Improve Documentation Coverage")
                .description("Add comprehensive documentation to entities with poor documentation scores")
                .priority(gap.getSeverity())
                .effortEstimate(CodeContextConstants.EffortEstimate.MEDIUM.getValue())
                .actionItems(List.of(
                    "Add Javadoc comments to public methods",
                    "Document method parameters and return values",
                    "Create README files for complex modules",
                    "Add inline comments for complex logic"
                ))
                .affectedEntities(gap.getAffectedEntities())
                .expectedImpact("Developers will better understand code purpose and usage")
                .implementation(Map.of(
                    "tools", List.of("IDE documentation generators", "Documentation linters"),
                    "timeEstimate", "2-4 hours per entity",
                    "priority", "Start with most frequently used entities"
                ))
                .build();
                
            case "INSUFFICIENT_TESTING" -> ContextRecommendation.builder()
                .recommendationType(CodeContextConstants.RecommendationType.TEST_IMPROVEMENT.name())
                .title("Enhance Test Coverage")
                .description("Improve test coverage and quality for entities with inadequate testing")
                .priority(gap.getSeverity())
                .effortEstimate(CodeContextConstants.EffortEstimate.HIGH.getValue())
                .actionItems(List.of(
                    "Write unit tests for core functionality",
                    "Add integration tests for complex workflows",
                    "Create test cases for edge conditions",
                    "Improve test naming and documentation"
                ))
                .affectedEntities(gap.getAffectedEntities())
                .expectedImpact("Reduced bugs and increased confidence in code changes")
                .implementation(Map.of(
                    "tools", List.of("Testing frameworks", "Coverage tools", "Test generators"),
                    "timeEstimate", "4-8 hours per entity",
                    "priority", "Start with critical business logic"
                ))
                .build();
                
            case "MISSING_USAGE_EXAMPLES" -> ContextRecommendation.builder()
                .recommendationType(CodeContextConstants.RecommendationType.USAGE_IMPROVEMENT.name())
                .title("Add Usage Examples")
                .description("Provide clear usage examples for entities lacking practical guidance")
                .priority(gap.getSeverity())
                .effortEstimate(CodeContextConstants.EffortEstimate.LOW.getValue())
                .actionItems(List.of(
                    "Create code examples in documentation",
                    "Add usage samples to README files",
                    "Document common use cases",
                    "Show integration patterns"
                ))
                .affectedEntities(gap.getAffectedEntities())
                .expectedImpact("Developers can more easily adopt and use these components")
                .implementation(Map.of(
                    "tools", List.of("Documentation platforms", "Code snippet tools"),
                    "timeEstimate", "1-2 hours per entity",
                    "priority", "Focus on public APIs and complex interfaces"
                ))
                .build();
                
            case "MISSING_BUSINESS_CONTEXT" -> ContextRecommendation.builder()
                .recommendationType(CodeContextConstants.RecommendationType.BUSINESS_CONTEXT_IMPROVEMENT.name())
                .title("Document Business Context")
                .description("Add business context and domain knowledge to improve understanding")
                .priority(gap.getSeverity())
                .effortEstimate(CodeContextConstants.EffortEstimate.MEDIUM.getValue())
                .actionItems(List.of(
                    "Document business rules and constraints",
                    "Add domain concept explanations", 
                    "Link to business requirements",
                    "Explain business workflow context"
                ))
                .affectedEntities(gap.getAffectedEntities())
                .expectedImpact("Better alignment between code and business requirements")
                .implementation(Map.of(
                    "tools", List.of("Requirement management tools", "Domain modeling tools"),
                    "timeEstimate", "2-3 hours per entity",
                    "priority", "Start with core business logic entities"
                ))
                .build();
                
            default -> null;
        };
    }
    
    /**
     * Generates entity-specific recommendations
     */
    private static List<ContextRecommendation> generateEntityRecommendations(
            EntityContextAnalysis entity, 
            AnalysisConfiguration config) {
        
        List<ContextRecommendation> recommendations = new ArrayList<>();
        EntityQualityScores scores = entity.getQualityScores();
        
        // High-performing entities - maintenance recommendations
        if (scores.getOverallScore() >= 0.8) {
            recommendations.add(ContextRecommendation.builder()
                .recommendationType("MAINTENANCE")
                .title("Maintain Excellence")
                .description("Keep up the excellent context quality for " + entity.getEntityId())
                .priority("LOW")
                .effortEstimate("LOW")
                .actionItems(List.of("Regular review", "Keep documentation updated"))
                .affectedEntities(List.of(entity.getEntityId()))
                .expectedImpact("Sustained high quality")
                .implementation(Map.of("schedule", "Monthly review"))
                .build());
        }
        
        // Specific improvement recommendations based on weakest scores
        if (scores.getDocumentationScore() < 0.4) {
            recommendations.add(createSpecificDocumentationRecommendation(entity));
        }
        
        if (scores.getTestScore() < 0.4) {
            recommendations.add(createSpecificTestRecommendation(entity));
        }
        
        return recommendations;
    }
    
    /**
     * Creates specific documentation recommendation for an entity
     */
    private static ContextRecommendation createSpecificDocumentationRecommendation(EntityContextAnalysis entity) {
        return ContextRecommendation.builder()
            .recommendationType("ENTITY_DOCUMENTATION")
            .title("Document " + extractClassName(entity.getEntityId()))
            .description("Add comprehensive documentation for this specific entity")
            .priority("HIGH")
            .effortEstimate("MEDIUM")
            .actionItems(List.of(
                "Add class-level Javadoc",
                "Document public methods",
                "Explain complex algorithms",
                "Add usage examples"
            ))
            .affectedEntities(List.of(entity.getEntityId()))
            .expectedImpact("Improved understanding of this specific component")
            .implementation(Map.of(
                "approach", "Start with public interface documentation",
                "timeEstimate", "3-4 hours"
            ))
            .build();
    }
    
    /**
     * Creates specific test recommendation for an entity
     */
    private static ContextRecommendation createSpecificTestRecommendation(EntityContextAnalysis entity) {
        return ContextRecommendation.builder()
            .recommendationType("ENTITY_TESTING")
            .title("Test " + extractClassName(entity.getEntityId()))
            .description("Improve test coverage for this specific entity")
            .priority("HIGH")
            .effortEstimate("HIGH")
            .actionItems(List.of(
                "Write unit tests for core methods",
                "Test edge cases and error conditions",
                "Add integration tests if needed",
                "Verify test coverage meets standards"
            ))
            .affectedEntities(List.of(entity.getEntityId()))
            .expectedImpact("Reduced risk and increased confidence in this component")
            .implementation(Map.of(
                "approach", "Test-driven development for new features",
                "timeEstimate", "6-8 hours"
            ))
            .build();
    }
    
    /**
     * Generates overall improvement recommendations
     */
    private static List<ContextRecommendation> generateOverallRecommendations(
            List<EntityContextAnalysis> entityAnalyses,
            List<ContextGap> contextGaps,
            AnalysisConfiguration config) {
        
        List<ContextRecommendation> recommendations = new ArrayList<>();
        
        // If many entities have similar issues, recommend systematic approach
        int totalEntities = entityAnalyses.size();
        long poorDocEntities = entityAnalyses.stream()
            .mapToLong(entity -> entity.getQualityScores().getDocumentationScore() < 0.5 ? 1 : 0)
            .sum();
        
        if (poorDocEntities > totalEntities * 0.6) {
            recommendations.add(ContextRecommendation.builder()
                .recommendationType("SYSTEMATIC_IMPROVEMENT")
                .title("Implement Documentation Standards")
                .description("Systematic improvement needed across the codebase")
                .priority("HIGH")
                .effortEstimate("HIGH")
                .actionItems(List.of(
                    "Establish documentation standards",
                    "Set up automated documentation checks",
                    "Train team on documentation best practices",
                    "Implement gradual improvement plan"
                ))
                .affectedEntities(entityAnalyses.stream()
                    .map(EntityContextAnalysis::getEntityId)
                    .collect(Collectors.toList()))
                .expectedImpact("Improved code maintainability across entire codebase")
                .implementation(Map.of(
                    "approach", "Phased implementation over 3-6 months",
                    "tools", List.of("Documentation linters", "Standards checkers")
                ))
                .build());
        }
        
        return recommendations;
    }
    
    /**
     * Prioritizes and deduplicates recommendations
     */
    private static List<ContextRecommendation> prioritizeAndDeduplicateRecommendations(
            List<ContextRecommendation> recommendations) {
        
        // Remove duplicates based on title and type
        Map<String, ContextRecommendation> uniqueRecs = new LinkedHashMap<>();
        
        for (ContextRecommendation rec : recommendations) {
            String key = rec.getRecommendationType() + ":" + rec.getTitle();
            if (!uniqueRecs.containsKey(key) || 
                isHigherPriority(rec, uniqueRecs.get(key))) {
                uniqueRecs.put(key, rec);
            }
        }
        
        // Sort by priority and effort
        return uniqueRecs.values().stream()
            .sorted((r1, r2) -> {
                int priorityCompare = comparePriority(r1.getPriority(), r2.getPriority());
                if (priorityCompare != 0) return priorityCompare;
                
                return compareEffort(r1.getEffortEstimate(), r2.getEffortEstimate());
            })
            .limit(15) // Top 15 recommendations
            .collect(Collectors.toList());
    }
    
    private static boolean isHigherPriority(ContextRecommendation rec1, ContextRecommendation rec2) {
        return comparePriority(rec1.getPriority(), rec2.getPriority()) < 0;
    }
    
    private static int comparePriority(String priority1, String priority2) {
        Map<String, Integer> priorityOrder = Map.of("HIGH", 3, "MEDIUM", 2, "LOW", 1);
        return priorityOrder.getOrDefault(priority2, 0).compareTo(priorityOrder.getOrDefault(priority1, 0));
    }
    
    private static int compareEffort(String effort1, String effort2) {
        Map<String, Integer> effortOrder = Map.of("LOW", 1, "MEDIUM", 2, "HIGH", 3);
        return effortOrder.getOrDefault(effort1, 2).compareTo(effortOrder.getOrDefault(effort2, 2));
    }
    
    /**
     * Updates context state for agent reasoning
     */
    private static void updateContextState(
            ToolContext ctx,
            List<String> entityIds,
            OverallQualityAssessment qualityAssessment,
            List<ContextGap> contextGaps) {
        CodeContextEnricherCalculations.updateContextState(ctx, entityIds, qualityAssessment, contextGaps);
    }
    
    /**
     * Determines next actions for the agent
     */
    private static List<String> determineNextActions(
            OverallQualityAssessment qualityAssessment,
            List<ContextGap> contextGaps,
            List<ContextRecommendation> recommendations) {
        return CodeContextEnricherCalculations.determineNextActions(qualityAssessment, contextGaps, recommendations);
    }
    
    /**
     * Calculates entity quality scores across all dimensions
     */
    private static EntityQualityScores calculateEntityQualityScores(
            DocumentationContext documentation,
            UsageContext usage,
            TestContext tests,
            BusinessContext business) {
        return CodeContextEnricherCalculations.calculateEntityQualityScores(documentation, usage, tests, business);
    }
    
    // Helper methods for calculations
    private static double calculateDocumentationCompleteness(List<String> javadoc, List<String> comments, List<String> readme) {
        double score = 0.0;
        if (!javadoc.isEmpty()) score += 0.5;
        if (!comments.isEmpty()) score += 0.3;
        if (!readme.isEmpty()) score += 0.2;
        return Math.min(1.0, score);
    }
    
    private static double calculateDocumentationQuality(List<String> javadoc, List<String> comments) {
        // Assess quality based on content richness, length, and structure
        double quality = 0.0;
        
        for (String doc : javadoc) {
            if (doc.length() > 100) quality += 0.3;  // Substantial content
            if (doc.contains("@param") || doc.contains("@return")) quality += 0.2;  // Structured
        }
        
        for (String comment : comments) {
            if (comment.length() > 50) quality += 0.1;  // Meaningful comments
        }
        
        return Math.min(1.0, quality);
    }
    
    private static double calculateUsageRichness(List<UsageExample> usageExamples) {
        return CodeContextEnricherCalculations.calculateUsageRichness(usageExamples);
    }
    
    private static double calculateUsageDiversity(List<UsageExample> usageExamples) {
        return CodeContextEnricherCalculations.calculateUsageDiversity(usageExamples);
    }
    
    private static String determineTestCategory(String testMethodName) {
        return CodeContextEnricherCalculations.determineTestCategory(testMethodName);
    }
    
    private static double calculateEstimatedCoverage(List<TestReference> testReferences) {
        return CodeContextEnricherCalculations.calculateEstimatedCoverage(testReferences);
    }
    
    private static double calculateTestQuality(List<TestReference> testReferences) {
        return CodeContextEnricherCalculations.calculateTestQuality(testReferences);
    }
    
    private static Set<String> extractDomainConcepts(List<String> businessDescriptions, String entityId) {
        return CodeContextEnricherCalculations.extractDomainConcepts(businessDescriptions, entityId);
    }
    
    private static List<String> extractBusinessRules(List<String> businessDescriptions) {
        return CodeContextEnricherCalculations.extractBusinessRules(businessDescriptions);
    }
    
    private static double calculateBusinessContextRichness(List<String> businessDescriptions, Set<String> domainConcepts, List<String> businessRules) {
        return CodeContextEnricherCalculations.calculateBusinessContextRichness(businessDescriptions, domainConcepts, businessRules);
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
    
    private static String extractPackageName(String entityId) {
        if (entityId.contains(".")) {
            String[] parts = entityId.split("\\.");
            if (parts.length > 2) {
                return String.join(".", Arrays.copyOf(parts, parts.length - 2));
            }
        }
        return "com.example";
    }
    
}