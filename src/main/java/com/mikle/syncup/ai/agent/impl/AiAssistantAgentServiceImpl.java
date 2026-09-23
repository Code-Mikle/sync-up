package com.mikle.syncup.ai.agent.impl;

import com.mikle.syncup.ai.agent.AiAgentToolContext;
import com.mikle.syncup.ai.agent.AiAssistantAgentService;
import com.mikle.syncup.ai.agent.AiAssistantTools;
import com.mikle.syncup.ai.agent.AssistantAgent;
import com.mikle.syncup.ai.config.AiAgentProperties;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.ai.model.vo.AiChatResponseVO;
import com.mikle.syncup.ai.service.memory.WorkingMemoryService;
import com.mikle.syncup.model.domain.User;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Objects;

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
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(session.getId(), "session.id must not be null");
        if (!aiAgentProperties.available()
                || StringUtils.isBlank(message)
                || message.length() > aiAgentProperties.getMaxInputLength()) {
            return Optional.empty();
        }
        String conversationId = session.getConversationId();
        String modelMessage = workingMemoryService.buildModelContext(session, loginUser, message);
        aiAgentToolContext.start(conversationId, loginUser, message);
        try {
            return Optional.of(invokeAssistant(message, conversationId, modelMessage));
        } catch (RuntimeException failure) {
            logAgentFailure(failure);
            return Optional.empty();
        } finally {
            aiAgentToolContext.clear();
        }
    }

    private AiChatResponseVO invokeAssistant(String originalMessage, String conversationId, String modelMessage) {
        String reply = buildAssistant().chat(modelMessage);
        AiAgentToolContext.State state = aiAgentToolContext.snapshot();
        AiChatResponseVO response = new AiChatResponseVO();
        response.setConversationId(conversationId);
        response.setReply(reply);
        response.getUiBlocks().addAll(state.getUiBlocks());
        response.setIntent(buildResponseIntent(originalMessage, state));
        return response;
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
                .toolArgumentsErrorHandler(this::handleToolArgumentsError)
                .maxSequentialToolsInvocations(Math.max(1, aiAgentProperties.getMaxToolCalls()))
                .build();
    }

    private ToolErrorHandlerResult handleToolArgumentsError(Throwable error, ToolErrorContext context) {
        String toolName = StringUtils.defaultIfBlank(context.toolExecutionRequest().name(), "unknown");
        String reason = error == null ? null : error.getMessage();
        reason = StringUtils.abbreviate(
                StringUtils.defaultIfBlank(StringUtils.normalizeSpace(reason), "参数无法按照工具定义解析"),
                300
        );
        return ToolErrorHandlerResult.text(
                "工具 '%s' 的参数无效：%s。请严格按照该工具的参数定义修正后重新调用；"
                        .formatted(toolName, reason)
                        + "不要传入未定义字段，未提供的可选参数请省略。"
        );
    }

    private void logAgentFailure(RuntimeException failure) {
        log.warn("AI agent failed, fallback to deterministic flow. provider={}, model={}, errorType={}, message={}",
                aiAgentProperties.getProvider(), aiAgentProperties.getModel(),
                failure.getClass().getSimpleName(), failure.getMessage(), failure);
    }
}
