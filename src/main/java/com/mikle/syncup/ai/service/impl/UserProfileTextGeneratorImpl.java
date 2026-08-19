package com.mikle.syncup.ai.service.impl;

import com.mikle.syncup.ai.agent.UserProfileGenerationAgent;
import com.mikle.syncup.ai.config.AiAgentProperties;
import com.mikle.syncup.ai.model.schema.GeneratedUserProfile;
import com.mikle.syncup.ai.service.UserProfileTextAssembler;
import com.mikle.syncup.ai.service.UserProfileTextGenerator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class UserProfileTextGeneratorImpl implements UserProfileTextGenerator {

    private static final String PROMPT_VERSION = "user-profile-v1";

    @Resource
    private AiAgentProperties aiAgentProperties;

    @Resource
    private ChatModel chatModelPrototype;

    @Resource
    private UserProfileTextAssembler assembler;

    @Override
    public GeneratedUserProfile generate(String sourceText) {
        if (!isAvailable()) {
            throw new IllegalStateException("AI profile generator is unavailable");
        }
        UserProfileGenerationAgent agent = AiServices.builder(UserProfileGenerationAgent.class)
                .chatModel(chatModelPrototype)
                .build();
        return assembler.parse(agent.generate(sourceText));
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
