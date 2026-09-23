package com.trialsync.backend.research.rag;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

/**
 * Spring configuration wiring LangChain4j embedding model, in-memory vector store,
 * and Gemini chat model for trial criteria RAG.
 */
@Configuration
public class RagConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RagConfiguration.class);

    @Bean
    public EmbeddingStore<TextSegment> criteriaEmbeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    public EmbeddingModel criteriaEmbeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    public GeminiChatModelHolder geminiChatModelHolder(ResearchRagProperties properties) {
        if (!properties.hasGeminiApiKey() || "disabled".equalsIgnoreCase(properties.getProvider())) {
            log.info("Gemini RAG provider is unconfigured or disabled (no GEMINI_API_KEY). Initializing holder as unavailable.");
            return new GeminiChatModelHolder(null, false);
        }

        try {
            ChatLanguageModel geminiModel = GoogleAiGeminiChatModel.builder()
                    .apiKey(properties.getGeminiApiKey())
                    .modelName(properties.getGeminiModel())
                    .temperature(0.0)
                    .timeout(Duration.ofSeconds((long) properties.getTimeoutSeconds()))
                    .maxRetries(properties.getMaxRetries())
                    .build();
            log.info("Initialized Gemini ChatLanguageModel for RAG using model: {}", properties.getGeminiModel());
            return new GeminiChatModelHolder(geminiModel, true);
        } catch (Exception e) {
            log.warn("Failed to initialize Gemini model: {}. Setting holder to unavailable.", e.getMessage());
            return new GeminiChatModelHolder(null, false);
        }
    }
}
