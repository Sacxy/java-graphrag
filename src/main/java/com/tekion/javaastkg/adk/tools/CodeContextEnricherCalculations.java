package com.tekion.javaastkg.adk.tools;

import com.google.adk.tools.ToolContext;
import com.tekion.javaastkg.adk.tools.CodeContextEnricherDataModels.*;
import static com.tekion.javaastkg.adk.tools.CodeContextEnricherDataModels.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Calculation methods for Code Context Enricher
 * 
 * This class contains all the calculation and analysis methods used by the Code Context Enricher
 * for quality scoring, gap identification, and recommendation generation.
 */
@Slf4j
public class CodeContextEnricherCalculations {

    /**
     * Calculates entity-specific quality scores across all dimensions
     */
    public static EntityQualityScores calculateEntityQualityScores(
            DocumentationContext documentation,
            UsageContext usage,
            TestContext tests,
            BusinessContext business) {
        
        double docScore = calculateDocumentationScore(documentation);
        double usageScore = calculateUsageScore(usage);
        double testScore = calculateTestScore(tests);
        double businessScore = calculateBusinessScore(business);
        
        // Weighted overall score
        double overallScore = (docScore * 0.3) + (usageScore * 0.25) + (testScore * 0.25) + (businessScore * 0.2);
        String overallGrade = calculateQualityGrade(overallScore);
        
        return EntityQualityScores.builder()
            .documentationScore(docScore)
            .usageScore(usageScore)
            .testScore(testScore)
            .businessScore(businessScore)
            .overallScore(overallScore)
            .overallGrade(overallGrade)
            .build();
    }
    
    /**
     * Calculates documentation quality score
     */
    private static double calculateDocumentationScore(DocumentationContext documentation) {
        if (documentation == null) return 0.0;
        
        double score = 0.0;
        
        // Completeness component (50%)
        score += documentation.getCompletenessScore() * 0.5;
        
        // Quality component (50%)
        score += documentation.getQualityScore() * 0.5;
        
        return Math.min(1.0, score);
    }
    
    /**
     * Calculates usage examples quality score
     */
    private static double calculateUsageScore(UsageContext usage) {
        if (usage == null) return 0.0;
        
        double score = 0.0;
        
        // Usage richness (60%)
        score += usage.getUsageRichness() * 0.6;
        
        // Usage diversity (40%)
        score += usage.getUsageDiversity() * 0.4;
        
        return Math.min(1.0, score);
    }
    
    /**
     * Calculates test coverage and quality score
     */
    private static double calculateTestScore(TestContext tests) {
        if (tests == null) return 0.0;
        
        double score = 0.0;
        
        // Coverage component (70%)
        score += tests.getEstimatedCoverage() * 0.7;
        
        // Quality component (30%)
        score += tests.getTestQuality() * 0.3;
        
        return Math.min(1.0, score);
    }
    
    /**
     * Calculates business context richness score
     */
    private static double calculateBusinessScore(BusinessContext business) {
        if (business == null) return 0.0;
        
        return business.getBusinessContextRichness();
    }
    
    /**
     * Calculates usage richness based on example count and quality
     */
    public static double calculateUsageRichness(List<UsageExample> usageExamples) {
        if (usageExamples.isEmpty()) return 0.0;
        
        // Base score from count
        double countScore = Math.min(1.0, usageExamples.size() * 0.1);
        
        // Quality bonus for detailed examples
        long detailedExamples = usageExamples.stream()
            .mapToLong(example -> example.getExample().length() > 50 ? 1 : 0)
            .sum();
        
        double qualityBonus = Math.min(0.3, detailedExamples * 0.05);
        
        return Math.min(1.0, countScore + qualityBonus);
    }
    
    /**
     * Calculates usage diversity based on different usage types
     */
    public static double calculateUsageDiversity(List<UsageExample> usageExamples) {
        if (usageExamples.isEmpty()) return 0.0;
        
        Set<String> uniqueSources = usageExamples.stream()
            .map(UsageExample::getSource)
            .collect(Collectors.toSet());
        
        Set<String> uniquePackages = usageExamples.stream()
            .map(UsageExample::getPackageContext)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        
        // Diversity score based on source types and package spread
        double sourceDiv = Math.min(1.0, uniqueSources.size() * 0.33);  // 3 types = full score
        double packageDiv = Math.min(1.0, uniquePackages.size() * 0.2); // 5 packages = full score
        
        return (sourceDiv + packageDiv) / 2.0;
    }
    
