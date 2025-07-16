package com.tekion.javaastkg.adk.tools;

import com.tekion.javaastkg.adk.tools.FlowNarrativeGeneratorDataModels.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Flow Narrative Generator Calculations
 * 
 * Contains all calculation algorithms and helper methods for generating intelligent narratives.
 * Designed specifically for AI agents to process complex code analysis and create human-readable outputs.
 * 
 * Key capabilities:
 * - Information extraction and synthesis algorithms
 * - Multi-perspective narrative generation logic
 * - Quality validation and scoring systems
 * - Readability assessment using Flesch Reading Ease
 * - Interactive element generation
 */
@Slf4j
public class FlowNarrativeGeneratorCalculations {

    // ============================================================================
    // INFORMATION EXTRACTION METHODS
    // ============================================================================

    public static List<StructuralInsight> extractStructuralInsights(Map<String, Object> structural) {
        List<StructuralInsight> insights = new ArrayList<>();
        
        if (structural.containsKey("structuralFindings")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> findings = (List<Map<String, Object>>) structural.get("structuralFindings");
            
            for (Map<String, Object> finding : findings) {
                insights.add(StructuralInsight.builder()
                    .id(UUID.randomUUID().toString())
                    .title((String) finding.getOrDefault("title", "Structural Finding"))
                    .description((String) finding.getOrDefault("description", ""))
                    .insightType((String) finding.getOrDefault("type", "STRUCTURE"))
                    .confidence(((Number) finding.getOrDefault("confidence", 0.8)).doubleValue())
                    .complexity((String) finding.getOrDefault("complexity", "MODERATE"))
                    .details(finding)
                    .build());
            }
        }
        
        return insights;
    }

    public static List<ExecutionInsight> extractExecutionInsights(Map<String, Object> execution) {
        List<ExecutionInsight> insights = new ArrayList<>();
        
        if (execution.containsKey("executionPaths")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> paths = (List<Map<String, Object>>) execution.get("executionPaths");
            
            for (Map<String, Object> path : paths) {
                insights.add(ExecutionInsight.builder()
                    .id(UUID.randomUUID().toString())
                    .title((String) path.getOrDefault("title", "Execution Path"))
                    .description((String) path.getOrDefault("description", ""))
                    .pathType((String) path.getOrDefault("pathType", "SEQUENTIAL"))
                    .executionSteps((List<String>) path.getOrDefault("steps", new ArrayList<>()))
                    .confidence(((Number) path.getOrDefault("confidence", 0.8)).doubleValue())
                    .details(path)
                    .build());
            }
        }
        
        return insights;
    }

    public static List<ContextInsight> extractContextInsights(Map<String, Object> context) {
        List<ContextInsight> insights = new ArrayList<>();
        
        if (context.containsKey("contextEnrichments")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> enrichments = (List<Map<String, Object>>) context.get("contextEnrichments");
            
            for (Map<String, Object> enrichment : enrichments) {
                insights.add(ContextInsight.builder()
                    .id(UUID.randomUUID().toString())
                    .title((String) enrichment.getOrDefault("title", "Context Enrichment"))
                    .description((String) enrichment.getOrDefault("description", ""))
                    .contextType((String) enrichment.getOrDefault("type", "GENERAL"))
                    .relevance(((Number) enrichment.getOrDefault("relevance", 0.7)).doubleValue())
                    .enrichment(enrichment)
                    .build());
            }
        }
        
        return insights;
    }

    public static List<KeyEntity> extractStructuralEntities(Map<String, Object> structural) {
        List<KeyEntity> entities = new ArrayList<>();
        
        if (structural.containsKey("entities")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entityData = (List<Map<String, Object>>) structural.get("entities");
            
            for (Map<String, Object> entity : entityData) {
                entities.add(KeyEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .name((String) entity.getOrDefault("name", "Unknown Entity"))
                    .type((String) entity.getOrDefault("type", "CLASS"))
                    .description((String) entity.getOrDefault("description", ""))
                    .importance(((Number) entity.getOrDefault("importance", 0.5)).doubleValue())
                    .attributes(entity)
                    .build());
            }
        }
        
        return entities;
    }

    public static List<KeyEntity> extractSemanticEntities(Map<String, Object> semantic) {
        return extractStructuralEntities(semantic); // Similar extraction logic
    }

    public static List<KeyEntity> extractContextEntities(Map<String, Object> context) {
        return extractStructuralEntities(context); // Similar extraction logic
    }

