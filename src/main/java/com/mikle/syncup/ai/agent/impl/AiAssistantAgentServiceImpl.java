package com.mikle.syncup.ai.agent.impl;

import com.mikle.syncup.ai.agent.AiAgentToolContext;
import com.mikle.syncup.ai.agent.AiAssistantAgentService;
import com.mikle.syncup.ai.agent.AiAssistantTools;
import com.mikle.syncup.ai.agent.AssistantAgent;
import com.mikle.syncup.ai.config.AiAgentProperties;
import com.mikle.syncup.ai.exception.InvalidToolArgumentsException;
import com.mikle.syncup.ai.memory.PersistentChatMemoryStore;
import com.mikle.syncup.ai.model.vo.AiChatResponseVO;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.service.AiConversationContextService;
import com.mikle.syncup.model.domain.User;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
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
        String memoryId = buildMemoryId(loginUser, sessionId);
        String modelMessage = buildModelMessage(message, sessionId, loginUser);
        aiAgentToolContext.start(sessionId, loginUser);
        try {
            try {
                return Optional.of(invokeAssistant(message, sessionId, memoryId, modelMessage));
            } catch (RuntimeException firstFailure) {
                if (!canSafelyRetryAfterToolArgumentsFailure(firstFailure)) {
                    logAgentFailure(firstFailure, false);
                    return Optional.empty();
                }

                log.warn("Invalid AI tool arguments detected before any tool completed; clearing chat memory and retrying once. " +
                                "provider={}, model={}, errorType={}",
                        aiAgentProperties.getProvider(), aiAgentProperties.getModel(),
                        firstFailure.getClass().getSimpleName());
                try {
                    persistentChatMemoryStore.deleteMessages(memoryId);
                } catch (RuntimeException clearFailure) {
                    log.warn("Failed to clear invalid AI chat memory; skip retry. provider={}, model={}, errorType={}",
                            aiAgentProperties.getProvider(), aiAgentProperties.getModel(),
                            clearFailure.getClass().getSimpleName());
                    return Optional.empty();
                }
                aiAgentToolContext.start(sessionId, loginUser);

                try {
                    return Optional.of(invokeAssistant(message, sessionId, memoryId, modelMessage));
                } catch (RuntimeException retryFailure) {
                    logAgentFailure(retryFailure, true);
                    return Optional.empty();
                }
            }
        } finally {
            aiAgentToolContext.clear();
        }
    }

    private AiChatResponseVO invokeAssistant(String originalMessage,
                                             String sessionId,
                                             String memoryId,
                                             String modelMessage) {
        AssistantAgent assistant = buildAssistant();
        String reply = assistant.chat(memoryId, modelMessage);
        AiAgentToolContext.State state = aiAgentToolContext.snapshot();

        AiChatResponseVO response = new AiChatResponseVO();
        response.setSessionId(sessionId);
        response.setReply(reply);
        response.setDraft(state.getDraft());
        response.setDeleteConfirmation(state.getDeleteConfirmation());
        response.getToolResults().addAll(state.getToolResults());
        response.setIntent(buildResponseIntent(originalMessage, state));
        return response;
    }

    private boolean canSafelyRetryAfterToolArgumentsFailure(RuntimeException failure) {
        AiAgentToolContext.State state = aiAgentToolContext.snapshot();
        if (!state.getToolResults().isEmpty()
                || state.getDraft() != null
                || state.getDeleteConfirmation() != null) {
            return false;
        }
        return isToolArgumentsFailure(failure);
    }

    private boolean isToolArgumentsFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof InvalidToolArgumentsException) {
                return true;
            }
            String message = current.getMessage();
            if (StringUtils.containsIgnoreCase(message, "function.arguments")
                    && (StringUtils.containsIgnoreCase(message, "JSON")
                    || StringUtils.containsIgnoreCase(message, "invalid_parameter_error"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void logAgentFailure(RuntimeException failure, boolean retried) {
        log.warn("AI agent failed, fallback to deterministic flow. provider={}, model={}, retried={}, errorType={}",
                aiAgentProperties.getProvider(), aiAgentProperties.getModel(), retried,
                failure.getClass().getSimpleName());
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
                .toolArgumentsErrorHandler((error, context) -> {
                    String toolName = context.toolExecutionRequest() == null
                            ? "unknown"
                            : context.toolExecutionRequest().name();
                    log.warn("AI tool argument binding failed. toolName={}, errorType={}",
                            toolName, error.getClass().getSimpleName());
                    return ToolErrorHandlerResult.text(
                            "工具参数格式不正确，请重新调用该工具；未提供的可选参数必须省略。"
                    );
                })
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
