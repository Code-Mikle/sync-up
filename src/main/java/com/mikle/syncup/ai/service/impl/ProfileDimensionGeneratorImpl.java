package com.mikle.syncup.ai.service.impl;

import com.mikle.syncup.ai.agent.ProfileDimensionGenerationAgent;
import com.mikle.syncup.ai.config.AiAgentProperties;
import com.mikle.syncup.ai.model.entity.AiUserEpisode;
import com.mikle.syncup.ai.model.enums.ProfileType;
import com.mikle.syncup.ai.service.ProfileDimensionGenerator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProfileDimensionGeneratorImpl implements ProfileDimensionGenerator {

    private static final String PROMPT_VERSION = "profile-dimension-v1";
    private static final int MAX_LENGTH = 200;

    @Resource private AiAgentProperties aiAgentProperties;
    @Resource private ChatModel chatModelPrototype;

    @Override
    public String generate(ProfileType profileType, String currentText, List<AiUserEpisode> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return "暂未观察到明确偏好";
        }
        StringBuilder input = new StringBuilder("画像维度：").append(profileType.name())
                .append("\n当前维度画像：").append(StringUtils.defaultIfBlank(currentText, "暂无"))
                .append("\n有效证据：");
        for (AiUserEpisode episode : evidence) {
            input.append("\n-").append(episode.getSignalType()).append("：").append(episode.getContent());
        }
        ProfileDimensionGenerationAgent agent = AiServices.builder(ProfileDimensionGenerationAgent.class)
                .chatModel(chatModelPrototype).build();
        String result = StringUtils.defaultString(agent.generate(input.toString())).trim()
                .replace("```text", "").replace("```", "").trim();
        if (StringUtils.isBlank(result) || result.length() > MAX_LENGTH
                || result.matches("(?s).*(1[3-9]\\d{9}|[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}).*")) {
            throw new IllegalArgumentException("generated profile dimension is invalid");
        }
        return result;
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
