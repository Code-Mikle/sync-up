package com.mikle.syncup.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.mikle.syncup.ai.agent.AiAgentToolContext;
import com.mikle.syncup.ai.agent.AiAssistantTools;
import com.mikle.syncup.ai.model.entity.AiTeamDraft;
import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.vo.TeamDraftVO;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.service.AiTeamDraftService;
import com.mikle.syncup.ai.service.AiChatMessageService;
import com.mikle.syncup.ai.service.AiConversationContextService;
import com.mikle.syncup.ai.service.TeamIntentParser;
import com.mikle.syncup.ai.tool.AiToolRegistry;
import dev.langchain4j.agent.tool.Tool;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.mapper.TeamMapper;
import com.mikle.syncup.mapper.UserMapper;
import com.mikle.syncup.mapper.UserTeamMapper;
import com.mikle.syncup.model.domain.Team;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.service.TeamService;
import com.mikle.syncup.service.UserService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "sync-up.ai.agent.enabled=false")
@AutoConfigureMockMvc
class AiChatServiceTest {

    private static final String RAW_PASSWORD = "Password123";

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Resource
    private TeamIntentParser teamIntentParser;

    @Resource
    private AiToolRegistry aiToolRegistry;

    @Resource
    private TeamService teamService;

    @Resource
    private AiTeamDraftService aiTeamDraftService;

    @Resource
    private AiAssistantTools aiAssistantTools;

    @Resource
    private AiAgentToolContext aiAgentToolContext;

    @Resource
    private UserService userService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private UserTeamMapper userTeamMapper;

    @Resource
    private MockMvc mockMvc;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private AiChatMessageService aiChatMessageService;

    @Resource
    private AiConversationContextService aiConversationContextService;

    @BeforeEach
    void ensureAiTeamDraftTable() {
        addUserProfileColumnIfMissing();
        addUserColumnIfMissing("city", "alter table user add column city varchar(64) null comment '常驻城市' after email");
        addUserColumnIfMissing("lastActiveTime", "alter table user add column lastActiveTime datetime null comment '最近活跃时间' after updateTime");
        jdbcTemplate.execute("""
                create table if not exists ai_team_draft
                (
                    id              bigint auto_increment comment 'id' primary key,
                    draftId         varchar(64) not null comment 'AI 草稿公开 id',
                    sessionId       varchar(64) null comment 'AI 对话会话 id',
                    userId          bigint not null comment '草稿所属用户 id',
                    name            varchar(256) not null comment '队伍名称',
                    description     varchar(1024) null comment '描述',
                    maxNum          int not null comment '最大人数',
                    activityCategory int default 9 null comment '活动大类',
                    activityType    varchar(64) null comment '活动类型',
                    city            varchar(64) null comment '城市',
                    district        varchar(64) null comment '区域',
                    startTime       datetime null comment '活动开始时间',
                    durationMinutes int null comment '预计时长，单位分钟',
                    budgetPerPerson decimal(10, 2) null comment '人均预算',
                    skillLevel      varchar(32) null comment '水平要求',
                    status          tinyint default 0 not null comment '0 - 待确认，1 - 已确认，2 - 已过期',
                    confirmedTeamId bigint null comment '确认后创建的队伍 id',
                    confirmedAt     datetime null comment '确认时间',
                    expiresAt       datetime not null comment '草稿过期时间',
                    createTime      datetime default CURRENT_TIMESTAMP null comment '创建时间',
                    updateTime      datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
                    isDelete        tinyint default 0 not null comment '是否删除'
                ) comment 'AI 队伍草稿'
                """);
        addAiTeamDraftActivityCategoryColumnIfMissing();
        addIndexIfMissing("uk_ai_team_draft_draftId",
                "alter table ai_team_draft add unique index uk_ai_team_draft_draftId (draftId)");
        addIndexIfMissing("idx_ai_team_draft_user_status",
                "alter table ai_team_draft add index idx_ai_team_draft_user_status (userId, status, expiresAt)");
        ensureAiToolCallLogTable();
        ensureAiChatMessageTable();
    }

    private void addUserProfileColumnIfMissing() {
        Integer count = jdbcTemplate.queryForObject("""
                        select count(1)
                        from information_schema.columns
                        where table_schema = database()
                          and table_name = 'user'
                          and column_name = 'profile'
                        """,
                Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.execute("alter table user add column profile varchar(1024) null comment '个人简介 / 自我介绍' after tags");
        }
    }