    public static List<KeyRelationship> extractStructuralRelationships(Map<String, Object> structural) {
        List<KeyRelationship> relationships = new ArrayList<>();
        
        if (structural.containsKey("relationships")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> relationshipData = (List<Map<String, Object>>) structural.get("relationships");
            
            for (Map<String, Object> rel : relationshipData) {
                relationships.add(KeyRelationship.builder()
                    .id(UUID.randomUUID().toString())
                    .sourceId((String) rel.getOrDefault("sourceId", ""))
                    .targetId((String) rel.getOrDefault("targetId", ""))
                    .relationshipType((String) rel.getOrDefault("type", "DEPENDS_ON"))
                    .description((String) rel.getOrDefault("description", ""))
                    .strength(((Number) rel.getOrDefault("strength", 0.5)).doubleValue())
                    .properties(rel)
                    .build());
            }
        }
        
        return relationships;
    }

    public static List<KeyRelationship> extractSemanticRelationships(Map<String, Object> semantic) {
        return extractStructuralRelationships(semantic); // Similar extraction logic
    }

    public static List<KeyRelationship> extractExecutionPaths(Map<String, Object> execution) {
        return extractStructuralRelationships(execution); // Similar extraction logic
    }

    // ============================================================================
    // INFORMATION CONSOLIDATION METHODS
    // ============================================================================

    public static ConsolidatedInformation consolidateInformation(KeyInformation keyInfo) {
        // Combine all insights
        List<Insight> allInsights = new ArrayList<>();
        
        // Convert structural insights
        allInsights.addAll(keyInfo.getStructuralInsights().stream()
            .map(si -> Insight.builder()
                .id(si.getId())
                .title(si.getTitle())
                .description(si.getDescription())
                .type("STRUCTURAL")
                .complexity(si.getComplexity())
                .confidence(si.getConfidence())
                .importance(calculateImportanceFromConfidence(si.getConfidence()))
                .source("StructuralCodeExplorer")
                .details(si.getDetails())
                .build())
            .collect(Collectors.toList()));
        
        // Convert execution insights
        allInsights.addAll(keyInfo.getExecutionInsights().stream()
            .map(ei -> Insight.builder()
                .id(ei.getId())
                .title(ei.getTitle())
                .description(ei.getDescription())
                .type("EXECUTION")
                .complexity("MODERATE")
                .confidence(ei.getConfidence())
                .importance(calculateImportanceFromConfidence(ei.getConfidence()))
                .source("ExecutionPathTracer")
                .details(ei.getDetails())
                .build())
            .collect(Collectors.toList()));
        
        // Convert context insights
        allInsights.addAll(keyInfo.getContextInsights().stream()
            .map(ci -> Insight.builder()
                .id(ci.getId())
                .title(ci.getTitle())
                .description(ci.getDescription())
                .type("CONTEXT")
                .complexity("MODERATE")
                .confidence(0.8) // Default confidence for context
                .importance(ci.getRelevance())
                .source("CodeContextEnricher")
                .details(ci.getEnrichment())
                .build())
            .collect(Collectors.toList()));
        
        // Remove duplicates and merge similar insights
        List<Insight> uniqueInsights = deduplicateInsights(allInsights);
        
        return ConsolidatedInformation.builder()
            .uniqueInsights(uniqueInsights)
            .mergedEntities(keyInfo.getKeyEntities())
            .consolidatedRelationships(keyInfo.getKeyRelationships())
            .consolidationMetadata(Map.of(
                "originalInsightCount", allInsights.size(),
                "uniqueInsightCount", uniqueInsights.size(),
                "deduplicationRatio", (double) uniqueInsights.size() / allInsights.size(),
                "consolidationTimestamp", System.currentTimeMillis()
            ))
            .build();
    }

    public static List<Insight> deduplicateInsights(List<Insight> insights) {
        Map<String, Insight> uniqueInsights = new HashMap<>();
        
        for (Insight insight : insights) {
            String key = generateInsightKey(insight);
            
            if (uniqueInsights.containsKey(key)) {
                // Merge insights with same key
                Insight existing = uniqueInsights.get(key);
                Insight merged = mergeInsights(existing, insight);
                uniqueInsights.put(key, merged);
            } else {
                uniqueInsights.put(key, insight);
            }
        }
        
        return new ArrayList<>(uniqueInsights.values());
    }

    private static String generateInsightKey(Insight insight) {
        // Create key based on title and type for deduplication
        String title = insight.getTitle().toLowerCase().replaceAll("\\s+", "_");
        return insight.getType() + ":" + title;
    }

