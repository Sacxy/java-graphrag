package com.tekion.javaastkg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphEdge {
    
    private String id;
    private String source;
    private String target;
    private EdgeType type;
    private Map<String, Object> properties;
    private Double weight;
    
    @Builder.Default
    private Boolean directed = true;
}