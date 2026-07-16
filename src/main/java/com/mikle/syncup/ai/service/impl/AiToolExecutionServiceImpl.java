package com.mikle.syncup.ai.service.impl;

import com.mikle.syncup.ai.model.AiToolResult;
import com.mikle.syncup.ai.model.TeamIntent;
import com.mikle.syncup.ai.service.AiToolCallLogService;
import com.mikle.syncup.ai.service.AiToolExecutionService;
import com.mikle.syncup.ai.tool.AiToolRegistry;
import com.mikle.syncup.model.domain.User;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class AiToolExecutionServiceImpl implements AiToolExecutionService {

    @Resource
    private AiToolRegistry aiToolRegistry;

    @Resource
    private AiToolCallLogService aiToolCallLogService;

    @Override
    public AiToolResult execute(String toolName, TeamIntent intent, User loginUser, String sessionId) {
        long start = System.currentTimeMillis();
        try {
            AiToolResult result = aiToolRegistry.execute(toolName, intent, loginUser);
            aiToolCallLogService.recordToolCall(
                    sessionId,
                    loginUser,
                    toolName,
                    result.isSuccess() ? "success" : "failed",
                    buildIntentSummary(intent),
                    buildResultSummary(result),
                    result.isSuccess() ? null : result.getSummary(),
                    System.currentTimeMillis() - start
            );
            return result;
        } catch (RuntimeException e) {
            aiToolCallLogService.recordToolCall(
                    sessionId,
                    loginUser,
                    toolName,
                    "failed",
                    buildIntentSummary(intent),
                    null,
                    e.getMessage(),
                    System.currentTimeMillis() - start
            );
            throw e;
        }
    }

    private String buildIntentSummary(TeamIntent intent) {
        if (intent == null) {
            return null;
        }
        return "activityType=" + intent.getActivityType()
                + ", teamId=" + intent.getTeamId()
                + ", city=" + intent.getCity()
                + ", memberCount=" + intent.getMemberCount()
                + ", createTeamRequested=" + intent.isCreateTeamRequested();
    }

    private String buildResultSummary(AiToolResult result) {
        if (result == null) {
            return null;
        }
        Object data = result.getData();
        String dataType = data == null ? "null" : data.getClass().getSimpleName();
        return "success=" + result.isSuccess()
                + ", type=" + result.getType()
                + ", dataType=" + dataType
                + ", summary=" + result.getSummary();
    }
}