    private static Insight mergeInsights(Insight existing, Insight newInsight) {
        return Insight.builder()
            .id(existing.getId())
            .title(existing.getTitle())
            .description(combineDescriptions(existing.getDescription(), newInsight.getDescription()))
            .type(existing.getType())
            .complexity(selectHighestComplexity(existing.getComplexity(), newInsight.getComplexity()))
            .confidence(Math.max(existing.getConfidence(), newInsight.getConfidence()))
            .importance(Math.max(existing.getImportance(), newInsight.getImportance()))
            .source(existing.getSource() + "+" + newInsight.getSource())
            .details(mergeDetailMaps(existing.getDetails(), newInsight.getDetails()))
            .build();
    }

    private static String combineDescriptions(String desc1, String desc2) {
        if (desc1 == null || desc1.isEmpty()) return desc2;
        if (desc2 == null || desc2.isEmpty()) return desc1;
        if (desc1.equals(desc2)) return desc1;
        return desc1 + " " + desc2;
    }

    private static String selectHighestComplexity(String complexity1, String complexity2) {
        List<String> complexityOrder = List.of("LOW", "MODERATE", "HIGH", "COMPLEX");
        int idx1 = complexityOrder.indexOf(complexity1);
        int idx2 = complexityOrder.indexOf(complexity2);
        return complexityOrder.get(Math.max(idx1, idx2));
    }

    private static Map<String, Object> mergeDetailMaps(Map<String, Object> details1, Map<String, Object> details2) {
        Map<String, Object> merged = new HashMap<>();
        if (details1 != null) merged.putAll(details1);
        if (details2 != null) merged.putAll(details2);
        return merged;
    }

    // ============================================================================
    // NARRATIVE ORGANIZATION METHODS
    // ============================================================================

    public static OrganizedFlow organizeInformationFlow(ConsolidatedInformation consolidated, String originalQuery) {
        // Determine narrative structure
        NarrativeStructure structure = determineNarrativeStructure(originalQuery);
        
        // Order insights by importance and logical flow
        List<Insight> orderedInsights = orderInsights(consolidated.getUniqueInsights(), structure);
        
        // Create narrative sections
        List<NarrativeSection> sections = createNarrativeSections(orderedInsights, structure);
        
        return OrganizedFlow.builder()
            .narrativeStructure(structure)
            .orderedInsights(orderedInsights)
            .narrativeSections(sections)
            .flowMetadata(Map.of(
                "sectionCount", sections.size(),
                "insightCount", orderedInsights.size(),
                "structureType", structure.name(),
                "organizationTimestamp", System.currentTimeMillis()
            ))
            .build();
    }

    public static NarrativeStructure determineNarrativeStructure(String originalQuery) {
        String queryLower = originalQuery.toLowerCase();
        
        if (queryLower.contains("how")) {
            return NarrativeStructure.PROCESS_EXPLANATION;
        } else if (queryLower.contains("why")) {
            return NarrativeStructure.CAUSAL_ANALYSIS;
        } else if (queryLower.contains("what")) {
            return NarrativeStructure.DESCRIPTIVE_OVERVIEW;
        } else if (queryLower.contains("compare") || queryLower.contains("difference") || queryLower.contains("vs")) {
            return NarrativeStructure.COMPARATIVE_ANALYSIS;
        } else {
            return NarrativeStructure.EXPLORATORY_INVESTIGATION;
        }
    }

    public static List<Insight> orderInsights(List<Insight> insights, NarrativeStructure structure) {
        switch (structure) {
            case PROCESS_EXPLANATION:
                return insights.stream()
                    .sorted(Comparator.comparing(Insight::getImportance).reversed()
                        .thenComparing(insight -> insight.getType().equals("EXECUTION") ? 0 : 1))
                    .collect(Collectors.toList());
                
            case CAUSAL_ANALYSIS:
                return insights.stream()
                    .sorted(Comparator.comparing(Insight::getConfidence).reversed()
                        .thenComparing(Insight::getImportance).reversed())
                    .collect(Collectors.toList());
                
            case DESCRIPTIVE_OVERVIEW:
                return insights.stream()
                    .sorted(Comparator.comparing(Insight::getImportance).reversed()
                        .thenComparing(insight -> insight.getType().equals("STRUCTURAL") ? 0 : 1))
                    .collect(Collectors.toList());
                
            default:
                return insights.stream()
                    .sorted(Comparator.comparing(Insight::getImportance).reversed()
                        .thenComparing(Insight::getConfidence).reversed())
                    .collect(Collectors.toList());
        }
    }

