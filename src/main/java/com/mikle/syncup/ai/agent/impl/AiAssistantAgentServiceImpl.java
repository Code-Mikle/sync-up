package com.mikle.syncup.ai.agent.impl;

import com.mikle.syncup.ai.agent.AiAgentToolContext;
import com.mikle.syncup.ai.agent.AiAssistantAgentService;
import com.mikle.syncup.ai.agent.AiAssistantTools;
import com.mikle.syncup.ai.agent.AssistantAgent;
import com.mikle.syncup.ai.config.AiAgentProperties;
import com.mikle.syncup.ai.exception.InvalidToolArgumentsException;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.ai.model.vo.AiChatResponseVO;
import com.mikle.syncup.ai.service.WorkingMemoryService;
import com.mikle.syncup.model.domain.User;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class AiAssistantAgentServiceImpl implements AiAssistantAgentService {

    @Resource
    private AiAgentProperties aiAgentProperties;

    @Resource
    private AiAssistantTools aiAssistantTools;

    @Resource
    private AiAgentToolContext aiAgentToolContext;

    @Resource
    private WorkingMemoryService workingMemoryService;

    @Resource(name = "chatModelPrototype")
    private ChatModel chatModel;

    @Override
    public Optional<AiChatResponseVO> chat(String message, AiChatSession session, User loginUser) {
        if (!aiAgentProperties.available()
                || StringUtils.isBlank(message)
                || message.length() > aiAgentProperties.getMaxInputLength()) {
            return Optional.empty();
        }
        String sessionKey = session == null ? "stateless" : session.getSessionKey();
        String modelMessage = workingMemoryService.buildModelContext(session, loginUser, message);
        aiAgentToolContext.start(sessionKey, loginUser, message);
        try {
            try {
                return Optional.of(invokeAssistant(message, sessionKey, modelMessage));
            } catch (RuntimeException firstFailure) {
                if (!canSafelyRetryAfterToolArgumentsFailure(firstFailure)) {
                    logAgentFailure(firstFailure, false);
                    return Optional.empty();
                }
                aiAgentToolContext.start(sessionKey, loginUser, message);
                try {
                    return Optional.of(invokeAssistant(message, sessionKey, modelMessage));
                } catch (RuntimeException retryFailure) {
                    logAgentFailure(retryFailure, true);
                    return Optional.empty();
                }
            }
        } finally {
            aiAgentToolContext.clear();
        }
    }

    private AiChatResponseVO invokeAssistant(String originalMessage, String sessionKey, String modelMessage) {
        String reply = buildAssistant().chat(modelMessage);
        AiAgentToolContext.State state = aiAgentToolContext.snapshot();
        AiChatResponseVO response = new AiChatResponseVO();
        response.setSessionId(sessionKey);
        response.setReply(reply);
        response.getUiBlocks().addAll(state.getUiBlocks());
        response.setIntent(buildResponseIntent(originalMessage, state));
        return response;
    }

    private boolean canSafelyRetryAfterToolArgumentsFailure(RuntimeException failure) {
        AiAgentToolContext.State state = aiAgentToolContext.snapshot();
        return state.getToolResults().isEmpty()
                && state.getDraft() == null
                && state.getDeleteConfirmation() == null
                && isToolArgumentsFailure(failure);
    }

    private boolean isToolArgumentsFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof InvalidToolArgumentsException) {
                return true;
            }
            String message = current.getMessage();
            if (StringUtils.containsIgnoreCase(message, "function.arguments")
                    && StringUtils.containsIgnoreCase(message, "JSON")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private TeamIntent buildResponseIntent(String message, AiAgentToolContext.State state) {
        TeamIntent intent = state.getTeamIntent();
        if (intent == null) {
            intent = new TeamIntent();
        }
        intent.setSourceText(message);
        intent.setTeamRelated(intent.isTeamRelated() || !state.getToolResults().isEmpty());
        return intent;
    }

    private AssistantAgent buildAssistant() {
        return AiServices.builder(AssistantAgent.class)
                .chatModel(chatModel)
                .tools(aiAssistantTools)
                .toolArgumentsErrorHandler((error, context) -> ToolErrorHandlerResult.text(
                        "工具参数格式不正确，请重新调用该工具；未提供的可选参数必须省略。"))
                .maxSequentialToolsInvocations(Math.max(1, aiAgentProperties.getMaxToolCalls()))
                .build();
    }

    private void logAgentFailure(RuntimeException failure, boolean retried) {
        log.warn("AI agent failed, fallback to deterministic flow. provider={}, model={}, retried={}, errorType={}," +
                        "message={}",
                aiAgentProperties.getProvider(), aiAgentProperties.getModel(), retried,
                failure.getClass().getSimpleName(), failure.getMessage(), failure);
    }
}
