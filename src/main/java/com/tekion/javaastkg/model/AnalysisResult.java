package com.tekion.javaastkg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResult {
    
    private String analysisId;
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;
    private GraphMetadata metadata;
    private AnalysisStatus status;
    private Integer progress;
    private String currentPhase;
    private String errorMessage;
    private String errorStackTrace;
}