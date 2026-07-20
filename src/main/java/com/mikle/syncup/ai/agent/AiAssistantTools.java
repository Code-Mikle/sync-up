package com.mikle.syncup.ai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.vo.AiTeamDeleteConfirmationVO;
import com.mikle.syncup.ai.model.vo.TeamDraftVO;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.service.AiTeamDraftService;
import com.mikle.syncup.ai.service.AiToolExecutionService;
import com.mikle.syncup.ai.tool.CreateTeamDraftTool;
import com.mikle.syncup.ai.tool.GetMyProfileTool;
import com.mikle.syncup.ai.tool.GetTeamDetailsTool;
import com.mikle.syncup.ai.tool.ListMyJoinedTeamsTool;
import com.mikle.syncup.ai.tool.ListMyCreatedTeamsTool;
import com.mikle.syncup.ai.tool.RecommendUsersTool;
import com.mikle.syncup.ai.tool.PrepareDeleteTeamTool;
import com.mikle.syncup.ai.tool.SearchTeamsTool;
import com.mikle.syncup.ai.tool.UpdateMyProfileTool;
import com.mikle.syncup.model.domain.User;
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

    @Tool(name = SearchTeamsTool.TOOL_NAME, value = "Search public available teams by activity category, city, budget and skill level. Call it without filters for general team recommendations. activityCategory codes: 1=运动健身, 2=户外出行, 3=游戏电竞, 4=桌游剧本, 5=休闲娱乐, 6=美食探店, 7=学习成长, 8=旅行出游, 9=其他.")
    public String searchTeams(@P(value = "Optional activity category code. Use 2 for 户外运动/户外出行. Do not convert broad categories into specific activities.", required = false) Integer activityCategory,
                              @P(value = "Optional specific activity text, for example 羽毛球, 骑行, 徒步. Do not guess a specific activity when the user only gives a broad category.", required = false) String activityType,
                              @P(value = "City name, for example 西安.", required = false) String city,
                              @P(value = "District or business area.", required = false) String district,
                              @P(value = "Start time in yyyy-MM-dd HH:mm:ss if user provides an exact or relative time.", required = false) String startTime,
                              @P(value = "Maximum budget per person.", required = false) Double budgetMax,
                              @P(value = "Skill level, for example 入门, 中等, 熟练.", required = false) String skillLevel) {
        TeamIntent intent = buildIntent(activityCategory, activityType, city, district, startTime, null, budgetMax, skillLevel, null);
        return executeAndRemember(SearchTeamsTool.TOOL_NAME, intent);
    }

    @Tool(name = RecommendUsersTool.TOOL_NAME, value = "Recommend users or partners. If activityType and tag are omitted, recommend based on the current user's tags and profile.")
    public String recommendUsers(@P(value = "Optional activity type or primary tag. Omit it when the user only asks for general partner recommendations.", required = false) String activityType,
                                 @P(value = "Optional additional tag. Omit it when the user does not provide a specific preference.", required = false) String tag) {
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

    @Tool(name = PrepareDeleteTeamTool.TOOL_NAME, value = "Prepare a delete-team confirmation card. This does not delete anything. Use it when the user asks to delete/cancel/remove a team they created and you can identify the team id from the current conversation, for example 已创建队伍 #392. If the user says 刚刚创建的那个队伍, use the most recent confirmed team id in chat memory. If no team id is known, call listMyCreatedTeams first or ask the user which team to delete.")
    public String prepareDeleteTeam(@P(value = "Team id to delete. Must come from user input, previous confirmed team id in the conversation, or listMyCreatedTeams result; do not invent it.", required = true) Long teamId) {
        TeamIntent intent = new TeamIntent();
        intent.setTeamId(teamId);
        intent.setTeamRelated(true);
        AiAgentToolContext.State state = aiAgentToolContext.getRequired();
        state.setIntent(mergeIntent(state.getIntent(), intent));
        AiToolResult result = aiToolExecutionService.execute(PrepareDeleteTeamTool.TOOL_NAME, intent, state.getLoginUser(), state.getSessionId());
        if (result.getData() instanceof AiTeamDeleteConfirmationVO confirmation) {
            state.setDeleteConfirmation(confirmation);
        }
        state.getToolResults().add(result);
        return toJson(result);
    }

    @Tool(name = ListMyJoinedTeamsTool.TOOL_NAME, value = "List teams joined by current logged-in user.")
    public String listMyJoinedTeams() {
        TeamIntent intent = new TeamIntent();
        intent.setTeamRelated(true);
        return executeAndRemember(ListMyJoinedTeamsTool.TOOL_NAME, intent);
    }

    @Tool(name = GetMyProfileTool.TOOL_NAME, value = "Get current logged-in user's public profile fields, including city. Do not use for general team recommendations unless the user explicitly asks to recommend by my profile, interests or tags.")
    public String getMyProfile() {
        return executeAndRemember(GetMyProfileTool.TOOL_NAME, new TeamIntent());
    }

    @Tool(name = UpdateMyProfileTool.TOOL_NAME, value = "Update current logged-in user's self introduction and confirmed structured profile. Only use when user explicitly asks to update their own profile or self introduction.")
    public String updateMyProfile(@P(value = "The user's self introduction text to save. Do not include phone, email, password, token or API key.", required = true) String profileText) {
        TeamIntent intent = new TeamIntent();
        intent.setProfileText(profileText);
        return executeAndRemember(UpdateMyProfileTool.TOOL_NAME, intent);
    }

    @Tool(name = CreateTeamDraftTool.TOOL_NAME, value = "Create a team draft for user confirmation. This does not write final team tables. If the user did not provide city, omit city and this tool will use the current user's city when available. Never guess city from landmarks such as 钟楼. If both user input and current profile have no city, ask the user for city. If teamName or description is not provided, use a concise default based on known activity, time and place. activityCategory codes: 1=运动健身, 2=户外出行, 3=游戏电竞, 4=桌游剧本, 5=休闲娱乐, 6=美食探店, 7=学习成长, 8=旅行出游, 9=其他.")
    public String createTeamDraft(@P(value = "Required activity category code. Use the closest broad category, not a guessed specific activity.", required = true) Integer activityCategory,
                                  @P(value = "Optional specific activity text, for example 足球, 羽毛球, 骑行, 徒步. Leave empty when user only gives a broad category.", required = false) String activityType,
                                  @P(value = "Optional city name explicitly provided by the user, for example 西安. Do not infer it from a landmark or business area; leave empty when the user only says 钟楼附近, 体育场附近, 商场名, etc.", required = false) String city,
                                  @P(value = "District, venue, landmark or business area, for example 钟楼附近 or 西安市运动公园.", required = false) String district,
                                  @P(value = "Start time in yyyy-MM-dd HH:mm:ss. Infer relative time from the conversation date when user says 明天/周末/下午五点.", required = false) String startTime,
                                  @P(value = "Activity duration in minutes, for example 180 for 3 hours.", required = false) Integer durationMinutes,
                                  @P(value = "Maximum team member count.", required = true) Integer memberCount,
                                  @P(value = "Optional team name. Generate a short natural name if the user did not provide one.", required = false) String teamName,
                                  @P(value = "Optional team description. Generate a short factual description from known activity, time and place if the user did not provide one.", required = false) String description,
                                  @P(value = "Maximum budget per person. Use 0 when user says free, no fee, 无需支付, 不收费.", required = false) Double budgetMax,
                                  @P(value = "Skill level, for example 入门, 中等, 熟练.", required = false) String skillLevel) {
        AiAgentToolContext.State state = aiAgentToolContext.getRequired();
        String resolvedCity = resolveDraftCity(city, state.getLoginUser());
        TeamIntent intent = buildIntent(activityCategory, activityType, resolvedCity, district, startTime, durationMinutes, budgetMax, skillLevel, memberCount);
        intent.setCreateTeamRequested(true);
        intent.setTeamName(teamName);
        intent.setDescription(description);
        state.setIntent(mergeIntent(state.getIntent(), intent));
        AiToolResult result = aiToolExecutionService.execute(CreateTeamDraftTool.TOOL_NAME, intent, state.getLoginUser(), state.getSessionId());
        if (result.getData() instanceof TeamDraftVO draft) {
            TeamDraftVO savedDraft = aiTeamDraftService.saveDraft(draft, state.getLoginUser(), state.getSessionId());
            result.setData(savedDraft);
            state.setDraft(savedDraft);
        }
        state.getToolResults().add(result);
        return toJson(result);
    }

    private String resolveDraftCity(String city, User loginUser) {
        if (StringUtils.isNotBlank(city)) {
            return city.trim();
        }
        if (loginUser != null && StringUtils.isNotBlank(loginUser.getCity())) {
            return loginUser.getCity().trim();
        }
        return null;
    }

    private TeamIntent buildIntent(Integer activityCategory,
                                   String activityType,
                                   String city,
                                   String district,
                                   String startTime,
                                   Integer durationMinutes,
                                   Double budgetMax,
                                   String skillLevel,
                                   Integer memberCount) {
        TeamIntent intent = new TeamIntent();
        intent.setTeamRelated(true);
        if (activityCategory != null) {
            intent.setActivityCategory(activityCategory);
        }
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
        if (incoming.getActivityCategory() != null) {
            existing.setActivityCategory(incoming.getActivityCategory());
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
