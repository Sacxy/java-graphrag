package com.tekion.javaastkg.agents.tools;

import com.tekion.javaastkg.agents.models.*;
import com.tekion.javaastkg.service.Neo4jService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 📖 Flow Narrative Generator Tool
 * 
 * This tool specializes in generating human-readable narratives from code analysis:
 * - Creates comprehensive flow explanations
 * - Generates business process documentation
 * - Builds user journey mappings
 * - Produces architectural overviews
 * 
 * Domain Focus: GENERATING human-readable narratives from technical analysis
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FlowNarrativeGenerator {
    
    private final Neo4jService neo4jService;
    
    /**
     * 📖 Generate flow narrative from code analysis
     * 
     * @param request The narrative generation request
     * @return Comprehensive narrative generation results
     */
    public NarrativeGenerationResult generateNarrative(NarrativeGenerationRequest request) {
        
        log.debug("📖 Starting narrative generation: type={}, audience={}, entities={}", 
            request.getNarrativeType(), request.getAudience(), request.getTargetEntities().size());
        
        try {
            // Step 1: Initialize generation context
            NarrativeGenerationContext context = initializeGenerationContext(request);
            
            // Step 2: Analyze source entities
            analyzeSourceEntities(context);
            
            // Step 3: Generate primary narrative
            FlowNarrative primaryNarrative = generatePrimaryNarrative(context);
            
            // Step 4: Generate alternative narratives
            List<FlowNarrative> alternativeNarratives = generateAlternativeNarratives(context);
            
            // Step 5: Assess generation quality
            NarrativeGenerationResult.GenerationQualityAssessment qualityAssessment = 
                assessGenerationQuality(primaryNarrative, alternativeNarratives, context);
            
            // Step 6: Generate adaptation suggestions
            List<NarrativeGenerationResult.NarrativeAdaptation> adaptationSuggestions = 
                generateAdaptationSuggestions(primaryNarrative, context);
            
            // Step 7: Identify content improvements
            List<NarrativeGenerationResult.ContentImprovement> contentImprovements = 
                identifyContentImprovements(primaryNarrative, qualityAssessment, context);
            
            // Step 8: Calculate engagement metrics
            NarrativeGenerationResult.AudienceEngagementMetrics engagementMetrics = 
                calculateEngagementMetrics(primaryNarrative, context);
            
            // Step 9: Build source analysis summary
            NarrativeGenerationResult.SourceAnalysisSummary sourceAnalysis = 
                buildSourceAnalysisSummary(context);
            
            // Step 10: Build final result
            return NarrativeGenerationResult.builder()
                .primaryNarrative(primaryNarrative)
                .alternativeNarratives(alternativeNarratives)
                .qualityAssessment(qualityAssessment)
                .adaptationSuggestions(adaptationSuggestions)
                .contentImprovements(contentImprovements)
                .engagementMetrics(engagementMetrics)
                .sourceAnalysis(sourceAnalysis)
                .confidence(calculateGenerationConfidence(context, primaryNarrative))
                .generationSummary(buildGenerationSummary(context, primaryNarrative))
                .metadata(Map.of(
                    "generationTimeMs", System.currentTimeMillis() - context.getStartTime(),
                    "successful", true,
                    "timestamp", LocalDateTime.now().toString(),
                    "narrativeType", request.getNarrativeType().name(),
                    "audience", request.getAudience().name()
                ))
                .build();
                
        } catch (Exception e) {
            log.error("❌ Narrative generation failed", e);
            
            return NarrativeGenerationResult.error("Narrative generation failed: " + e.getMessage());
        }
    }
    
    /**
     * 🎯 Initialize generation context
     */
    private NarrativeGenerationContext initializeGenerationContext(NarrativeGenerationRequest request) {
        
        return NarrativeGenerationContext.builder()
            .startTime(System.currentTimeMillis())
            .request(request)
            .analyzedEntities(new HashMap<>())
            .discoveredFlows(new ArrayList<>())
            .businessContext(new HashMap<>())
            .technicalPatterns(new ArrayList<>())
            .narrativeElements(new ArrayList<>())
            .qualityFactors(new HashMap<>())
            .build();
    }
    
    /**
     * 🔍 Analyze source entities for narrative generation
     */
    private void analyzeSourceEntities(NarrativeGenerationContext context) {
        
        log.debug("🔍 Analyzing {} source entities", context.getRequest().getTargetEntities().size());
        
        for (String entityId : context.getRequest().getTargetEntities()) {
            try {
                // Get entity information
                Map<String, Object> entityInfo = getEntityInformation(entityId);
                
                // Analyze business context
                Map<String, Object> businessContext = analyzeBusinessContext(entityId, entityInfo);
                
                // Analyze technical patterns
                List<String> technicalPatterns = analyzeTechnicalPatterns(entityId, entityInfo);
                
                // Store analysis results
                context.getAnalyzedEntities().put(entityId, entityInfo);
                context.getBusinessContext().putAll(businessContext);
                context.getTechnicalPatterns().addAll(technicalPatterns);
                
            } catch (Exception e) {
                log.warn("⚠️ Entity analysis failed for {}: {}", entityId, e.getMessage());
            }
        }
        
        log.debug("🔍 Analyzed {} entities successfully", context.getAnalyzedEntities().size());
    }
    
    /**
     * 📖 Generate primary narrative
     */
    private FlowNarrative generatePrimaryNarrative(NarrativeGenerationContext context) {
        
        log.debug("📖 Generating primary narrative");
        
        NarrativeGenerationRequest.NarrativeType narrativeType = context.getRequest().getNarrativeType();
        
        return switch (narrativeType) {
            case FLOW_EXPLANATION -> generateFlowExplanation(context);
            case BUSINESS_PROCESS -> generateBusinessProcessNarrative(context);
            case USER_JOURNEY -> generateUserJourneyNarrative(context);
            case PROBLEM_ANALYSIS -> generateProblemAnalysisNarrative(context);
            case PERFORMANCE_ANALYSIS -> generatePerformanceAnalysisNarrative(context);
            case ARCHITECTURE_OVERVIEW -> generateArchitectureOverviewNarrative(context);
            case COMPREHENSIVE_DOCUMENTATION -> generateComprehensiveDocumentation(context);
        };
    }
    
    /**
     * 🔄 Generate flow explanation narrative
     */
    private FlowNarrative generateFlowExplanation(NarrativeGenerationContext context) {
        
        log.debug("🔄 Generating flow explanation narrative");
        
        // Build narrative sections
        List<FlowNarrative.NarrativeSection> sections = new ArrayList<>();
        
        // Overview section
        sections.add(FlowNarrative.NarrativeSection.builder()
            .sectionTitle("Overview")
            .content(generateOverviewContent(context))
            .type(FlowNarrative.NarrativeSection.SectionType.OVERVIEW)
            .order(1)
            .build());
        
        // Flow description section
        sections.add(FlowNarrative.NarrativeSection.builder()
            .sectionTitle("Flow Description")
            .content(generateFlowDescriptionContent(context))
            .type(FlowNarrative.NarrativeSection.SectionType.FLOW_DESCRIPTION)
            .order(2)
            .codeExamples(generateCodeExamples(context))
            .build());
        
        // Technical details section
        sections.add(FlowNarrative.NarrativeSection.builder()
            .sectionTitle("Technical Implementation")
            .content(generateTechnicalDetailsContent(context))
            .type(FlowNarrative.NarrativeSection.SectionType.TECHNICAL_DETAILS)
            .order(3)
            .build());
        
        // Performance analysis section
        if (context.getRequest().getFocusAreas() != null && 
            context.getRequest().getFocusAreas().contains(NarrativeGenerationRequest.NarrativeFocus.PERFORMANCE_CHARACTERISTICS)) {
            sections.add(FlowNarrative.NarrativeSection.builder()
                .sectionTitle("Performance Analysis")
                .content(generatePerformanceContent(context))
                .type(FlowNarrative.NarrativeSection.SectionType.PERFORMANCE_ANALYSIS)
                .order(4)
                .build());
        }
        
        // Recommendations section
        if (context.getRequest().isIncludeRecommendations()) {
            sections.add(FlowNarrative.NarrativeSection.builder()
                .sectionTitle("Recommendations")
                .content(generateRecommendationsContent(context))
                .type(FlowNarrative.NarrativeSection.SectionType.RECOMMENDATIONS)
                .order(5)
                .build());
        }
        
        // Generate key insights
        List<String> keyInsights = generateKeyInsights(context);
        
        // Generate recommendations
        List<FlowNarrative.NarrativeRecommendation> recommendations = generateNarrativeRecommendations(context);
        
        // Calculate quality metrics
        FlowNarrative.NarrativeQuality quality = calculateNarrativeQuality(sections, keyInsights, recommendations);
        
        return FlowNarrative.builder()
            .title(generateNarrativeTitle(context))
            .executiveSummary(generateExecutiveSummary(context))
            .sections(sections)
            .keyInsights(keyInsights)
            .recommendations(recommendations)
            .quality(quality)
            .targetAudience(context.getRequest().getAudience().getDescription())
            .tags(generateNarrativeTags(context))
            .build();
    }
    
    /**
     * 🏢 Generate business process narrative
     */
    private FlowNarrative generateBusinessProcessNarrative(NarrativeGenerationContext context) {
        
        log.debug("🏢 Generating business process narrative");
        
        // For now, use flow explanation as base and adapt for business audience
        FlowNarrative baseNarrative = generateFlowExplanation(context);
        
        // Adapt content for business audience
        List<FlowNarrative.NarrativeSection> businessSections = baseNarrative.getSections().stream()
            .map(section -> adaptSectionForBusiness(section, context))
            .collect(Collectors.toList());
        
        return baseNarrative.toBuilder()
            .title("Business Process: " + generateNarrativeTitle(context))
            .sections(businessSections)
            .build();
    }
    
    /**
     * 🎯 Generate user journey narrative
     */
    private FlowNarrative generateUserJourneyNarrative(NarrativeGenerationContext context) {
        
        log.debug("🎯 Generating user journey narrative");
        
        // For now, use flow explanation as base and adapt for user journey
        FlowNarrative baseNarrative = generateFlowExplanation(context);
        
        return baseNarrative.toBuilder()
            .title("User Journey: " + generateNarrativeTitle(context))
            .executiveSummary(generateUserJourneyExecutiveSummary(context))
            .build();
    }
    
    /**
     * 🔍 Generate problem analysis narrative
     */
    private FlowNarrative generateProblemAnalysisNarrative(NarrativeGenerationContext context) {
        
        log.debug("🔍 Generating problem analysis narrative");
        
        // For now, use flow explanation as base
        return generateFlowExplanation(context);
    }
    
    /**
     * 📊 Generate performance analysis narrative
     */
    private FlowNarrative generatePerformanceAnalysisNarrative(NarrativeGenerationContext context) {
        
        log.debug("📊 Generating performance analysis narrative");
        
        // For now, use flow explanation as base
        return generateFlowExplanation(context);
    }
    
    /**
     * 🎨 Generate architecture overview narrative
     */
    private FlowNarrative generateArchitectureOverviewNarrative(NarrativeGenerationContext context) {
        
        log.debug("🎨 Generating architecture overview narrative");
        
        // For now, use flow explanation as base
        return generateFlowExplanation(context);
    }
    
    /**
     * 📚 Generate comprehensive documentation
     */
    private FlowNarrative generateComprehensiveDocumentation(NarrativeGenerationContext context) {
        
        log.debug("📚 Generating comprehensive documentation");
        
        // For now, use flow explanation as base
        return generateFlowExplanation(context);
    }
    
    /**
     * 🎲 Generate alternative narratives
     */
    private List<FlowNarrative> generateAlternativeNarratives(NarrativeGenerationContext context) {
        
        // For now, return empty list - in real implementation, would generate alternatives
        return List.of();
    }
    
    // Helper methods for narrative generation (mock implementations)
    
    private Map<String, Object> getEntityInformation(String entityId) {
        // TODO: Query Neo4j for entity information
        return Map.of(
            "id", entityId,
            "name", extractEntityName(entityId),
            "type", "CLASS"
        );
    }
    
    private Map<String, Object> analyzeBusinessContext(String entityId, Map<String, Object> entityInfo) {
        // TODO: Analyze business context
        return Map.of(
            "businessDomain", "Application Services",
            "businessValue", "Core functionality"
        );
    }
    
    private List<String> analyzeTechnicalPatterns(String entityId, Map<String, Object> entityInfo) {
        // TODO: Analyze technical patterns
        return List.of("Service Layer", "Dependency Injection");
    }
    
    private String generateNarrativeTitle(NarrativeGenerationContext context) {
        String baseTitle = "Code Flow Analysis";
        
        if (context.getRequest().getTargetEntities().size() == 1) {
            String entityName = extractEntityName(context.getRequest().getTargetEntities().get(0));
            return baseTitle + ": " + entityName;
        }
        
        return baseTitle + ": " + context.getRequest().getTargetEntities().size() + " Components";
    }
    
    private String generateExecutiveSummary(NarrativeGenerationContext context) {
        return String.format("This narrative analyzes the flow and implementation of %d code entities, " +
            "providing insights into their business purpose, technical implementation, and usage patterns.",
            context.getAnalyzedEntities().size());
    }
    
    private String generateUserJourneyExecutiveSummary(NarrativeGenerationContext context) {
        return String.format("This user journey maps how users interact with %d system components, " +
            "highlighting the user experience and business value delivered through these interactions.",
            context.getAnalyzedEntities().size());
    }
    
    private String generateOverviewContent(NarrativeGenerationContext context) {
        return String.format("This analysis covers %d code entities and their interactions within the system. " +
            "The components work together to provide core functionality while maintaining clear separation of concerns.",
            context.getAnalyzedEntities().size());
    }
    
    private String generateFlowDescriptionContent(NarrativeGenerationContext context) {
        return "The execution flow follows a clear pattern: input validation, business logic processing, " +
            "and result generation. Each component has a specific role in this process, ensuring maintainability " +
            "and testability.";
    }
    
    private String generateTechnicalDetailsContent(NarrativeGenerationContext context) {
        return "The implementation uses modern Java patterns including dependency injection, " +
            "service layers, and repository patterns. The code follows SOLID principles and " +
            "maintains clean architecture standards.";
    }
    
    private String generatePerformanceContent(NarrativeGenerationContext context) {
        return "Performance analysis indicates efficient resource utilization with minimal overhead. " +
            "The implementation scales well under load and provides consistent response times.";
    }
    
    private String generateRecommendationsContent(NarrativeGenerationContext context) {
        return "Consider implementing additional caching layers for frequently accessed data. " +
            "Monitor performance metrics and consider asynchronous processing for long-running operations.";
    }
    
    private List<String> generateCodeExamples(NarrativeGenerationContext context) {
        return List.of(
            "// Example usage\nservice.processRequest(request);",
            "// Configuration\n@Service\npublic class ExampleService { ... }"
        );
    }
    
    private List<String> generateKeyInsights(NarrativeGenerationContext context) {
        return List.of(
            "Components follow clear separation of concerns",
            "Business logic is well-encapsulated",
            "Error handling is comprehensive",
            "Performance is optimized for common use cases"
        );
    }
    
    private List<FlowNarrative.NarrativeRecommendation> generateNarrativeRecommendations(NarrativeGenerationContext context) {
        return List.of(
            FlowNarrative.NarrativeRecommendation.builder()
                .title("Add Performance Monitoring")
                .description("Implement comprehensive performance monitoring to track response times and resource usage")
                .priority(0.8)
                .expectedBenefit("Better visibility into system performance")
                .build(),
            FlowNarrative.NarrativeRecommendation.builder()
                .title("Enhance Error Handling")
                .description("Add more specific error handling for edge cases")
                .priority(0.6)
                .expectedBenefit("Improved system reliability")
                .build()
        );
    }
    
    private FlowNarrative.NarrativeQuality calculateNarrativeQuality(
            List<FlowNarrative.NarrativeSection> sections, 
            List<String> insights, 
            List<FlowNarrative.NarrativeRecommendation> recommendations) {
        
        return FlowNarrative.NarrativeQuality.builder()
            .clarity(0.85)
            .completeness(0.80)
            .accuracy(0.90)
            .relevance(0.85)
            .engagement(0.75)
            .overallGrade("B+")
            .strengths(List.of("Clear structure", "Good technical details", "Actionable recommendations"))
            .improvementAreas(List.of("More business context", "Additional examples"))
            .build();
    }
    
    private List<String> generateNarrativeTags(NarrativeGenerationContext context) {
        return List.of(
            "code-analysis",
            "flow-explanation",
            context.getRequest().getNarrativeType().name().toLowerCase(),
            context.getRequest().getAudience().name().toLowerCase()
        );
    }
    
    private FlowNarrative.NarrativeSection adaptSectionForBusiness(
            FlowNarrative.NarrativeSection section, 
            NarrativeGenerationContext context) {
        
        // Adapt technical content for business audience
        String businessContent = section.getContent()
            .replace("implementation", "solution")
            .replace("technical", "business")
            .replace("code", "system");
        
        return section.toBuilder()
            .content(businessContent)
            .build();
    }
    
    private String extractEntityName(String entityId) {
        String[] parts = entityId.split("\\.");
        return parts[parts.length - 1];
    }
    
    private NarrativeGenerationResult.GenerationQualityAssessment assessGenerationQuality(
            FlowNarrative primary, List<FlowNarrative> alternatives, NarrativeGenerationContext context) {
        
        return NarrativeGenerationResult.GenerationQualityAssessment.builder()
            .narrativeClarity(0.85)
            .technicalAccuracy(0.80)
            .businessRelevance(0.75)
            .completeness(0.80)
            .readability(0.85)
            .structuralCoherence(0.90)
            .overallGrade("B+")
            .strengths(List.of("Clear structure", "Good flow", "Actionable insights"))
            .weaknesses(List.of("Could use more examples", "Business context could be deeper"))
            .missingElements(List.of("Detailed performance metrics", "Security considerations"))
            .build();
    }
    
    private List<NarrativeGenerationResult.NarrativeAdaptation> generateAdaptationSuggestions(
            FlowNarrative narrative, NarrativeGenerationContext context) {
        
        return List.of(
            NarrativeGenerationResult.NarrativeAdaptation.builder()
                .adaptationType("Business Focus")
                .targetAudience("Business Stakeholders")
                .description("Adapt language and focus for business audience")
                .feasibility(0.8)
                .expectedImpact("Better business understanding")
                .build()
        );
    }
    
    private List<NarrativeGenerationResult.ContentImprovement> identifyContentImprovements(
            FlowNarrative narrative, 
            NarrativeGenerationResult.GenerationQualityAssessment assessment,
            NarrativeGenerationContext context) {
        
        return List.of(
            NarrativeGenerationResult.ContentImprovement.builder()
                .type(NarrativeGenerationResult.ContentImprovement.ImprovementType.EXAMPLE_ADDITION)
                .title("Add Code Examples")
                .description("Include more practical code examples")
                .priority(0.7)
                .expectedBenefit("Better understanding of implementation")
                .build()
        );
    }
    
    private NarrativeGenerationResult.AudienceEngagementMetrics calculateEngagementMetrics(
            FlowNarrative narrative, NarrativeGenerationContext context) {
        
        return NarrativeGenerationResult.AudienceEngagementMetrics.builder()
            .readabilityScore(0.80)
            .technicalDepthMatch(0.85)
            .businessValueClarity(0.75)
            .actionabilityScore(0.70)
            .comprehensiveness(0.80)
            .audienceFitAssessment("Good fit for technical audience")
            .build();
    }
    
    private NarrativeGenerationResult.SourceAnalysisSummary buildSourceAnalysisSummary(NarrativeGenerationContext context) {
        return NarrativeGenerationResult.SourceAnalysisSummary.builder()
            .entitiesAnalyzed(context.getAnalyzedEntities().size())
            .codePathsTraced(context.getDiscoveredFlows().size())
            .businessRulesIdentified(3)
            .technicalPatternsFound(context.getTechnicalPatterns().size())
            .sourceComplexity(0.6)
            .analysisDepth(0.8)
            .dataQualityIssues(List.of())
            .analysisLimitations(List.of("Limited business context data"))
            .build();
    }
    
    private double calculateGenerationConfidence(NarrativeGenerationContext context, FlowNarrative narrative) {
        double baseConfidence = 0.75;
        
        if (narrative != null && narrative.hasHighQuality()) {
            baseConfidence += 0.15;
        }
        
        if (context.getAnalyzedEntities().size() > 3) {
            baseConfidence += 0.05;
        }
        
        return Math.max(0.0, Math.min(1.0, baseConfidence));
    }
    
    private String buildGenerationSummary(NarrativeGenerationContext context, FlowNarrative narrative) {
        return String.format("Generated %s narrative for %d entities with %s audience focus", 
            context.getRequest().getNarrativeType().name(),
            context.getAnalyzedEntities().size(),
            context.getRequest().getAudience().name());
    }
    
    // Supporting data class
    
    @lombok.Data
    @lombok.Builder
    public static class NarrativeGenerationContext {
        private long startTime;
        private NarrativeGenerationRequest request;
        private Map<String, Map<String, Object>> analyzedEntities;
        private List<String> discoveredFlows;
        private Map<String, Object> businessContext;
        private List<String> technicalPatterns;
        private List<String> narrativeElements;
        private Map<String, Double> qualityFactors;
    }
}