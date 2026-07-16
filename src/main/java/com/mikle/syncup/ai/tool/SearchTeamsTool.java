package com.mikle.syncup.ai.tool;

import com.mikle.syncup.ai.model.AiToolResult;
import com.mikle.syncup.ai.model.TeamIntent;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.dto.TeamQuery;
import com.mikle.syncup.model.vo.TeamUserVO;
import com.mikle.syncup.service.TeamService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchTeamsTool implements AiTool {

    public static final String TOOL_NAME = "searchTeams";

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
        TeamQuery teamQuery = new TeamQuery();
        teamQuery.setActivityType(intent.getActivityType());
        teamQuery.setCity(intent.getCity());
        teamQuery.setDistrict(intent.getDistrict());
        teamQuery.setStartTimeBegin(intent.getStartTime());
        teamQuery.setMaxBudgetPerPerson(intent.getBudgetMax());
        teamQuery.setSkillLevel(intent.getSkillLevel());
        teamQuery.setOnlyAvailable(true);
        teamQuery.setStatus(0);

        List<TeamUserVO> teams = teamService.listTeams(teamQuery, false);
        int limit = Math.min(teams.size(), 5);
        List<TeamUserVO> limitedTeams = teams.subList(0, limit);
        return AiToolResult.success(name(), type(), "found " + limitedTeams.size() + " available teams", limitedTeams);
    }
}