    /**
     * Determines test category from test method name
     */
    public static String determineTestCategory(String testMethodName) {
        String lowerName = testMethodName.toLowerCase();
        
        if (lowerName.contains("integration") || lowerName.contains("it")) return "integration";
        if (lowerName.contains("e2e") || lowerName.contains("endtoend")) return "end-to-end";
        if (lowerName.contains("unit")) return "unit";
        if (lowerName.contains("performance") || lowerName.contains("load")) return "performance";
        
        return "unit"; // Default assumption
    }
    
    /**
     * Calculates estimated test coverage based on test references
     */
    public static double calculateEstimatedCoverage(List<TestReference> testReferences) {
        if (testReferences.isEmpty()) return 0.0;
        
        // Basic coverage estimation
        int testClassCount = (int) testReferences.stream()
            .filter(ref -> "class".equals(ref.getTestType()))
            .count();
        
        int testMethodCount = (int) testReferences.stream()
            .filter(ref -> "method".equals(ref.getTestType()))
            .count();
        
        // Rough estimation formula
        double baseCoverage = 0.0;
        if (testClassCount > 0) baseCoverage += 0.3;  // Having test class = 30%
        if (testMethodCount >= 1) baseCoverage += 0.2; // 1+ test method = 20%
        if (testMethodCount >= 3) baseCoverage += 0.3; // 3+ test methods = additional 30%
        if (testMethodCount >= 5) baseCoverage += 0.2; // 5+ test methods = additional 20%
        
        return Math.min(1.0, baseCoverage);
    }
    
    /**
     * Calculates test quality based on test diversity and naming
     */
    public static double calculateTestQuality(List<TestReference> testReferences) {
        if (testReferences.isEmpty()) return 0.0;
        
        // Check for test diversity
        Set<String> testCategories = testReferences.stream()
            .map(TestReference::getTestCategory)
            .collect(Collectors.toSet());
        
        double diversityScore = Math.min(1.0, testCategories.size() * 0.25); // 4 categories = full score
        
        // Check for descriptive test names
        long descriptiveTests = testReferences.stream()
            .filter(ref -> "method".equals(ref.getTestType()))
            .mapToLong(ref -> ref.getTestName().length() > 20 ? 1 : 0) // Longer names usually more descriptive
            .sum();
        
        double nameQualityScore = Math.min(1.0, descriptiveTests * 0.2);
        
        return (diversityScore + nameQualityScore) / 2.0;
    }
    
    /**
     * Extracts domain concepts from business descriptions using configurable mappings
     */
    public static Set<String> extractDomainConcepts(List<String> businessDescriptions, String entityId) {
        // Use configurable domain concept extractor for better extensibility
        CodeContextConstants.DomainConceptExtractor extractor = 
            CodeContextConstants.DomainConceptExtractor.fromConfig("default");
        
        return extractor.extractConcepts(businessDescriptions, entityId);
    }
    
    /**
     * Extracts business rules from descriptions
     */
    public static List<String> extractBusinessRules(List<String> businessDescriptions) {
        List<String> rules = new ArrayList<>();
        
        for (String description : businessDescriptions) {
            // Look for sentences containing business rule indicators
            String[] sentences = description.split("\\.");
            
            for (String sentence : sentences) {
                String lowerSentence = sentence.toLowerCase().trim();
                
                // Common business rule patterns
                if (lowerSentence.contains("must") || lowerSentence.contains("required") ||
                    lowerSentence.contains("cannot") || lowerSentence.contains("should not") ||
                    lowerSentence.contains("always") || lowerSentence.contains("never") ||
                    lowerSentence.contains("only if") || lowerSentence.contains("when")) {
                    
                    if (sentence.trim().length() > 10) { // Filter out very short fragments
                        rules.add(sentence.trim());
                    }
                }
            }
        }
        
        return rules;
    }
    
    /**
     * Calculates business context richness
     */
    public static double calculateBusinessContextRichness(
            List<String> businessDescriptions,
            Set<String> domainConcepts,
            List<String> businessRules) {
        
        double score = 0.0;
        
        // Description richness (40%)
        if (!businessDescriptions.isEmpty()) {
            double avgLength = businessDescriptions.stream()
                .mapToDouble(String::length)
                .average()
                .orElse(0.0);
            score += Math.min(0.4, avgLength / 500.0); // 500 chars = full description score
        }
        
        // Domain concept richness (30%)
        score += Math.min(0.3, domainConcepts.size() * 0.05); // 6 concepts = full score
        
        // Business rules richness (30%)
        score += Math.min(0.3, businessRules.size() * 0.1); // 3 rules = full score
        
        return Math.min(1.0, score);
    }
    
