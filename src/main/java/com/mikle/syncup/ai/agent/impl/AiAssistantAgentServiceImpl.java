package com.mikle.syncup.ai.agent.impl;

import com.mikle.syncup.ai.agent.AiAgentToolContext;
import com.mikle.syncup.ai.agent.AiAssistantAgentService;
import com.mikle.syncup.ai.agent.AiAssistantTools;
import com.mikle.syncup.ai.agent.AssistantAgent;
import com.mikle.syncup.ai.config.AiAgentProperties;
import com.mikle.syncup.ai.memory.PersistentChatMemoryStore;
import com.mikle.syncup.ai.model.vo.AiChatResponseVO;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.service.AiConversationContextService;
import com.mikle.syncup.model.domain.User;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Service
public class AiAssistantAgentServiceImpl implements AiAssistantAgentService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private AiAgentProperties aiAgentProperties;

    @Resource
    private AiAssistantTools aiAssistantTools;

    @Resource
    private AiAgentToolContext aiAgentToolContext;

    @Resource
    private PersistentChatMemoryStore persistentChatMemoryStore;

    @Resource
    private AiConversationContextService aiConversationContextService;

    @Resource(name = "chatModelPrototype")
    private ChatModel chatModel;

    @Override
    public Optional<AiChatResponseVO> chat(String message, String sessionId, User loginUser) {
        if (!aiAgentProperties.available()) {
            return Optional.empty();
        }
        if (StringUtils.isBlank(message) || message.length() > aiAgentProperties.getMaxInputLength()) {
            return Optional.empty();
        }
        aiAgentToolContext.start(sessionId, loginUser);
        try {
            AssistantAgent assistant = buildAssistant();

            String reply = assistant.chat(buildMemoryId(loginUser, sessionId), buildModelMessage(message, sessionId, loginUser));
            AiAgentToolContext.State state = aiAgentToolContext.snapshot();

            AiChatResponseVO response = new AiChatResponseVO();
            response.setSessionId(sessionId);
            response.setReply(reply);
            response.setDraft(state.getDraft());
            response.setDeleteConfirmation(state.getDeleteConfirmation());
            response.getToolResults().addAll(state.getToolResults());
            response.setIntent(buildResponseIntent(message, state));
            return Optional.of(response);
        } catch (RuntimeException e) {
            log.warn("AI agent failed, fallback to deterministic flow. provider={}, model={}, error={}",
                    aiAgentProperties.getProvider(), aiAgentProperties.getModel(), e.getMessage(), e);
            return Optional.empty();
        } finally {
            aiAgentToolContext.clear();
        }
    }

    private String buildMemoryId(User loginUser, String sessionId) {
        return loginUser.getId() + ":" + sessionId;
    }

    private String buildModelMessage(String message, String sessionId, User loginUser) {
        StringBuilder builder = new StringBuilder()
                .append("当前服务端时间：")
                .append(LocalDateTime.now().format(DATE_TIME_FORMATTER));
        String recentBusinessContext = aiConversationContextService.buildRecentBusinessContext(loginUser, sessionId);
        if (StringUtils.isNotBlank(recentBusinessContext)) {
            builder.append("\n").append(recentBusinessContext);
        }
        return builder.append("\n用户原始需求：").append(message).toString();
    }

    private TeamIntent buildResponseIntent(String message, AiAgentToolContext.State state) {
        TeamIntent intent = state.getIntent();
        if (intent == null) {
            intent = new TeamIntent();
        }
        intent.setSourceText(message);
        intent.setTeamRelated(intent.isTeamRelated() || !state.getToolResults().isEmpty());
        return intent;
    }

    private AssistantAgent buildAssistant() {
        AiServices<AssistantAgent> builder = AiServices.builder(AssistantAgent.class)
                .chatModel(chatModel)
                .tools(aiAssistantTools)
                .maxSequentialToolsInvocations(Math.max(1, aiAgentProperties.getMaxToolCalls()));
        if (aiAgentProperties.getMemory().isEnabled()) {
            builder.chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(Math.max(2, aiAgentProperties.getMemory().getMaxMessages()))
                    .chatMemoryStore(persistentChatMemoryStore)
                    .build());
        }
        return builder.build();
    }

}