    public static List<NarrativeSection> createNarrativeSections(List<Insight> orderedInsights, NarrativeStructure structure) {
        List<NarrativeSection> sections = new ArrayList<>();
        
        // Group insights by type and importance
        Map<String, List<Insight>> groupedInsights = groupInsightsForSections(orderedInsights);
        
        for (Map.Entry<String, List<Insight>> entry : groupedInsights.entrySet()) {
            String sectionType = entry.getKey();
            List<Insight> sectionInsights = entry.getValue();
            
            NarrativeSection section = NarrativeSection.builder()
                .id(UUID.randomUUID().toString())
                .title(generateSectionTitle(sectionType, structure))
                .content(generateSectionContent(sectionInsights))
                .insights(sectionInsights)
                .supportingDetails(extractSupportingDetails(sectionInsights))
                .codeExamples(extractCodeExamples(sectionInsights))
                .diagrams(new ArrayList<>()) // Will be populated by interactive elements
                .build();
            
            sections.add(section);
        }
        
        return sections;
    }

    private static Map<String, List<Insight>> groupInsightsForSections(List<Insight> insights) {
        return insights.stream()
            .collect(Collectors.groupingBy(
                insight -> insight.getType(),
                LinkedHashMap::new,
                Collectors.toList()
            ));
    }

    private static String generateSectionTitle(String sectionType, NarrativeStructure structure) {
        switch (sectionType) {
            case "STRUCTURAL":
                return "Code Structure and Architecture";
            case "EXECUTION":
                return "Execution Flow and Behavior";
            case "CONTEXT":
                return "Context and Dependencies";
            default:
                return "Analysis Findings";
        }
    }

    private static String generateSectionContent(List<Insight> insights) {
        StringBuilder content = new StringBuilder();
        
        for (Insight insight : insights) {
            content.append("### ").append(insight.getTitle()).append("\n\n");
            content.append(insight.getDescription()).append("\n\n");
            
            if (insight.getConfidence() > 0.9) {
                content.append("*High confidence finding*\n\n");
            }
        }
        
        return content.toString();
    }

    private static List<String> extractSupportingDetails(List<Insight> insights) {
        return insights.stream()
            .map(insight -> "Confidence: " + String.format("%.1f%%", insight.getConfidence() * 100) + 
                          " | Importance: " + String.format("%.1f", insight.getImportance()) +
                          " | Source: " + insight.getSource())
            .collect(Collectors.toList());
    }

    private static List<CodeExample> extractCodeExamples(List<Insight> insights) {
        List<CodeExample> examples = new ArrayList<>();
        
        for (Insight insight : insights) {
            if (insight.getDetails().containsKey("codeSnippet")) {
                String code = (String) insight.getDetails().get("codeSnippet");
                examples.add(CodeExample.builder()
                    .id(UUID.randomUUID().toString())
                    .methodName(insight.getTitle())
                    .signature(extractMethodSignature(code))
                    .fullCode(code)
                    .language("java")
                    .lineCount(countLines(code))
                    .description(insight.getDescription())
                    .build());
            }
        }
        
        return examples;
    }

    private static String extractMethodSignature(String code) {
        Pattern pattern = Pattern.compile("(public|private|protected)?\\s*\\w+\\s+\\w+\\s*\\([^)]*\\)");
        Matcher matcher = pattern.matcher(code);
        return matcher.find() ? matcher.group() : "Unknown signature";
    }

    private static int countLines(String text) {
        return text == null ? 0 : (int) text.lines().count();
    }

    // ============================================================================
    // NARRATIVE THEME IDENTIFICATION
    // ============================================================================

    public static List<NarrativeTheme> identifyNarrativeThemes(OrganizedFlow organizedFlow, String originalQuery) {
        List<NarrativeTheme> themes = new ArrayList<>();
        
        // Analyze query for theme hints
        if (originalQuery.toLowerCase().contains("performance")) {
            themes.add(createPerformanceTheme(organizedFlow));
        }
        
        if (originalQuery.toLowerCase().contains("security")) {
            themes.add(createSecurityTheme(organizedFlow));
        }
        
        if (originalQuery.toLowerCase().contains("architecture") || originalQuery.toLowerCase().contains("design")) {
            themes.add(createArchitectureTheme(organizedFlow));
        }
        
        // Default themes based on insight types
        Map<String, Long> insightCounts = organizedFlow.getOrderedInsights().stream()
            .collect(Collectors.groupingBy(Insight::getType, Collectors.counting()));
        
        if (insightCounts.getOrDefault("STRUCTURAL", 0L) > 0) {
            themes.add(createStructuralTheme(organizedFlow));
        }
        
        if (insightCounts.getOrDefault("EXECUTION", 0L) > 0) {
            themes.add(createExecutionTheme(organizedFlow));
        }
        
        return themes;
    }

