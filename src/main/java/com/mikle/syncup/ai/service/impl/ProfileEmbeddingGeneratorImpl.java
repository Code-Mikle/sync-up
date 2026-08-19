package com.mikle.syncup.ai.service.impl;

import com.mikle.syncup.ai.config.AiEmbeddingProperties;
import com.mikle.syncup.ai.model.schema.GeneratedEmbedding;
import com.mikle.syncup.ai.service.ProfileEmbeddingGenerator;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ProfileEmbeddingGeneratorImpl implements ProfileEmbeddingGenerator {

    @Resource
    private AiEmbeddingProperties properties;

    private volatile EmbeddingModel embeddingModel;

    @Override
    public GeneratedEmbedding generate(String text) {
        if (!isAvailable()) {
            throw new IllegalStateException("profile embedding generator is unavailable");
        }
        Embedding embedding = model().embed(text).content();
        if (embedding == null) {
            throw new IllegalStateException("embedding provider returned empty result");
        }
        float[] vector = embedding.vector();
        Integer configuredDimensions = properties.getDimensions();
        if (configuredDimensions != null && configuredDimensions > 0
                && vector.length != configuredDimensions) {
            throw new IllegalStateException("embedding dimension does not match configuration");
        }
        return new GeneratedEmbedding(modelName(), vector);
    }

    @Override
    public boolean isAvailable() {
        return properties.available();
    }

    @Override
    public String modelName() {
        return properties.getModel();
    }

    private EmbeddingModel model() {
        EmbeddingModel current = embeddingModel;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (embeddingModel == null) {
                OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                        .baseUrl(properties.getBaseUrl())
                        .apiKey(properties.getApiKey())
                        .modelName(properties.getModel())
                        .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                        .maxRetries(Math.max(0, properties.getMaxRetries()))
                        .logRequests(properties.isLogRequests())
                        .logResponses(properties.isLogResponses());
                if (properties.getDimensions() != null && properties.getDimensions() > 0) {
                    builder.dimensions(properties.getDimensions());
                }
                embeddingModel = builder.build();
            }
            return embeddingModel;
        }
    }
}
