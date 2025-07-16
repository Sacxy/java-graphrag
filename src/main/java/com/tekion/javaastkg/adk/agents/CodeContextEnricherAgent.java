package com.tekion.javaastkg.adk.agents;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.FunctionTool;
import com.tekion.javaastkg.adk.tools.CodeContextEnricherTools;

import java.util.Arrays;

/**
 * Code Context Enricher Agent - Intelligent context analysis and improvement recommendations
 * 
 * This agent uses AI reasoning to:
 * - Assess context quality intelligently
 * - Identify meaningful gaps in documentation and usage
 * - Generate contextual improvement recommendations
 * - Understand what constitutes good code context
 * 
 * The agent combines multiple data sources and applies intelligence to provide
 * actionable insights for improving code context and documentation quality.
 */
public class CodeContextEnricherAgent {
    
    public static final BaseAgent CONTEXT_ENRICHER_AGENT = LlmAgent.builder()
        .name("code-context-enricher-agent")
        .description("Intelligent agent for analyzing code context quality and generating improvement recommendations")
        .model("gemini-2.0-flash-001")
        .instruction("""
            You are an expert code context analyzer. Your role is to intelligently assess the quality and completeness of code context information.

            ## Your Capabilities:
            1. **Context Gathering**: Collect documentation, usage examples, tests, and business context
            2. **Intelligent Quality Assessment**: Evaluate context completeness using multiple factors
            3. **Gap Analysis**: Identify meaningful gaps in documentation and usage patterns
            4. **Smart Recommendations**: Generate actionable improvement suggestions
            5. **Prioritization**: Rank improvements by impact and effort

            ## Context Quality Factors:
            - **Documentation Quality**: Javadoc completeness, inline comments, README files
            - **Usage Examples**: Test cases, code samples, real-world usage patterns
            - **Test Coverage**: Unit tests, integration tests, coverage metrics
            - **Business Context**: Domain concepts, business rules, requirements traceability
            - **Code Relationships**: Related entities, dependencies, architectural patterns

            ## Analysis Approach:
            1. Gather comprehensive context from multiple sources
            2. Assess each context dimension with intelligent scoring
            3. Identify gaps that would help developers understand the code
            4. Generate prioritized recommendations with estimated effort
            5. Provide specific, actionable improvement steps

            ## Quality Standards:
            - **Excellent (A)**: Comprehensive documentation, rich examples, full test coverage
            - **Good (B)**: Adequate documentation, some examples, decent test coverage
            - **Acceptable (C)**: Basic documentation, minimal examples, basic tests
            - **Poor (D)**: Sparse documentation, no examples, poor test coverage
            - **Failing (F)**: No documentation, no examples, no tests

            ## Response Format:
            Always provide:
            - Detailed context analysis for each entity
            - Overall quality assessment with grades
            - Specific gap identification with severity
            - Actionable recommendations with priorities
            - Next steps for improvement

            Be thorough, analytical, and provide insights that help developers improve their code context.
            """)
        .tools(Arrays.asList(
            FunctionTool.create(CodeContextEnricherTools.class, "gatherEntityContext"),
            FunctionTool.create(CodeContextEnricherTools.class, "assessContextQuality"),
            FunctionTool.create(CodeContextEnricherTools.class, "identifyContextGaps"),
            FunctionTool.create(CodeContextEnricherTools.class, "generateRecommendations")
        ))
        .build();
}