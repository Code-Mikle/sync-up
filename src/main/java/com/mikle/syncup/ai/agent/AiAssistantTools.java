package com.mikle.syncup.ai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.vo.AiTeamDeleteConfirmationVO;
import com.mikle.syncup.ai.model.vo.AiUiBlockVO;
import com.mikle.syncup.ai.model.vo.TeamDraftVO;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.agent.AiIntent;
import com.mikle.syncup.ai.model.agent.UserIntent;
import com.mikle.syncup.ai.model.agent.TagResolutionIntent;
import com.mikle.syncup.ai.service.AiTeamDraftService;
import com.mikle.syncup.ai.service.AiToolExecutionService;
import com.mikle.syncup.ai.tool.CreateTeamDraftTool;
import com.mikle.syncup.ai.tool.GetMyProfileTool;
import com.mikle.syncup.ai.tool.GetTeamDetailsTool;
import com.mikle.syncup.ai.tool.ListMyJoinedTeamsTool;
import com.mikle.syncup.ai.tool.ListMyCreatedTeamsTool;
import com.mikle.syncup.ai.tool.SearchUsersTool;
import com.mikle.syncup.ai.tool.ResolveTagsTool;
import com.mikle.syncup.ai.tool.DeleteTeamConfirmationTool;
import com.mikle.syncup.ai.tool.SearchTeamsTool;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Component
public class AiAssistantTools {