    private static NarrativeTheme createPerformanceTheme(OrganizedFlow flow) {
        List<String> supportingInsights = flow.getOrderedInsights().stream()
            .filter(insight -> insight.getDescription().toLowerCase().contains("performance") ||
                             insight.getDescription().toLowerCase().contains("optimization"))
            .map(Insight::getId)
            .collect(Collectors.toList());
        
        return NarrativeTheme.builder()
            .id(UUID.randomUUID().toString())
            .name("Performance Analysis")
            .description("Code performance characteristics and optimization opportunities")
            .relevance(0.8)
            .supportingInsights(supportingInsights)
            .build();
    }

    private static NarrativeTheme createSecurityTheme(OrganizedFlow flow) {
        return NarrativeTheme.builder()
            .id(UUID.randomUUID().toString())
            .name("Security Considerations")
            .description("Security implications and best practices")
            .relevance(0.9)
            .supportingInsights(new ArrayList<>())
            .build();
    }

    private static NarrativeTheme createArchitectureTheme(OrganizedFlow flow) {
        List<String> structuralInsights = flow.getOrderedInsights().stream()
            .filter(insight -> "STRUCTURAL".equals(insight.getType()))
            .map(Insight::getId)
            .collect(Collectors.toList());
        
        return NarrativeTheme.builder()
            .id(UUID.randomUUID().toString())
            .name("Architecture and Design")
            .description("System architecture patterns and design principles")
            .relevance(0.85)
            .supportingInsights(structuralInsights)
            .build();
    }

    private static NarrativeTheme createStructuralTheme(OrganizedFlow flow) {
        return NarrativeTheme.builder()
            .id(UUID.randomUUID().toString())
            .name("Code Structure")
            .description("Code organization and structural patterns")
            .relevance(0.7)
            .supportingInsights(new ArrayList<>())
            .build();
    }

    private static NarrativeTheme createExecutionTheme(OrganizedFlow flow) {
        return NarrativeTheme.builder()
            .id(UUID.randomUUID().toString())
            .name("Execution Flow")
            .description("Runtime behavior and execution patterns")
            .relevance(0.75)
            .supportingInsights(new ArrayList<>())
            .build();
    }

    // ============================================================================
    // EVIDENCE STRUCTURE BUILDING
    // ============================================================================

    public static EvidenceStructure buildEvidenceStructure(Map<String, Object> enrichedContext, OrganizedFlow organizedFlow) {
        List<Evidence> structuralEvidence = extractStructuralEvidence(enrichedContext);
        List<Evidence> executionEvidence = extractExecutionEvidence(enrichedContext);
        List<Evidence> contextEvidence = extractContextEvidence(enrichedContext);
        
        return EvidenceStructure.builder()
            .structuralEvidence(structuralEvidence)
            .executionEvidence(executionEvidence)
            .contextEvidence(contextEvidence)
            .evidenceMetadata(Map.of(
                "structuralEvidenceCount", structuralEvidence.size(),
                "executionEvidenceCount", executionEvidence.size(),
                "contextEvidenceCount", contextEvidence.size(),
                "totalEvidenceCount", structuralEvidence.size() + executionEvidence.size() + contextEvidence.size(),
                "evidenceGenerationTimestamp", System.currentTimeMillis()
            ))
            .build();
    }

    private static List<Evidence> extractStructuralEvidence(Map<String, Object> enrichedContext) {
        List<Evidence> evidence = new ArrayList<>();
        
        if (enrichedContext.containsKey("structuralAnalysis")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> structural = (Map<String, Object>) enrichedContext.get("structuralAnalysis");
            
            evidence.add(Evidence.builder()
                .type("CODE")
                .description("Structural analysis findings")
                .source("StructuralCodeExplorer")
                .relevance(0.9)
                .details(structural)
                .build());
        }
        
        return evidence;
    }

    private static List<Evidence> extractExecutionEvidence(Map<String, Object> enrichedContext) {
        List<Evidence> evidence = new ArrayList<>();
        
        if (enrichedContext.containsKey("executionAnalysis")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> execution = (Map<String, Object>) enrichedContext.get("executionAnalysis");
            
            evidence.add(Evidence.builder()
                .type("PATTERN")
                .description("Execution flow analysis")
                .source("ExecutionPathTracer")
                .relevance(0.85)
                .details(execution)
                .build());
        }
        
        return evidence;
    }

    private static List<Evidence> extractContextEvidence(Map<String, Object> enrichedContext) {
        List<Evidence> evidence = new ArrayList<>();
        
        if (enrichedContext.containsKey("contextAnalysis")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> context = (Map<String, Object>) enrichedContext.get("contextAnalysis");
            
            evidence.add(Evidence.builder()
                .type("DOCUMENTATION")
                .description("Context enrichment analysis")
                .source("CodeContextEnricher")
                .relevance(0.8)
                .details(context)
                .build());
        }
        
        return evidence;
    }

    // ============================================================================
    // COMPLEXITY ASSESSMENT
    // ============================================================================

