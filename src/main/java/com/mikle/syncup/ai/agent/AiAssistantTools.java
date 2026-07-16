package com.mikle.syncup.ai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.model.AiToolResult;
import com.mikle.syncup.ai.model.TeamDraft;
import com.mikle.syncup.ai.model.TeamIntent;
import com.mikle.syncup.ai.service.AiTeamDraftService;
import com.mikle.syncup.ai.service.AiToolExecutionService;
import com.mikle.syncup.ai.tool.CreateTeamDraftTool;
import com.mikle.syncup.ai.tool.GetMyProfileTool;
import com.mikle.syncup.ai.tool.GetTeamDetailsTool;
import com.mikle.syncup.ai.tool.ListMyCreatedTeamsTool;
import com.mikle.syncup.ai.tool.RecommendUsersTool;
import com.mikle.syncup.ai.tool.SearchTeamsTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class AiAssistantTools {

    @Resource
    private AiAgentToolContext aiAgentToolContext;

    @Resource
    private AiToolExecutionService aiToolExecutionService;

    @Resource
    private AiTeamDraftService aiTeamDraftService;

    @Resource
    private ObjectMapper objectMapper;

    @Tool(name = SearchTeamsTool.TOOL_NAME, value = "Search public available teams by activity, city, budget and skill level.")
    public String searchTeams(@P(value = "Activity type, for example 羽毛球, 健身, 徒步.", required = false) String activityType,
                              @P(value = "City name, for example 西安.", required = false) String city,
                              @P(value = "District or business area.", required = false) String district,
                              @P(value = "Maximum budget per person.", required = false) Double budgetMax,
                              @P(value = "Skill level, for example 入门, 中等, 熟练.", required = false) String skillLevel) {
        TeamIntent intent = buildIntent(activityType, city, district, budgetMax, skillLevel, null);
        return executeAndRemember(SearchTeamsTool.TOOL_NAME, intent);
    }

    @Tool(name = RecommendUsersTool.TOOL_NAME, value = "Recommend users or partners by activity tags and current user's tags.")
    public String recommendUsers(@P(value = "Activity type or primary tag.", required = false) String activityType,
                                 @P(value = "Additional tag.", required = false) String tag) {
        TeamIntent intent = new TeamIntent();
        if (StringUtils.isNotBlank(activityType)) {
            intent.setActivityType(activityType.trim());
            intent.getTags().add(activityType.trim());
        }
        if (StringUtils.isNotBlank(tag) && !intent.getTags().contains(tag.trim())) {
            intent.getTags().add(tag.trim());
        }
        intent.setTeamRelated(true);
        return executeAndRemember(RecommendUsersTool.TOOL_NAME, intent);
    }

    @Tool(name = GetTeamDetailsTool.TOOL_NAME, value = "Get public details of a team by team id.")
    public String getTeamDetails(@P(value = "Team id.", required = true) Long teamId) {
        TeamIntent intent = new TeamIntent();
        intent.setTeamId(teamId);
        intent.setTeamRelated(true);
        return executeAndRemember(GetTeamDetailsTool.TOOL_NAME, intent);
    }

    @Tool(name = ListMyCreatedTeamsTool.TOOL_NAME, value = "List teams created by current logged-in user.")
    public String listMyCreatedTeams() {
        TeamIntent intent = new TeamIntent();
        intent.setTeamRelated(true);
        return executeAndRemember(ListMyCreatedTeamsTool.TOOL_NAME, intent);
    }

    @Tool(name = GetMyProfileTool.TOOL_NAME, value = "Get current logged-in user's public profile fields.")
    public String getMyProfile() {
        return executeAndRemember(GetMyProfileTool.TOOL_NAME, new TeamIntent());
    }

    @Tool(name = CreateTeamDraftTool.TOOL_NAME, value = "Create a team draft for user confirmation. This does not write final team tables.")
    public String createTeamDraft(@P(value = "Activity type, for example 羽毛球, 健身, 徒步.", required = true) String activityType,
                                  @P(value = "City name, for example 西安.", required = true) String city,
                                  @P(value = "Maximum team member count.", required = true) Integer memberCount,
                                  @P(value = "Team name.", required = false) String teamName,
                                  @P(value = "Team description.", required = false) String description,
                                  @P(value = "Maximum budget per person.", required = false) Double budgetMax,
                                  @P(value = "Skill level, for example 入门, 中等, 熟练.", required = false) String skillLevel) {
        TeamIntent intent = buildIntent(activityType, city, null, budgetMax, skillLevel, memberCount);
        intent.setCreateTeamRequested(true);
        intent.setTeamName(teamName);
        intent.setDescription(description);

        AiAgentToolContext.State state = aiAgentToolContext.getRequired();
        AiToolResult result = aiToolExecutionService.execute(CreateTeamDraftTool.TOOL_NAME, intent, state.getLoginUser(), state.getSessionId());
        if (result.getData() instanceof TeamDraft draft) {
            TeamDraft savedDraft = aiTeamDraftService.saveDraft(draft, state.getLoginUser(), state.getSessionId());
            result.setData(savedDraft);
            state.setDraft(savedDraft);
        }
        state.getToolResults().add(result);
        return toJson(result);
    }

    private TeamIntent buildIntent(String activityType,
                                   String city,
                                   String district,
                                   Double budgetMax,
                                   String skillLevel,
                                   Integer memberCount) {
        TeamIntent intent = new TeamIntent();
        intent.setTeamRelated(true);
        if (StringUtils.isNotBlank(activityType)) {
            intent.setActivityType(activityType.trim());
            intent.getTags().add(activityType.trim());
        }
        if (StringUtils.isNotBlank(city)) {
            intent.setCity(city.trim());
        }
        if (StringUtils.isNotBlank(district)) {
            intent.setDistrict(district.trim());
        }
        if (budgetMax != null && budgetMax >= 0) {
            intent.setBudgetMax(BigDecimal.valueOf(budgetMax));
        }
        if (StringUtils.isNotBlank(skillLevel)) {
            intent.setSkillLevel(skillLevel.trim());
        }
        if (memberCount != null && memberCount > 0) {
            intent.setMemberCount(memberCount);
        }
        return intent;
    }

    private String executeAndRemember(String toolName, TeamIntent intent) {
        AiAgentToolContext.State state = aiAgentToolContext.getRequired();
        AiToolResult result = aiToolExecutionService.execute(toolName, intent, state.getLoginUser(), state.getSessionId());
        state.getToolResults().add(result);
        return toJson(result);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
