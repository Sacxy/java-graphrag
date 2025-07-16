package com.tekion.javaastkg.adk.service;

import com.tekion.javaastkg.adk.tools.*;
import com.tekion.javaastkg.service.Neo4jService;
import com.tekion.javaastkg.agents.entity.registry.LuceneEntityIndex;
import com.tekion.javaastkg.query.services.ParallelSearchService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * ADK Tool Initializer
 * 
 * Initializes all ADK tools with required services on application startup.
 * This ensures tools have access to Neo4j and other dependencies.
 */
@Component
@Slf4j
public class AdkToolInitializer {
    
    private final Neo4jService neo4jService;
    private final LuceneEntityIndex luceneEntityIndex;
    private final ParallelSearchService parallelSearchService;
    private final EmbeddingModel embeddingModel;
    
    @Autowired
    public AdkToolInitializer(Neo4jService neo4jService, 
                             LuceneEntityIndex luceneEntityIndex,
                             ParallelSearchService parallelSearchService,
                             EmbeddingModel embeddingModel) {
        this.neo4jService = neo4jService;
        this.luceneEntityIndex = luceneEntityIndex;
        this.parallelSearchService = parallelSearchService;
        this.embeddingModel = embeddingModel;
    }
    
    @PostConstruct
    public void initializeTools() {
        log.info("🚀 Initializing ADK tools with services");
        
        try {
            // Initialize tools with step-by-step semantic capabilities
            SemanticCodeHunter.initialize(neo4jService, luceneEntityIndex, parallelSearchService, embeddingModel);
            log.info("✅ Initialized SemanticCodeHunter with semantic search capabilities");
            
            // Initialize other ADK tools with Neo4j service
            StructuralCodeExplorer.initialize(neo4jService);
            log.info("✅ Initialized StructuralCodeExplorer with Neo4jService");
            
            ExecutionPathTracer.initialize(neo4jService);
            log.info("✅ Initialized ExecutionPathTracer with Neo4jService");
            
            CodeContextEnricher.initialize(neo4jService);
            log.info("✅ Initialized CodeContextEnricher with Neo4jService");
            
            log.info("✅ All ADK tools initialized successfully");
            
        } catch (Exception e) {
            log.error("❌ Failed to initialize ADK tools", e);
            throw new RuntimeException("ADK tool initialization failed", e);
        }
    }
}