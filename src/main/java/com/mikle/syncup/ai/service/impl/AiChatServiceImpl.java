package com.mikle.syncup.ai.service.impl;

import com.mikle.syncup.ai.agent.AiAssistantAgentService;
import com.mikle.syncup.ai.model.AiChatRequest;
import com.mikle.syncup.ai.model.AiChatResponse;
import com.mikle.syncup.ai.model.AiTeamDetailsRequest;
import com.mikle.syncup.ai.model.AiToolResult;
import com.mikle.syncup.ai.model.TeamDraft;
import com.mikle.syncup.ai.model.TeamIntent;
import com.mikle.syncup.ai.service.AiChatService;
import com.mikle.syncup.ai.service.AiTeamDraftService;
import com.mikle.syncup.ai.service.AiToolExecutionService;
import com.mikle.syncup.ai.service.TeamIntentParser;
import com.mikle.syncup.ai.tool.CreateTeamDraftTool;
import com.mikle.syncup.ai.tool.GetTeamDetailsTool;
import com.mikle.syncup.ai.tool.RecommendUsersTool;
import com.mikle.syncup.ai.tool.SearchTeamsTool;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
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

    @Resource
    private UserService userService;

    @Resource
    private TeamIntentParser teamIntentParser;

    @Resource
    private AiAssistantAgentService aiAssistantAgentService;

    @Resource
    private AiTeamDraftService aiTeamDraftService;

    @Resource
    private AiToolExecutionService aiToolExecutionService;

    @Override
    public AiChatResponse chat(AiChatRequest aiChatRequest, HttpServletRequest request) {
        if (aiChatRequest == null || StringUtils.isBlank(aiChatRequest.getMessage())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "message is required");
        }
        String message = aiChatRequest.getMessage().trim();
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "message is too long");
        }
        User loginUser = userService.getLoginUser(request);
        String sessionId = resolveSessionId(aiChatRequest.getSessionId());
        Optional<AiChatResponse> agentResponse = aiAssistantAgentService.chat(message, sessionId, loginUser);
        if (agentResponse.isPresent()) {
            return agentResponse.get();
        }

        TeamIntent intent = teamIntentParser.parse(message);

        AiChatResponse response = new AiChatResponse();
        response.setSessionId(sessionId);
        response.setIntent(intent);

        if (!intent.isTeamRelated()) {
            response.setReply("我目前主要负责搭子和队伍相关需求。你可以告诉我想找的活动、城市、时间或预算。");
            response.setNeedClarification(false);
            return response;
        }

        if (!hasMinimumFields(intent)) {
            response.setNeedClarification(true);
            response.setReply("我还需要补充一些信息，才能帮你查找合适的队伍。");
            response.getClarificationQuestions().addAll(buildClarificationQuestions(intent));
            return response;
        }

        AiToolResult searchResult = executeToolWithAudit(SearchTeamsTool.TOOL_NAME, intent, loginUser, sessionId);
        response.getToolResults().add(searchResult);
        AiToolResult recommendResult = executeToolWithAudit(RecommendUsersTool.TOOL_NAME, intent, loginUser, sessionId);
        response.getToolResults().add(recommendResult);
        response.setReply(searchResult.isSuccess()
                ? "我先按你的需求查找了可加入的队伍。"
                : "我暂时没能完成队伍查询，请稍后再试。");

        if (intent.isCreateTeamRequested()) {
            AiToolResult draftResult = executeToolWithAudit(CreateTeamDraftTool.TOOL_NAME, intent, loginUser, sessionId);
            response.getToolResults().add(draftResult);
            if (draftResult.getData() instanceof TeamDraft draft) {
                response.setDraft(aiTeamDraftService.saveDraft(draft, loginUser, sessionId));
            }
        }
        return response;
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

    private String resolveSessionId(String sessionId) {
        if (StringUtils.isNotBlank(sessionId)) {
            return sessionId.trim();
        }
        return UUID.randomUUID().toString();
    }

    private boolean hasMinimumFields(TeamIntent intent) {
        if (intent.getActivityType() == null || intent.getCity() == null) {
            return false;
        }
        return !intent.isCreateTeamRequested() || intent.getMemberCount() != null;
    }

    private AiToolResult executeToolWithAudit(String toolName, TeamIntent intent, User loginUser, String sessionId) {
        return aiToolExecutionService.execute(toolName, intent, loginUser, sessionId);
    }

    private java.util.List<String> buildClarificationQuestions(TeamIntent intent) {
        java.util.List<String> questions = new java.util.ArrayList<>();
        if (intent.getMissingFields().contains("activityType")) {
            questions.add("你想找哪类活动？例如羽毛球、健身、徒步。");
        }
        if (intent.getMissingFields().contains("city")) {
            questions.add("你希望在哪个城市找搭子或队伍？");
        }
        if (intent.getMissingFields().contains("memberCount")) {
            questions.add("你希望队伍最多几个人，或者还想找几位搭子？");
        }
        return questions;
    }

}
