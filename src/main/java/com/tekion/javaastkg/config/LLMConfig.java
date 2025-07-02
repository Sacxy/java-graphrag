package com.tekion.javaastkg.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.voyageai.VoyageAiEmbeddingModel;
import dev.langchain4j.model.voyageai.VoyageAiEmbeddingModelName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;
import java.time.Duration;

/**
 * Configuration for Language Model integrations.
 * Supports both chat models and embedding models.
 */
@Configuration
@Slf4j
public class LLMConfig {

    @Value("${llm.openai.api-key}")
    private String openAiApiKey;

    @Value("${llm.openai.model:gpt-4}")
    private String chatModelName;

    @Value("${llm.openai.temperature:0.1}")
    private double temperature;

    @Value("${llm.openai.max-tokens:2000}")
    private int maxTokens;

    @Value("${llm.voyage.api-key}")
    private String voyageApiKey;

    @Value("${llm.voyage.model:voyage-code-3}")
    private String embeddingModelName;

    /**
     * Configures the main chat model for semantic enrichment and query processing
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("Configuring OpenAI chat model: {}", chatModelName);

        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(chatModelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(3)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * Configures the Voyage AI embedding model for code vectorization
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("Configuring Voyage AI embedding model: {}", embeddingModelName);

        return VoyageAiEmbeddingModel.builder()
                .apiKey(voyageApiKey)
                .modelName(embeddingModelName)
                .inputType("document") // Use "document" for indexing, "query" for search queries
                .timeout(Duration.ofSeconds(60))
                .maxRetries(3)
                .logRequests(true)
                .logResponses(false)
                .build();
    }
}