    public static String determineComplexityLevel(KeyInformation keyInfo, List<NarrativeTheme> themes) {
        int complexityScore = 0;
        
        // Factor in number of insights
        int totalInsights = keyInfo.getStructuralInsights().size() + 
                           keyInfo.getExecutionInsights().size() + 
                           keyInfo.getContextInsights().size();
        
        if (totalInsights > 20) complexityScore += 3;
        else if (totalInsights > 10) complexityScore += 2;
        else if (totalInsights > 5) complexityScore += 1;
        
        // Factor in entity count
        if (keyInfo.getKeyEntities().size() > 15) complexityScore += 2;
        else if (keyInfo.getKeyEntities().size() > 8) complexityScore += 1;
        
        // Factor in relationship count
        if (keyInfo.getKeyRelationships().size() > 10) complexityScore += 2;
        else if (keyInfo.getKeyRelationships().size() > 5) complexityScore += 1;
        
        // Factor in theme count
        if (themes.size() > 3) complexityScore += 1;
        
        if (complexityScore >= 6) return "COMPLEX";
        else if (complexityScore >= 3) return "MODERATE";
        else return "SIMPLE";
    }

    // ============================================================================
    // PERSPECTIVE-SPECIFIC NARRATIVE GENERATION
    // ============================================================================

    public static PerspectiveSettings selectPerspectiveSettings(String perspective) {
        switch (perspective.toUpperCase()) {
            case "TECHNICAL":
                return PerspectiveSettings.builder()
                    .perspective("TECHNICAL")
                    .audienceLevel("EXPERT")
                    .technicalDetail("HIGH")
                    .businessContext("LOW")
                    .codeExamples("EXTENSIVE")
                    .terminology("TECHNICAL")
                    .build();
                    
            case "BUSINESS":
                return PerspectiveSettings.builder()
                    .perspective("BUSINESS")
                    .audienceLevel("MANAGER")
                    .technicalDetail("LOW")
                    .businessContext("HIGH")
                    .codeExamples("MINIMAL")
                    .terminology("BUSINESS")
                    .build();
                    
            case "USER":
                return PerspectiveSettings.builder()
                    .perspective("USER")
                    .audienceLevel("END_USER")
                    .technicalDetail("MINIMAL")
                    .businessContext("MEDIUM")
                    .codeExamples("NONE")
                    .terminology("PLAIN_LANGUAGE")
                    .build();
                    
            default:
                return PerspectiveSettings.builder()
                    .perspective("MIXED")
                    .audienceLevel("MIXED")
                    .technicalDetail("MEDIUM")
                    .businessContext("MEDIUM")
                    .codeExamples("MODERATE")
                    .terminology("BALANCED")
                    .build();
        }
    }

    public static String buildIntroduction(String originalQuery, SynthesizedContent synthesized, PerspectiveSettings settings) {
        StringBuilder intro = new StringBuilder();
        
        // Query restatement
        intro.append("Based on the analysis of your query \"").append(originalQuery).append("\", ");
        
        // Context setting based on perspective
        if ("BUSINESS".equals(settings.getPerspective())) {
            intro.append("here's a business-focused overview of the findings: ");
        } else if ("TECHNICAL".equals(settings.getPerspective())) {
            intro.append("here's a technical deep-dive into the code structure and behavior: ");
        } else if ("USER".equals(settings.getPerspective())) {
            intro.append("here's a user-friendly explanation of what the code does: ");
        } else {
            intro.append("here's a comprehensive overview of the analysis results: ");
        }
        
        // Overview of complexity
        intro.append("The analysis reveals a ").append(synthesized.getComplexityLevel().toLowerCase())
             .append(" system with ").append(synthesized.getKeyInformation().getKeyEntities().size())
             .append(" key components and ").append(synthesized.getNarrativeThemes().size())
             .append(" main themes to explore.");
        
        return intro.toString();
    }

    public static List<NarrativeSection> buildContentSections(OrganizedFlow organizedFlow, PerspectiveSettings settings) {
        List<NarrativeSection> sections = new ArrayList<>();
        
        for (NarrativeSection originalSection : organizedFlow.getNarrativeSections()) {
            NarrativeSection adaptedSection = NarrativeSection.builder()
                .id(originalSection.getId())
                .title(adaptSectionTitle(originalSection.getTitle(), settings))
                .content(adaptSectionContent(originalSection.getContent(), settings))
                .supportingDetails(adaptSupportingDetails(originalSection.getSupportingDetails(), settings))
                .codeExamples(adaptCodeExamples(originalSection.getCodeExamples(), settings))
                .diagrams(originalSection.getDiagrams())
                .insights(originalSection.getInsights())
                .build();
            
            sections.add(adaptedSection);
        }
        
        return sections;
    }

