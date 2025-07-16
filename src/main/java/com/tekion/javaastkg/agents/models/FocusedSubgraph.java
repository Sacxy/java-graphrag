package com.tekion.javaastkg.agents.models;

import com.tekion.javaastkg.model.GraphEdge;
import com.tekion.javaastkg.model.GraphNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A focused subgraph around specific entities
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FocusedSubgraph {
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;
    private List<String> seedNodeIds;
    private String explorationFocus;
    private int nodeCount;
    private int edgeCount;
    
    public static FocusedSubgraph empty() {
        return FocusedSubgraph.builder()
            .nodes(List.of())
            .edges(List.of())
            .seedNodeIds(List.of())
            .nodeCount(0)
            .edgeCount(0)
            .build();
    }
}