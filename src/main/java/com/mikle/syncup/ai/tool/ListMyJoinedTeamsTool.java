package com.mikle.syncup.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.agent.TeamIntent;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ListMyJoinedTeamsTool implements AiTool {

    public static final String TOOL_NAME = "listMyJoinedTeams";

    private static final int DEFAULT_LIMIT = 10;

    @Resource
    private UserTeamService userTeamService;

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
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("userId", loginUser.getId());
        List<UserTeam> userTeams = userTeamService.list(userTeamQueryWrapper);
        if (userTeams.isEmpty()) {
            return AiToolResult.success(name(), type(), "current user has not joined any teams", new ArrayList<>());
        }

        List<Long> teamIds = userTeams.stream()
                .map(UserTeam::getTeamId)
                .distinct()
                .toList();
        TeamQuery teamQuery = new TeamQuery();
        teamQuery.setIdList(teamIds);
        List<TeamUserVO> teams = teamService.listTeams(teamQuery, true);
        fillTeamState(teams, loginUser.getId());
        int limit = Math.min(teams.size(), DEFAULT_LIMIT);
        List<TeamUserVO> limitedTeams = teams.subList(0, limit);
        return AiToolResult.success(name(), type(), "found " + limitedTeams.size() + " teams joined by current user", limitedTeams);
    }

    private void fillTeamState(List<TeamUserVO> teams, long userId) {
        if (teams == null || teams.isEmpty()) {
            return;
        }
        List<Long> teamIds = teams.stream().map(TeamUserVO::getId).toList();
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("teamId", teamIds);
        List<UserTeam> userTeams = userTeamService.list(queryWrapper);
        Map<Long, List<UserTeam>> teamUserMap = userTeams.stream()
                .collect(Collectors.groupingBy(UserTeam::getTeamId));
        Set<Long> joinedTeamIds = userTeams.stream()
                .filter(userTeam -> userTeam.getUserId() == userId)
                .map(UserTeam::getTeamId)
                .collect(Collectors.toSet());
        teams.forEach(team -> {
            team.setHasJoin(true);
            team.setHasJoin(joinedTeamIds.contains(team.getId()));
            team.setHasJoinNum(teamUserMap.getOrDefault(team.getId(), List.of()).size());
        });
    }
}