    /**
     * Calculates quality grade from numeric score using consistent thresholds
     */
    public static String calculateQualityGrade(double score) {
        if (score >= CodeContextConstants.GRADE_A_THRESHOLD) return "A";
        if (score >= CodeContextConstants.GRADE_B_THRESHOLD) return "B";
        if (score >= CodeContextConstants.GRADE_C_THRESHOLD) return "C";
        if (score >= CodeContextConstants.GRADE_D_THRESHOLD) return "D";
        return "F";
    }
    
    /**
     * Assesses overall quality across all entities
     */
    public static OverallQualityAssessment assessOverallQuality(
            List<EntityContextAnalysis> entityAnalyses,
            CodeContextEnricherRedesigned.QualityScoring scoring) {
        
        if (entityAnalyses.isEmpty()) {
            return OverallQualityAssessment.builder()
                .overallScore(0.0)
                .overallGrade("F")
                .categoryScores(Map.of())
                .gradeDistribution(Map.of())
                .qualityStrengths(List.of())
                .qualityWeaknesses(List.of("No entities analyzed"))
                .improvementPriorities(List.of("Add entities to analyze"))
                .build();
        }
        
        // Calculate average scores
        double avgDocScore = entityAnalyses.stream()
            .mapToDouble(entity -> entity.getQualityScores().getDocumentationScore())
            .average().orElse(0.0);
        
        double avgUsageScore = entityAnalyses.stream()
            .mapToDouble(entity -> entity.getQualityScores().getUsageScore())
            .average().orElse(0.0);
        
        double avgTestScore = entityAnalyses.stream()
            .mapToDouble(entity -> entity.getQualityScores().getTestScore())
            .average().orElse(0.0);
        
        double avgBusinessScore = entityAnalyses.stream()
            .mapToDouble(entity -> entity.getQualityScores().getBusinessScore())
            .average().orElse(0.0);
        
        // Calculate weighted overall score
        double overallScore = (avgDocScore * scoring.getDocumentationWeight()) +
                             (avgUsageScore * scoring.getUsageWeight()) +
                             (avgTestScore * scoring.getTestWeight()) +
                             (avgBusinessScore * scoring.getBusinessWeight());
        
        String overallGrade = calculateQualityGrade(overallScore);
        
        // Grade distribution
        Map<String, Integer> gradeDistribution = entityAnalyses.stream()
            .collect(Collectors.groupingBy(
                entity -> entity.getQualityScores().getOverallGrade(),
                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
            ));
        
        // Identify strengths and weaknesses
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        List<String> priorities = new ArrayList<>();
        
        if (avgDocScore >= 0.8) strengths.add("Excellent documentation quality");
        else if (avgDocScore < 0.4) weaknesses.add("Poor documentation coverage");
        
        if (avgUsageScore >= 0.8) strengths.add("Rich usage examples");
        else if (avgUsageScore < 0.4) weaknesses.add("Limited usage examples");
        
        if (avgTestScore >= 0.8) strengths.add("Comprehensive test coverage");
        else if (avgTestScore < 0.4) weaknesses.add("Insufficient test coverage");
        
        if (avgBusinessScore >= 0.8) strengths.add("Clear business context");
        else if (avgBusinessScore < 0.4) weaknesses.add("Missing business context");
        
        // Prioritize improvements
        Map<String, Double> categoryScores = Map.of(
            "documentation", avgDocScore,
            "usage", avgUsageScore,
            "tests", avgTestScore,
            "business", avgBusinessScore
        );
        
        priorities.addAll(categoryScores.entrySet().stream()
            .filter(entry -> entry.getValue() < 0.6)
            .sorted(Map.Entry.comparingByValue())
            .map(entry -> "Improve " + entry.getKey() + " quality")
            .collect(Collectors.toList()));
        
        return OverallQualityAssessment.builder()
            .overallScore(overallScore)
            .overallGrade(overallGrade)
            .categoryScores(categoryScores)
            .gradeDistribution(gradeDistribution)
            .qualityStrengths(strengths)
            .qualityWeaknesses(weaknesses)
            .improvementPriorities(priorities)
            .build();
    }
    
    /**
     * Identifies context gaps across all entities
     */
    public static List<ContextGap> identifyContextGaps(
            List<EntityContextAnalysis> entityAnalyses,
            CodeContextEnricherRedesigned.AnalysisConfiguration config,
            CodeContextEnricherRedesigned.QualityScoring scoring) {
        
        List<ContextGap> gaps = new ArrayList<>();
        Map<String, Double> thresholds = config.getQualityThresholds();
        
        // Analyze each quality dimension
        analyzeDocumentationGaps(entityAnalyses, thresholds, gaps);
        analyzeUsageGaps(entityAnalyses, thresholds, gaps);
        analyzeTestGaps(entityAnalyses, thresholds, gaps);
        analyzeBusinessContextGaps(entityAnalyses, thresholds, gaps);
        
        // Sort gaps by priority score (highest first)
        gaps.sort((g1, g2) -> Double.compare(g2.getPriorityScore(), g1.getPriorityScore()));
        
        return gaps;
    }
    
