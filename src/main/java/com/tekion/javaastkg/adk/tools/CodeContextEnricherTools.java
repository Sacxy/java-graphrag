package com.tekion.javaastkg.adk.tools;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import com.tekion.javaastkg.adk.core.BaseAdkTool;
import com.tekion.javaastkg.service.Neo4jService;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Code Context Enricher Function Tools - Data gathering tools for the Context Enricher Agent
 * 
 * These tools gather actual context data from our Neo4j knowledge graph:
 * - Documentation from Description and FileDoc nodes
 * - Usage examples from method calls and relationships
 * - Test information from test-related patterns
 * - Business context from LLM-generated descriptions
 * 
 * The tools provide structured data for the LLM agent to reason about context quality.
 */
@Slf4j
public class CodeContextEnricherTools extends BaseAdkTool {

    private static Neo4jService neo4jService;
    
    /**
     * Initialize tools with Neo4j service
     */
    public static void initialize(Neo4jService service) {
        neo4jService = service;
    }

    /**
     * Gathers comprehensive context for entities using real Neo4j data
     */
    @Schema(description = "Gather comprehensive context information for code entities from Neo4j knowledge graph")
    public static Map<String, Object> gatherEntityContext(
            @Schema(description = "Entity IDs to gather context for") List<String> entityIds,
            @Schema(description = "Context types to gather") List<String> contextTypes,
            @Schema(name = "toolContext") ToolContext ctx
    ) {
        try {
            if (neo4jService == null) {
                return errorResponse("gatherEntityContext", "Neo4j service not initialized");
            }
            
            log.info("Gathering context for {} entities with types: {}", entityIds.size(), contextTypes);
            
            List<Map<String, Object>> entityContexts = new ArrayList<>();
            
            for (String entityId : entityIds) {
                Map<String, Object> entityContext = new HashMap<>();
                entityContext.put("entityId", entityId);
                
                // Gather documentation using actual Neo4j data
                if (contextTypes.contains("DOCUMENTATION")) {
                    entityContext.put("documentation", gatherRealDocumentation(entityId));
                }
                
                // Gather usage examples from actual relationships
                if (contextTypes.contains("USAGE_EXAMPLES")) {
                    entityContext.put("usageExamples", gatherRealUsageExamples(entityId));
                }
                
                // Gather test information from patterns
                if (contextTypes.contains("TESTS")) {
                    entityContext.put("tests", gatherRealTestInformation(entityId));
                }
                
                // Gather business context from descriptions
                if (contextTypes.contains("BUSINESS_CONTEXT")) {
                    entityContext.put("businessContext", gatherRealBusinessContext(entityId));
                }
                
                // Gather related entities from graph relationships
                entityContext.put("relatedEntities", gatherRealRelatedEntities(entityId));
                
                entityContexts.add(entityContext);
            }
            
            return Map.of(
                "status", "success",
                "entityContexts", entityContexts,
                "totalEntities", entityIds.size(),
                "contextTypesGathered", contextTypes
            );
            
        } catch (Exception e) {
            log.error("Failed to gather entity context for: {}", entityIds, e);
            return errorResponse("gatherEntityContext", "Context gathering failed: " + e.getMessage());
        }
    }
    
