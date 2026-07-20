package com.mikle.syncup.ai.tool;

import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.request.TeamJoinRequest;
import com.mikle.syncup.service.TeamService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class JoinTeamTool implements AiTool {

    public static final String TOOL_NAME = "joinTeam";

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
        TeamJoinRequest request = new TeamJoinRequest();
        request.setTeamId(intent.getTeamId());
        if (StringUtils.isNotBlank(intent.getTeamPassword())) {
            request.setPassword(intent.getTeamPassword().trim());
        }
        boolean joined = teamService.joinTeam(request, loginUser);
        return AiToolResult.success(name(), type(), "joined team successfully", Map.of(
                "teamId", intent.getTeamId(),
                "joined", joined
        ));
    }
}

