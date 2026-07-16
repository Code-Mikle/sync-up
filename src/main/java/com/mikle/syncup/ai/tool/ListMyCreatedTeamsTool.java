package com.mikle.syncup.ai.tool;

import com.mikle.syncup.ai.model.AiToolResult;
import com.mikle.syncup.ai.model.TeamIntent;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.dto.TeamQuery;
import com.mikle.syncup.model.vo.TeamUserVO;
import com.mikle.syncup.service.TeamService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ListMyCreatedTeamsTool implements AiTool {

    public static final String TOOL_NAME = "listMyCreatedTeams";

    private static final int DEFAULT_LIMIT = 10;

    @Resource
    private TeamService teamService;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String type() {
        return "read";
    }

    @Override
    public AiToolResult execute(TeamIntent intent, User loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        TeamQuery teamQuery = new TeamQuery();
        teamQuery.setUserId(loginUser.getId());
        List<TeamUserVO> teams = teamService.listTeams(teamQuery, true);
        int limit = Math.min(teams.size(), DEFAULT_LIMIT);
        List<TeamUserVO> limitedTeams = teams.subList(0, limit);
        return AiToolResult.success(name(), type(), "found " + limitedTeams.size() + " teams created by current user", limitedTeams);
    }
}
