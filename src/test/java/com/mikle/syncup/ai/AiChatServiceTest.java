package com.mikle.syncup.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.mikle.syncup.ai.model.AiTeamDraft;
import com.mikle.syncup.ai.model.AiToolResult;
import com.mikle.syncup.ai.model.TeamIntent;
import com.mikle.syncup.ai.service.AiTeamDraftService;
import com.mikle.syncup.ai.service.TeamIntentParser;
import com.mikle.syncup.ai.tool.AiToolRegistry;
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
import java.util.Date;
import java.util.Map;
import java.util.UUID;

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

    @BeforeEach
    void ensureAiTeamDraftTable() {
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
        addIndexIfMissing("uk_ai_team_draft_draftId",
                "alter table ai_team_draft add unique index uk_ai_team_draft_draftId (draftId)");
        addIndexIfMissing("idx_ai_team_draft_user_status",
                "alter table ai_team_draft add index idx_ai_team_draft_user_status (userId, status, expiresAt)");
        ensureAiToolCallLogTable();
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
    void chat_missingActivityType_shouldClarifyAndNotCallTools() throws Exception {
        User user = null;
        try {
            user = createTestUser();

            JsonNode response = chat(loginToken(user), "我想在西安找搭子，预算50以内");

            Assertions.assertTrue(response.at("/data/needClarification").asBoolean());
            Assertions.assertTrue(response.at("/data/intent/missingFields").toString().contains("activityType"));
            Assertions.assertEquals(0, response.at("/data/toolResults").size());
        } finally {
            cleanupUserAndTeams(user);
        }
    }

    @Test
    void chat_irrelevantInput_shouldNotCallBusinessTools() throws Exception {
        User user = null;
        try {
            user = createTestUser();

            JsonNode response = chat(loginToken(user), "帮我解释一下 Java 的泛型");

            Assertions.assertFalse(response.at("/data/needClarification").asBoolean());
            Assertions.assertFalse(response.at("/data/intent/teamRelated").asBoolean());
            Assertions.assertEquals(0, response.at("/data/toolResults").size());
        } finally {
            cleanupUserAndTeams(user);
        }
    }

    @Test
    void chat_validTeamIntent_shouldCallSearchTeamsTool() throws Exception {
        User creator = null;
        User loginUser = null;
        try {
            creator = createTestUser("[\"羽毛球\",\"中等\"]");
            loginUser = createTestUser("[\"羽毛球\"]");
            long teamId = createStructuredTeam(creator, "羽毛球", "西安", new BigDecimal("45.00"), 6);

            JsonNode response = chat(loginToken(loginUser), "我想这个周末在西安找羽毛球搭子，预算每人50以内");

            Assertions.assertFalse(response.at("/data/needClarification").asBoolean());
            JsonNode searchResult = findToolResult(response, "searchTeams");
            Assertions.assertFalse(searchResult.isMissingNode());
            JsonNode teams = searchResult.at("/data");
            Assertions.assertTrue(teams.isArray());
            Assertions.assertTrue(teams.toString().contains(String.valueOf(teamId)));
            JsonNode recommendResult = findToolResult(response, "recommendUsers");
            Assertions.assertFalse(recommendResult.isMissingNode());
            JsonNode users = recommendResult.at("/data");
            Assertions.assertTrue(users.isArray());
            Assertions.assertTrue(users.toString().contains(String.valueOf(creator.getId())));
            Assertions.assertTrue(users.findPath("userAccount").isMissingNode());
            Assertions.assertTrue(users.findPath("phone").isMissingNode());
            Assertions.assertTrue(users.findPath("email").isMissingNode());
            Assertions.assertEquals(1L,
                    countAuditLogs(loginUser.getId(), response.at("/data/sessionId").asText(), "tool", "searchTeams", "success"));
            Assertions.assertEquals(1L,
                    countAuditLogs(loginUser.getId(), response.at("/data/sessionId").asText(), "tool", "recommendUsers", "success"));
        } finally {
            cleanupUserAndTeams(creator);
            cleanupUserAndTeams(loginUser);
        }
    }

    @Test
    void recommendUsers_noMatch_shouldReturnEmptyList() {
        User loginUser = null;
        try {
            loginUser = createTestUser();
            TeamIntent intent = new TeamIntent();
            intent.setActivityType("stage16_no_match_" + UUID.randomUUID().toString().replace("-", ""));

            AiToolResult result = aiToolRegistry.execute("recommendUsers", intent, loginUser);

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
            TeamIntent intent = new TeamIntent();
            intent.setActivityType(uniqueTag);

            AiToolResult result = aiToolRegistry.execute("recommendUsers", intent, loginUser);

            Assertions.assertTrue(result.isSuccess());
            JsonNode users = objectMapper.valueToTree(result.getData());
            Assertions.assertTrue(users.isArray());
            Assertions.assertFalse(users.toString().contains(String.valueOf(loginUser.getId())));
        } finally {
            cleanupUserAndTeams(loginUser);
        }
    }

    @Test
    void chat_createTeamRequest_shouldOnlyReturnDraftAndNotWriteTeam() throws Exception {
        User user = null;
        try {
            user = createTestUser();
            long beforeCount = countTeamsCreatedBy(user.getId());

            JsonNode response = chat(loginToken(user), "帮我在西安创建一个4人的羽毛球队伍，预算每人50以内");

            Assertions.assertFalse(response.at("/data/draft").isMissingNode());
            Assertions.assertFalse(response.at("/data/draft/draftId").asText().isBlank());
            Assertions.assertFalse(findToolResult(response, "recommendUsers").isMissingNode());
            Assertions.assertFalse(findToolResult(response, "createTeamDraft").isMissingNode());
            Assertions.assertEquals(beforeCount, countTeamsCreatedBy(user.getId()));
            Assertions.assertEquals(1L,
                    countAuditLogs(user.getId(), response.at("/data/sessionId").asText(), "tool", "createTeamDraft", "success"));
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
            JsonNode chatResponse = chat(loginToken(user), "帮我在西安创建一个4人的羽毛球队伍，预算每人50以内");
            String draftId = chatResponse.at("/data/draft/draftId").asText();

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
            JsonNode chatResponse = chat(token, "帮我在西安创建一个4人的羽毛球队伍，预算每人50以内");
            String draftId = chatResponse.at("/data/draft/draftId").asText();

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
            JsonNode chatResponse = chat(loginToken(owner), "帮我在西安创建一个4人的羽毛球队伍，预算每人50以内");
            String draftId = chatResponse.at("/data/draft/draftId").asText();

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
        Assertions.assertTrue(aiToolRegistry.contains("listMyCreatedTeams"));
        Assertions.assertTrue(aiToolRegistry.contains("getMyProfile"));
    }

    @Test
    void getMyProfile_shouldReturnOnlyPublicProfileFields() {
        User user = null;
        try {
            user = createTestUser("[\"羽毛球\",\"健身\"]");

            AiToolResult result = aiToolRegistry.execute("getMyProfile", new TeamIntent(), user);

            Assertions.assertTrue(result.isSuccess());
            JsonNode profile = objectMapper.valueToTree(result.getData());
            Assertions.assertEquals(user.getId(), profile.at("/id").asLong());
            Assertions.assertEquals(user.getUsername(), profile.at("/username").asText());
            Assertions.assertTrue(profile.findPath("userAccount").isMissingNode());
            Assertions.assertTrue(profile.findPath("phone").isMissingNode());
            Assertions.assertTrue(profile.findPath("email").isMissingNode());
            Assertions.assertTrue(profile.findPath("userPassword").isMissingNode());
        } finally {
            cleanupUserAndTeams(user);
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

    private long createStructuredTeam(User creator, String activityType, String city, BigDecimal budgetPerPerson, int maxNum) {
        Team team = new Team();
        team.setName("ai_t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        team.setDescription("ai stage 1.1 test");
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

    private long countTeamsCreatedBy(long userId) {
        return teamService.count(new QueryWrapper<Team>().eq("userId", userId));
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