    private void addUserColumnIfMissing(String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject("""
                        select count(1)
                        from information_schema.columns
                        where table_schema = database()
                          and table_name = 'user'
                          and column_name = ?
                        """,
                Integer.class,
                columnName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void addAiTeamDraftActivityCategoryColumnIfMissing() {
        Integer count = jdbcTemplate.queryForObject("""
                        select count(1)
                        from information_schema.columns
                        where table_schema = database()
                          and table_name = 'ai_team_draft'
                          and column_name = 'activityCategory'
                        """,
                Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.execute("alter table ai_team_draft add column activityCategory int default 9 null comment '活动大类' after maxNum");
        }
    }

    private void addIndexIfMissing(String indexName, String ddl) {
        Integer count = jdbcTemplate.queryForObject("""
                        select count(1)
                        from information_schema.statistics
                        where table_schema = database()
                          and table_name = 'ai_team_draft'
                          and index_name = ?
                        """,
                Integer.class,
                indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void ensureAiToolCallLogTable() {
        jdbcTemplate.execute("""
                create table if not exists ai_tool_call_log
                (
                    id               bigint auto_increment comment 'id' primary key,
                    sessionId        varchar(64) null comment 'AI 对话会话 id',
                    userId           bigint null comment '用户 id',
                    actionType       varchar(64) not null comment '动作类型',
                    toolName         varchar(64) not null comment '工具名称',
                    status           varchar(32) not null comment 'success / failed',
                    argumentsSummary varchar(1024) null comment '脱敏参数摘要',
                    resultSummary    varchar(1024) null comment '结果摘要',
                    errorMessage     varchar(1024) null comment '错误摘要',
                    durationMs       bigint null comment '耗时毫秒',
                    relatedDraftId   varchar(64) null comment '关联草稿 id',
                    relatedTeamId    bigint null comment '关联队伍 id',
                    createTime       datetime default CURRENT_TIMESTAMP null comment '创建时间',
                    updateTime       datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
                    isDelete         tinyint default 0 not null comment '是否删除'
                ) comment 'AI 工具调用审计'
                """);
        addToolCallLogIndexIfMissing("idx_ai_tool_call_log_user_time",
                "alter table ai_tool_call_log add index idx_ai_tool_call_log_user_time (userId, createTime)");
        addToolCallLogIndexIfMissing("idx_ai_tool_call_log_session",
                "alter table ai_tool_call_log add index idx_ai_tool_call_log_session (sessionId)");
        addToolCallLogIndexIfMissing("idx_ai_tool_call_log_action_status",
                "alter table ai_tool_call_log add index idx_ai_tool_call_log_action_status (actionType, status)");
    }

    private void ensureAiChatMessageTable() {
        jdbcTemplate.execute("""
                create table if not exists ai_chat_message
                (
                    id           bigint auto_increment comment 'id' primary key,
                    userId       bigint not null comment '用户 id',
                    sessionId    varchar(64) not null comment 'AI 对话会话 id',
                    role         varchar(16) not null comment 'user / assistant / event',
                    content      varchar(2048) null comment '展示文本或事件文本，已做最小化脱敏',
                    responseJson mediumtext null comment 'AI 响应或事件载荷 JSON',
                    visible      tinyint default 1 not null comment '是否在聊天页展示',
                    expireAt     datetime not null comment '过期时间',
                    createTime   datetime default CURRENT_TIMESTAMP null comment '创建时间',
                    updateTime   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
                    isDelete     tinyint default 0 not null comment '是否删除'
                ) comment 'AI 用户可见聊天记录'
                """);
        addChatMessageIndexIfMissing("idx_ai_chat_message_user_session_time",
                "alter table ai_chat_message add index idx_ai_chat_message_user_session_time (userId, sessionId, createTime)");
        addChatMessageIndexIfMissing("idx_ai_chat_message_user_time",
                "alter table ai_chat_message add index idx_ai_chat_message_user_time (userId, createTime)");
        addChatMessageIndexIfMissing("idx_ai_chat_message_expireAt",
                "alter table ai_chat_message add index idx_ai_chat_message_expireAt (expireAt)");
    }

    private void addChatMessageIndexIfMissing(String indexName, String ddl) {
        Integer count = jdbcTemplate.queryForObject("""
                        select count(1)
                        from information_schema.statistics
                        where table_schema = database()
                          and table_name = 'ai_chat_message'
                          and index_name = ?
                        """,
                Integer.class,
                indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void addToolCallLogIndexIfMissing(String indexName, String ddl) {
        Integer count = jdbcTemplate.queryForObject("""
                        select count(1)
                        from information_schema.statistics
                        where table_schema = database()
                          and table_name = 'ai_tool_call_log'
                          and index_name = ?
                        """,
                Integer.class,
                indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    @Test
    void mockParser_shouldExtractTeamIntentFromNaturalLanguage() {
        TeamIntent intent = teamIntentParser.parse("我想这个周末在西安找2个羽毛球搭子，预算每人50以内，水平中等");

        Assertions.assertEquals("羽毛球", intent.getActivityType());
        Assertions.assertEquals("西安", intent.getCity());
        Assertions.assertEquals(2, intent.getMemberCount());
        Assertions.assertEquals(new BigDecimal("50"), intent.getBudgetMax());
        Assertions.assertEquals("中等", intent.getSkillLevel());
        Assertions.assertTrue(intent.isTeamRelated());
    }

    @Test
    void chat_agentUnavailable_shouldReturnFallbackAndNotCallBusinessTools() throws Exception {
        User user = null;
        try {
            user = createTestUser();

            JsonNode response = chat(loginToken(user), "find badminton partners in xian");

            Assertions.assertFalse(response.at("/data/sessionId").asText().isBlank());
            Assertions.assertEquals("AI 助手暂时不可用，请稍后再试。", response.at("/data/reply").asText());
            Assertions.assertFalse(response.at("/data/needClarification").asBoolean());
            Assertions.assertTrue(response.at("/data/intent").isMissingNode() || response.at("/data/intent").isNull());
            Assertions.assertEquals(0, response.at("/data/toolResults").size());
        } finally {
            cleanupUserAndTeams(user);
        }
    }

    @Test
    void recommendUsers_withoutCurrentUserTags_shouldReturnEmptyList() {
        User loginUser = null;
        try {
            loginUser = createTestUser();

            AiToolResult result = aiToolRegistry.execute("recommendUsers", new TeamIntent(), loginUser);

            Assertions.assertTrue(result.isSuccess());
            JsonNode users = objectMapper.valueToTree(result.getData());
            Assertions.assertTrue(users.isArray());
            Assertions.assertEquals(0, users.size());
        } finally {
            cleanupUserAndTeams(loginUser);
        }
    }

    @Test
    void recommendUsers_shouldExcludeCurrentUser() {
        User loginUser = null;
        try {
            String uniqueTag = "stage16_self_" + UUID.randomUUID().toString().replace("-", "");
            loginUser = createTestUser("[\"" + uniqueTag + "\"]");

            AiToolResult result = aiToolRegistry.execute("recommendUsers", new TeamIntent(), loginUser);

            Assertions.assertTrue(result.isSuccess());
            JsonNode users = objectMapper.valueToTree(result.getData());
            Assertions.assertTrue(users.isArray());
            Assertions.assertFalse(users.toString().contains(String.valueOf(loginUser.getId())));
        } finally {
            cleanupUserAndTeams(loginUser);
        }
    }

    @Test
    void recommendUsers_shouldUseCurrentUserTags() {
        User loginUser = null;
        User matchedUser = null;
        try {
            String uniqueTag = "stage16_match_" + UUID.randomUUID().toString().replace("-", "");
            loginUser = createTestUser("[\"" + uniqueTag + "\"]");
            matchedUser = createTestUser("[\"" + uniqueTag + "\"]");

            AiToolResult result = aiToolRegistry.execute("recommendUsers", new TeamIntent(), loginUser);

            Assertions.assertTrue(result.isSuccess());
            JsonNode users = objectMapper.valueToTree(result.getData());
            Assertions.assertTrue(users.isArray());
            boolean containsMatchedUser = false;
            boolean containsLoginUser = false;
            for (JsonNode user : users) {
                containsMatchedUser |= user.path("id").asLong() == matchedUser.getId();
                containsLoginUser |= user.path("id").asLong() == loginUser.getId();
            }
            Assertions.assertTrue(containsMatchedUser);
            Assertions.assertFalse(containsLoginUser);
        } finally {
            cleanupUserAndTeams(loginUser);
            cleanupUserAndTeams(matchedUser);
        }
    }

    @Test
    void agentRecommendUsersTool_shouldNotAcceptTemporaryTags() throws Exception {
        Assertions.assertEquals(0, AiAssistantTools.class.getDeclaredMethod("recommendUsers").getParameterCount());
    }

    @Test
    void chat_createTeamRequest_agentUnavailable_shouldNotCreateDraftOrTeam() throws Exception {
        User user = null;
        try {
            user = createTestUser();
            long beforeCount = countTeamsCreatedBy(user.getId());

            JsonNode response = chat(loginToken(user), "create a badminton team in xian");

            Assertions.assertTrue(response.at("/data/draft").isMissingNode() || response.at("/data/draft").isNull());
            Assertions.assertEquals(0, response.at("/data/toolResults").size());
            Assertions.assertEquals(beforeCount, countTeamsCreatedBy(user.getId()));
        } finally {
            cleanupUserAndTeams(user);
        }
    }

    @Test
    void confirmTeamDraft_shouldCreateTeamAfterUserConfirmation() throws Exception {
        User user = null;
        try {
            user = createTestUser();
            long beforeCount = countTeamsCreatedBy(user.getId());
            String draftId = createPendingDraft(user);

            JsonNode confirmResponse = confirmDraft(loginToken(user), draftId, 0);

            Assertions.assertEquals(draftId, confirmResponse.at("/data/draftId").asText());
            Assertions.assertTrue(confirmResponse.at("/data/teamId").asLong() > 0);
            Assertions.assertEquals(beforeCount + 1, countTeamsCreatedBy(user.getId()));
            Assertions.assertEquals(1L,
                    countDraftConfirmAudit(draftId, "success"));
        } finally {
            cleanupUserAndTeams(user);
        }
    }

    @Test
    void confirmTeamDraft_confirmedDraft_shouldBeRejected() throws Exception {
        User user = null;
        try {
            user = createTestUser();
            String token = loginToken(user);
            String draftId = createPendingDraft(user);

            confirmDraft(token, draftId, 0);
            JsonNode secondResponse = confirmDraft(token, draftId, 40000);

            Assertions.assertEquals(40000, secondResponse.at("/code").asInt());
            Assertions.assertEquals(1L,
                    countDraftConfirmAudit(draftId, "failed"));
        } finally {
            cleanupUserAndTeams(user);
        }
    }

    @Test
    void confirmTeamDraft_otherUserDraft_shouldBeRejected() throws Exception {
        User owner = null;
        User other = null;
        try {
            owner = createTestUser();
            other = createTestUser();
            String draftId = createPendingDraft(owner);

            JsonNode response = confirmDraft(loginToken(other), draftId, 40101);

            Assertions.assertEquals(40101, response.at("/code").asInt());
            Assertions.assertEquals(1L,
                    countDraftConfirmAudit(draftId, "failed"));
        } finally {
            cleanupUserAndTeams(owner);
            cleanupUserAndTeams(other);
        }
    }

    @Test
    void confirmTeamDraft_expiredDraft_shouldBeRejected() throws Exception {
        User user = null;
        try {
            user = createTestUser();
            AiTeamDraft draft = new AiTeamDraft();
            draft.setDraftId(UUID.randomUUID().toString());
            draft.setSessionId(UUID.randomUUID().toString());
            draft.setUserId(user.getId());
            draft.setName("expired_ai_draft");
            draft.setDescription("expired draft test");
            draft.setMaxNum(4);
            draft.setActivityCategory(1);
            draft.setActivityType("羽毛球");
            draft.setCity("西安");
            draft.setStatus(0);
            draft.setStartTime(new Date(System.currentTimeMillis() + 2 * 24 * 60 * 60 * 1000));
            draft.setExpiresAt(new Date(System.currentTimeMillis() - 60 * 1000));
            Assertions.assertTrue(aiTeamDraftService.save(draft));

            JsonNode response = confirmDraft(loginToken(user), draft.getDraftId(), 40000);

            Assertions.assertEquals(40000, response.at("/code").asInt());
            Assertions.assertEquals(1L,
                    countDraftConfirmAudit(draft.getDraftId(), "failed"));
            AiTeamDraft expiredDraft = aiTeamDraftService.getById(draft.getId());
            Assertions.assertNotNull(expiredDraft);
            Assertions.assertEquals(2, expiredDraft.getStatus());
        } finally {
            cleanupUserAndTeams(user);
        }
    }

    @Test
    void getTeamDetails_shouldReturnPublicTeamDetailsAndAudit() throws Exception {
        User creator = null;
        User loginUser = null;
        try {
            creator = createTestUser();
            loginUser = createTestUser();
            long teamId = createStructuredTeam(creator, "羽毛球", "西安", new BigDecimal("45.00"), 6);
            String sessionId = UUID.randomUUID().toString();

            JsonNode response = getTeamDetails(loginToken(loginUser), teamId, sessionId, 0);

            Assertions.assertEquals("getTeamDetails", response.at("/data/toolName").asText());
            Assertions.assertTrue(response.at("/data/success").asBoolean());
            Assertions.assertEquals(teamId, response.at("/data/data/id").asLong());
            Assertions.assertEquals(1, response.at("/data/data/hasJoinNum").asInt());
            Assertions.assertFalse(response.at("/data/data/hasJoin").asBoolean());
            Assertions.assertEquals(1L,
                    countAuditLogs(loginUser.getId(), sessionId, "tool", "getTeamDetails", "success"));
        } finally {
            cleanupUserAndTeams(creator);
            cleanupUserAndTeams(loginUser);
        }
    }

    @Test
    void getTeamDetails_missingTeam_shouldBeRejectedAndAuditFailure() throws Exception {
        User user = null;
        try {
            user = createTestUser();
            String sessionId = UUID.randomUUID().toString();

            JsonNode response = getTeamDetails(loginToken(user), 999999999L, sessionId, 40001);

            Assertions.assertEquals(40001, response.at("/code").asInt());
            Assertions.assertEquals(1L,
                    countAuditLogs(user.getId(), sessionId, "tool", "getTeamDetails", "failed"));
        } finally {
            cleanupUserAndTeams(user);
        }
    }

    @Test
    void getTeamDetails_withoutLogin_shouldReturnNotLogin() throws Exception {
        mockMvc.perform(post("/ai/team/{teamId}/details", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void stage17Tools_shouldBeRegisteredInWhitelist() {
        Assertions.assertTrue(aiToolRegistry.contains("searchTeams"));
        Assertions.assertTrue(aiToolRegistry.contains("recommendUsers"));
        Assertions.assertTrue(aiToolRegistry.contains("createTeamDraft"));
        Assertions.assertTrue(aiToolRegistry.contains("prepareDeleteTeam"));
        Assertions.assertTrue(aiToolRegistry.contains("deleteTeam"));
        Assertions.assertTrue(aiToolRegistry.contains("listMyCreatedTeams"));
        Assertions.assertTrue(aiToolRegistry.contains("getMyProfile"));
        Assertions.assertTrue(aiToolRegistry.contains("updateMyProfile"));
        Assertions.assertTrue(aiToolRegistry.contains("listMyJoinedTeams"));
        Assertions.assertTrue(aiToolRegistry.contains("joinTeam"));
        Assertions.assertTrue(aiToolRegistry.contains("quitTeam"));
    }

    @Test
    void agentTools_shouldNotExposeUnsafeWriteToolsWithoutConfirmation() {
        Set<String> exposedToolNames = Arrays.stream(AiAssistantTools.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Tool.class))
                .filter(java.util.Objects::nonNull)
                .map(Tool::name)
                .collect(Collectors.toSet());

        Assertions.assertFalse(exposedToolNames.contains("joinTeam"));
        Assertions.assertFalse(exposedToolNames.contains("quitTeam"));
        Assertions.assertFalse(exposedToolNames.contains("deleteTeam"));
        Assertions.assertTrue(exposedToolNames.contains("prepareDeleteTeam"));
    }

    @Test
    void getMyProfile_shouldReturnOnlyPublicProfileFields() {
        User user = null;
        try {
            user = createTestUser("[\"羽毛球\",\"健身\"]");
            User updateUser = new User();
            updateUser.setId(user.getId());
            updateUser.setCity("西安");
            userService.updateById(updateUser);
            user.setCity("西安");

            AiToolResult result = aiToolRegistry.execute("getMyProfile", new TeamIntent(), user);

            Assertions.assertTrue(result.isSuccess());
            JsonNode profile = objectMapper.valueToTree(result.getData());
            Assertions.assertEquals(user.getId(), profile.at("/id").asLong());
            Assertions.assertEquals(user.getUsername(), profile.at("/username").asText());
            Assertions.assertEquals("西安", profile.at("/city").asText());
            Assertions.assertTrue(profile.findPath("userAccount").isMissingNode());
            Assertions.assertTrue(profile.findPath("phone").isMissingNode());
            Assertions.assertTrue(profile.findPath("email").isMissingNode());
            Assertions.assertTrue(profile.findPath("userPassword").isMissingNode());
        } finally {
            cleanupUserAndTeams(user);
        }
    }

    @Test
    void aiTeamOperationTools_shouldJoinListAndQuitTeam() {
        User creator = null;
        User loginUser = null;
        try {
            creator = createTestUser();
            loginUser = createTestUser();
            long teamId = createStructuredTeam(creator, "羽毛球", "西安", new BigDecimal("45.00"), 6);

            TeamIntent joinIntent = new TeamIntent();
            joinIntent.setTeamId(teamId);
            AiToolResult joinResult = aiToolRegistry.execute("joinTeam", joinIntent, loginUser);

            Assertions.assertTrue(joinResult.isSuccess());
            Assertions.assertEquals(1L, countUserTeam(loginUser.getId(), teamId));

            AiToolResult listResult = aiToolRegistry.execute("listMyJoinedTeams", new TeamIntent(), loginUser);
            JsonNode joinedTeams = objectMapper.valueToTree(listResult.getData());
            Assertions.assertTrue(joinedTeams.isArray());
            Assertions.assertTrue(joinedTeams.toString().contains(String.valueOf(teamId)));
            Assertions.assertTrue(joinedTeams.findPath("password").isMissingNode());

            TeamIntent quitIntent = new TeamIntent();
            quitIntent.setTeamId(teamId);
            AiToolResult quitResult = aiToolRegistry.execute("quitTeam", quitIntent, loginUser);

            Assertions.assertTrue(quitResult.isSuccess());
            Assertions.assertEquals(0L, countUserTeam(loginUser.getId(), teamId));
        } finally {
            cleanupUserAndTeams(creator);
            cleanupUserAndTeams(loginUser);
        }
    }

    @Test
    void joinTeamTool_missingTeamId_shouldBeRejected() {
        User loginUser = null;
        try {
            loginUser = createTestUser();
            User currentUser = loginUser;

            BusinessException exception = Assertions.assertThrows(
                    BusinessException.class,
                    () -> aiToolRegistry.execute("joinTeam", new TeamIntent(), currentUser)
            );

            Assertions.assertNotNull(exception);
        } finally {
            cleanupUserAndTeams(loginUser);
        }
    }

    @Test
    void listMyCreatedTeams_shouldOnlyReturnCurrentUserTeams() {
        User owner = null;
        User other = null;
        try {
            owner = createTestUser();
            other = createTestUser();
            long ownerTeamId = createStructuredTeam(owner, "羽毛球", "西安", new BigDecimal("45.00"), 6);
            long otherTeamId = createStructuredTeam(other, "健身", "西安", new BigDecimal("30.00"), 5);

            AiToolResult result = aiToolRegistry.execute("listMyCreatedTeams", new TeamIntent(), owner);

            Assertions.assertTrue(result.isSuccess());
            JsonNode teams = objectMapper.valueToTree(result.getData());
            Assertions.assertTrue(teams.isArray());
            Assertions.assertTrue(teams.toString().contains(String.valueOf(ownerTeamId)));
            Assertions.assertFalse(teams.toString().contains(String.valueOf(otherTeamId)));
            Assertions.assertTrue(teams.findPath("password").isMissingNode());
        } finally {
            cleanupUserAndTeams(owner);
            cleanupUserAndTeams(other);
        }
    }

    @Test
    void agentToolCall_shouldBackfillIntentFromModelParametersAndSaveDraft() throws Exception {
        User user = null;
        try {
            user = createTestUser();
            String sessionId = UUID.randomUUID().toString();
            aiAgentToolContext.start(sessionId, user);

            aiAssistantTools.createTeamDraft(
                    1,
                    "足球",
                    "西安",
                    "西安市运动公园",
                    "2026-07-20 17:00:00",
                    180,
                    11,
                    "明天下午足球活动",
                    "明天下午五点在西安市运动公园踢足球，活动持续约3小时，无需支付场地费用。",
                    0D,
                    null
            );

            AiAgentToolContext.State state = aiAgentToolContext.snapshot();
            TeamIntent intent = state.getIntent();
            TeamDraftVO draft = state.getDraft();

            Assertions.assertNotNull(intent);
            Assertions.assertEquals(1, intent.getActivityCategory());
            Assertions.assertEquals("足球", intent.getActivityType());
            Assertions.assertEquals("西安", intent.getCity());
            Assertions.assertEquals("西安市运动公园", intent.getDistrict());
            Assertions.assertEquals(11, intent.getMemberCount());
            Assertions.assertEquals(new BigDecimal("0.0"), intent.getBudgetMax());
            Assertions.assertEquals(180, intent.getDurationMinutes());
            Assertions.assertEquals("2026-07-20 17:00:00", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(intent.getStartTime()));
            Assertions.assertTrue(intent.isCreateTeamRequested());

            Assertions.assertNotNull(draft);
            Assertions.assertEquals(1, draft.getActivityCategory());
            Assertions.assertEquals("足球", draft.getActivityType());
            Assertions.assertEquals("西安", draft.getCity());
            Assertions.assertEquals("西安市运动公园", draft.getDistrict());
            Assertions.assertEquals(11, draft.getMaxNum());
            Assertions.assertEquals(new BigDecimal("0.0"), draft.getBudgetPerPerson());
            Assertions.assertEquals(180, draft.getDurationMinutes());
        } finally {
            aiAgentToolContext.clear();
            cleanupUserAndTeams(user);
        }
    }

    @Test
    void agentCreateTeamDraft_withoutCity_shouldUseLoginUserCity() throws Exception {
        User user = null;
        try {
            user = createTestUser();
            User updateUser = new User();
            updateUser.setId(user.getId());
            updateUser.setCity("西安");
            userService.updateById(updateUser);
            user.setCity("西安");
            String sessionId = UUID.randomUUID().toString();
            aiAgentToolContext.start(sessionId, user);

            aiAssistantTools.createTeamDraft(
                    4,
                    "桌游",
                    null,
                    "钟楼附近",
                    "2026-07-21 14:00:00",
                    null,
                    6,
                    "钟楼桌游局",
                    "明天下午 2 点在钟楼附近玩桌游，队伍人数上限 6 人。",
                    null,
                    null
            );

            AiAgentToolContext.State state = aiAgentToolContext.snapshot();

            Assertions.assertNotNull(state.getIntent());
            Assertions.assertEquals("西安", state.getIntent().getCity());
            Assertions.assertEquals("钟楼附近", state.getIntent().getDistrict());
            Assertions.assertNotNull(state.getDraft());
            Assertions.assertEquals("西安", state.getDraft().getCity());
            Assertions.assertEquals("钟楼附近", state.getDraft().getDistrict());
            Assertions.assertEquals(6, state.getDraft().getMaxNum());
        } finally {
            aiAgentToolContext.clear();
            cleanupUserAndTeams(user);
        }
    }

    @Test
    void agentCreateTeamDraft_withoutCityAndUserCity_shouldNotSaveDraft() throws Exception {
        User user = null;
        try {
            user = createTestUser();
            String sessionId = UUID.randomUUID().toString();
            aiAgentToolContext.start(sessionId, user);

            String resultJson = aiAssistantTools.createTeamDraft(
                    4,
                    "桌游",
                    null,
                    "钟楼附近",
                    "2026-07-21 14:00:00",
                    null,
                    6,
                    "钟楼桌游局",
                    "明天下午 2 点在钟楼附近玩桌游，队伍人数上限 6 人。",
                    null,
                    null
            );

            JsonNode result = objectMapper.readTree(resultJson);
            AiAgentToolContext.State state = aiAgentToolContext.snapshot();

            Assertions.assertFalse(result.at("/success").asBoolean());
            Assertions.assertTrue(result.at("/summary").asText().contains("城市"));
            Assertions.assertNull(state.getDraft());
            Assertions.assertEquals(0L, countPendingDrafts(user.getId()));
        } finally {
            aiAgentToolContext.clear();
            cleanupUserAndTeams(user);
        }
    }

    @Test
    void prepareDeleteTeam_shouldReturnConfirmationAndNotDeleteTeam() {
        User user = null;
        try {
            user = createTestUser();
            long teamId = createStructuredTeam(user, "桌游", "西安", BigDecimal.ZERO, 6);
            TeamIntent intent = new TeamIntent();
            intent.setTeamId(teamId);

            AiToolResult result = aiToolRegistry.execute("prepareDeleteTeam", intent, user);

            Assertions.assertTrue(result.isSuccess());
            JsonNode confirmation = objectMapper.valueToTree(result.getData());
            Assertions.assertEquals(teamId, confirmation.at("/teamId").asLong());
            Assertions.assertEquals("西安", confirmation.at("/city").asText());
            Assertions.assertEquals(1, countTeamsCreatedBy(user.getId()));
        } finally {
            cleanupUserAndTeams(user);
        }
    }

    @Test
    void agentToolCall_prepareDeleteTeam_shouldSetDeleteConfirmation() {
        User user = null;
        try {
            user = createTestUser();
            long teamId = createStructuredTeam(user, "桌游", "西安", BigDecimal.ZERO, 6);
            aiAgentToolContext.start(UUID.randomUUID().toString(), user);

            aiAssistantTools.prepareDeleteTeam(teamId);

            AiAgentToolContext.State state = aiAgentToolContext.snapshot();
            Assertions.assertNotNull(state.getDeleteConfirmation());
            Assertions.assertEquals(teamId, state.getDeleteConfirmation().getTeamId());
        } finally {
            aiAgentToolContext.clear();
            cleanupUserAndTeams(user);
        }
    }

    @Test
    void confirmDeleteTeam_shouldDeleteAfterUserConfirmation() throws Exception {
        User user = null;
        try {
            user = createTestUser();
            long teamId = createStructuredTeam(user, "桌游", "西安", BigDecimal.ZERO, 6);
            String sessionId = UUID.randomUUID().toString();

            JsonNode response = confirmDeleteTeam(loginToken(user), teamId, sessionId, 0);

            Assertions.assertTrue(response.at("/data/success").asBoolean());
            Assertions.assertEquals(teamId, response.at("/data/data/teamId").asLong());
            Assertions.assertEquals(0, countTeamsCreatedBy(user.getId()));
            Assertions.assertEquals(1L,
                    countAuditLogs(user.getId(), sessionId, "tool", "deleteTeam", "success"));
        } finally {
            cleanupUserAndTeams(user);
        }
    }

    @Test
    void confirmDeleteTeam_otherUserTeam_shouldBeRejected() throws Exception {
        User owner = null;
        User other = null;
        try {
            owner = createTestUser();
            other = createTestUser();
            long teamId = createStructuredTeam(owner, "桌游", "西安", BigDecimal.ZERO, 6);
            String sessionId = UUID.randomUUID().toString();

            JsonNode response = confirmDeleteTeam(loginToken(other), teamId, sessionId, 40101);

            Assertions.assertEquals(40101, response.at("/code").asInt());
            Assertions.assertEquals(1, countTeamsCreatedBy(owner.getId()));
            Assertions.assertEquals(1L,
                    countAuditLogs(other.getId(), sessionId, "tool", "deleteTeam", "failed"));
        } finally {
            cleanupUserAndTeams(owner);
            cleanupUserAndTeams(other);
        }
    }

    @Test
    void conversationContext_shouldSummarizeRecentBusinessEventsForReferences() {
        User user = null;
        try {
            user = createTestUser();
            String sessionId = UUID.randomUUID().toString();
            long createdTeamId = 9900001L;
            long deletedTeamId = 9900002L;

            aiChatMessageService.saveTeamDraftConfirmedEvent(user, sessionId, "draft_context_test", createdTeamId);
            aiChatMessageService.saveTeamDeletedEvent(user, sessionId, deletedTeamId);

            String context = aiConversationContextService.buildRecentBusinessContext(user, sessionId);

            Assertions.assertTrue(context.contains("当前会话近期业务事件"));
            Assertions.assertTrue(context.contains("TEAM_CREATED"));
            Assertions.assertTrue(context.contains("TEAM#" + createdTeamId));
            Assertions.assertTrue(context.contains("TEAM_DELETED"));
            Assertions.assertTrue(context.contains("TEAM#" + deletedTeamId));
            Assertions.assertTrue(context.contains("刚刚"));
            Assertions.assertTrue(context.contains("必须先生成确认"));
        } finally {
            cleanupUserAndTeams(user);
        }
    }

    @Test
    void toolRegistry_unknownTool_shouldBeRejected() {
        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> aiToolRegistry.execute("deleteEverything", new TeamIntent(), new User())
        );

        Assertions.assertNotNull(exception);
    }

    @Test
    void aiChatApi_withoutLogin_shouldReturnNotLogin() throws Exception {
        mockMvc.perform(post("/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"我想在西安找羽毛球搭子\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }

    private User createTestUser() {
        return createTestUser(null);
    }

    private User createTestUser(String tags) {
        User user = new User();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        user.setUsername("ai_test_" + suffix);
        user.setUserAccount("ai_" + suffix);
        user.setUserPassword(PASSWORD_ENCODER.encode(RAW_PASSWORD));
        user.setPlanetCode(UUID.randomUUID().toString().replace("-", "").substring(0, 5));
        user.setUserRole(0);
        user.setUserStatus(0);
        user.setTags(tags);
        boolean saved = userService.save(user);
        Assertions.assertTrue(saved, "test user should be created");
        return user;
    }

    private String loginToken(User user) throws Exception {
        String content = mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userAccount", user.getUserAccount(),
                                "userPassword", RAW_PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = objectMapper.readTree(content);
        return root.at("/data/tokenPrefix").asText() + " " + root.at("/data/token").asText();
    }

    private JsonNode chat(String token, String message) throws Exception {
        String content = mockMvc.perform(post("/ai/chat")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", message))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content);
    }

    private JsonNode confirmDraft(String token, String draftId, int expectedCode) throws Exception {
        String content = mockMvc.perform(post("/ai/team-draft/{draftId}/confirm", draftId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content);
    }

    private JsonNode findToolResult(JsonNode chatResponse, String toolName) {
        JsonNode toolResults = chatResponse.at("/data/toolResults");
        if (!toolResults.isArray()) {
            return MissingNode.getInstance();
        }
        for (JsonNode toolResult : toolResults) {
            if (toolName.equals(toolResult.path("toolName").asText())) {
                return toolResult;
            }
        }
        return MissingNode.getInstance();
    }

    private JsonNode getTeamDetails(String token, long teamId, String sessionId, int expectedCode) throws Exception {
        String content = mockMvc.perform(post("/ai/team/{teamId}/details", teamId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sessionId", sessionId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andReturn()
                .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(content);
    }

    private JsonNode confirmDeleteTeam(String token, long teamId, String sessionId, int expectedCode) throws Exception {
        String content = mockMvc.perform(post("/ai/team/{teamId}/delete/confirm", teamId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sessionId", sessionId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content);
    }

    private String createPendingDraft(User user) {
        AiTeamDraft draft = new AiTeamDraft();
        draft.setDraftId(UUID.randomUUID().toString());
        draft.setSessionId(UUID.randomUUID().toString());
        draft.setUserId(user.getId());
        draft.setName("pending_ai_draft");
        draft.setDescription("pending draft test");
        draft.setMaxNum(4);
        draft.setActivityCategory(1);
        draft.setActivityType("badminton");
        draft.setCity("xian");
        draft.setStatus(0);
        draft.setStartTime(new Date(System.currentTimeMillis() + 2 * 24 * 60 * 60 * 1000));
        draft.setExpiresAt(new Date(System.currentTimeMillis() + 30 * 60 * 1000));
        Assertions.assertTrue(aiTeamDraftService.save(draft));
        return draft.getDraftId();
    }

    private long createStructuredTeam(User creator, String activityType, String city, BigDecimal budgetPerPerson, int maxNum) {
        Team team = new Team();
        team.setName("ai_t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        team.setDescription("ai stage 1.1 test");
        team.setActivityCategory(resolveActivityCategory(activityType));
        team.setMaxNum(maxNum);
        team.setStatus(0);
        team.setExpireTime(new Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000));
        team.setActivityType(activityType);
        team.setCity(city);
        team.setStartTime(new Date(System.currentTimeMillis() + 2 * 24 * 60 * 60 * 1000));
        team.setDurationMinutes(120);
        team.setBudgetPerPerson(budgetPerPerson);
        team.setSkillLevel("中等");
        return teamService.addTeam(team, creator);
    }

    private int resolveActivityCategory(String activityType) {
        if ("徒步".equals(activityType)) {
            return 2;
        }
        if ("桌游".equals(activityType)) {
            return 4;
        }
        return 1;
    }

    private long countTeamsCreatedBy(long userId) {
        return teamService.count(new QueryWrapper<Team>().eq("userId", userId));
    }

    private Long countUserTeam(long userId, long teamId) {
        return jdbcTemplate.queryForObject("""
                        select count(1)
                        from user_team
                        where isDelete = 0
                          and userId = ?
                          and teamId = ?
                        """,
                Long.class,
                userId,
                teamId);
    }

    private Long countAuditLogs(long userId, String sessionId, String actionType, String toolName, String status) {
        return jdbcTemplate.queryForObject("""
                        select count(1)
                        from ai_tool_call_log
                        where isDelete = 0
                          and userId = ?
                          and sessionId = ?
                          and actionType = ?
                          and toolName = ?
                          and status = ?
                        """,
                Long.class,
                userId,
                sessionId,
                actionType,
                toolName,
                status);
    }

    private Long countDraftConfirmAudit(String draftId, String status) {
        return jdbcTemplate.queryForObject("""
                        select count(1)
                        from ai_tool_call_log
                        where isDelete = 0
                          and actionType = 'confirmDraft'
                          and toolName = 'confirmTeamDraft'
                          and relatedDraftId = ?
                          and status = ?
                        """,
                Long.class,
                draftId,
                status);
    }

    private Long countPendingDrafts(long userId) {
        return jdbcTemplate.queryForObject("""
                        select count(1)
                        from ai_team_draft
                        where isDelete = 0
                          and status = 0
                          and userId = ?
                        """,
                Long.class,
                userId);
    }

    private void cleanupUserAndTeams(User user) {
        if (user == null || user.getId() <= 0) {
            return;
        }
        userTeamMapper.deleteByTeamCreatorUserIdPhysically(user.getId());
        userTeamMapper.deleteByUserIdPhysically(user.getId());
        teamMapper.deleteByUserIdPhysically(user.getId());
        deleteAiTestDataPhysically(user.getId());
        userMapper.deleteByIdPhysically(user.getId());
    }

    private void deleteAiTestDataPhysically(long userId) {
        jdbcTemplate.update("delete from ai_chat_message where userId = ?", userId);
        jdbcTemplate.update("""
                        delete from ai_tool_call_log
                        where userId = ?
                           or relatedDraftId in (
                               select draftId from ai_team_draft where userId = ?
                           )
                        """,
                userId,
                userId);
        jdbcTemplate.update("delete from ai_team_draft where userId = ?", userId);
    }
}
