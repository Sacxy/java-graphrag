package com.tekion.javaastkg.ingestion;

import com.tekion.javaastkg.model.SpoonAST;
import org.neo4j.driver.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for constructing the knowledge graph in Neo4j.
 * Handles node creation and relationship establishment.
 */
@Service
@Slf4j
public class GraphBuilder {

    private final Driver neo4jDriver;
    private final SessionConfig sessionConfig;

    @Autowired
    public GraphBuilder(Driver neo4jDriver, SessionConfig sessionConfig) {
        this.neo4jDriver = neo4jDriver;
        this.sessionConfig = sessionConfig;
    }

    /**
     * Builds the complete graph from the AST data.
     * Creates nodes first, then establishes relationships.
     */
    public void buildGraph(SpoonAST ast) {
        log.info("Starting graph construction with {} classes and {} methods",
                ast.getClasses().size(), ast.getMethods().size());

        long startTime = System.currentTimeMillis();

        try (Session session = neo4jDriver.session(sessionConfig)) {
            // Clear existing data (for POC - in production, use incremental updates)
            clearGraph(session);

            // Create constraints and indexes
            createConstraintsAndIndexes(session);

            // Step 1: Create all nodes
            createClassNodes(session, ast.getClasses());
            createMethodNodes(session, ast.getMethods());
            createApiEndpointNodes(session, ast.getApiEndpoints());

            // Step 2: Create relationships
            createMethodRelationships(session, ast.getMethods());
            createClassRelationships(session, ast.getClasses());
            createApiEndpointRelationships(session, ast.getApiEndpoints());

            long duration = System.currentTimeMillis() - startTime;
            log.info("Graph construction completed in {} ms", duration);

        } catch (Exception e) {
            log.error("Failed to build graph", e);
            throw new RuntimeException("Graph construction failed", e);
        }
    }

    /**
     * Clears existing graph data - use with caution!
     */
    private void clearGraph(Session session) {
        log.warn("Clearing existing graph data");
        session.run("MATCH (n) DETACH DELETE n").consume();
    }

    /**
     * Creates database constraints and indexes for performance
     */
    private void createConstraintsAndIndexes(Session session) {
        log.info("Creating constraints and indexes");

        // Constraints
        session.run("CREATE CONSTRAINT IF NOT EXISTS FOR (c:Class) REQUIRE c.fullName IS UNIQUE").consume();
        session.run("CREATE CONSTRAINT IF NOT EXISTS FOR (m:Method) REQUIRE m.signature IS UNIQUE").consume();
        session.run("CREATE CONSTRAINT IF NOT EXISTS FOR (e:Endpoint) REQUIRE e.path IS UNIQUE").consume();

        // Indexes
        session.run("CREATE INDEX IF NOT EXISTS FOR (c:Class) ON (c.package)").consume();
        session.run("CREATE INDEX IF NOT EXISTS FOR (m:Method) ON (m.className)").consume();
        session.run("CREATE INDEX IF NOT EXISTS FOR (m:Method) ON (m.name)").consume();
    }

    /**
     * Creates class nodes in batch for efficiency
     */
    private void createClassNodes(Session session, List<SpoonAST.ClassInfo> classes) {
        log.info("Creating {} class nodes", classes.size());

        String query = """
            UNWIND $classes AS class
            MERGE (c:Class {fullName: class.fullName})
            SET c += {
                name: class.name,
                filePath: class.filePath,
                package: class.packageName,
                type: class.type,
                isInterface: class.isInterface,
                isAbstract: class.isAbstract,
                startLine: class.startLine,
                endLine: class.endLine,
                annotations: class.annotations,
                createdAt: datetime()
            }
            """;

        // Process in batches for better performance
        int batchSize = 100;
        for (int i = 0; i < classes.size(); i += batchSize) {
            List<Map<String, Object>> batch = classes
                    .subList(i, Math.min(i + batchSize, classes.size()))
                    .stream()
                    .map(this::classToMap)
                    .collect(Collectors.toList());

            session.run(query, Map.of("classes", batch)).consume();
            log.debug("Created class nodes batch {}/{}", i/batchSize + 1,
                    (classes.size() + batchSize - 1) / batchSize);
        }
    }