    private static String adaptSectionTitle(String title, PerspectiveSettings settings) {
        if ("BUSINESS".equals(settings.getPerspective())) {
            return title.replace("Code Structure", "System Components")
                       .replace("Execution Flow", "Process Flow")
                       .replace("Architecture", "System Design");
        } else if ("USER".equals(settings.getPerspective())) {
            return title.replace("Code Structure", "How It's Built")
                       .replace("Execution Flow", "How It Works")
                       .replace("Architecture", "System Overview");
        }
        return title;
    }

    private static String adaptSectionContent(String content, PerspectiveSettings settings) {
        if ("BUSINESS".equals(settings.getPerspective())) {
            // Simplify technical language
            return content.replace("class", "component")
                         .replace("method", "function")
                         .replace("implementation", "approach");
        } else if ("USER".equals(settings.getPerspective())) {
            // Use plain language
            return content.replace("class", "part")
                         .replace("method", "action")
                         .replace("implementation", "way it works");
        }
        return content;
    }

    private static List<String> adaptSupportingDetails(List<String> details, PerspectiveSettings settings) {
        if ("USER".equals(settings.getPerspective())) {
            // Filter out highly technical details for user perspective
            return details.stream()
                .filter(detail -> !detail.contains("Confidence:") && !detail.contains("Source:"))
                .collect(Collectors.toList());
        }
        return details;
    }

    private static List<CodeExample> adaptCodeExamples(List<CodeExample> examples, PerspectiveSettings settings) {
        if ("NONE".equals(settings.getCodeExamples())) {
            return new ArrayList<>();
        } else if ("MINIMAL".equals(settings.getCodeExamples())) {
            return examples.stream().limit(2).collect(Collectors.toList());
        }
        return examples;
    }

    public static String buildConclusion(SynthesizedContent synthesized, PerspectiveSettings settings, String originalQuery) {
        StringBuilder conclusion = new StringBuilder();
        
        if ("BUSINESS".equals(settings.getPerspective())) {
            conclusion.append("In summary, the system demonstrates ");
            if ("COMPLEX".equals(synthesized.getComplexityLevel())) {
                conclusion.append("sophisticated business logic with multiple interconnected components. ");
                conclusion.append("This suggests a mature system that may require careful change management.");
            } else {
                conclusion.append("a well-structured approach that should be maintainable and scalable.");
            }
        } else if ("TECHNICAL".equals(settings.getPerspective())) {
            conclusion.append("Technical analysis reveals ");
            conclusion.append(synthesized.getKeyInformation().getKeyEntities().size())
                     .append(" key entities with ")
                     .append(synthesized.getKeyInformation().getKeyRelationships().size())
                     .append(" relationships. ");
            conclusion.append("The code demonstrates ")
                     .append(synthesized.getComplexityLevel().toLowerCase())
                     .append(" complexity patterns.");
        } else {
            conclusion.append("To answer your question \"").append(originalQuery).append("\": ");
            conclusion.append("The system works through a series of well-defined steps and components ");
            conclusion.append("that work together to achieve the desired functionality.");
        }
        
        return conclusion.toString();
    }

    public static List<String> extractKeyTakeaways(SynthesizedContent synthesized, PerspectiveSettings settings) {
        List<String> takeaways = new ArrayList<>();
        
        // Extract top insights as takeaways
        List<Insight> topInsights = synthesized.getConsolidatedInformation().getUniqueInsights()
            .stream()
            .sorted(Comparator.comparing(Insight::getImportance).reversed())
            .limit(5)
            .collect(Collectors.toList());
        
        for (Insight insight : topInsights) {
            String takeaway = adaptTakeawayForPerspective(insight, settings);
            takeaways.add(takeaway);
        }
        
        return takeaways;
    }

    private static String adaptTakeawayForPerspective(Insight insight, PerspectiveSettings settings) {
        if ("BUSINESS".equals(settings.getPerspective())) {
            return "Business Impact: " + simplifyTechnicalLanguage(insight.getDescription());
        } else if ("USER".equals(settings.getPerspective())) {
            return "Key Point: " + translateToPlainLanguage(insight.getDescription());
        } else {
            return "Technical Finding: " + insight.getDescription();
        }
    }

    private static String simplifyTechnicalLanguage(String text) {
        return text.replace("class", "component")
                  .replace("method", "process")
                  .replace("implementation", "approach")
                  .replace("interface", "contract");
    }

    private static String translateToPlainLanguage(String text) {
        return text.replace("class", "part")
                  .replace("method", "action")
                  .replace("implementation", "how it works")
                  .replace("interface", "connection point");
    }

