package com.mikle.syncup.ai.service.impl;

import com.mikle.syncup.ai.agent.SessionSummaryAgent;
import com.mikle.syncup.ai.config.AiAgentProperties;
import com.mikle.syncup.ai.service.SessionSummaryGenerator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class SessionSummaryGeneratorImpl implements SessionSummaryGenerator {

    private static final String PROMPT_VERSION = "session-summary-v1";

    @Resource
    private AiAgentProperties aiAgentProperties;

    @Resource
    private ChatModel chatModelPrototype;

    @Override
    public String summarize(String input) {
        if (!isAvailable()) {
            throw new IllegalStateException("session summary generator is unavailable");
        }
        SessionSummaryAgent agent = AiServices.builder(SessionSummaryAgent.class)
                .chatModel(chatModelPrototype)
                .build();
        return agent.summarize(input);
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
