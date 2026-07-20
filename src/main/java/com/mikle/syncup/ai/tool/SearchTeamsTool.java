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

import java.util.List;

@Component
public class SearchTeamsTool implements AiTool {

    public static final String TOOL_NAME = "searchTeams";

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
        if (intent == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        TeamQuery teamQuery = new TeamQuery();
        teamQuery.setActivityCategory(intent.getActivityCategory());
        teamQuery.setActivityType(intent.getActivityType());
        teamQuery.setCity(intent.getCity());
        teamQuery.setDistrict(intent.getDistrict());
        teamQuery.setStartTimeBegin(intent.getStartTime());
        teamQuery.setMaxBudgetPerPerson(intent.getBudgetMax());
        teamQuery.setSkillLevel(intent.getSkillLevel());
        teamQuery.setOnlyAvailable(true);
        teamQuery.setStatus(0);

        int requiredAvailableSlots = intent.getMemberCount() == null
                ? 1
                : Math.max(1, intent.getMemberCount());
        List<TeamUserVO> teams = teamService.listTeams(teamQuery, false).stream()
                .filter(team -> hasEnoughAvailableSlots(team, requiredAvailableSlots))
                .limit(5)
                .toList();
        return AiToolResult.success(name(), type(), "found " + teams.size() + " available teams", teams);
    }

    private boolean hasEnoughAvailableSlots(TeamUserVO team, int requiredAvailableSlots) {
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("teamId", team.getId());
        int joined = Math.toIntExact(userTeamService.count(queryWrapper));
        team.setHasJoinNum(joined);
        return team.getMaxNum() != null && team.getMaxNum() - joined >= requiredAvailableSlots;
    }
}
