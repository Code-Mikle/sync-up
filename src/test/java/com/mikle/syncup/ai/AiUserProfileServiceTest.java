package com.mikle.syncup.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.vo.AiProfileResponse;
import com.mikle.syncup.ai.service.AiUserProfileService;
import com.mikle.syncup.ai.tool.AiToolRegistry;
import com.mikle.syncup.ai.tool.UpdateMyProfileTool;
import com.mikle.syncup.mapper.UserMapper;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.service.UserService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "sync-up.ai.agent.enabled=false")
@AutoConfigureMockMvc
class AiUserProfileServiceTest {

    private static final String RAW_PASSWORD = "Password123";

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Resource
    private UserService userService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private AiToolRegistry aiToolRegistry;

    @Resource
    private AiUserProfileService aiUserProfileService;

    @Resource
    private MockMvc mockMvc;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void ensureStage2Tables() {
        addUserProfileColumnIfMissing();
        jdbcTemplate.execute("""
                create table if not exists ai_user_profile
                (
                    id            bigint auto_increment comment 'id' primary key,
                    userId        bigint not null comment '用户 id',
                    profileJson   text not null comment '结构化画像 JSON',
                    sourceText    varchar(1024) null comment '画像来源文本，已做最小化脱敏',
                    modelVersion  varchar(64) not null comment '提取模型或规则版本',
                    status        tinyint default 1 not null comment '1 - 已确认',
                    confirmedAt   datetime null comment '用户确认时间',
                    createTime    datetime default CURRENT_TIMESTAMP null comment '创建时间',
                    updateTime    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
                    isDelete      tinyint default 0 not null comment '是否删除'
                ) comment 'AI 用户结构化画像'
                """);
        addIndexIfMissing("ai_user_profile", "uk_ai_user_profile_userId",
                "alter table ai_user_profile add unique index uk_ai_user_profile_userId (userId)");
        addIndexIfMissing("ai_user_profile", "idx_ai_user_profile_updateTime",
                "alter table ai_user_profile add index idx_ai_user_profile_updateTime (updateTime)");

        jdbcTemplate.execute("""
                create table if not exists ai_profile_draft
                (
                    id             bigint auto_increment comment 'id' primary key,
                    draftId        varchar(64) not null comment '画像草稿公开 id',
                    userId         bigint not null comment '用户 id',
                    sourceText     varchar(1024) not null comment '来源文本，已做最小化脱敏',
                    profileJson    text not null comment '待确认的结构化画像 JSON',
                    status         tinyint default 0 not null comment '0 - 待确认，1 - 已确认，2 - 已拒绝，3 - 已过期',
                    expiresAt      datetime not null comment '草稿过期时间',
                    confirmedAt    datetime null comment '用户确认时间',
                    modelVersion   varchar(64) not null comment '提取模型或规则版本',
                    createTime     datetime default CURRENT_TIMESTAMP null comment '创建时间',
                    updateTime     datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
                    isDelete       tinyint default 0 not null comment '是否删除'
                ) comment 'AI 用户画像草稿'
                """);
        addIndexIfMissing("ai_profile_draft", "uk_ai_profile_draft_draftId",
                "alter table ai_profile_draft add unique index uk_ai_profile_draft_draftId (draftId)");
        addIndexIfMissing("ai_profile_draft", "idx_ai_profile_draft_user_status",
                "alter table ai_profile_draft add index idx_ai_profile_draft_user_status (userId, status, expiresAt)");
    }

    @Test
    void profileDraft_shouldExtractConfirmAndReadCurrentProfile() throws Exception {
        User user = null;
        try {
            user = createTestUser();
            String token = loginToken(user);

            JsonNode extractResponse = createProfileDraft(token, "我在西安雁塔，周末晚上想找羽毛球搭子，预算50以内，中等水平，喜欢小队安静");
            String draftId = extractResponse.at("/data/draftId").asText();

            Assertions.assertFalse(draftId.isBlank());
            Assertions.assertEquals(0, extractResponse.at("/data/status").asInt());
            Assertions.assertEquals("西安", extractResponse.at("/data/profile/city").asText());
            Assertions.assertTrue(extractResponse.at("/data/profile/activityTypes").toString().contains("羽毛球"));
            Assertions.assertTrue(extractResponse.at("/data/profile/availableTimes").toString().contains("周末"));
            Assertions.assertFalse(extractResponse.at("/data/sourceText").asText().contains("@"));

            JsonNode confirmResponse = confirmProfile(token, draftId, 0);

            Assertions.assertEquals("西安", confirmResponse.at("/data/profile/city").asText());
            Assertions.assertTrue(confirmResponse.at("/data/profile/skillLevels").toString().contains("中等"));
            Assertions.assertEquals(1, confirmResponse.at("/data/status").asInt());

            JsonNode currentResponse = getCurrentProfile(token);
            Assertions.assertEquals("西安", currentResponse.at("/data/profile/city").asText());
            Assertions.assertEquals(1L, countCurrentProfiles(user.getId()));
        } finally {
            cleanupUserAndProfile(user);
        }
    }

    @Test
    void profileDraft_rejectDraft_shouldNotCreateCurrentProfile() throws Exception {
        User user = null;
        try {
            user = createTestUser();
            String token = loginToken(user);
            JsonNode extractResponse = createProfileDraft(token, "深圳周末健身，新手，预算30以内");
            String draftId = extractResponse.at("/data/draftId").asText();

            JsonNode rejectResponse = rejectProfile(token, draftId, 0);

            Assertions.assertEquals(2, rejectResponse.at("/data/status").asInt());
            Assertions.assertEquals(0L, countCurrentProfiles(user.getId()));
        } finally {
            cleanupUserAndProfile(user);
        }
    }