    /**
     * Gathers real documentation from Description and FileDoc nodes
     */
    private static Map<String, Object> gatherRealDocumentation(String entityId) {
        Map<String, Object> documentation = new HashMap<>();
        
        // Query for Description nodes linked to this entity
        String descriptionQuery = """
            MATCH (entity {id: $entityId})-[:HAS_DESCRIPTION]->(desc:Description)
            RETURN desc.content as content, desc.type as type, desc.createdAt as createdAt
            """;
        
        List<Map<String, Object>> descriptions = neo4jService.executeCypherQuery(descriptionQuery, Map.of("entityId", entityId));
        
        List<String> javadocContent = new ArrayList<>();
        List<String> inlineComments = new ArrayList<>();
        
        for (Map<String, Object> desc : descriptions) {
            String content = (String) desc.get("content");
            String type = (String) desc.get("type");
            
            if ("llm_generated".equals(type)) {
                javadocContent.add(content);
            } else {
                inlineComments.add(content);
            }
        }
        
        // Query for FileDoc nodes related to this entity's package
        String fileDocQuery = """
            MATCH (entity {id: $entityId})
            MATCH (fileDoc:FileDoc)
            WHERE entity.packageName = fileDoc.packageName OR fileDoc.fileName CONTAINS entity.className
            RETURN fileDoc.content as content, fileDoc.fileName as fileName
            LIMIT 5
            """;
        
        List<Map<String, Object>> fileDocs = neo4jService.executeCypherQuery(fileDocQuery, Map.of("entityId", entityId));
        
        List<String> readmeContent = new ArrayList<>();
        for (Map<String, Object> fileDoc : fileDocs) {
            String content = (String) fileDoc.get("content");
            String fileName = (String) fileDoc.get("fileName");
            
            if (fileName != null && fileName.toLowerCase().contains("readme")) {
                readmeContent.add(content);
            }
        }
        
        documentation.put("javadocContent", javadocContent);
        documentation.put("inlineComments", inlineComments);
        documentation.put("readmeContent", readmeContent);
        documentation.put("hasDocumentation", !javadocContent.isEmpty() || !inlineComments.isEmpty());
        documentation.put("documentationScore", calculateDocumentationScore(javadocContent, inlineComments, readmeContent));
        
        return documentation;
    }
    
    /**
     * Gathers real usage examples from method calls and relationships
     */
    private static Map<String, Object> gatherRealUsageExamples(String entityId) {
        Map<String, Object> usage = new HashMap<>();
        
        // Find methods that call this entity
        String callerQuery = """
            MATCH (caller)-[:CALLS]->(entity {id: $entityId})
            RETURN caller.id as callerId, caller.name as callerName, caller.className as callerClass
            LIMIT 10
            """;
        
        List<Map<String, Object>> callers = neo4jService.executeCypherQuery(callerQuery, Map.of("entityId", entityId));
        
        // Find methods this entity calls
        String calleeQuery = """
            MATCH (entity {id: $entityId})-[:CALLS]->(callee)
            RETURN callee.id as calleeId, callee.name as calleeName, callee.className as calleeClass
            LIMIT 10
            """;
        
        List<Map<String, Object>> callees = neo4jService.executeCypherQuery(calleeQuery, Map.of("entityId", entityId));
        
        // Find usage patterns through field access
        String fieldUsageQuery = """
            MATCH (entity {id: $entityId})-[:USES_FIELD]->(field)
            RETURN field.id as fieldId, field.name as fieldName, field.type as fieldType
            LIMIT 5
            """;
        
        List<Map<String, Object>> fieldUsage = neo4jService.executeCypherQuery(fieldUsageQuery, Map.of("entityId", entityId));
        
        List<Map<String, Object>> usageExamples = new ArrayList<>();
        
        // Create usage examples from callers
        for (Map<String, Object> caller : callers) {
            usageExamples.add(Map.of(
                "type", "method_call",
                "source", caller.get("callerClass"),
                "example", caller.get("callerName") + "() calls " + extractMethodName(entityId) + "()",
                "context", "Called by " + caller.get("callerClass")
            ));
        }
        
        // Create usage examples from callees
        for (Map<String, Object> callee : callees) {
            usageExamples.add(Map.of(
                "type", "calls_method",
                "source", callee.get("calleeClass"),
                "example", extractMethodName(entityId) + "() calls " + callee.get("calleeName") + "()",
                "context", "Calls " + callee.get("calleeClass")
            ));
        }
        
        // Create usage examples from field access
        for (Map<String, Object> field : fieldUsage) {
            usageExamples.add(Map.of(
                "type", "field_access",
                "source", "field_usage",
                "example", "Uses field: " + field.get("fieldName"),
                "context", "Field of type: " + field.get("fieldType")
            ));
        }
        
        usage.put("usageExamples", usageExamples);
        usage.put("hasUsageExamples", !usageExamples.isEmpty());
        usage.put("usageCount", usageExamples.size());
        usage.put("callerCount", callers.size());
        usage.put("calleeCount", callees.size());
        
        return usage;
    }
    
