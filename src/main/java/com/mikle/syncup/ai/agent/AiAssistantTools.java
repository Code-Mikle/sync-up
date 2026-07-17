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
import com.mikle.syncup.ai.tool.ListMyJoinedTeamsTool;
import com.mikle.syncup.ai.tool.ListMyCreatedTeamsTool;
import com.mikle.syncup.ai.tool.RecommendUsersTool;
import com.mikle.syncup.ai.tool.SearchTeamsTool;
import com.mikle.syncup.ai.tool.UpdateMyProfileTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

@Component
public class AiAssistantTools {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
                              @P(value = "Start time in yyyy-MM-dd HH:mm:ss if user provides an exact or relative time.", required = false) String startTime,
                              @P(value = "Maximum budget per person.", required = false) Double budgetMax,
                              @P(value = "Skill level, for example 入门, 中等, 熟练.", required = false) String skillLevel) {
        TeamIntent intent = buildIntent(activityType, city, district, startTime, null, budgetMax, skillLevel, null);
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

    @Tool(name = ListMyJoinedTeamsTool.TOOL_NAME, value = "List teams joined by current logged-in user.")
    public String listMyJoinedTeams() {
        TeamIntent intent = new TeamIntent();
        intent.setTeamRelated(true);
        return executeAndRemember(ListMyJoinedTeamsTool.TOOL_NAME, intent);
    }

    @Tool(name = GetMyProfileTool.TOOL_NAME, value = "Get current logged-in user's public profile fields.")
    public String getMyProfile() {
        return executeAndRemember(GetMyProfileTool.TOOL_NAME, new TeamIntent());
    }

    @Tool(name = UpdateMyProfileTool.TOOL_NAME, value = "Update current logged-in user's self introduction and confirmed structured profile. Only use when user explicitly asks to update their own profile or self introduction.")
    public String updateMyProfile(@P(value = "The user's self introduction text to save. Do not include phone, email, password, token or API key.", required = true) String profileText) {
        TeamIntent intent = new TeamIntent();
        intent.setProfileText(profileText);
        return executeAndRemember(UpdateMyProfileTool.TOOL_NAME, intent);
    }

    @Tool(name = CreateTeamDraftTool.TOOL_NAME, value = "Create a team draft for user confirmation. This does not write final team tables.")
    public String createTeamDraft(@P(value = "Activity type inferred from user semantics, for example 足球, 羽毛球, 健身, 徒步.", required = true) String activityType,
                                  @P(value = "City name, for example 西安.", required = true) String city,
                                  @P(value = "District, venue or business area, for example 西安市运动公园.", required = false) String district,
                                  @P(value = "Start time in yyyy-MM-dd HH:mm:ss. Infer relative time from the conversation date when user says 明天/周末/下午五点.", required = false) String startTime,
                                  @P(value = "Activity duration in minutes, for example 180 for 3 hours.", required = false) Integer durationMinutes,
                                  @P(value = "Maximum team member count.", required = true) Integer memberCount,
                                  @P(value = "Team name.", required = false) String teamName,
                                  @P(value = "Team description.", required = false) String description,
                                  @P(value = "Maximum budget per person. Use 0 when user says free, no fee, 无需支付, 不收费.", required = false) Double budgetMax,
                                  @P(value = "Skill level, for example 入门, 中等, 熟练.", required = false) String skillLevel) {
        TeamIntent intent = buildIntent(activityType, city, district, startTime, durationMinutes, budgetMax, skillLevel, memberCount);
        intent.setCreateTeamRequested(true);
        intent.setTeamName(teamName);
        intent.setDescription(description);

        AiAgentToolContext.State state = aiAgentToolContext.getRequired();
        state.setIntent(mergeIntent(state.getIntent(), intent));
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
                                   String startTime,
                                   Integer durationMinutes,
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
        Date parsedStartTime = parseStartTime(startTime);
        if (parsedStartTime != null) {
            intent.setStartTime(parsedStartTime);
        }
        if (durationMinutes != null && durationMinutes > 0) {
            intent.setDurationMinutes(durationMinutes);
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
        state.setIntent(mergeIntent(state.getIntent(), intent));
        AiToolResult result = aiToolExecutionService.execute(toolName, intent, state.getLoginUser(), state.getSessionId());
        state.getToolResults().add(result);
        return toJson(result);
    }

    private TeamIntent mergeIntent(TeamIntent existing, TeamIntent incoming) {
        if (existing == null) {
            return incoming;
        }
        if (StringUtils.isNotBlank(incoming.getSourceText())) {
            existing.setSourceText(incoming.getSourceText());
        }
        if (incoming.getTeamId() != null) {
            existing.setTeamId(incoming.getTeamId());
        }
        if (StringUtils.isNotBlank(incoming.getTeamPassword())) {
            existing.setTeamPassword(incoming.getTeamPassword());
        }
        if (StringUtils.isNotBlank(incoming.getActivityType())) {
            existing.setActivityType(incoming.getActivityType());
        }
        if (StringUtils.isNotBlank(incoming.getCity())) {
            existing.setCity(incoming.getCity());
        }
        if (StringUtils.isNotBlank(incoming.getDistrict())) {
            existing.setDistrict(incoming.getDistrict());
        }
        if (incoming.getStartTime() != null) {
            existing.setStartTime(incoming.getStartTime());
        }
        if (incoming.getDurationMinutes() != null) {
            existing.setDurationMinutes(incoming.getDurationMinutes());
        }
        if (incoming.getMemberCount() != null) {
            existing.setMemberCount(incoming.getMemberCount());
        }
        if (incoming.getBudgetMin() != null) {
            existing.setBudgetMin(incoming.getBudgetMin());
        }
        if (incoming.getBudgetMax() != null) {
            existing.setBudgetMax(incoming.getBudgetMax());
        }
        if (StringUtils.isNotBlank(incoming.getSkillLevel())) {
            existing.setSkillLevel(incoming.getSkillLevel());
        }
        if (StringUtils.isNotBlank(incoming.getTeamName())) {
            existing.setTeamName(incoming.getTeamName());
        }
        if (StringUtils.isNotBlank(incoming.getDescription())) {
            existing.setDescription(incoming.getDescription());
        }
        if (StringUtils.isNotBlank(incoming.getProfileText())) {
            existing.setProfileText(incoming.getProfileText());
        }
        existing.setCreateTeamRequested(existing.isCreateTeamRequested() || incoming.isCreateTeamRequested());
        existing.setTeamRelated(existing.isTeamRelated() || incoming.isTeamRelated());
        incoming.getTags().forEach(tag -> {
            if (!existing.getTags().contains(tag)) {
                existing.getTags().add(tag);
            }
        });
        incoming.getMissingFields().forEach(field -> {
            if (!existing.getMissingFields().contains(field)) {
                existing.getMissingFields().add(field);
            }
        });
        return existing;
    }

    private Date parseStartTime(String startTime) {
        if (StringUtils.isBlank(startTime)) {
            return null;
        }
        String normalized = startTime.trim().replace('T', ' ');
        if (normalized.length() == 16) {
            normalized = normalized + ":00";
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(normalized, DATE_TIME_FORMATTER);
            return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
