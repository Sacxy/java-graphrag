package com.tekion.javaastkg.query.services;

import com.tekion.javaastkg.model.QueryModels;
import com.tekion.javaastkg.query.QueryExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for the verification step in the query processing pipeline.
 * Validates generated answers against the actual graph data.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VerificationService {
    
    private final Driver neo4jDriver;
    private final SessionConfig sessionConfig;
    
    /**
     * Executes the verification step
     */
    @Async("stepExecutor")
    public CompletableFuture<QueryExecutionContext> verify(QueryExecutionContext context) {
        log.debug("Executing verification [{}]", context.getExecutionId());
        
        try {
            if (context.getGeneratedResult() == null) {
                log.warn("No generated result available for verification [{}]", context.getExecutionId());
                context.setVerified(false);
                context.addVerificationError("No result to verify");
                return CompletableFuture.completedFuture(context);
            }
            
            QueryModels.QueryResult result = context.getGeneratedResult();
            List<String> errors = new ArrayList<>();
            int totalRelationships = result.getRelationships().size();
            int verifiedRelationships = 0;
            
            // Verify each relationship mentioned in the result
            for (QueryModels.RelationshipInsight relationship : result.getRelationships()) {
                if (verifyRelationship(relationship)) {
                    relationship.setVerified(true);
                    verifiedRelationships++;
                } else {
                    errors.add(String.format("Invalid relationship claim: %s -> %s (%s)",
                            relationship.getFromComponent(), 
                            relationship.getToComponent(), 
                            relationship.getRelationshipType()));
                }
            }
            
            boolean isValid = errors.isEmpty();
            context.setVerified(isValid);
            context.setVerificationErrors(errors);
            
            // Add verification metadata
            context.getMetadata().put("verificationAttempt", context.getRefinementCount() + 1);
            context.getMetadata().put("totalRelationships", totalRelationships);
            context.getMetadata().put("verifiedRelationships", verifiedRelationships);
            context.getMetadata().put("verificationSuccessRate", 
                    totalRelationships == 0 ? 1.0 : (double) verifiedRelationships / totalRelationships);
            
            log.debug("Verification result: {} ({}/{} relationships verified) [{}]", 
                    isValid, verifiedRelationships, totalRelationships, context.getExecutionId());
            
            return CompletableFuture.completedFuture(context);
        } catch (Exception e) {
            log.error("Verification failed [{}]", context.getExecutionId(), e);
            context.getMetadata().put("verificationError", e.getMessage());
            context.setVerified(false);
            context.addVerificationError("Verification process failed: " + e.getMessage());
            return CompletableFuture.completedFuture(context);
        }
    }
    
    /**
     * Verifies if a claimed relationship actually exists in the graph
     */
    private boolean verifyRelationship(QueryModels.RelationshipInsight relationship) {
        try (Session session = neo4jDriver.session(sessionConfig)) {
            String query = """
                MATCH (from {name: $fromName})-[r]->(to {name: $toName})
                WHERE type(r) = $relType
                RETURN count(r) > 0 as exists
                """;
            
            Map<String, Object> params = Map.of(
                    "fromName", relationship.getFromComponent(),
                    "toName", relationship.getToComponent(),
                    "relType", relationship.getRelationshipType()
            );
            
            Record result = session.run(query, params).single();
            return result.get("exists").asBoolean();
            
        } catch (Exception e) {
            log.warn("Failed to verify relationship: {} [relationship={}]", 
                    e.getMessage(), relationship, e);
            return false;
        }
    }
}