    /**
     * Gathers test information from naming patterns and relationships
     */
    private static Map<String, Object> gatherRealTestInformation(String entityId) {
        Map<String, Object> testInfo = new HashMap<>();
        
        String className = extractClassName(entityId);
        String methodName = extractMethodName(entityId);
        
        // Look for test classes that might test this entity
        String testClassQuery = """
            MATCH (testClass:Class)
            WHERE testClass.name CONTAINS 'Test' 
            AND (testClass.name CONTAINS $className OR testClass.packageName = $packageName)
            RETURN testClass.name as testClassName, testClass.packageName as packageName
            LIMIT 5
            """;
        
        Map<String, Object> params = Map.of(
            "className", className,
            "packageName", extractPackageName(entityId)
        );
        
        List<Map<String, Object>> testClasses = neo4jService.executeCypherQuery(testClassQuery, params);
        
        // Look for test methods that might test this entity
        String testMethodQuery = """
            MATCH (testMethod:Method)
            WHERE testMethod.name CONTAINS 'test' 
            AND (testMethod.name CONTAINS $methodName OR testMethod.className CONTAINS $className)
            RETURN testMethod.name as testMethodName, testMethod.className as testClassName
            LIMIT 10
            """;
        
        Map<String, Object> methodParams = Map.of(
            "methodName", methodName,
            "className", className
        );
        
        List<Map<String, Object>> testMethods = neo4jService.executeCypherQuery(testMethodQuery, methodParams);
        
        List<Map<String, Object>> testReferences = new ArrayList<>();
        
        // Create test references from test classes
        for (Map<String, Object> testClass : testClasses) {
            testReferences.add(Map.of(
                "testType", "class",
                "testName", testClass.get("testClassName"),
                "testCategory", "unit",
                "packageName", testClass.get("packageName")
            ));
        }
        
        // Create test references from test methods
        for (Map<String, Object> testMethod : testMethods) {
            testReferences.add(Map.of(
                "testType", "method",
                "testName", testMethod.get("testMethodName"),
                "testClass", testMethod.get("testClassName"),
                "testCategory", determineTestCategory((String) testMethod.get("testMethodName"))
            ));
        }
        
        testInfo.put("testReferences", testReferences);
        testInfo.put("hasTests", !testReferences.isEmpty());
        testInfo.put("testClassCount", testClasses.size());
        testInfo.put("testMethodCount", testMethods.size());
        testInfo.put("estimatedCoverage", calculateEstimatedCoverage(testReferences.size(), entityId));
        
        return testInfo;
    }
    
    /**
     * Gathers business context from LLM-generated descriptions
     */
    private static Map<String, Object> gatherRealBusinessContext(String entityId) {
        Map<String, Object> businessContext = new HashMap<>();
        
        // Get LLM-generated descriptions that contain business context
        String businessQuery = """
            MATCH (entity {id: $entityId})-[:HAS_DESCRIPTION]->(desc:Description)
            WHERE desc.type = 'llm_generated'
            RETURN desc.content as content
            """;
        
        List<Map<String, Object>> descriptions = neo4jService.executeCypherQuery(businessQuery, Map.of("entityId", entityId));
        
        List<String> businessDescriptions = new ArrayList<>();
        Set<String> domainConcepts = new HashSet<>();
        List<String> businessRules = new ArrayList<>();
        
        for (Map<String, Object> desc : descriptions) {
            String content = (String) desc.get("content");
            businessDescriptions.add(content);
            
            // Extract domain concepts from description
            domainConcepts.addAll(extractDomainConcepts(content, entityId));
            
            // Extract business rules from description
            businessRules.addAll(extractBusinessRules(content));
        }
        
        businessContext.put("businessDescriptions", businessDescriptions);
        businessContext.put("domainConcepts", new ArrayList<>(domainConcepts));
        businessContext.put("businessRules", businessRules);
        businessContext.put("hasBusinessContext", !businessDescriptions.isEmpty());
        businessContext.put("businessContextScore", calculateBusinessContextScore(businessDescriptions, domainConcepts, businessRules));
        
        return businessContext;
    }
    
