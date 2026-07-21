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
import com.mikle.syncup.ai.tool.PrepareProfileUpdateTool;
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

    @Tool(
            name = SearchTeamsTool.TOOL_NAME,
            value = "查询和筛选当前公开可用的队伍。用户想搜索、浏览、筛选或泛化推荐队伍时调用。用户没有提供筛选条件时，所有可选参数留空并直接调用。" +
                    "只有用户明确要求根据自己的资料、兴趣或标签推荐时，才需要先读取用户公开资料。")
    public String searchTeams(
            @P(value = "活动大类编码：1=运动健身, 2=户外出行, 3=游戏电竞, 4=桌游剧本, 5=休闲娱乐, 6=美食探店, 7=学习成长, " +
                    "8=旅行出游, 9=其他。使用最接近的宽泛类别，不得虚构具体活动。", required = false) Integer activityCategory,
            @P(value = "具体活动名称,例如“足球”、“羽毛球”、“骑行”。若用户仅给出宽泛类别，则留空。", required = false
            ) String activityType,
            @P(value = "用户明确提供的城市。用户只提供地标、商圈或场馆时不要推断城市，应留空，由后端尝试补齐。", required = false) String city,
            @P(value = "区域、场馆、地标或商圈。例如“钟楼附近”或“西安市运动公园”。", required = false) String district,
            @P(value = "开始时间，格式为 yyyy-MM-dd HH:mm:ss。当用户提及“明天”、“周末”、“下午五点”等相对时间时，" +
                    "需根据对话日期推断具体时间。", required = false) String startTime,
            @P(value = "每人最高预算", required = false) Double budgetMax,
            @P(value = "技能熟练度，分为入门, 中等, 熟练。", required = false) String skillLevel) {
        TeamIntent intent = buildIntent(activityCategory, activityType, city, district, startTime, null, budgetMax, skillLevel, null);
        return executeAndRemember(SearchTeamsTool.TOOL_NAME, intent);
    }

    @Tool(
            name = RecommendUsersTool.TOOL_NAME,
            value = "根据当前登录用户已保存的标签，推荐适合成为搭子的其他用户。无需从本轮消息中提取活动类型或临时标签。" +
                    "只返回允许公开展示的用户资料。")
    public String recommendUsers() {
        TeamIntent intent = new TeamIntent();
        intent.setTeamRelated(true);
        return executeAndRemember(RecommendUsersTool.TOOL_NAME, intent);
    }

    @Tool(name = GetTeamDetailsTool.TOOL_NAME, value = "根据队伍 id 获取队伍的公开详情")
    public String getTeamDetails(@P(value = "队伍 id", required = true) Long teamId) {
        TeamIntent intent = new TeamIntent();
        intent.setTeamId(teamId);
        intent.setTeamRelated(true);
        return executeAndRemember(GetTeamDetailsTool.TOOL_NAME, intent);
    }

    @Tool(name = ListMyCreatedTeamsTool.TOOL_NAME, value = "查询当前用户创建的队伍")
    public String listMyCreatedTeams() {
        TeamIntent intent = new TeamIntent();
        intent.setTeamRelated(true);
        return executeAndRemember(ListMyCreatedTeamsTool.TOOL_NAME, intent);
    }

    @Tool(
            name = PrepareDeleteTeamTool.TOOL_NAME,
            value = "为删除当前用户创建的队伍生成待确认卡片，不执行实际删除。仅在用户明确要求删除、取消或移除自己创建的队伍，" +
                    "并且能够确定队伍 ID 时调用。无法确定目标队伍时，先查询当前用户创建的队伍或向用户确认。")
    public String prepareDeleteTeam(
            @P(value = "要删除的队伍 ID。必须来自用户明确输入、当前对话中的已确认结果或查询工具返回结果，不得编造。", required = true)
            Long teamId)
    {
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

    @Tool(name = ListMyJoinedTeamsTool.TOOL_NAME, value = "查询当前登录用户创建的队伍")
    public String listMyJoinedTeams() {
        TeamIntent intent = new TeamIntent();
        intent.setTeamRelated(true);
        return executeAndRemember(ListMyJoinedTeamsTool.TOOL_NAME, intent);
    }

    @Tool(name = GetMyProfileTool.TOOL_NAME, value = "获取当前登录用户的公开资料字段（包含城市）")
    public String getMyProfile() {
        return executeAndRemember(GetMyProfileTool.TOOL_NAME, new TeamIntent());
    }

    @Tool(
            name = PrepareProfileUpdateTool.TOOL_NAME,
            value = "根据用户明确提出的修改内容，生成当前用户公开资料的待确认修改草稿。" +
                    "该工具不会执行正式写入。仅当用户明确要求修改自己的公开资料或个人简介时调用。")
    public String prepareMyProfileUpdate(@P(value = "用户明确要求保存的公开资料内容。不得包含手机号、邮箱、密码、Token、API Key 等敏感信息。", required = true) String profileText) {
        TeamIntent intent = new TeamIntent();
        intent.setProfileText(profileText);
        return executeAndRemember(PrepareProfileUpdateTool.TOOL_NAME, intent);
    }

    @Tool(
            name = CreateTeamDraftTool.TOOL_NAME,
            value = "根据用户提供的信息生成创建队伍的待确认草稿。该工具不会创建正式队伍。" +
                    "当用户明确要求创建、发起或组建队伍时调用。未提供城市时，将由后端尝试使用当前用户的常驻城市。")
    public String createTeamDraft(
            @P(value = "活动大类编码：1=运动健身, 2=户外出行, 3=游戏电竞, 4=桌游剧本, 5=休闲娱乐, 6=美食探店, 7=学习成长, " +
                            "8=旅行出游, 9=其他。使用最接近的宽泛类别，不得虚构具体活动。", required = true
            ) Integer activityCategory,
            @P(value = "具体活动名称,例如“足球”、“羽毛球”、“骑行”。若用户仅给出宽泛类别，则留空。", required = false) String activityType,
            @P(value = "用户明确提供的城市。用户只提供地标、商圈或场馆时不要推断城市，应留空，由后端尝试补齐。", required = false
            ) String city,
            @P(value = "区域、场馆、地标或商圈。例如“钟楼附近”或“西安市运动公园”。", required = false) String district,
            @P(value = "开始时间，格式为 yyyy-MM-dd HH:mm:ss。当用户提及“明天”、“周末”、“下午五点”等相对时间时，" +
                    "需根据对话日期推断具体时间。", required = false
            ) String startTime,
            @P(value = "活动时长（分钟）", required = false) Integer durationMinutes,
            @P(value = "队伍总人数上限", required = true) Integer memberCount,
            @P(value = "队伍名。如果用户没有提供，则生成一个简短的默认名称。", required = false) String teamName,
            @P(value = "可选的队伍描述。若用户未提供，则根据已知的活动、时间与地点生成一段简短的事实性描述。", required = false
            ) String description,
            @P(value = "每个人的最大花费。如果没有提供默认为 0。", required = false) Double budgetMax,
            @P(value = "技能熟练度，分为入门, 中等, 熟练。", required = false) String skillLevel) {
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
