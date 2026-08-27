package com.mikle.syncup.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.agent.EpisodeExtractionAgent;
import com.mikle.syncup.ai.config.AiAgentProperties;
import com.mikle.syncup.ai.model.schema.GeneratedEpisodeExtraction;
import com.mikle.syncup.ai.service.EpisodeExtractor;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class EpisodeExtractorImpl implements EpisodeExtractor {

    private static final String PROMPT_VERSION = "episode-extraction-v1";

    @Resource
    private AiAgentProperties aiAgentProperties;

    @Resource
    private ChatModel chatModelPrototype;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public GeneratedEpisodeExtraction extract(String sourceText) {
        if (!isAvailable()) {
            throw new IllegalStateException("AI episode extractor is unavailable");
        }
        EpisodeExtractionAgent agent = AiServices.builder(EpisodeExtractionAgent.class)
                .chatModel(chatModelPrototype)
                .build();
        try {
            String raw = agent.extract(sourceText);
            String json = StringUtils.defaultString(raw).trim()
                    .replace("```json", "").replace("```", "").trim();
            return objectMapper.readValue(json, GeneratedEpisodeExtraction.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("episode extraction result is invalid", e);
        }
    }

    @Override
    public boolean isAvailable() {
        return aiAgentProperties.available() && StringUtils.isNotBlank(aiAgentProperties.getModel());
    }

    @Override
    public String modelName() {
        return StringUtils.defaultIfBlank(aiAgentProperties.getModel(), "unconfigured");
    }

    @Override
    public String promptVersion() {
        return PROMPT_VERSION;
    }
}