    /**
     * Gathers related entities from actual graph relationships
     */
    private static List<Map<String, Object>> gatherRealRelatedEntities(String entityId) {
        String relatedQuery = """
            MATCH (entity {id: $entityId})-[r]-(related)
            WHERE type(r) IN ['CONTAINS', 'EXTENDS', 'IMPLEMENTS', 'CALLS', 'USES_FIELD', 'HAS_FIELD']
            RETURN related.id as relatedId, related.name as relatedName, type(r) as relationshipType, 
                   labels(related) as labels
            LIMIT 20
            """;
        
        List<Map<String, Object>> relatedEntities = neo4jService.executeCypherQuery(relatedQuery, Map.of("entityId", entityId));
        
        return relatedEntities.stream()
            .map(entity -> Map.of(
                "entityId", entity.get("relatedId"),
                "entityName", entity.get("relatedName"),
                "relationshipType", entity.get("relationshipType"),
                "entityType", entity.get("labels")
            ))
            .collect(Collectors.toList());
    }
    
    /**
     * Assesses context quality using intelligent metrics
     */
    @Schema(description = "Assess the quality of gathered context using intelligent analysis")
    public static Map<String, Object> assessContextQuality(
            @Schema(description = "Entity contexts to assess") List<Map<String, Object>> entityContexts,
            @Schema(description = "Quality criteria and thresholds") Map<String, Object> qualityCriteria,
            @Schema(name = "toolContext") ToolContext ctx
    ) {
        try {
            log.info("Assessing context quality for {} entities", entityContexts.size());
            
            List<Map<String, Object>> qualityAssessments = new ArrayList<>();
            double totalScore = 0.0;
            
            for (Map<String, Object> entityContext : entityContexts) {
                Map<String, Object> assessment = assessSingleEntityQuality(entityContext, qualityCriteria);
                qualityAssessments.add(assessment);
                totalScore += (Double) assessment.get("overallScore");
            }
            
            double averageScore = totalScore / entityContexts.size();
            String overallGrade = calculateQualityGrade(averageScore);
            
            return Map.of(
                "status", "success",
                "qualityAssessments", qualityAssessments,
                "overallScore", averageScore,
                "overallGrade", overallGrade,
                "totalEntities", entityContexts.size(),
                "qualityMetrics", generateQualityMetrics(qualityAssessments)
            );
            
        } catch (Exception e) {
            log.error("Failed to assess context quality", e);
            return errorResponse("assessContextQuality", "Quality assessment failed: " + e.getMessage());
        }
    }
    
    /**
     * Identifies context gaps using pattern analysis
     */
    @Schema(description = "Identify gaps in context coverage and quality")
    public static Map<String, Object> identifyContextGaps(
            @Schema(description = "Quality assessments from context analysis") List<Map<String, Object>> qualityAssessments,
            @Schema(description = "Expected context standards") Map<String, Object> contextStandards,
            @Schema(name = "toolContext") ToolContext ctx
    ) {
        try {
            log.info("Identifying context gaps for {} assessments", qualityAssessments.size());
            
            List<Map<String, Object>> gaps = new ArrayList<>();
            Map<String, Integer> gapCounts = new HashMap<>();
            
            for (Map<String, Object> assessment : qualityAssessments) {
                List<Map<String, Object>> entityGaps = identifyEntityGaps(assessment, contextStandards);
                gaps.addAll(entityGaps);
                
                // Count gap types
                for (Map<String, Object> gap : entityGaps) {
                    String gapType = (String) gap.get("gapType");
                    gapCounts.merge(gapType, 1, Integer::sum);
                }
            }
            
            return Map.of(
                "status", "success",
                "contextGaps", gaps,
                "gapSummary", gapCounts,
                "totalGaps", gaps.size(),
                "entitiesAffected", qualityAssessments.size(),
                "gapPriorities", prioritizeGaps(gaps)
            );
            
        } catch (Exception e) {
            log.error("Failed to identify context gaps", e);
            return errorResponse("identifyContextGaps", "Gap identification failed: " + e.getMessage());
        }
    }
    
