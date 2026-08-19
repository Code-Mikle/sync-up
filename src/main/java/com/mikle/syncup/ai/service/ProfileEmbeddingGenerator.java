package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.schema.GeneratedEmbedding;

public interface ProfileEmbeddingGenerator {

    GeneratedEmbedding generate(String text);

    boolean isAvailable();

    String modelName();
}