    /**
     * Creates method nodes with all metadata
     */
    private void createMethodNodes(Session session, List<SpoonAST.MethodInfo> methods) {
        log.info("Creating {} method nodes", methods.size());

        String query = """
            UNWIND $methods AS method
            MERGE (m:Method {signature: method.fullSignature})
            SET m += {
                name: method.name,
                className: method.className,
                modifier: method.modifier,
                returnType: method.returnType,
                startLine: method.startLine,
                endLine: method.endLine,
                isStatic: method.isStatic,
                arguments: method.arguments,
                annotations: method.annotations,
                exceptions: method.exceptions,
                createdAt: datetime()
            }
            """;

        int batchSize = 100;
        for (int i = 0; i < methods.size(); i += batchSize) {
            List<Map<String, Object>> batch = methods
                    .subList(i, Math.min(i + batchSize, methods.size()))
                    .stream()
                    .map(this::methodToMap)
                    .collect(Collectors.toList());

            session.run(query, Map.of("methods", batch)).consume();
        }
    }

    /**
     * Creates API endpoint nodes
     */
    private void createApiEndpointNodes(Session session, List<SpoonAST.ApiEndpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return;
        }

        log.info("Creating {} API endpoint nodes", endpoints.size());

        String query = """
            UNWIND $endpoints AS endpoint
            MERGE (e:Endpoint {path: endpoint.path, method: endpoint.method})
            SET e += {
                controllerClass: endpoint.controllerClass,
                handlerMethod: endpoint.handlerMethod,
                createdAt: datetime()
            }
            """;

        List<Map<String, Object>> endpointMaps = endpoints.stream()
                .map(this::endpointToMap)
                .collect(Collectors.toList());

