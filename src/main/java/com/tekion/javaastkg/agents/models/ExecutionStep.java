package com.tekion.javaastkg.agents.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 👣 Execution Step - Single step in an execution path
 * 
 * Represents one step in the traced execution flow:
 * - Method calls, data transformations, control decisions
 * - Context information and parameters
 * - Timing and performance data
 * - Related code entities and dependencies
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStep {
    
    /**
     * 🆔 Unique identifier for this step
     */
    private String stepId;
    
    /**
     * 📈 Step number in the execution sequence
     */
    private int sequenceNumber;
    
    /**
     * 🎯 Type of execution step
     */
    private StepType stepType;
    
    /**
     * 📍 Method or entity being executed
     */
    private String methodName;
    
    /**
     * 🏢 Class containing the method
     */
    private String className;
    
    /**
     * 📝 Human-readable description of what happens
     */
    private String description;
    
    /**
     * 📊 Input parameters and data
     */
    private List<String> inputParameters;
    
    /**
     * 📤 Output or return values
     */
    private List<String> outputValues;
    
    /**
     * 🔗 Next possible steps in execution
     */
    private List<String> nextStepIds;
    
    /**
     * ⏱️ Performance characteristics
     */
    private Map<String, Object> performanceMetrics;
    
    /**
     * 🎭 Execution context and conditions
     */
    private Map<String, Object> executionContext;
    
    /**
     * 📏 Complexity and risk indicators
     */
    private StepComplexity complexity;
    
    /**
     * 🔍 Additional metadata about this step
     */
    private Map<String, Object> metadata;
    
    public enum StepType {
        METHOD_CALL("Method invocation"),
        DATA_TRANSFORMATION("Data processing or transformation"),
        CONDITION_CHECK("Conditional logic evaluation"),
        LOOP_ITERATION("Loop or iteration step"),
        EXCEPTION_HANDLING("Exception handling or recovery"),
        EXTERNAL_CALL("External service or API call"),
        DATABASE_OPERATION("Database query or update"),
        VALIDATION("Data validation or business rule check"),
        RETURN_VALUE("Method return or exit point");
        
        private final String description;
        
        StepType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepComplexity {
        private int cyclomaticComplexity;
        private int dataComplexity;
        private double riskScore;
        private List<String> riskFactors;
    }
    
    /**
     * 📞 Create a method call step
     */
    public static ExecutionStep methodCall(String methodName, String className, int sequenceNumber) {
        return ExecutionStep.builder()
            .stepType(StepType.METHOD_CALL)
            .methodName(methodName)
            .className(className)
            .sequenceNumber(sequenceNumber)
            .stepId(generateStepId(methodName, sequenceNumber))
            .description("Invoke " + methodName + " in " + className)
            .build();
    }
    
    /**
     * 🔀 Create a condition check step
     */
    public static ExecutionStep conditionCheck(String condition, int sequenceNumber) {
        return ExecutionStep.builder()
            .stepType(StepType.CONDITION_CHECK)
            .description("Evaluate condition: " + condition)
            .sequenceNumber(sequenceNumber)
            .stepId(generateStepId("condition", sequenceNumber))
            .build();
    }
    
    /**
     * 📊 Create a data transformation step
     */
    public static ExecutionStep dataTransformation(String description, int sequenceNumber) {
        return ExecutionStep.builder()
            .stepType(StepType.DATA_TRANSFORMATION)
            .description(description)
            .sequenceNumber(sequenceNumber)
            .stepId(generateStepId("transform", sequenceNumber))
            .build();
    }
    
    private static String generateStepId(String base, int sequence) {
        return base.replaceAll("[^a-zA-Z0-9]", "_") + "_" + sequence;
    }
    
    /**
     * ⚡ Check if this step is performance critical
     */
    public boolean isPerformanceCritical() {
        return complexity != null && complexity.getRiskScore() > 0.7;
    }
    
    /**
     * 🎯 Check if this step involves external dependencies
     */
    public boolean hasExternalDependencies() {
        return stepType == StepType.EXTERNAL_CALL || stepType == StepType.DATABASE_OPERATION;
    }
}