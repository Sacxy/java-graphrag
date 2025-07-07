package com.tekion.javaastkg.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class CypherQueryLoader {
    
    private final Map<String, String> queryCache = new ConcurrentHashMap<>();
    
    /**
     * Load a Cypher query from resources
     */
    public String loadQuery(String queryPath) {
        return queryCache.computeIfAbsent(queryPath, path -> {
            try {
                ClassPathResource resource = new ClassPathResource(path);
                String query = Files.readString(Path.of(resource.getURI()));
                log.debug("Loaded query from: {}", path);
                return query;
            } catch (IOException e) {
                log.error("Failed to load query from: {}", path, e);
                throw new RuntimeException("Failed to load Cypher query: " + path, e);
            }
        });
    }
    
    /**
     * Get node creation query for a specific node type
     */
    public String getNodeQuery(String nodeType) {
        String path = String.format("cypher/nodes/%s.cypher", nodeType.toLowerCase());
        return loadQuery(path);
    }
    
    /**
     * Get edge creation query for a specific edge type
     */
    public String getEdgeQuery(String edgeType) {
        String path = String.format("cypher/edges/%s.cypher", edgeType.toLowerCase());
        return loadQuery(path);
    }
}