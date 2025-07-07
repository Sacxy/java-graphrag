package com.tekion.javaastkg.debug;

import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;

import java.util.List;
import java.util.Map;

/**
 * Simple test to check Neo4j embeddings status
 */
public class CheckEmbeddings {
    
    private static final String NEO4J_URI = "bolt://localhost:7687";
    private static final String NEO4J_USERNAME = "neo4j";
    private static final String NEO4J_PASSWORD = "tekion123";
    
    public static void main(String[] args) {
        // Create driver
        Config config = Config.builder()
                .withMaxConnectionPoolSize(50)
                .withConnectionAcquisitionTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .withMaxTransactionRetryTime(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
                
        try (Driver driver = GraphDatabase.driver(NEO4J_URI, AuthTokens.basic(NEO4J_USERNAME, NEO4J_PASSWORD), config)) {
            
            System.out.println("=== Neo4j Embeddings Check ===\n");
            
            try (Session session = driver.session()) {
                
                // 1. Check total Method nodes
                Result totalResult = session.run("MATCH (m:Method) RETURN count(m) as total");
                long totalMethods = totalResult.single().get("total").asLong();
                System.out.println("1. Total Method nodes: " + totalMethods);
                
                // 2. Check Method nodes with embeddings
                Result embeddingResult = session.run("MATCH (m:Method) WHERE m.embedding IS NOT NULL RETURN count(m) as withEmbeddings");
                long methodsWithEmbeddings = embeddingResult.single().get("withEmbeddings").asLong();
                System.out.println("2. Method nodes with embeddings: " + methodsWithEmbeddings);
                
                // Calculate percentage
                if (totalMethods > 0) {
                    double percentage = (methodsWithEmbeddings * 100.0) / totalMethods;
                    System.out.println("   Percentage with embeddings: " + String.format("%.2f", percentage) + "%");
                }
                
                // 3. Check if vector index exists
                System.out.println("\n3. Vector index check:");
                Result indexResult = session.run("SHOW INDEXES");
                boolean vectorIndexFound = false;
                
                while (indexResult.hasNext()) {
                    Record record = indexResult.next();
                    String indexName = record.get("name").asString();
                    String indexType = record.get("type").asString();
                    
                    if (indexName.equals("method_embeddings") || indexType.contains("VECTOR")) {
                        vectorIndexFound = true;
                        System.out.println("   Found vector index: " + indexName);
                        System.out.println("   Type: " + indexType);
                        System.out.println("   State: " + record.get("state").asString());
                        
                        // Try to get more details about the index
                        try {
                            List<Object> labelsOrTypes = record.get("labelsOrTypes").asList();
                            List<Object> properties = record.get("properties").asList();
                            System.out.println("   Labels: " + labelsOrTypes);
                            System.out.println("   Properties: " + properties);
                            
                            // Check if options field exists for vector dimension info
                            if (record.containsKey("options")) {
                                Map<String, Object> options = record.get("options").asMap();
                                System.out.println("   Options: " + options);
                            }
                        } catch (Exception e) {
                            // Some fields might not exist in all Neo4j versions
                        }
                    }
                }
                
                if (!vectorIndexFound) {
                    System.out.println("   No vector index 'method_embeddings' found");
                }
                
                // 4. Check embedding dimensions
                System.out.println("\n4. Embedding dimension check:");
                Result dimensionResult = session.run(
                    "MATCH (m:Method) " +
                    "WHERE m.embedding IS NOT NULL " +
                    "RETURN m.name as methodName, size(m.embedding) as dimension " +
                    "LIMIT 5"
                );
                
                boolean foundEmbedding = false;
                while (dimensionResult.hasNext()) {
                    Record record = dimensionResult.next();
                    String methodName = record.get("methodName").asString();
                    int dimension = record.get("dimension").asInt();
                    System.out.println("   Method: " + methodName + " - Dimension: " + dimension);
                    foundEmbedding = true;
                }
                
                if (!foundEmbedding) {
                    System.out.println("   No embeddings found to check dimensions");
                }
                
                // Additional check: Get statistics about embeddings
                System.out.println("\n5. Additional embedding statistics:");
                Result statsResult = session.run(
                    "MATCH (m:Method) " +
                    "WHERE m.embedding IS NOT NULL " +
                    "RETURN " +
                    "min(size(m.embedding)) as minDim, " +
                    "max(size(m.embedding)) as maxDim, " +
                    "avg(size(m.embedding)) as avgDim"
                );
                
                if (statsResult.hasNext()) {
                    Record stats = statsResult.single();
                    try {
                        System.out.println("   Min dimension: " + stats.get("minDim").asInt());
                        System.out.println("   Max dimension: " + stats.get("maxDim").asInt());
                        System.out.println("   Avg dimension: " + String.format("%.2f", stats.get("avgDim").asDouble()));
                    } catch (Exception e) {
                        System.out.println("   No embedding statistics available");
                    }
                }
                
                // Check for any errors or issues
                System.out.println("\n6. Checking for potential issues:");
                
                // Check for null embeddings
                Result nullCheckResult = session.run(
                    "MATCH (m:Method) " +
                    "WHERE m.embedding IS NULL " +
                    "RETURN count(m) as nullCount"
                );
                long nullCount = nullCheckResult.single().get("nullCount").asLong();
                if (nullCount > 0) {
                    System.out.println("   Methods without embeddings: " + nullCount);
                }
                
                // Check for empty embeddings
                Result emptyCheckResult = session.run(
                    "MATCH (m:Method) " +
                    "WHERE m.embedding IS NOT NULL AND size(m.embedding) = 0 " +
                    "RETURN count(m) as emptyCount"
                );
                long emptyCount = emptyCheckResult.single().get("emptyCount").asLong();
                if (emptyCount > 0) {
                    System.out.println("   Methods with empty embeddings: " + emptyCount);
                }
                
            } catch (Exception e) {
                System.err.println("Error during checks: " + e.getMessage());
                e.printStackTrace();
            }
            
            System.out.println("\n=== Check Complete ===");
            
        } catch (Exception e) {
            System.err.println("Failed to connect to Neo4j: " + e.getMessage());
            e.printStackTrace();
        }
    }
}