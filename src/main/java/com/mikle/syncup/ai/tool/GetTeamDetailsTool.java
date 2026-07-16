package com.mikle.syncup.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mikle.syncup.ai.model.AiToolResult;
import com.mikle.syncup.ai.model.TeamIntent;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.domain.UserTeam;
import com.mikle.syncup.model.dto.TeamQuery;
import com.mikle.syncup.model.vo.TeamUserVO;
import com.mikle.syncup.service.TeamService;
import com.mikle.syncup.service.UserTeamService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetTeamDetailsTool implements AiTool {

    public static final String TOOL_NAME = "getTeamDetails";

    @Resource
    private TeamService teamService;

    @Resource
    private UserTeamService userTeamService;

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
        if (intent == null || intent.getTeamId() == null || intent.getTeamId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "teamId is required");
        }

        TeamQuery teamQuery = new TeamQuery();
        teamQuery.setId(intent.getTeamId());
        teamQuery.setStatus(0);
        List<TeamUserVO> teams = teamService.listTeams(teamQuery, false);
        if (teams.isEmpty()) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "team does not exist or is not public");
        }

        TeamUserVO team = teams.get(0);
        int joinCount = countTeamMembers(team.getId());
        team.setHasJoinNum(joinCount);
        team.setHasJoin(hasJoined(team.getId(), loginUser.getId()));
        boolean available = team.getMaxNum() == null || joinCount < team.getMaxNum();
        String summary = available ? "team is available" : "team is full";
        return AiToolResult.success(name(), type(), summary, team);
    }

    private int countTeamMembers(Long teamId) {
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("teamId", teamId);
        return Math.toIntExact(userTeamService.count(queryWrapper));
    }

    private boolean hasJoined(Long teamId, Long userId) {
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("teamId", teamId);
        queryWrapper.eq("userId", userId);
        return userTeamService.count(queryWrapper) > 0;
    }
}
