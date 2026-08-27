package com.mikle.syncup.ai.service.impl;

import com.mikle.syncup.ai.agent.AiAssistantAgentService;
import com.mikle.syncup.ai.model.dto.AiChatRequest;
import com.mikle.syncup.ai.model.vo.AiChatHistoryVO;
import com.mikle.syncup.ai.model.vo.AiChatResponseVO;
import com.mikle.syncup.ai.model.dto.AiTeamDetailsRequest;
import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.service.AiChatMessageService;
import com.mikle.syncup.ai.service.AiChatSessionService;
import com.mikle.syncup.ai.service.AiMemoryPipelineService;
import com.mikle.syncup.ai.service.AiChatService;
import com.mikle.syncup.ai.service.AiToolExecutionService;
import com.mikle.syncup.ai.tool.DeleteTeamTool;
import com.mikle.syncup.ai.tool.GetTeamDetailsTool;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AiChatServiceImpl implements AiChatService {

    private static final int MAX_MESSAGE_LENGTH = 500;

    private static final String AI_UNAVAILABLE_REPLY = "AI 助手暂时不可用，请稍后再试。";

    @Resource
    private UserService userService;

    @Resource
    private AiAssistantAgentService aiAssistantAgentService;

    @Resource
    private AiChatMessageService aiChatMessageService;

    @Resource
    private AiChatSessionService aiChatSessionService;

    @Resource
    private AiMemoryPipelineService memoryPipelineService;

    @Resource
    private AiToolExecutionService aiToolExecutionService;

    @Override
    public AiChatResponseVO chat(AiChatRequest aiChatRequest, HttpServletRequest request) {
        if (aiChatRequest == null || StringUtils.isBlank(aiChatRequest.getMessage())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "message is required");
        }
        String message = aiChatRequest.getMessage().trim();
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "message is too long");
        }
        User loginUser = userService.getLoginUser(request);
        String sessionId = resolveSessionId(aiChatRequest.getSessionId());
        AiChatSession session = aiChatSessionService.getOrCreate(loginUser.getId(), sessionId);
        aiChatMessageService.saveUserMessage(loginUser, session, message);
        Optional<AiChatResponseVO> agentResponse = aiAssistantAgentService.chat(message, session, loginUser);
        if (agentResponse.isPresent()) {
            AiChatResponseVO response = agentResponse.get();
            response.setSessionId(sessionId);
            var assistantMessage = aiChatMessageService.saveAssistantMessage(loginUser, session, response.getReply(), response);
            memoryPipelineService.onChatTurnCompleted(loginUser.getId(), session, assistantMessage.getId());
            return response;
        }

        AiChatResponseVO response = new AiChatResponseVO();
        response.setSessionId(sessionId);
        response.setReply(AI_UNAVAILABLE_REPLY);
        response.setNeedClarification(false);
        var assistantMessage = aiChatMessageService.saveAssistantMessage(loginUser, session, response.getReply(), response);
        memoryPipelineService.onChatTurnCompleted(loginUser.getId(), session, assistantMessage.getId());
        return response;
    }

    @Override
    public AiChatHistoryVO getHistory(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return aiChatMessageService.getLatestHistory(loginUser);
    }

    @Override
    public AiToolResult getTeamDetails(Long teamId,
                                       AiTeamDetailsRequest aiTeamDetailsRequest,
                                       HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        TeamIntent intent = new TeamIntent();
        intent.setTeamId(teamId);
        String sessionId = resolveSessionId(aiTeamDetailsRequest == null ? null : aiTeamDetailsRequest.getSessionId());
        return executeToolWithAudit(GetTeamDetailsTool.TOOL_NAME, intent, loginUser, sessionId);
    }

    @Override
    public AiToolResult deleteTeam(Long teamId,
                                   AiTeamDetailsRequest aiTeamDetailsRequest,
                                   HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        TeamIntent intent = new TeamIntent();
        intent.setTeamId(teamId);
        String sessionId = resolveSessionId(aiTeamDetailsRequest == null ? null : aiTeamDetailsRequest.getSessionId());
        AiToolResult result = executeToolWithAudit(DeleteTeamTool.TOOL_NAME, intent, loginUser, sessionId);
        if (result.isSuccess()) {
            AiChatSession session = aiChatSessionService.getOrCreate(loginUser.getId(), sessionId);
            var event = aiChatMessageService.saveTeamDeletedEvent(loginUser, session, teamId);
            if (event != null) memoryPipelineService.onChatTurnCompleted(loginUser.getId(), session, event.getId());
        }
        return result;
    }

    private String resolveSessionId(String sessionId) {
        if (StringUtils.isNotBlank(sessionId)) {
            String normalizedSessionId = sessionId.trim();
            if (normalizedSessionId.length() > 64) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "sessionId is too long");
            }
            return normalizedSessionId;
        }
        return UUID.randomUUID().toString();
    }

    private AiToolResult executeToolWithAudit(String toolName, TeamIntent intent, User loginUser, String sessionId) {
        return aiToolExecutionService.execute(toolName, intent, loginUser, sessionId);
    }
}