    @Test
    void profileDraft_otherUserDraft_shouldBeRejected() throws Exception {
        User owner = null;
        User other = null;
        try {
            owner = createTestUser();
            other = createTestUser();
            JsonNode extractResponse = createProfileDraft(loginToken(owner), "杭州周末骑行，进阶");
            String draftId = extractResponse.at("/data/draftId").asText();

            JsonNode response = confirmProfile(loginToken(other), draftId, 40101);

            Assertions.assertEquals(40101, response.at("/code").asInt());
            Assertions.assertEquals(0L, countCurrentProfiles(owner.getId()));
        } finally {
            cleanupUserAndProfile(owner);
            cleanupUserAndProfile(other);
        }
    }

    @Test
    void profileDraft_expiredDraft_shouldBeRejected() throws Exception {
        User user = null;
        try {
            user = createTestUser();
            String token = loginToken(user);
            JsonNode createResponse = createProfileDraft(token, "西安周末桌游，新手友好");
            String draftId = createResponse.at("/data/draftId").asText();
            jdbcTemplate.update("update ai_profile_draft set expiresAt = ? where draftId = ?",
                    new Date(System.currentTimeMillis() - 60 * 1000),
                    draftId);

            JsonNode response = confirmProfile(token, draftId, 40000);

            Assertions.assertEquals(40000, response.at("/code").asInt());
            Assertions.assertEquals(0L, countCurrentProfiles(user.getId()));
        } finally {
            cleanupUserAndProfile(user);
        }
    }

    @Test
    void updateMyProfileTool_shouldCreateDraftAndOnlyUpdateAfterConfirmation() {
        User user = null;
        try {
            user = createTestUser();
            TeamIntent intent = new TeamIntent();
            intent.setProfileText("我在西安雁塔，周末晚上打羽毛球，中等水平，预算50以内，邮箱 test@example.com");

            AiToolResult result = aiToolRegistry.execute(UpdateMyProfileTool.TOOL_NAME, intent, user);

            Assertions.assertTrue(result.isSuccess());
            Assertions.assertEquals("draft", result.getType());
            Assertions.assertNull(userService.getById(user.getId()).getProfile());
            Assertions.assertEquals(0L, countCurrentProfiles(user.getId()));

            AiProfileResponse draft = objectMapper.convertValue(result.getData(), AiProfileResponse.class);
            Assertions.assertNotNull(draft.getDraftId());
            aiUserProfileService.confirmDraft(draft.getDraftId(), null, user);

            User updatedUser = userService.getById(user.getId());
            Assertions.assertTrue(updatedUser.getProfile().contains("羽毛球"));
            Assertions.assertFalse(updatedUser.getProfile().contains("test@example.com"));
            Assertions.assertTrue(updatedUser.getProfile().contains("***@***"));
            JsonNode profile = objectMapper.valueToTree(aiUserProfileService.getCurrentProfile(user));
            Assertions.assertEquals("西安", profile.at("/profile/city").asText());
            Assertions.assertTrue(profile.at("/profile/activityTypes").toString().contains("羽毛球"));
            Assertions.assertEquals(1L, countCurrentProfiles(user.getId()));
        } finally {
            cleanupUserAndProfile(user);
        }
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

    private void addIndexIfMissing(String tableName, String indexName, String ddl) {
        Integer count = jdbcTemplate.queryForObject("""
                        select count(1)
                        from information_schema.statistics
                        where table_schema = database()
                          and table_name = ?
                          and index_name = ?
                        """,
                Integer.class,
                tableName,
                indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private User createTestUser() {
        User user = new User();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        user.setUsername("profile_test_" + suffix);
        user.setUserAccount("pf_" + suffix);
        user.setUserPassword(PASSWORD_ENCODER.encode(RAW_PASSWORD));
        user.setPlanetCode(UUID.randomUUID().toString().replace("-", "").substring(0, 5));
        user.setUserRole(0);
        user.setUserStatus(0);
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

    private JsonNode createProfileDraft(String token, String sourceText) throws Exception {
        String content = mockMvc.perform(post("/ai/profile-draft")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("sourceText", sourceText))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content);
    }

    private JsonNode confirmProfile(String token, String draftId, int expectedCode) throws Exception {
        String content = mockMvc.perform(post("/ai/profile-draft/{draftId}/confirm", draftId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content);
    }

    private JsonNode rejectProfile(String token, String draftId, int expectedCode) throws Exception {
        String content = mockMvc.perform(post("/ai/profile-draft/{draftId}/reject", draftId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content);
    }

    private JsonNode getCurrentProfile(String token) throws Exception {
        String content = mockMvc.perform(get("/ai/profile/current")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(content);
    }

    private Long countCurrentProfiles(long userId) {
        return jdbcTemplate.queryForObject("""
                        select count(1)
                        from ai_user_profile
                        where isDelete = 0
                          and userId = ?
                        """,
                Long.class,
                userId);
    }

    private void cleanupUserAndProfile(User user) {
        if (user == null || user.getId() <= 0) {
            return;
        }
        jdbcTemplate.update("delete from ai_user_profile where userId = ?", user.getId());
        jdbcTemplate.update("delete from ai_profile_draft where userId = ?", user.getId());
        userMapper.deleteByIdPhysically(user.getId());
    }
}