    /**
     * Generates intelligent recommendations for context improvement
     */
    @Schema(description = "Generate actionable recommendations for improving code context")
    public static Map<String, Object> generateRecommendations(
            @Schema(description = "Identified context gaps") List<Map<String, Object>> contextGaps,
            @Schema(description = "Quality assessments") List<Map<String, Object>> qualityAssessments,
            @Schema(description = "Recommendation preferences") Map<String, Object> preferences,
            @Schema(name = "toolContext") ToolContext ctx
    ) {
        try {
            log.info("Generating recommendations for {} gaps and {} assessments", 
                    contextGaps.size(), qualityAssessments.size());
            
            List<Map<String, Object>> recommendations = new ArrayList<>();
            
            // Generate gap-based recommendations
            for (Map<String, Object> gap : contextGaps) {
                Map<String, Object> recommendation = generateGapRecommendation(gap, preferences);
                if (recommendation != null) {
                    recommendations.add(recommendation);
                }
            }
            
            // Generate quality-based recommendations
            for (Map<String, Object> assessment : qualityAssessments) {
                List<Map<String, Object>> qualityRecs = generateQualityRecommendations(assessment, preferences);
                recommendations.addAll(qualityRecs);
            }
            
            // Prioritize and deduplicate recommendations
            List<Map<String, Object>> prioritizedRecs = prioritizeRecommendations(recommendations);
            
            return Map.of(
                "status", "success",
                "recommendations", prioritizedRecs,
                "totalRecommendations", recommendations.size(),
                "prioritizedCount", prioritizedRecs.size(),
                "recommendationCategories", categorizeRecommendations(prioritizedRecs)
            );
            
        } catch (Exception e) {
            log.error("Failed to generate recommendations", e);
            return errorResponse("generateRecommendations", "Recommendation generation failed: " + e.getMessage());
        }
    }
    
    // Helper methods for data processing
    
    private static double calculateDocumentationScore(List<String> javadoc, List<String> comments, List<String> readme) {
        double score = 0.0;
        if (!javadoc.isEmpty()) score += 0.5;
        if (!comments.isEmpty()) score += 0.3;
        if (!readme.isEmpty()) score += 0.2;
        return Math.min(1.0, score);
    }
    
    private static String determineTestCategory(String testMethodName) {
        if (testMethodName.toLowerCase().contains("integration")) return "integration";
        if (testMethodName.toLowerCase().contains("unit")) return "unit";
        return "unit"; // default
    }
    
    private static double calculateEstimatedCoverage(int testCount, String entityId) {
        // Rough estimation based on test count
        if (testCount == 0) return 0.0;
        if (testCount >= 3) return 0.8;
        if (testCount >= 2) return 0.6;
        return 0.3;
    }
    
    private static Set<String> extractDomainConcepts(String content, String entityId) {
        Set<String> concepts = new HashSet<>();
        
        // Extract domain concepts from content
        if (content.toLowerCase().contains("payment")) concepts.add("Payment");
        if (content.toLowerCase().contains("user")) concepts.add("User");
        if (content.toLowerCase().contains("order")) concepts.add("Order");
        if (content.toLowerCase().contains("transaction")) concepts.add("Transaction");
        if (content.toLowerCase().contains("account")) concepts.add("Account");
        
        // Add entity class name as a concept
        concepts.add(extractClassName(entityId));
        
        return concepts;
    }
    
    private static List<String> extractBusinessRules(String content) {
        List<String> rules = new ArrayList<>();
        
        // Extract business rules from content patterns
        if (content.toLowerCase().contains("must")) {
            String[] sentences = content.split("\\.");
            for (String sentence : sentences) {
                if (sentence.toLowerCase().contains("must")) {
                    rules.add(sentence.trim());
                }
            }
        }
        
        return rules;
    }
    
