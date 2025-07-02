package com.tekion.javaastkg.config;

import org.neo4j.driver.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

/**
 * Neo4j database configuration.
 * Provides connection management and driver configuration.
 */
@Configuration
@Slf4j
public class Neo4jConfig {

    @Value("${neo4j.uri}")
    private String uri;

    @Value("${neo4j.username}")
    private String username;

    @Value("${neo4j.password}")
    private String password;

    @Value("${neo4j.database:neo4j}")
    private String database;

    /**
     * Creates and configures the Neo4j driver with connection pooling
     * and proper timeout settings.
     */
    @Bean
    public Driver neo4jDriver() {
        log.info("Configuring Neo4j driver for URI: {}", uri);

        Config config = Config.builder()
                .withMaxConnectionPoolSize(50)
                .withConnectionAcquisitionTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .withMaxTransactionRetryTime(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);

        // Verify connectivity
        try (Session session = driver.session()) {
            session.run("RETURN 1").consume();
            log.info("Successfully connected to Neo4j database");
        } catch (Exception e) {
            log.error("Failed to connect to Neo4j", e);
            throw new RuntimeException("Neo4j connection failed", e);
        }

        return driver;
    }

    /**
     * Session configuration for database operations
     */
    @Bean
    public SessionConfig sessionConfig() {
        return SessionConfig.builder()
                .withDatabase(database)
                .withDefaultAccessMode(AccessMode.WRITE)
                .build();
    }
}
