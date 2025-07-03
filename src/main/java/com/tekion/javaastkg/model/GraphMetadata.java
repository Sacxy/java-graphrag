package com.tekion.javaastkg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphMetadata {
    
    private Integer totalNodes;
    private Integer totalEdges;
    private Map<String, Integer> nodeTypeCount;
    private Map<String, Integer> edgeTypeCount;
    private Long analysisTime;
    private Long analysisDurationMs;
    private List<String> analyzedPaths;
    private List<List<String>> circularDependencies;
    
    @Builder.Default
    private Boolean hasCycles = false;
    
    private Integer maxCycleLength;
    private Integer filesProcessed;
    private Integer errorCount;
    private List<String> errors;
    private Double density;
    private Integer maxPackageDepth;
    private Double averageMethodsPerClass;
    private Double averageFieldsPerClass;
}