    public static List<Evidence> buildSupportingEvidence(EvidenceStructure evidenceStructure, PerspectiveSettings settings) {
        List<Evidence> evidence = new ArrayList<>();
        
        evidence.addAll(evidenceStructure.getStructuralEvidence());
        evidence.addAll(evidenceStructure.getExecutionEvidence());
        evidence.addAll(evidenceStructure.getContextEvidence());
        
        // Filter evidence based on perspective
        return evidence.stream()
            .filter(e -> isEvidenceRelevantForPerspective(e, settings))
            .sorted(Comparator.comparing(Evidence::getRelevance).reversed())
            .collect(Collectors.toList());
    }

    private static boolean isEvidenceRelevantForPerspective(Evidence evidence, PerspectiveSettings settings) {
        if ("USER".equals(settings.getPerspective())) {
            // Users typically don't need highly technical evidence
            return !"CODE".equals(evidence.getType()) || evidence.getRelevance() > 0.8;
        } else if ("BUSINESS".equals(settings.getPerspective())) {
            // Business perspective focuses on patterns and documentation
            return "PATTERN".equals(evidence.getType()) || "DOCUMENTATION".equals(evidence.getType());
        }
        return true; // Technical perspective includes all evidence
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    private static double calculateImportanceFromConfidence(double confidence) {
        // Convert confidence to importance with some variance
        return Math.min(1.0, confidence * 1.2);
    }

    public static double calculateNarrativeConfidence(ValidationResults validation, Map<String, ReadabilityScore> readabilityScores) {
        double validationScore = validation.getOverallScore();
        double avgReadability = readabilityScores.values().stream()
            .mapToDouble(ReadabilityScore::getOverallScore)
            .average()
            .orElse(0.5);
        
        // Weight validation more heavily than readability
        return (validationScore * 0.7) + (avgReadability * 0.3);
    }

    public static String buildGenerationExplanation(SynthesizedContent synthesized, ValidationResults validation, double confidence) {
        StringBuilder explanation = new StringBuilder();
        
        explanation.append("Generated narrative from ")
                  .append(synthesized.getKeyInformation().getStructuralInsights().size() +
                         synthesized.getKeyInformation().getExecutionInsights().size() +
                         synthesized.getKeyInformation().getContextInsights().size())
                  .append(" insights across ")
                  .append(synthesized.getNarrativeThemes().size())
                  .append(" themes. ");
        
        explanation.append("Quality score: ").append(String.format("%.1f%%", validation.getOverallScore() * 100))
                  .append(", Confidence: ").append(String.format("%.1f%%", confidence * 100)).append(".");
        
        if (validation.getValidationIssues().size() > 0) {
            explanation.append(" Found ").append(validation.getValidationIssues().size())
                      .append(" validation issues for review.");
        }
        
        return explanation.toString();
    }

    public static List<String> determineNextActions(ValidationResults validation, double confidence, NarrativeConfiguration config) {
        List<String> actions = new ArrayList<>();
        
        if (confidence < 0.7) {
            actions.add("Review and improve narrative quality");
        }
        
        if (validation.getOverallScore() < 0.8) {
            actions.add("Address validation issues to improve accuracy");
        }
        
        if ("INTERACTIVE".equals(config.getInteractivityLevel())) {
            actions.add("Explore interactive elements for deeper understanding");
        }
        
        long highSeverityIssues = validation.getValidationIssues().stream()
            .filter(issue -> "HIGH".equals(issue.getSeverity()))
            .count();
        
        if (highSeverityIssues > 0) {
            actions.add("Resolve " + highSeverityIssues + " high-priority validation issues");
        }
        
        if (actions.isEmpty()) {
            actions.add("Narrative generation completed successfully");
        }
        
        return actions;
    }

    public static NarrativeContent selectPrimaryNarrative(Map<String, NarrativeContent> narrativeVariants, 
                                                         Map<String, ReadabilityScore> readabilityScores, 
                                                         List<String> perspectives) {
        
        // Select based on highest readability score, with preference for requested perspectives
        return narrativeVariants.entrySet().stream()
            .max((a, b) -> {
                double scoreA = readabilityScores.get(a.getKey()).getOverallScore();
                double scoreB = readabilityScores.get(b.getKey()).getOverallScore();
                
                // Boost score for preferred perspectives
                if (perspectives.contains(a.getKey())) scoreA += 0.1;
                if (perspectives.contains(b.getKey())) scoreB += 0.1;
                
                return Double.compare(scoreA, scoreB);
            })
            .map(Map.Entry::getValue)
            .orElse(narrativeVariants.values().iterator().next());
    }
}