    private static void analyzeDocumentationGaps(
            List<EntityContextAnalysis> entityAnalyses, 
            Map<String, Double> thresholds, 
            List<ContextGap> gaps) {
        
        double threshold = thresholds.getOrDefault("documentation", 0.6);
        
        List<String> poorDocEntities = entityAnalyses.stream()
            .filter(entity -> entity.getQualityScores().getDocumentationScore() < threshold)
            .map(EntityContextAnalysis::getEntityId)
            .collect(Collectors.toList());
        
        if (!poorDocEntities.isEmpty()) {
            CodeContextConstants.GapType gapType = CodeContextConstants.GapType.MISSING_DOCUMENTATION;
            gaps.add(ContextGap.builder()
                .gapType(gapType.name())
                .gapDescription(String.format("%d entities have insufficient documentation", poorDocEntities.size()))
                .severity(determineSeverity(poorDocEntities.size(), entityAnalyses.size()))
                .affectedEntities(poorDocEntities)
                .impact("Developers struggle to understand code purpose and usage")
                .rootCauses(List.of("Missing Javadoc comments", "No inline documentation", "Absence of README files"))
                .priorityScore(calculatePriorityScore("documentation", poorDocEntities.size(), entityAnalyses.size()))
                .build());
        }
    }
    
    private static void analyzeUsageGaps(
            List<EntityContextAnalysis> entityAnalyses,
            Map<String, Double> thresholds,
            List<ContextGap> gaps) {
        
        double threshold = thresholds.getOrDefault("usage", 0.4);
        
        List<String> poorUsageEntities = entityAnalyses.stream()
            .filter(entity -> entity.getQualityScores().getUsageScore() < threshold)
            .map(EntityContextAnalysis::getEntityId)
            .collect(Collectors.toList());
        
        if (!poorUsageEntities.isEmpty()) {
            CodeContextConstants.GapType gapType = CodeContextConstants.GapType.MISSING_USAGE_EXAMPLES;
            gaps.add(ContextGap.builder()
                .gapType(gapType.name())
                .gapDescription(String.format("%d entities lack clear usage examples", poorUsageEntities.size()))
                .severity(determineSeverity(poorUsageEntities.size(), entityAnalyses.size()))
                .affectedEntities(poorUsageEntities)
                .impact("Developers don't know how to use these components effectively")
                .rootCauses(List.of("Limited method calls", "No code examples", "Poor usage documentation"))
                .priorityScore(calculatePriorityScore("usage", poorUsageEntities.size(), entityAnalyses.size()))
                .build());
        }
    }
    
    private static void analyzeTestGaps(
            List<EntityContextAnalysis> entityAnalyses,
            Map<String, Double> thresholds,
            List<ContextGap> gaps) {
        
        double threshold = thresholds.getOrDefault("tests", 0.5);
        
        List<String> poorTestEntities = entityAnalyses.stream()
            .filter(entity -> entity.getQualityScores().getTestScore() < threshold)
            .map(EntityContextAnalysis::getEntityId)
            .collect(Collectors.toList());
        
        if (!poorTestEntities.isEmpty()) {
            CodeContextConstants.GapType gapType = CodeContextConstants.GapType.INSUFFICIENT_TESTING;
            gaps.add(ContextGap.builder()
                .gapType(gapType.name())
                .gapDescription(String.format("%d entities have inadequate test coverage", poorTestEntities.size()))
                .severity(determineSeverity(poorTestEntities.size(), entityAnalyses.size()))
                .affectedEntities(poorTestEntities)
                .impact("Higher risk of bugs and reduced confidence in code changes")
                .rootCauses(List.of("Missing unit tests", "No integration tests", "Poor test naming"))
                .priorityScore(calculatePriorityScore("tests", poorTestEntities.size(), entityAnalyses.size()))
                .build());
        }
    }
    
