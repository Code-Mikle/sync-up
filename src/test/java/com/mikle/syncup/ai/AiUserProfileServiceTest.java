package com.mikle.syncup.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.model.entity.AiUserProfileEntity;
import com.mikle.syncup.ai.model.entity.AiUserProfileEmbedding;
import com.mikle.syncup.ai.model.schema.GeneratedEmbedding;
import com.mikle.syncup.ai.model.schema.GeneratedUserProfile;
import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.vo.AiUserProfile;
import com.mikle.syncup.ai.service.AiUserProfileService;
import com.mikle.syncup.ai.service.ProfileEmbeddingGenerator;
import com.mikle.syncup.ai.service.UserProfileTextGenerator;
import com.mikle.syncup.ai.tool.AiToolRegistry;
import com.mikle.syncup.ai.tool.GetMyProfileTool;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "sync-up.ai.agent.enabled=false",
        "sync-up.ai.profile-generation.fixed-delay-ms=3600000"
})
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

    @MockitoBean
    private UserProfileTextGenerator generator;

    @MockitoBean
    private ProfileEmbeddingGenerator embeddingGenerator;

    @BeforeEach
    void prepareStage2BSchemaAndGenerators() {
        addUserProfileColumnIfMissing();
        recreateProfileTables();
        when(generator.isAvailable()).thenReturn(true);
        when(generator.modelName()).thenReturn("test-model");
        when(generator.promptVersion()).thenReturn("user-profile-test-v1");
        when(generator.generate(anyString())).thenReturn(defaultGeneratedProfile());
        when(embeddingGenerator.isAvailable()).thenReturn(true);
        when(embeddingGenerator.modelName()).thenReturn("test-embedding-model");
        when(embeddingGenerator.generate(anyString())).thenReturn(
                new GeneratedEmbedding("test-embedding-model", new float[]{3F, 4F}));
    }

    @Test
    void selfIntroductionChange_shouldGenerateInternalFiveSectionProfile() throws Exception {
        User user = null;
        try {
            user = createTestUser();
            updateSelfIntroduction(user, "我在西安，周末喜欢羽毛球和桌游，偏好小范围轻松交流，邮箱 test@example.com");

            Assertions.assertEquals(1L, countTasks(user.getId()));
            String storedSource = jdbcTemplate.queryForObject(
                    "select sourceText from ai_profile_generation_task where userId = ? order by id desc limit 1",
                    String.class, user.getId());
            Assertions.assertNotNull(storedSource);
            Assertions.assertFalse(storedSource.contains("test@example.com"));
            Assertions.assertTrue(storedSource.contains("***@***"));

            Assertions.assertEquals(1, aiUserProfileService.processPendingTasks());
            AiUserProfileEntity profile = aiUserProfileService.getInternalProfile(user.getId());

            Assertions.assertNotNull(profile);
            Assertions.assertEquals(1, profile.getProfileVersion());
            Assertions.assertTrue(profile.getProfileText().contains("【兴趣与活动偏好】"));
            Assertions.assertTrue(profile.getProfileText().contains("【AI 交互偏好】"));
            Assertions.assertTrue(profile.getMatchProfileText().contains("【搭子匹配偏好】"));
            Assertions.assertFalse(profile.getMatchProfileText().contains("【AI 交互偏好】"));
            Assertions.assertTrue(profile.getInteractionProfileText().startsWith("【AI 交互偏好】"));
            Assertions.assertFalse(profile.getProfileText().contains("西安"), "hard filter fields must not enter AI profile text");
            AiUserProfileEmbedding embedding = aiUserProfileService.getActiveEmbedding(user.getId());
            Assertions.assertNotNull(embedding);
            Assertions.assertEquals(profile.getProfileVersion(), embedding.getProfileVersion());
            Assertions.assertEquals("test-embedding-model", embedding.getEmbeddingModel());
            Assertions.assertEquals(2, embedding.getDimensions());
            float[] vector = objectMapper.readValue(embedding.getVectorJson(), float[].class);
            Assertions.assertArrayEquals(new float[]{0.6F, 0.8F}, vector, 0.0001F);
            Assertions.assertEquals(2, latestTaskStatus(user.getId()));
        } finally {
            cleanupUserAndProfile(user);
        }
    }

    @Test
    void newerSelfIntroduction_shouldCreateNextProfileVersion() {
        User user = null;
        try {
            user = createTestUser();
            updateSelfIntroduction(user, "周末喜欢羽毛球");
            aiUserProfileService.processPendingTasks();

            GeneratedUserProfile updated = defaultGeneratedProfile();
            updated.setInterestAndActivityPreference("更喜欢徒步，也愿意参加轻松的桌游活动。");
            when(generator.generate(anyString())).thenReturn(updated);
            updateSelfIntroduction(user, "最近更喜欢徒步，也愿意参加桌游");
            aiUserProfileService.processPendingTasks();

            AiUserProfileEntity profile = aiUserProfileService.getInternalProfile(user.getId());
            Assertions.assertNotNull(profile);
            Assertions.assertEquals(2, profile.getProfileVersion());
            Assertions.assertTrue(profile.getProfileText().contains("更喜欢徒步"));
            AiUserProfileEmbedding activeEmbedding = aiUserProfileService.getActiveEmbedding(user.getId());
            Assertions.assertNotNull(activeEmbedding);
            Assertions.assertEquals(2, activeEmbedding.getProfileVersion());
            Assertions.assertEquals(1L, countEmbeddingsByStatus(user.getId(), 0));
            Assertions.assertEquals(1L, countEmbeddingsByStatus(user.getId(), 1));
        } finally {
            cleanupUserAndProfile(user);
        }
    }

    @Test
    void generationFailure_shouldKeepLastSuccessfulProfile() {
        User user = null;
        try {
            user = createTestUser();
            updateSelfIntroduction(user, "周末喜欢羽毛球");
            aiUserProfileService.processPendingTasks();
            String oldProfileText = aiUserProfileService.getInternalProfile(user.getId()).getProfileText();

            when(generator.generate(anyString())).thenThrow(new IllegalStateException("model timeout"));
            updateSelfIntroduction(user, "最近只想参加徒步活动");
            aiUserProfileService.processPendingTasks();

            AiUserProfileEntity current = aiUserProfileService.getInternalProfile(user.getId());
            Assertions.assertNotNull(current);
            Assertions.assertEquals(1, current.getProfileVersion());
            Assertions.assertEquals(oldProfileText, current.getProfileText());
            Assertions.assertEquals(1, aiUserProfileService.getActiveEmbedding(user.getId()).getProfileVersion());
            Assertions.assertEquals(0, latestTaskStatus(user.getId()));
            Assertions.assertEquals(1, latestRetryCount(user.getId()));
        } finally {
            cleanupUserAndProfile(user);
        }
    }

    @Test
    void embeddingFailure_shouldKeepLastTextAndEmbeddingVersionTogether() {
        User user = null;
        try {
            user = createTestUser();
            updateSelfIntroduction(user, "周末喜欢羽毛球");
            aiUserProfileService.processPendingTasks();
            String oldProfileText = aiUserProfileService.getInternalProfile(user.getId()).getProfileText();

            when(embeddingGenerator.generate(anyString()))
                    .thenThrow(new IllegalStateException("embedding timeout"));
            updateSelfIntroduction(user, "最近更喜欢徒步和露营");
            aiUserProfileService.processPendingTasks();

            AiUserProfileEntity currentProfile = aiUserProfileService.getInternalProfile(user.getId());
            AiUserProfileEmbedding currentEmbedding = aiUserProfileService.getActiveEmbedding(user.getId());
            Assertions.assertNotNull(currentProfile);
            Assertions.assertNotNull(currentEmbedding);
            Assertions.assertEquals(1, currentProfile.getProfileVersion());
            Assertions.assertEquals(1, currentEmbedding.getProfileVersion());
            Assertions.assertEquals(oldProfileText, currentProfile.getProfileText());
            Assertions.assertEquals(1L, countEmbeddings(user.getId()));
            Assertions.assertEquals(0, latestTaskStatus(user.getId()));
            Assertions.assertEquals(1, latestRetryCount(user.getId()));
        } finally {
            cleanupUserAndProfile(user);
        }
    }

    @Test
    void generatedProfileContainingSensitiveData_shouldBeRejected() {
        User user = null;
        try {
            user = createTestUser();
            GeneratedUserProfile unsafeProfile = defaultGeneratedProfile();
            unsafeProfile.setPartnerMatchingPreference("可以联系手机号 13800138000 安排活动。");
            when(generator.generate(anyString())).thenReturn(unsafeProfile);

            updateSelfIntroduction(user, "周末喜欢羽毛球");
            aiUserProfileService.processPendingTasks();

            Assertions.assertNull(aiUserProfileService.getInternalProfile(user.getId()));
            Assertions.assertNull(aiUserProfileService.getActiveEmbedding(user.getId()));
            Assertions.assertEquals(0L, countEmbeddings(user.getId()));
            Assertions.assertEquals(0, latestTaskStatus(user.getId()));
            Assertions.assertEquals(1, latestRetryCount(user.getId()));
        } finally {
            cleanupUserAndProfile(user);
        }
    }

    @Test
    void clearingSelfIntroduction_shouldDeleteInternalProfileAndSupersedeTask() {
        User user = null;
        try {
            user = createTestUser();
            updateSelfIntroduction(user, "周末喜欢羽毛球");
            aiUserProfileService.processPendingTasks();
            updateSelfIntroduction(user, "新的介绍暂时等待生成");

            updateSelfIntroduction(user, "");

            Assertions.assertNull(aiUserProfileService.getInternalProfile(user.getId()));
            Assertions.assertEquals(4, latestTaskStatus(user.getId()));
        } finally {
            cleanupUserAndProfile(user);
        }
    }

    @Test
    void publicProfileTool_shouldNotExposeInternalProfile() {
        User user = null;
        try {
            user = createTestUser();
            updateSelfIntroduction(user, "周末喜欢羽毛球");
            aiUserProfileService.processPendingTasks();

            AiToolResult result = aiToolRegistry.execute(GetMyProfileTool.TOOL_NAME, new TeamIntent(), user);
            AiUserProfile publicProfile = objectMapper.convertValue(result.getData(), AiUserProfile.class);

            Assertions.assertTrue(result.isSuccess());
            Assertions.assertEquals("周末喜欢羽毛球", publicProfile.getProfile());
            JsonNode serialized = objectMapper.valueToTree(result.getData());
            Assertions.assertFalse(serialized.has("profileText"));
            Assertions.assertFalse(serialized.has("matchProfileText"));
            Assertions.assertFalse(serialized.has("interactionProfileText"));
            Assertions.assertFalse(serialized.has("structuredProfile"));
        } finally {
            cleanupUserAndProfile(user);
        }
    }

    @Test
    void oldProfileEndpoints_shouldNoLongerExist() throws Exception {
        User user = null;
        try {
            user = createTestUser();
            String token = loginToken(user);
            mockMvc.perform(get("/ai/profile/current").header("Authorization", token))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/ai/profile-draft")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isNotFound());
        } finally {
            cleanupUserAndProfile(user);
        }
    }

    private GeneratedUserProfile defaultGeneratedProfile() {
        return new GeneratedUserProfile(
                "喜欢羽毛球和桌游，倾向周末参加活动，不追求高强度竞技。",
                "性格偏慢热，更适合人数较少、氛围自然的活动。",
                "倾向守时、沟通直接、不过度强势的搭子，不喜欢临时改变计划。",
                "通常周末有空，偏好距离较近、预算适中的轻松活动。",
                "适合由 AI 主动提供两到三个明确选项，不宜连续提出大量开放式问题。"
        );
    }

    private void updateSelfIntroduction(User loginUser, String sourceText) {
        User updateUser = new User();
        updateUser.setId(loginUser.getId());
        updateUser.setProfile(sourceText);
        Assertions.assertEquals(1, userService.updateUser(updateUser, loginUser));
        loginUser.setProfile(sourceText);
    }

    private void recreateProfileTables() {
        jdbcTemplate.execute("drop table if exists ai_profile_draft");
        jdbcTemplate.execute("drop table if exists ai_profile_generation_task");
        jdbcTemplate.execute("drop table if exists ai_user_profile_embedding");
        jdbcTemplate.execute("drop table if exists ai_user_profile");
        jdbcTemplate.execute("""
                create table ai_user_profile
                (
                    id bigint auto_increment primary key,
                    userId bigint not null,
                    profileText text not null,
                    matchProfileText text not null,
                    interactionProfileText text not null,
                    profileVersion int not null,
                    sourceHash char(64) not null,
                    model varchar(128) not null,
                    promptVersion varchar(64) not null,
                    status tinyint default 1 not null,
                    generatedAt datetime not null,
                    createTime datetime default CURRENT_TIMESTAMP null,
                    updateTime datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
                    isDelete tinyint default 0 not null,
                    unique key uk_ai_user_profile_userId (userId)
                )
                """);
        jdbcTemplate.execute("""
                create table ai_user_profile_embedding
                (
                    id bigint auto_increment primary key,
                    userId bigint not null,
                    profileVersion int not null,
                    matchTextHash char(64) not null,
                    embeddingModel varchar(128) not null,
                    dimensions int not null,
                    vectorJson mediumtext not null,
                    status tinyint default 1 not null,
                    generatedAt datetime not null,
                    createTime datetime default CURRENT_TIMESTAMP null,
                    updateTime datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
                    isDelete tinyint default 0 not null,
                    unique key uk_ai_profile_embedding_user_version (userId, profileVersion),
                    key idx_ai_profile_embedding_user_status (userId, status)
                )
                """);
        jdbcTemplate.execute("""
                create table ai_profile_generation_task
                (
                    id bigint auto_increment primary key,
                    userId bigint not null,
                    sourceText varchar(1000) not null,
                    sourceHash char(64) not null,
                    status tinyint default 0 not null,
                    retryCount int default 0 not null,
                    nextRetryAt datetime null,
                    lastError varchar(1024) null,
                    model varchar(128) null,
                    promptVersion varchar(64) not null,
                    profileVersion int null,
                    createTime datetime default CURRENT_TIMESTAMP null,
                    updateTime datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
                    isDelete tinyint default 0 not null,
                    key idx_ai_profile_generation_status_retry (status, nextRetryAt),
                    key idx_ai_profile_generation_user_time (userId, createTime)
                )
                """);
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

    private User createTestUser() {
        User user = new User();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        user.setUsername("profile_test_" + suffix);
        user.setUserAccount("pf_" + suffix);
        user.setUserPassword(PASSWORD_ENCODER.encode(RAW_PASSWORD));
        user.setPlanetCode(UUID.randomUUID().toString().replace("-", "").substring(0, 5));
        user.setUserRole(0);
        user.setUserStatus(0);
        Assertions.assertTrue(userService.save(user), "test user should be created");
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

    private Long countTasks(long userId) {
        return jdbcTemplate.queryForObject(
                "select count(1) from ai_profile_generation_task where userId = ? and isDelete = 0",
                Long.class, userId);
    }

    private Integer latestTaskStatus(long userId) {
        return jdbcTemplate.queryForObject(
                "select status from ai_profile_generation_task where userId = ? order by id desc limit 1",
                Integer.class, userId);
    }

    private Integer latestRetryCount(long userId) {
        return jdbcTemplate.queryForObject(
                "select retryCount from ai_profile_generation_task where userId = ? order by id desc limit 1",
                Integer.class, userId);
    }

    private Long countEmbeddings(long userId) {
        return jdbcTemplate.queryForObject(
                "select count(1) from ai_user_profile_embedding where userId = ? and isDelete = 0",
                Long.class, userId);
    }

    private Long countEmbeddingsByStatus(long userId, int status) {
        return jdbcTemplate.queryForObject(
                "select count(1) from ai_user_profile_embedding where userId = ? and status = ? and isDelete = 0",
                Long.class, userId, status);
    }

    private void cleanupUserAndProfile(User user) {
        if (user == null || user.getId() <= 0) {
            return;
        }
        jdbcTemplate.update("delete from ai_user_profile where userId = ?", user.getId());
        jdbcTemplate.update("delete from ai_user_profile_embedding where userId = ?", user.getId());
        jdbcTemplate.update("delete from ai_profile_generation_task where userId = ?", user.getId());
        userMapper.deleteByIdPhysically(user.getId());
    }
}
