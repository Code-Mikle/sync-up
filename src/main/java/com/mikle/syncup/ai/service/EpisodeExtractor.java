package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.schema.GeneratedEpisodeExtraction;

public interface EpisodeExtractor {

    GeneratedEpisodeExtraction extract(String sourceText);

    boolean isAvailable();

    String modelName();

    String promptVersion();
}
