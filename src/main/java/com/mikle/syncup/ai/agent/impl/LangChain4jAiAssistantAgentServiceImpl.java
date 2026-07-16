package com.mikle.syncup.ai.agent.impl;

import com.mikle.syncup.ai.agent.AiAgentToolContext;
import com.mikle.syncup.ai.agent.AiAssistantAgentService;
import com.mikle.syncup.ai.agent.AiAssistantTools;
import com.mikle.syncup.ai.config.AiAgentProperties;
import com.mikle.syncup.ai.model.AiChatResponse;
import com.mikle.syncup.ai.model.TeamIntent;
import com.mikle.syncup.model.domain.User;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
public class LangChain4jAiAssistantAgentServiceImpl implements AiAssistantAgentService {

    private static final String SYSTEM_PROMPT = """
            你是 Sync Up 的组队助手。你可以根据用户需求选择后端工具，但必须遵守：
            1. 查询队伍、推荐搭子、查看个人公开资料、查看我创建的队伍可以直接调用工具。
            2. 创建队伍只能调用 createTeamDraft 生成草稿，不能承诺已经正式创建。
            3. 不能要求或推断用户 id，当前用户身份由后端提供。
            4. 不要输出账号、手机号、邮箱、密码、登录 token 或 API key。
            5. 如果信息不足，直接用中文追问缺少的信息。
            6. 回复要简洁，并说明已调用的工具结果。
            """;

    @Resource
    private AiAgentProperties aiAgentProperties;

    @Resource
    private AiAssistantTools aiAssistantTools;

    @Resource
    private AiAgentToolContext aiAgentToolContext;

    @Override
    public Optional<AiChatResponse> chat(String message, String sessionId, User loginUser) {
        if (!aiAgentProperties.available()) {
            return Optional.empty();
        }
        if (StringUtils.isBlank(message) || message.length() > aiAgentProperties.getMaxInputLength()) {
            return Optional.empty();
        }
        aiAgentToolContext.start(sessionId, loginUser);
        try {
            Assistant assistant = buildAssistant();
            String reply = assistant.chat(message);
            AiAgentToolContext.State state = aiAgentToolContext.snapshot();

            AiChatResponse response = new AiChatResponse();
            response.setSessionId(sessionId);
            response.setReply(StringUtils.defaultIfBlank(reply, "我已经处理了你的请求。"));
            response.setDraft(state.getDraft());
            response.getToolResults().addAll(state.getToolResults());
            TeamIntent intent = new TeamIntent();
            intent.setSourceText(message);
            intent.setTeamRelated(!state.getToolResults().isEmpty());
            response.setIntent(intent);
            return Optional.of(response);
        } catch (RuntimeException e) {
            log.warn("AI agent failed, fallback to deterministic flow. provider={}, model={}, error={}",
                    aiAgentProperties.getProvider(), aiAgentProperties.getModel(), e.getMessage());
            return Optional.empty();
        } finally {
            aiAgentToolContext.clear();
        }
    }

    private Assistant buildAssistant() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(aiAgentProperties.getBaseUrl())
                .apiKey(aiAgentProperties.getApiKey())
                .modelName(aiAgentProperties.getModel())
                .temperature(aiAgentProperties.getTemperature())
                .timeout(Duration.ofMillis(aiAgentProperties.getTimeoutMs()))
                .maxRetries(1)
                .build();
        return AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(aiAssistantTools)
                .maxSequentialToolsInvocations(Math.max(1, aiAgentProperties.getMaxToolCalls()))
                .build();
    }

    interface Assistant {

        @SystemMessage(SYSTEM_PROMPT)
        String chat(@UserMessage String message);
    }
}