    private static double calculateBusinessContextScore(List<String> descriptions, Set<String> concepts, List<String> rules) {
        double score = 0.0;
        if (!descriptions.isEmpty()) score += 0.4;
        if (!concepts.isEmpty()) score += 0.3;
        if (!rules.isEmpty()) score += 0.3;
        return Math.min(1.0, score);
    }
    
    private static Map<String, Object> assessSingleEntityQuality(Map<String, Object> entityContext, Map<String, Object> criteria) {
        String entityId = (String) entityContext.get("entityId");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> documentation = (Map<String, Object>) entityContext.get("documentation");
        @SuppressWarnings("unchecked")
        Map<String, Object> usage = (Map<String, Object>) entityContext.get("usageExamples");
        @SuppressWarnings("unchecked")
        Map<String, Object> tests = (Map<String, Object>) entityContext.get("tests");
        @SuppressWarnings("unchecked")
        Map<String, Object> business = (Map<String, Object>) entityContext.get("businessContext");
        
        double docScore = documentation != null ? (Double) documentation.getOrDefault("documentationScore", 0.0) : 0.0;
        double usageScore = usage != null ? Math.min(1.0, (Integer) usage.getOrDefault("usageCount", 0) * 0.2) : 0.0;
        double testScore = tests != null ? (Double) tests.getOrDefault("estimatedCoverage", 0.0) : 0.0;
        double businessScore = business != null ? (Double) business.getOrDefault("businessContextScore", 0.0) : 0.0;
        
        double overallScore = (docScore * 0.3) + (usageScore * 0.25) + (testScore * 0.25) + (businessScore * 0.2);
        
        return Map.of(
            "entityId", entityId,
            "overallScore", overallScore,
            "documentationScore", docScore,
            "usageScore", usageScore,
            "testScore", testScore,
            "businessScore", businessScore,
            "grade", calculateQualityGrade(overallScore)
        );
    }
    
    private static String calculateQualityGrade(double score) {
        if (score >= 0.9) return "A";
        if (score >= 0.8) return "B";
        if (score >= 0.7) return "C";
        if (score >= 0.6) return "D";
        return "F";
    }
    
    private static Map<String, Object> generateQualityMetrics(List<Map<String, Object>> assessments) {
        Map<String, Integer> gradeCounts = new HashMap<>();
        double avgDoc = 0.0, avgUsage = 0.0, avgTest = 0.0, avgBusiness = 0.0;
        
        for (Map<String, Object> assessment : assessments) {
            String grade = (String) assessment.get("grade");
            gradeCounts.merge(grade, 1, Integer::sum);
            
            avgDoc += (Double) assessment.get("documentationScore");
            avgUsage += (Double) assessment.get("usageScore");
            avgTest += (Double) assessment.get("testScore");
            avgBusiness += (Double) assessment.get("businessScore");
        }
        
        int size = assessments.size();
        return Map.of(
            "gradeDistribution", gradeCounts,
            "averageDocumentationScore", avgDoc / size,
            "averageUsageScore", avgUsage / size,
            "averageTestScore", avgTest / size,
            "averageBusinessScore", avgBusiness / size
        );
    }
    
    private static List<Map<String, Object>> identifyEntityGaps(Map<String, Object> assessment, Map<String, Object> standards) {
        List<Map<String, Object>> gaps = new ArrayList<>();
        String entityId = (String) assessment.get("entityId");
        
        if ((Double) assessment.get("documentationScore") < 0.3) {
            gaps.add(Map.of(
                "gapType", "MISSING_DOCUMENTATION",
                "entityId", entityId,
                "severity", "HIGH",
                "currentScore", assessment.get("documentationScore")
            ));
        }
        
        if ((Double) assessment.get("testScore") < 0.3) {
            gaps.add(Map.of(
                "gapType", "INSUFFICIENT_TESTING",
                "entityId", entityId,
                "severity", "MEDIUM",
                "currentScore", assessment.get("testScore")
            ));
        }
        
        if ((Double) assessment.get("usageScore") < 0.2) {
            gaps.add(Map.of(
                "gapType", "MISSING_USAGE_EXAMPLES",
                "entityId", entityId,
                "severity", "MEDIUM",
                "currentScore", assessment.get("usageScore")
            ));
        }
        
        if ((Double) assessment.get("businessScore") < 0.2) {
            gaps.add(Map.of(
                "gapType", "MISSING_BUSINESS_CONTEXT",
                "entityId", entityId,
                "severity", "LOW",
                "currentScore", assessment.get("businessScore")
            ));
        }
        
        return gaps;
    }
    