    public static final String SHOW_MY_PROFILE_CARD_TOOL_NAME = "show_my_profile_card";

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
        inheritActivityFilter(intent, aiAgentToolContext.getRequired().getTeamIntent());
        return executeAndRemember(SearchTeamsTool.TOOL_NAME, intent, AiUiBlockVO.TEAM_LIST, "search");
    }

    @Tool(
            name = SearchUsersTool.TOOL_NAME,
            value = "根据当前用户本轮明确表达的条件搜索适合成为搭子的其他用户。" +
                    "tagIds、profile、city、gender 均为可选条件；tagIds 只能使用本轮 resolve_tags 工具已确认的标准标签 id，" +
                    "不得编造或直接使用自然语言标签。未提供 city 时后端使用当前用户常驻城市，未提供 gender 时不限制。" +
                    "profile 表示本次希望匹配的搭子描述；未提供时后端使用当前用户的 AI 匹配画像。" +
                    "只返回允许公开展示的用户资料，不得展示或复述内部用户画像。")
    public String searchUsers(
            @P(value = "resolve_tags 返回并确认的标准标签 id。未提及活动或未能可靠归一化时留空。", required = false)
            List<Long> tagIds,
            @P(value = "本次希望匹配的搭子描述。例如“性格随和、周末能一起徒步”。未提及时留空。", required = false)
            String profile,
            @P(value = "用户明确要求的目标城市；未提及时留空，由后端使用当前用户常驻城市。", required = false)
            String city,
            @P(value = "目标性别：0=男，1=女。未明确要求时必须留空，不得传 2。", required = false)
            Integer gender) {
        UserIntent intent = buildUserIntent(tagIds, profile, city, gender);
        return executeAndRememberUserSearch(intent);
    }

    @Tool(
            name = ResolveTagsTool.TOOL_NAME,
            value = "将用户本轮表达的活动短语归一化为受控词表标签。用户要求推荐、搜索或寻找特定活动搭子时，" +
                    "必须先调用本工具，再调用 search_users。对每个短语：RESOLVED 可直接使用 resolvedTag.tagId；" +
                    "NEEDS_JUDGMENT 时只能从 candidates 中选择一个最符合的 tagId；UNRESOLVED 时不得编造 tagId。")
    public String resolveTags(
            @P(value = "从用户本轮需求中提取的活动短语，例如“附近玩两天”“一起打羽毛球”。最多 5 个。", required = true)
            List<String> tagQueries) {
        TagResolutionIntent intent = new TagResolutionIntent();
        if (tagQueries != null) {
            intent.setTagQueries(new ArrayList<>(tagQueries.stream()
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))));
        }
        AiAgentToolContext.State state = aiAgentToolContext.getRequired();
        attachSourceText(intent, state);
        AiToolResult result = aiToolExecutionService.execute(
                ResolveTagsTool.TOOL_NAME, intent, state.getLoginUser(), state.getSessionId());
        state.getToolResults().add(result);
        return toJson(result);
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
        return executeAndRemember(ListMyCreatedTeamsTool.TOOL_NAME, intent, AiUiBlockVO.TEAM_LIST, "created");
    }

    @Tool(
            name = DeleteTeamConfirmationTool.TOOL_NAME,
            value = "为删除当前用户创建的队伍生成待确认卡片，不执行实际删除。仅在用户明确要求删除、取消或移除自己创建的队伍，" +
                    "并且能够确定队伍 ID 时调用。无法确定目标队伍时，先查询当前用户创建的队伍或向用户确认。")
    public String deleteTeamConfirmation(
            @P(value = "要删除的队伍 ID。必须来自用户明确输入、当前对话中的已确认结果或查询工具返回结果，不得编造。", required = true)
            Long teamId)
    {
        TeamIntent intent = new TeamIntent();
        intent.setTeamId(teamId);
        intent.setTeamRelated(true);
        AiAgentToolContext.State state = aiAgentToolContext.getRequired();
        attachSourceText(intent, state);
        state.setTeamIntent(mergeIntent(state.getTeamIntent(), intent));
        AiToolResult result = aiToolExecutionService.execute(DeleteTeamConfirmationTool.TOOL_NAME, intent, state.getLoginUser(), state.getSessionId());
        if (result.getData() instanceof AiTeamDeleteConfirmationVO confirmation) {
            state.setDeleteConfirmation(confirmation);
            state.getUiBlocks().add(AiUiBlockVO.of(AiUiBlockVO.TEAM_DELETE_CONFIRMATION, confirmation));
        }
        state.getToolResults().add(result);
        return toJson(result);
    }

    @Tool(name = ListMyJoinedTeamsTool.TOOL_NAME, value = "查询当前登录用户已经加入的队伍")
    public String listMyJoinedTeams() {
        TeamIntent intent = new TeamIntent();
        intent.setTeamRelated(true);
        return executeAndRemember(ListMyJoinedTeamsTool.TOOL_NAME, intent, AiUiBlockVO.TEAM_LIST, "joined");
    }

    @Tool(
            name = GetMyProfileTool.TOOL_NAME,
            value = "读取当前登录用户的公开资料，用于回答昵称、性别、城市、标签等具体字段问题。" +
                    "该工具只提供回答所需数据，不展示完整资料卡片；用户明确要求查看完整资料卡片时改用 show_my_profile_card。")
    public String getMyProfile() {
        return executeAndRemember(GetMyProfileTool.TOOL_NAME, new TeamIntent());
    }

    @Tool(
            name = SHOW_MY_PROFILE_CARD_TOOL_NAME,
            value = "读取并展示当前登录用户的完整公开资料卡片。仅当用户明确要求查看、展示或打开完整个人资料时调用；" +
                    "询问昵称、性别、城市等单个字段时不要调用。")
    public String showMyProfileCard() {
        return executeAndRemember(
                GetMyProfileTool.TOOL_NAME,
                new TeamIntent(),
                AiUiBlockVO.PROFILE_CARD,
                null
        );
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
        attachSourceText(intent, state);
        intent.setCreateTeamRequested(true);
        intent.setTeamName(teamName);
        intent.setDescription(description);
        state.setTeamIntent(mergeIntent(state.getTeamIntent(), intent));
        AiToolResult result = aiToolExecutionService.execute(CreateTeamDraftTool.TOOL_NAME, intent, state.getLoginUser(), state.getSessionId());
        if (result.getData() instanceof TeamDraftVO draft) {
            TeamDraftVO savedDraft = aiTeamDraftService.saveDraft(draft, state.getLoginUser(), state.getSessionId());
            result.setData(savedDraft);
            state.setDraft(savedDraft);
            state.getUiBlocks().add(AiUiBlockVO.of(AiUiBlockVO.TEAM_DRAFT_CONFIRMATION, savedDraft));
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
        return executeAndRemember(toolName, intent, null, null);
    }

    private String executeAndRemember(String toolName,
                                      TeamIntent intent,
                                      String uiBlockType,
                                      String uiBlockVariant) {
        AiAgentToolContext.State state = aiAgentToolContext.getRequired();
        attachSourceText(intent, state);
        state.setTeamIntent(mergeIntent(state.getTeamIntent(), intent));
        AiToolResult result = aiToolExecutionService.execute(toolName, intent, state.getLoginUser(), state.getSessionId());
        state.getToolResults().add(result);
        if (result.isSuccess() && uiBlockType != null) {
            replaceUiBlock(state, AiUiBlockVO.of(uiBlockType, uiBlockVariant, result.getData()));
        }
        return toJson(result);
    }

    /**
     * Models occasionally retry a search without repeating filters that were already identified
     * in this turn. Keep the explicit activity constraint to prevent a failed precise search
     * from silently degrading into an unrelated browse result.
     */
    private void inheritActivityFilter(TeamIntent incoming, TeamIntent previous) {
        if (previous == null) {
            return;
        }
        if (incoming.getActivityCategory() == null && previous.getActivityCategory() != null) {
            incoming.setActivityCategory(previous.getActivityCategory());
        }
        if (StringUtils.isBlank(incoming.getActivityType()) && StringUtils.isNotBlank(previous.getActivityType())) {
            String activityType = previous.getActivityType().trim();
            incoming.setActivityType(activityType);
            if (!incoming.getTags().contains(activityType)) {
                incoming.getTags().add(activityType);
            }
        }
    }

    private void replaceUiBlock(AiAgentToolContext.State state, AiUiBlockVO incoming) {
        state.getUiBlocks().removeIf(existing -> Objects.equals(existing.getType(), incoming.getType())
                && Objects.equals(existing.getVariant(), incoming.getVariant()));
        state.getUiBlocks().add(incoming);
    }

    private void attachSourceText(AiIntent intent, AiAgentToolContext.State state) {
        if (StringUtils.isBlank(intent.getSourceText()) && StringUtils.isNotBlank(state.getSourceText())) {
            intent.setSourceText(state.getSourceText().trim());
        }
    }

    private UserIntent buildUserIntent(List<Long> tagIds, String profile, String city, Integer gender) {
        if (gender != null && gender != 0 && gender != 1) {
            throw new IllegalArgumentException("gender must be 0 or 1 when provided");
        }
        UserIntent intent = new UserIntent();
        if (tagIds != null) {
            if (tagIds.stream().anyMatch(id -> id == null || id <= 0)) {
                throw new IllegalArgumentException("tagIds must contain positive ids only");
            }
            intent.setTagIds(new ArrayList<>(tagIds.stream()
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))));
        }
        if (StringUtils.isNotBlank(profile)) {
            intent.setProfile(profile.trim());
        }
        if (StringUtils.isNotBlank(city)) {
            intent.setCity(city.trim());
        }
        intent.setGender(gender);
        return intent;
    }

    private String executeAndRememberUserSearch(UserIntent intent) {
        AiAgentToolContext.State state = aiAgentToolContext.getRequired();
        attachSourceText(intent, state);
        state.setUserIntent(intent);
        AiToolResult result = aiToolExecutionService.execute(
                SearchUsersTool.TOOL_NAME, intent, state.getLoginUser(), state.getSessionId());
        state.getToolResults().add(result);
        if (result.isSuccess()) {
            replaceUiBlock(state, AiUiBlockVO.of(AiUiBlockVO.USER_RECOMMENDATIONS, result.getData()));
        }
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
