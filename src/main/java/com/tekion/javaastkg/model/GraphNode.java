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
public class GraphNode {
    
    private String id;
    private NodeType type;
    private String label;
    private Map<String, Object> properties;
    private String sourceFile;
    private Integer lineNumber;
    private Integer columnNumber;
}