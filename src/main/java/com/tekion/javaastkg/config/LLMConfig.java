package com.tekion.javaastkg.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.voyageai.VoyageAiEmbeddingModel;
import dev.langchain4j.model.voyageai.VoyageAiEmbeddingModelName;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import lombok.extern.slf4j.Slf4j;
import java.time.Duration;

/**
 * Configuration for Language Model integrations.
 * Supports multiple models for different tasks.
 */
@Configuration
@Slf4j
public class LLMConfig {

    @Value("${llm.openai.api-key}")
    private String openAiApiKey;

    @Value("${llm.voyage.api-key}")
    private String voyageApiKey;

    /**
     * Chat model for semantic enrichment (bulk processing)
     */
    @Bean
    @Qualifier("semanticEnricherModel")
    public ChatLanguageModel semanticEnricherModel(
            @Value("${llm.semantic-enricher.model:gpt-4o-mini}") String modelName,
            @Value("${llm.semantic-enricher.temperature:0.1}") double temperature,
            @Value("${llm.semantic-enricher.max-tokens:2000}") int maxTokens) {
        
        log.info("Configuring Semantic Enricher model: {}", modelName);
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(3)
                .logRequests(false) // Reduce logs for bulk processing
                .logResponses(false)
                .build();
    }

    /**
     * Chat model for context distillation (simple classification)
     */
    @Bean
    @Qualifier("contextDistillerModel")
    public ChatLanguageModel contextDistillerModel(
            @Value("${llm.context-distiller.model:gpt-4o-mini}") String modelName,
            @Value("${llm.context-distiller.temperature:0.0}") double temperature,
            @Value("${llm.context-distiller.max-tokens:500}") int maxTokens) {
        
        log.info("Configuring Context Distiller model: {}", modelName);
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(3)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    /**
     * Chat model for generation service (user-facing responses)
     */
    @Bean
    @Qualifier("generationServiceModel")
    @Primary
    public ChatLanguageModel generationServiceModel(
            @Value("${llm.generation-service.model:gpt-4o}") String modelName,
            @Value("${llm.generation-service.temperature:0.1}") double temperature,
            @Value("${llm.generation-service.max-tokens:2000}") int maxTokens) {
        
        log.info("Configuring Generation Service model: {}", modelName);
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(3)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * Legacy bean for backward compatibility
     */
    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Qualifier("generationServiceModel") ChatLanguageModel generationModel) {
        return generationModel;
    }

    /**
     * Configures the Voyage AI embedding model for document vectorization (unstructured text)
     */
    @Bean
    @Qualifier("documentEmbeddingModel")
    public EmbeddingModel documentEmbeddingModel() {
        
        log.info("Configuring Voyage AI document embedding model: voyage-code-3");

        return VoyageAiEmbeddingModel.builder()
                .apiKey(voyageApiKey)
                .modelName("voyage-code-3") // Better for unstructured text
                .inputType("document") // For creating embeddings
                .timeout(Duration.ofSeconds(60))
                .maxRetries(3)
                .logRequests(true)
                .logResponses(false)
                .build();
    }
    
    /**
     * Configures the Voyage AI embedding model for search queries
     */
    @Bean
    @Qualifier("queryEmbeddingModel")
    @Primary
    public EmbeddingModel queryEmbeddingModel() {

        log.info("Configuring Voyage AI document embedding model: voyage-code-3");

        return VoyageAiEmbeddingModel.builder()
                .apiKey(voyageApiKey)
                .modelName("voyage-code-3")
                .inputType("query") // For search queries
                .timeout(Duration.ofSeconds(60))
                .maxRetries(3)
                .logRequests(true)
                .logResponses(false)
                .build();
    }
}
