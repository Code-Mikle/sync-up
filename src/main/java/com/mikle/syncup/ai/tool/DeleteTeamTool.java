package com.mikle.syncup.ai.tool;

import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.service.TeamService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DeleteTeamTool implements AiTool {

    public static final String TOOL_NAME = "deleteTeam";

    @Resource
    private TeamService teamService;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String type() {
        return "write";
    }

    @Override
    public AiToolResult execute(TeamIntent intent, User loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        if (intent == null || intent.getTeamId() == null || intent.getTeamId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "teamId is required");
        }
        boolean deleted = teamService.deleteTeam(intent.getTeamId(), loginUser);
        return AiToolResult.success(name(), type(), "deleted team successfully", Map.of(
                "teamId", intent.getTeamId(),
                "deleted", deleted
        ));
    }
}