    private static void analyzeBusinessContextGaps(
            List<EntityContextAnalysis> entityAnalyses,
            Map<String, Double> thresholds,
            List<ContextGap> gaps) {
        
        double threshold = thresholds.getOrDefault("business", 0.3);
        
        List<String> poorBusinessEntities = entityAnalyses.stream()
            .filter(entity -> entity.getQualityScores().getBusinessScore() < threshold)
            .map(EntityContextAnalysis::getEntityId)
            .collect(Collectors.toList());
        
        if (!poorBusinessEntities.isEmpty()) {
            CodeContextConstants.GapType gapType = CodeContextConstants.GapType.MISSING_BUSINESS_CONTEXT;
            gaps.add(ContextGap.builder()
                .gapType(gapType.name())
                .gapDescription(String.format("%d entities lack business context", poorBusinessEntities.size()))
                .severity(determineSeverity(poorBusinessEntities.size(), entityAnalyses.size()))
                .affectedEntities(poorBusinessEntities)
                .impact("Developers can't understand business requirements and constraints")
                .rootCauses(List.of("No business documentation", "Missing domain knowledge", "Unclear requirements"))
                .priorityScore(calculatePriorityScore("business", poorBusinessEntities.size(), entityAnalyses.size()))
                .build());
        }
    }
    
    private static String determineSeverity(int affectedCount, int totalCount) {
        double percentage = (double) affectedCount / totalCount;
        if (percentage >= CodeContextConstants.HIGH_SEVERITY_THRESHOLD) return CodeContextConstants.Priority.HIGH.getValue();
        if (percentage >= CodeContextConstants.MEDIUM_SEVERITY_THRESHOLD) return CodeContextConstants.Priority.MEDIUM.getValue();
        return CodeContextConstants.Priority.LOW.getValue();
    }
    
    private static double calculatePriorityScore(String gapType, int affectedCount, int totalCount) {
        double baseScore = (double) affectedCount / totalCount;
        
        // Weight by gap type importance
        double typeWeight = switch (gapType) {
            case "documentation" -> 1.0;
            case "tests" -> 0.9;
            case "usage" -> 0.8;
            case "business" -> 0.6;
            default -> 0.5;
        };
        
        return baseScore * typeWeight;
    }
    
    /**
     * Updates context state for agent reasoning
     */
    public static void updateContextState(
            ToolContext ctx,
            List<String> entityIds,
            OverallQualityAssessment qualityAssessment,
            List<ContextGap> contextGaps) {
        
        if (ctx == null || ctx.state() == null) return;
        
        // Save analyzed entities
        ctx.state().put("app:analyzed_entities", entityIds);
        
        // Save quality metrics
        ctx.state().put("app:overall_quality_score", qualityAssessment.getOverallScore());
        ctx.state().put("app:overall_quality_grade", qualityAssessment.getOverallGrade());
        ctx.state().put("app:category_scores", qualityAssessment.getCategoryScores());
        
        // Save gap information
        ctx.state().put("app:context_gaps_count", contextGaps.size());
        ctx.state().put("app:high_priority_gaps", contextGaps.stream()
            .filter(gap -> "HIGH".equals(gap.getSeverity()))
            .map(ContextGap::getGapType)
            .collect(Collectors.toList()));
        
        // Save improvement priorities
        ctx.state().put("app:improvement_priorities", qualityAssessment.getImprovementPriorities());
    }
    
    /**
     * Determines next actions based on analysis results
     */
    public static List<String> determineNextActions(
            OverallQualityAssessment qualityAssessment,
            List<ContextGap> contextGaps,
            List<ContextRecommendation> recommendations) {
        
        List<String> nextActions = new ArrayList<>();
        
        // Based on overall quality
        if (qualityAssessment.getOverallScore() < 0.4) {
            nextActions.add("URGENT_IMPROVEMENT_NEEDED");
        } else if (qualityAssessment.getOverallScore() < 0.6) {
            nextActions.add("SYSTEMATIC_IMPROVEMENT_RECOMMENDED");
        } else if (qualityAssessment.getOverallScore() >= 0.8) {
            nextActions.add("MAINTAIN_CURRENT_QUALITY");
        }
        
        // Based on gaps
        boolean hasHighPriorityGaps = contextGaps.stream()
            .anyMatch(gap -> "HIGH".equals(gap.getSeverity()));
        
        if (hasHighPriorityGaps) {
            nextActions.add("ADDRESS_HIGH_PRIORITY_GAPS");
        }
        
        // Based on recommendations
        if (recommendations.size() > 10) {
            nextActions.add("PRIORITIZE_RECOMMENDATIONS");
        } else if (!recommendations.isEmpty()) {
            nextActions.add("IMPLEMENT_RECOMMENDATIONS");
        }
        
        // Default action if no specific guidance
        if (nextActions.isEmpty()) {
            nextActions.add("CONTINUE_MONITORING");
        }
        
        return nextActions;
    }
    
    // Helper method to extract class name from entity ID
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
}