        session.run(query, Map.of("endpoints", endpointMaps)).consume();
    }

    /**
     * Creates method relationships including DEFINED_IN and CALLS
     */
    private void createMethodRelationships(Session session, List<SpoonAST.MethodInfo> methods) {
        log.info("Creating method relationships");

        // DEFINED_IN relationships
        String definedInQuery = """
            UNWIND $methods AS method
            MATCH (m:Method {signature: method.signature})
            MATCH (c:Class {fullName: method.className})
            MERGE (m)-[:DEFINED_IN]->(c)
            """;

        List<Map<String, String>> methodRelData = methods.stream()
                .map(m -> Map.of(
                        "signature", m.getFullSignature(),
                        "className", m.getClassName()
                ))
                .toList();

        session.run(definedInQuery, Map.of("methods", methodRelData)).consume();

        // CALLS relationships
        for (SpoonAST.MethodInfo method : methods) {
            if (method.getCallsTo() != null && !method.getCallsTo().isEmpty()) {
                String callsQuery = """
                    MATCH (caller:Method {signature: $callerSignature})
                    UNWIND $calleeSignatures AS calleeSignature
                    MATCH (callee:Method {signature: calleeSignature})
                    MERGE (caller)-[:CALLS]->(callee)
                    """;

                session.run(callsQuery, Map.of(
                        "callerSignature", method.getFullSignature(),
                        "calleeSignatures", method.getCallsTo()
                )).consume();
            }
        }
    }

    /**
     * Creates class relationships (EXTENDS, IMPLEMENTS)
     */
    private void createClassRelationships(Session session, List<SpoonAST.ClassInfo> classes) {
        log.info("Creating class relationships");

        for (SpoonAST.ClassInfo classInfo : classes) {
            // EXTENDS relationship
            if (classInfo.getExtendsClass() != null && !classInfo.getExtendsClass().isEmpty()) {
                String extendsQuery = """
                    MATCH (child:Class {fullName: $childName})
                    MATCH (parent:Class {fullName: $parentName})
                    MERGE (child)-[:EXTENDS]->(parent)
                    """;

                try {
                    session.run(extendsQuery, Map.of(
                            "childName", classInfo.getFullName(),
                            "parentName", classInfo.getExtendsClass()
                    )).consume();
                } catch (Exception e) {
                    log.warn("Could not create EXTENDS relationship from {} to {}",
                            classInfo.getFullName(), classInfo.getExtendsClass());
                }
            }

            // IMPLEMENTS relationship
            if (classInfo.getImplementsClass() != null && !classInfo.getImplementsClass().isEmpty()) {
                String implementsQuery = """
                    MATCH (impl:Class {fullName: $implName})
                    MATCH (interface:Class {fullName: $interfaceName})
                    MERGE (impl)-[:IMPLEMENTS]->(interface)
                    """;

                try {
                    session.run(implementsQuery, Map.of(
                            "implName", classInfo.getFullName(),
                            "interfaceName", classInfo.getImplementsClass()
                    )).consume();
                } catch (Exception e) {
                    log.warn("Could not create IMPLEMENTS relationship from {} to {}",
                            classInfo.getFullName(), classInfo.getImplementsClass());
                }
            }
        }
    }

    /**
     * Creates relationships between API endpoints and their handler methods
     */
    private void createApiEndpointRelationships(Session session, List<SpoonAST.ApiEndpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return;
        }

        log.info("Creating API endpoint relationships");

        String query = """
            UNWIND $endpoints AS endpoint
            MATCH (e:Endpoint {path: endpoint.path, method: endpoint.method})
            MATCH (m:Method {signature: endpoint.handlerMethod})
            MERGE (e)-[:HANDLES]->(m)
            """;

        List<Map<String, String>> endpointRelData = endpoints.stream()
                .filter(e -> e.getHandlerMethod() != null)
                .map(e -> Map.of(
                        "path", e.getPath(),
                        "method", e.getMethod(),
                        "handlerMethod", e.getHandlerMethod()
                ))
                .toList();

        if (!endpointRelData.isEmpty()) {
            session.run(query, Map.of("endpoints", endpointRelData)).consume();
        }
    }

    // Helper methods to convert objects to maps for Neo4j

    private Map<String, Object> classToMap(SpoonAST.ClassInfo classInfo) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", classInfo.getName());
        map.put("fullName", classInfo.getFullName());
        map.put("filePath", classInfo.getFilePath());
        map.put("packageName", classInfo.getPackageName());
        map.put("type", classInfo.getType());
        map.put("isInterface", classInfo.isInterface());
        map.put("isAbstract", classInfo.isAbstract());
        map.put("startLine", classInfo.getStartLine());
        map.put("endLine", classInfo.getEndLine());
        map.put("annotations", classInfo.getAnnotations());
        return map;
    }

    private Map<String, Object> methodToMap(SpoonAST.MethodInfo method) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", method.getName());
        map.put("fullSignature", method.getFullSignature());
        map.put("className", method.getClassName());
        map.put("modifier", method.getModifier());
        map.put("returnType", method.getReturnType());
        map.put("startLine", method.getStartLine());
        map.put("endLine", method.getEndLine());
        map.put("isStatic", method.isStatic());
        map.put("arguments", method.getArguments());
        map.put("annotations", method.getAnnotations());
        map.put("exceptions", method.getExceptions());
        return map;
    }

    private Map<String, Object> endpointToMap(SpoonAST.ApiEndpoint endpoint) {
        Map<String, Object> map = new HashMap<>();
        map.put("path", endpoint.getPath());
        map.put("method", endpoint.getMethod());
        map.put("controllerClass", endpoint.getControllerClass());
        map.put("handlerMethod", endpoint.getHandlerMethod());
        return map;
    }
}
