package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 📖 Narrative Generation Request - Input for generating flow narratives
 * 
 * Represents different types of narrative generation requests:
 * - Creating human-readable explanations of code flows
 * - Generating documentation from code analysis
 * - Building stories around execution paths
 * - Creating comprehensive flow explanations
 * 
 * Domain Focus: GENERATING human-readable narratives from code analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NarrativeGenerationRequest {
    
    /**
     * 🎯 Target entities or flows to generate narratives for
     */
    private List<String> targetEntities;
    
    /**
     * 📖 Type of narrative to generate
     */
    private NarrativeType narrativeType;
    
    /**
     * 🎭 Intended audience for the narrative
     */
    private AudienceType audience;
    
    /**
     * 📊 Level of detail in the narrative
     */
    @Builder.Default
    private DetailLevel detailLevel = DetailLevel.MODERATE;
    
    /**
     * 🎨 Narrative style and tone
     */
    @Builder.Default
    private NarrativeStyle style = NarrativeStyle.TECHNICAL;
    
    /**
     * 🔍 Focus areas for the narrative
     */
    private List<NarrativeFocus> focusAreas;
    
    /**
     * 📚 Include supporting context and examples
     */
    @Builder.Default
    private boolean includeContext = true;
    
    /**
     * 🎯 Include recommendations and insights
     */
    @Builder.Default
    private boolean includeRecommendations = true;
    
    /**
     * 📊 Include metrics and performance data
     */
    @Builder.Default
    private boolean includeMetrics = false;
    
    /**
     * 🎨 Include visualizations and diagrams
     */
    @Builder.Default
    private boolean includeVisualizations = false;
    
    /**
     * ⚙️ Additional narrative generation parameters
     */
    private Map<String, Object> generationParameters;
    
    public enum NarrativeType {
        /**
         * 📝 Code flow explanation
         */
        FLOW_EXPLANATION("Explain how code flows and executes"),
        
        /**
         * 🏢 Business process documentation
         */
        BUSINESS_PROCESS("Document business processes implemented in code"),
        
        /**
         * 🎯 User journey mapping
         */
        USER_JOURNEY("Map user journeys through system interactions"),
        
        /**
         * 🔍 Problem analysis story
         */
        PROBLEM_ANALYSIS("Analyze and explain problems or issues"),
        
        /**
         * 📊 Performance analysis narrative
         */
        PERFORMANCE_ANALYSIS("Analyze and explain performance characteristics"),
        
        /**
         * 🎨 Architecture overview
         */
        ARCHITECTURE_OVERVIEW("Provide architectural overview and explanations"),
        
        /**
         * 📚 Comprehensive documentation
         */
        COMPREHENSIVE_DOCUMENTATION("Generate comprehensive documentation");
        
        private final String description;
        
        NarrativeType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum AudienceType {
        /**
         * 👨‍💻 Technical developers
         */
        DEVELOPERS("Technical developers and engineers"),
        
        /**
         * 🏢 Business stakeholders
         */
        BUSINESS_STAKEHOLDERS("Business stakeholders and product owners"),
        
        /**
         * 📊 System architects
         */
        ARCHITECTS("System architects and technical leads"),
        
        /**
         * 🎓 New team members
         */
        NEW_TEAM_MEMBERS("New team members and onboarding"),
        
        /**
         * 🔍 Code reviewers
         */
        CODE_REVIEWERS("Code reviewers and quality assurance"),
        
        /**
         * 🌐 General audience
         */
        GENERAL("General technical audience");
        
        private final String description;
        
        AudienceType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum DetailLevel {
        /**
         * 📝 High-level overview
         */
        HIGH_LEVEL("High-level overview with key concepts"),
        
        /**
         * ⚖️ Moderate detail
         */
        MODERATE("Moderate detail with important specifics"),
        
        /**
         * 🔍 Detailed analysis
         */
        DETAILED("Detailed analysis with comprehensive coverage"),
        
        /**
         * 🔬 Deep dive
         */
        DEEP_DIVE("Deep dive with extensive technical details");
        
        private final String description;
        
        DetailLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum NarrativeStyle {
        /**
         * 🔧 Technical and precise
         */
        TECHNICAL("Technical and precise language"),
        
        /**
         * 🏢 Business-focused
         */
        BUSINESS_FOCUSED("Business-focused and outcome-oriented"),
        
        /**
         * 🎓 Educational and explanatory
         */
        EDUCATIONAL("Educational with learning focus"),
        
        /**
         * 📊 Analytical and data-driven
         */
        ANALYTICAL("Analytical with data and metrics focus"),
        
        /**
         * 📖 Narrative storytelling
         */
        STORYTELLING("Narrative storytelling approach"),
        
        /**
         * 🎯 Conversational and approachable
         */
        CONVERSATIONAL("Conversational and approachable tone");
        
        private final String description;
        
        NarrativeStyle(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum NarrativeFocus {
        BUSINESS_VALUE,
        TECHNICAL_IMPLEMENTATION,
        PERFORMANCE_CHARACTERISTICS,
        SECURITY_CONSIDERATIONS,
        ERROR_HANDLING,
        USER_EXPERIENCE,
        ARCHITECTURAL_PATTERNS,
        BEST_PRACTICES,
        POTENTIAL_IMPROVEMENTS,
        RISK_ASSESSMENT
    }
    
    /**
     * 📝 Create request for flow explanation
     */
    public static NarrativeGenerationRequest flowExplanation(List<String> entities, AudienceType audience) {
        return NarrativeGenerationRequest.builder()
            .targetEntities(entities)
            .narrativeType(NarrativeType.FLOW_EXPLANATION)
            .audience(audience)
            .detailLevel(DetailLevel.MODERATE)
            .style(NarrativeStyle.TECHNICAL)
            .focusAreas(List.of(
                NarrativeFocus.TECHNICAL_IMPLEMENTATION,
                NarrativeFocus.BUSINESS_VALUE,
                NarrativeFocus.PERFORMANCE_CHARACTERISTICS
            ))
            .includeContext(true)
            .includeRecommendations(true)
            .build();
    }
    
    /**
     * 🏢 Create request for business process documentation
     */
    public static NarrativeGenerationRequest businessProcess(List<String> entities) {
        return NarrativeGenerationRequest.builder()
            .targetEntities(entities)
            .narrativeType(NarrativeType.BUSINESS_PROCESS)
            .audience(AudienceType.BUSINESS_STAKEHOLDERS)
            .detailLevel(DetailLevel.MODERATE)
            .style(NarrativeStyle.BUSINESS_FOCUSED)
            .focusAreas(List.of(
                NarrativeFocus.BUSINESS_VALUE,
                NarrativeFocus.USER_EXPERIENCE,
                NarrativeFocus.POTENTIAL_IMPROVEMENTS
            ))
            .includeContext(true)
            .includeRecommendations(true)
            .build();
    }
    
    /**
     * 🎯 Create request for user journey mapping
     */
    public static NarrativeGenerationRequest userJourney(List<String> entities) {
        return NarrativeGenerationRequest.builder()
            .targetEntities(entities)
            .narrativeType(NarrativeType.USER_JOURNEY)
            .audience(AudienceType.BUSINESS_STAKEHOLDERS)
            .detailLevel(DetailLevel.MODERATE)
            .style(NarrativeStyle.STORYTELLING)
            .focusAreas(List.of(
                NarrativeFocus.USER_EXPERIENCE,
                NarrativeFocus.BUSINESS_VALUE,
                NarrativeFocus.POTENTIAL_IMPROVEMENTS
            ))
            .includeContext(true)
            .includeVisualizations(true)
            .build();
    }
    
    /**
     * 🎨 Create request for architecture overview
     */
    public static NarrativeGenerationRequest architectureOverview(List<String> entities, AudienceType audience) {
        return NarrativeGenerationRequest.builder()
            .targetEntities(entities)
            .narrativeType(NarrativeType.ARCHITECTURE_OVERVIEW)
            .audience(audience)
            .detailLevel(DetailLevel.DETAILED)
            .style(NarrativeStyle.TECHNICAL)
            .focusAreas(List.of(
                NarrativeFocus.ARCHITECTURAL_PATTERNS,
                NarrativeFocus.TECHNICAL_IMPLEMENTATION,
                NarrativeFocus.BEST_PRACTICES
            ))
            .includeContext(true)
            .includeRecommendations(true)
            .includeVisualizations(true)
            .build();
    }
    
    /**
     * 📊 Create request for performance analysis
     */
    public static NarrativeGenerationRequest performanceAnalysis(List<String> entities) {
        return NarrativeGenerationRequest.builder()
            .targetEntities(entities)
            .narrativeType(NarrativeType.PERFORMANCE_ANALYSIS)
            .audience(AudienceType.DEVELOPERS)
            .detailLevel(DetailLevel.DETAILED)
            .style(NarrativeStyle.ANALYTICAL)
            .focusAreas(List.of(
                NarrativeFocus.PERFORMANCE_CHARACTERISTICS,
                NarrativeFocus.TECHNICAL_IMPLEMENTATION,
                NarrativeFocus.POTENTIAL_IMPROVEMENTS
            ))
            .includeContext(true)
            .includeMetrics(true)
            .includeRecommendations(true)
            .build();
    }
    
    /**
     * 📚 Create request for comprehensive documentation
     */
    public static NarrativeGenerationRequest comprehensiveDocumentation(List<String> entities, AudienceType audience) {
        return NarrativeGenerationRequest.builder()
            .targetEntities(entities)
            .narrativeType(NarrativeType.COMPREHENSIVE_DOCUMENTATION)
            .audience(audience)
            .detailLevel(DetailLevel.DETAILED)
            .style(NarrativeStyle.EDUCATIONAL)
            .focusAreas(List.of(NarrativeFocus.values()))
            .includeContext(true)
            .includeRecommendations(true)
            .includeMetrics(true)
            .includeVisualizations(true)
            .build();
    }
}