package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 🧠 Orchestration Request - Input for intelligent orchestration
 * 
 * Represents high-level requests that require coordination of multiple tools:
 * - Complex analysis tasks spanning multiple domains
 * - Multi-step workflows with dependencies
 * - Adaptive tool selection based on context
 * - Comprehensive code understanding tasks
 * 
 * Domain Focus: COORDINATING multiple tools to achieve complex goals
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrchestrationRequest {
    
    /**
     * 📝 User's high-level query or task
     */
    private String userQuery;
    
    /**
     * 🎯 Primary goal of the orchestration
     */
    private OrchestrationGoal goal;
    
    /**
     * 📊 Context information for the request
     */
    private String context;
    
    /**
     * 🔍 Specific entities or areas to focus on
     */
    private List<String> focusEntities;
    
    /**
     * 🎭 Desired depth of analysis
     */
    @Builder.Default
    private AnalysisDepth analysisDepth = AnalysisDepth.MODERATE;
    
    /**
     * 🏢 Business domain context
     */
    private String businessDomain;
    
    /**
     * 🎪 Preferred orchestration style
     */
    @Builder.Default
    private OrchestrationStyle style = OrchestrationStyle.ADAPTIVE;
    
    /**
     * ⏱️ Time constraints for the analysis
     */
    private Integer maxTimeoutSeconds;
    
    /**
     * 📋 Specific requirements or constraints
     */
    private List<String> requirements;
    
    /**
     * 🎯 Expected output format
     */
    @Builder.Default
    private OutputFormat outputFormat = OutputFormat.COMPREHENSIVE;
    
    /**
     * ⚙️ Additional orchestration parameters
     */
    private Map<String, Object> parameters;
    
    public enum OrchestrationGoal {
        /**
         * 🔍 Understand code structure and behavior
         */
        CODE_UNDERSTANDING("Understand code structure, purpose, and behavior"),
        
        /**
         * 🚀 Analyze performance and optimization opportunities
         */
        PERFORMANCE_ANALYSIS("Analyze performance characteristics and optimization opportunities"),
        
        /**
         * 🔒 Security analysis and vulnerability assessment
         */
        SECURITY_ANALYSIS("Analyze security implications and identify vulnerabilities"),
        
        /**
         * 📊 Quality assessment and improvement recommendations
         */
        QUALITY_ASSESSMENT("Assess code quality and provide improvement recommendations"),
        
        /**
         * 🏗️ Architecture analysis and design patterns
         */
        ARCHITECTURE_ANALYSIS("Analyze architecture patterns and design decisions"),
        
        /**
         * 📖 Generate comprehensive documentation
         */
        DOCUMENTATION_GENERATION("Generate comprehensive documentation and explanations"),
        
        /**
         * 🔄 Impact analysis for changes
         */
        IMPACT_ANALYSIS("Analyze impact of potential changes or modifications"),
        
        /**
         * 🎯 Custom analysis based on specific requirements
         */
        CUSTOM_ANALYSIS("Custom analysis based on specific requirements");
        
        private final String description;
        
        OrchestrationGoal(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum AnalysisDepth {
        /**
         * 📝 High-level overview
         */
        SHALLOW("High-level overview and key insights"),
        
        /**
         * ⚖️ Moderate depth with important details
         */
        MODERATE("Moderate depth with important details and context"),
        
        /**
         * 🔍 Deep analysis with comprehensive coverage
         */
        DEEP("Deep analysis with comprehensive coverage and details"),
        
        /**
         * 🔬 Exhaustive analysis with maximum detail
         */
        EXHAUSTIVE("Exhaustive analysis with maximum detail and coverage");
        
        private final String description;
        
        AnalysisDepth(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum OrchestrationStyle {
        /**
         * 📏 Sequential execution of tools
         */
        SEQUENTIAL("Execute tools in sequential order"),
        
        /**
         * 🔄 Parallel execution where possible
         */
        PARALLEL("Execute tools in parallel where possible"),
        
        /**
         * 🎯 Adaptive based on context and requirements
         */
        ADAPTIVE("Adaptive execution based on context and requirements"),
        
        /**
         * 🚀 Optimized for speed
         */
        SPEED_OPTIMIZED("Optimized for fastest execution"),
        
        /**
         * 📊 Optimized for comprehensive results
         */
        QUALITY_OPTIMIZED("Optimized for most comprehensive results");
        
        private final String description;
        
        OrchestrationStyle(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum OutputFormat {
        /**
         * 📋 Structured summary
         */
        SUMMARY("Structured summary of key findings"),
        
        /**
         * 📊 Detailed analysis report
         */
        DETAILED("Detailed analysis report with comprehensive findings"),
        
        /**
         * 📖 Narrative documentation
         */
        NARRATIVE("Human-readable narrative documentation"),
        
        /**
         * 🔄 Comprehensive multi-format output
         */
        COMPREHENSIVE("Comprehensive output including all formats"),
        
        /**
         * 📈 Executive dashboard
         */
        DASHBOARD("Executive dashboard with key metrics and insights");
        
        private final String description;
        
        OutputFormat(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 🔍 Create request for code understanding
     */
    public static OrchestrationRequest codeUnderstanding(String query, List<String> entities) {
        return OrchestrationRequest.builder()
            .userQuery(query)
            .goal(OrchestrationGoal.CODE_UNDERSTANDING)
            .focusEntities(entities)
            .analysisDepth(AnalysisDepth.MODERATE)
            .style(OrchestrationStyle.ADAPTIVE)
            .outputFormat(OutputFormat.COMPREHENSIVE)
            .build();
    }
    
    /**
     * 🚀 Create request for performance analysis
     */
    public static OrchestrationRequest performanceAnalysis(String query, List<String> entities) {
        return OrchestrationRequest.builder()
            .userQuery(query)
            .goal(OrchestrationGoal.PERFORMANCE_ANALYSIS)
            .focusEntities(entities)
            .analysisDepth(AnalysisDepth.DEEP)
            .style(OrchestrationStyle.QUALITY_OPTIMIZED)
            .outputFormat(OutputFormat.DETAILED)
            .build();
    }
    
    /**
     * 🏗️ Create request for architecture analysis
     */
    public static OrchestrationRequest architectureAnalysis(String query, List<String> entities) {
        return OrchestrationRequest.builder()
            .userQuery(query)
            .goal(OrchestrationGoal.ARCHITECTURE_ANALYSIS)
            .focusEntities(entities)
            .analysisDepth(AnalysisDepth.DEEP)
            .style(OrchestrationStyle.ADAPTIVE)
            .outputFormat(OutputFormat.COMPREHENSIVE)
            .build();
    }
    
    /**
     * 📖 Create request for documentation generation
     */
    public static OrchestrationRequest documentationGeneration(String query, List<String> entities) {
        return OrchestrationRequest.builder()
            .userQuery(query)
            .goal(OrchestrationGoal.DOCUMENTATION_GENERATION)
            .focusEntities(entities)
            .analysisDepth(AnalysisDepth.MODERATE)
            .style(OrchestrationStyle.SEQUENTIAL)
            .outputFormat(OutputFormat.NARRATIVE)
            .build();
    }
    
    /**
     * 📊 Create request for quality assessment
     */
    public static OrchestrationRequest qualityAssessment(String query, List<String> entities) {
        return OrchestrationRequest.builder()
            .userQuery(query)
            .goal(OrchestrationGoal.QUALITY_ASSESSMENT)
            .focusEntities(entities)
            .analysisDepth(AnalysisDepth.DEEP)
            .style(OrchestrationStyle.PARALLEL)
            .outputFormat(OutputFormat.DASHBOARD)
            .build();
    }
}