    private static List<Map<String, Object>> prioritizeGaps(List<Map<String, Object>> gaps) {
        return gaps.stream()
            .sorted((g1, g2) -> {
                String sev1 = (String) g1.get("severity");
                String sev2 = (String) g2.get("severity");
                return compareSeverity(sev2, sev1); // Reverse for high-to-low
            })
            .collect(Collectors.toList());
    }
    
    private static int compareSeverity(String sev1, String sev2) {
        Map<String, Integer> severityOrder = Map.of("HIGH", 3, "MEDIUM", 2, "LOW", 1);
        return severityOrder.getOrDefault(sev1, 0).compareTo(severityOrder.getOrDefault(sev2, 0));
    }
    
    private static Map<String, Object> generateGapRecommendation(Map<String, Object> gap, Map<String, Object> preferences) {
        String gapType = (String) gap.get("gapType");
        String entityId = (String) gap.get("entityId");
        String severity = (String) gap.get("severity");
        
        switch (gapType) {
            case "MISSING_DOCUMENTATION":
                return Map.of(
                    "type", "DOCUMENTATION_IMPROVEMENT",
                    "priority", severity,
                    "entityId", entityId,
                    "title", "Add comprehensive documentation",
                    "description", "Entity lacks adequate documentation",
                    "actionItems", Arrays.asList("Add Javadoc comments", "Create usage examples", "Document parameters and returns"),
                    "estimatedEffort", "MEDIUM"
                );
            case "INSUFFICIENT_TESTING":
                return Map.of(
                    "type", "TEST_IMPROVEMENT",
                    "priority", severity,
                    "entityId", entityId,
                    "title", "Improve test coverage",
                    "description", "Entity has insufficient test coverage",
                    "actionItems", Arrays.asList("Write unit tests", "Add integration tests", "Test edge cases"),
                    "estimatedEffort", "HIGH"
                );
            case "MISSING_USAGE_EXAMPLES":
                return Map.of(
                    "type", "USAGE_IMPROVEMENT",
                    "priority", severity,
                    "entityId", entityId,
                    "title", "Add usage examples",
                    "description", "Entity lacks clear usage examples",
                    "actionItems", Arrays.asList("Create code examples", "Add to documentation", "Show common use cases"),
                    "estimatedEffort", "LOW"
                );
            default:
                return null;
        }
    }
    
    private static List<Map<String, Object>> generateQualityRecommendations(Map<String, Object> assessment, Map<String, Object> preferences) {
        List<Map<String, Object>> recommendations = new ArrayList<>();
        
        String grade = (String) assessment.get("grade");
        if ("A".equals(grade)) {
            recommendations.add(Map.of(
                "type", "MAINTENANCE",
                "priority", "LOW",
                "title", "Maintain excellent quality",
                "description", "Context quality is excellent - maintain current standards"
            ));
        }
        
        return recommendations;
    }
    
    private static List<Map<String, Object>> prioritizeRecommendations(List<Map<String, Object>> recommendations) {
        return recommendations.stream()
            .sorted((r1, r2) -> {
                String priority1 = (String) r1.get("priority");
                String priority2 = (String) r2.get("priority");
                return compareSeverity(priority2, priority1);
            })
            .limit(10) // Top 10 recommendations
            .collect(Collectors.toList());
    }
    
    private static Map<String, Long> categorizeRecommendations(List<Map<String, Object>> recommendations) {
        return recommendations.stream()
            .collect(Collectors.groupingBy(
                rec -> (String) rec.get("type"),
                Collectors.counting()
            ));
    }
    
    // Helper methods for entity extraction
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