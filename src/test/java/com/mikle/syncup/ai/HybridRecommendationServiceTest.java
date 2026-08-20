package com.mikle.syncup.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.schema.GeneratedEmbedding;
import com.mikle.syncup.ai.model.vo.AiUserRecommendation;
import com.mikle.syncup.ai.model.vo.HybridRecommendationResult;
import com.mikle.syncup.ai.service.HybridRecommendationService;
import com.mikle.syncup.ai.service.ProfileEmbeddingGenerator;
import com.mikle.syncup.ai.service.ProfileEmbeddingCodec;
import com.mikle.syncup.ai.service.TeamRetrievalTextBuilder;
import com.mikle.syncup.ai.service.TextHashService;
import com.mikle.syncup.mapper.UserMapper;
import com.mikle.syncup.model.domain.Team;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.vo.TeamUserVO;
import com.mikle.syncup.service.TeamService;
import com.mikle.syncup.service.UserService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "sync-up.ai.agent.enabled=false",
        "sync-up.ai.profile-generation.fixed-delay-ms=3600000",
        "sync-up.ai.team-embedding.initial-delay-ms=3600000"
})
class HybridRecommendationServiceTest {

    @Resource
    private HybridRecommendationService recommendationService;

    @Resource
    private UserService userService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private TeamService teamService;

    @Resource
    private TeamRetrievalTextBuilder teamTextBuilder;

    @Resource
    private TextHashService textHashService;

    @Resource
    private ProfileEmbeddingCodec embeddingCodec;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ProfileEmbeddingGenerator embeddingGenerator;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> teamIds = new ArrayList<>();

    @BeforeEach
    void prepareSchemaAndEmbeddingGenerator() {
        createTeamEmbeddingTableIfMissing();
        when(embeddingGenerator.isAvailable()).thenReturn(true);
        when(embeddingGenerator.modelName()).thenReturn("test-embedding-model");
        when(embeddingGenerator.generate(anyString())).thenReturn(
                new GeneratedEmbedding("test-embedding-model", new float[]{1F, 0F}));
    }

    @AfterEach
    void cleanup() {
        for (Long teamId : teamIds) {
            jdbcTemplate.update("delete from ai_team_embedding where teamId = ?", teamId);
            jdbcTemplate.update("delete from user_team where teamId = ?", teamId);
            jdbcTemplate.update("delete from team where id = ?", teamId);
        }
        for (Long userId : userIds) {
            jdbcTemplate.update("delete from ai_user_profile_embedding where userId = ?", userId);
            jdbcTemplate.update("delete from ai_user_profile where userId = ?", userId);
            jdbcTemplate.update("delete from ai_profile_generation_task where userId = ?", userId);
            userMapper.deleteByIdPhysically(userId);
        }
    }

    @Test
    void recommendUsers_shouldHardFilterAndRankByProfileEmbedding() {
        User current = createUser("西安", "[\"羽毛球\"]");
        User best = createUser("西安", "[\"羽毛球\"]");
        User second = createUser("西安", "[\"羽毛球\"]");
        User wrongCity = createUser("北京", "[\"羽毛球\"]");
        insertProfile(current.getId(), "喜欢轻松羽毛球和小范围交流", 1);
        insertProfileAndEmbedding(best.getId(), "喜欢轻松羽毛球和小范围交流", 1, new float[]{1F, 0F});
        insertProfileAndEmbedding(second.getId(), "喜欢竞技羽毛球", 1, new float[]{0F, 1F});
        insertProfileAndEmbedding(wrongCity.getId(), "喜欢轻松羽毛球", 1, new float[]{1F, 0F});

        TeamIntent intent = new TeamIntent();
        intent.setSourceText("这个周末想打羽毛球，想找轻松一点的搭子");
        intent.setActivityType("羽毛球");
        intent.setCity("西安");

        HybridRecommendationResult<AiUserRecommendation> result =
                recommendationService.recommendUsers(intent, current, 3);

        Assertions.assertFalse(result.degraded());
        Assertions.assertEquals(2, result.candidateCount());
        Assertions.assertEquals(best.getId(), result.items().getFirst().getId());
        Assertions.assertTrue(result.items().getFirst().getReasons().contains("活动与社交偏好较接近"));
        Assertions.assertTrue(result.items().stream().noneMatch(item -> item.getId().equals(wrongCity.getId())));
    }

    @Test
    void recommendUsers_embeddingFailure_shouldFallbackToStructuredTags() {
        User current = createUser("西安", "[\"桌游\"]");
        User candidate = createUser("西安", "[\"桌游\"]");
        when(embeddingGenerator.generate(anyString())).thenThrow(new IllegalStateException("embedding timeout"));

        TeamIntent intent = new TeamIntent();
        intent.setSourceText("想找桌游搭子");
        intent.setActivityType("桌游");
        intent.setCity("西安");

        HybridRecommendationResult<AiUserRecommendation> result =
                recommendationService.recommendUsers(intent, current, 3);

        Assertions.assertTrue(result.degraded());
        Assertions.assertEquals(1, result.items().size());
        Assertions.assertEquals(candidate.getId(), result.items().getFirst().getId());
        Assertions.assertTrue(result.items().getFirst().getReasons().stream()
                .anyMatch(reason -> reason.contains("桌游")));
    }

    @Test
    void recommendTeams_shouldRankValidCurrentTeamEmbedding() {
        User current = createUser("西安", "[\"羽毛球\"]");
        insertProfile(current.getId(), "喜欢轻松羽毛球，不追求高强度竞技", 1);
        Team best = createTeam(current, "轻松羽毛球局", "新手友好，轻松交流");
        Team second = createTeam(current, "羽毛球训练局", "偏竞技训练");
        insertTeamEmbedding(best, 1, new float[]{1F, 0F});
        insertTeamEmbedding(second, 1, new float[]{0F, 1F});

        TeamIntent intent = new TeamIntent();
        intent.setSourceText("想参加轻松一点的羽毛球活动");
        intent.setActivityCategory(1);
        intent.setActivityType("羽毛球");
        intent.setCity("西安");
        intent.setMemberCount(1);

        HybridRecommendationResult<TeamUserVO> result =
                recommendationService.recommendTeams(intent, current, 3);

        Assertions.assertFalse(result.degraded());
        Assertions.assertEquals(2, result.candidateCount());
        Assertions.assertEquals(best.getId(), result.items().getFirst().getId());
        Assertions.assertTrue(result.items().getFirst().getRecommendationReasons()
                .contains("活动描述与个人偏好较接近"));
    }

    @Test
    void recommendTeams_staleEmbedding_shouldFallbackWithoutUsingOldVector() {
        User current = createUser("西安", "[\"桌游\"]");
        Team team = createTeam(current, "桌游局", "轻松桌游");
        insertTeamEmbedding(team, 1, new float[]{1F, 0F});
        jdbcTemplate.update("update team set description = ? where id = ?", "描述已经变化", team.getId());

        TeamIntent intent = new TeamIntent();
        intent.setSourceText("想玩桌游");
        intent.setActivityCategory(1);
        intent.setActivityType("羽毛球");
        intent.setCity("西安");

        // Keep the hard fields aligned with the persisted team and only change retrieval text.
        jdbcTemplate.update("update team set activityType = ?, name = ? where id = ?", "羽毛球", "羽毛球局", team.getId());
        HybridRecommendationResult<TeamUserVO> result =
                recommendationService.recommendTeams(intent, current, 3);

        Assertions.assertTrue(result.degraded());
        Assertions.assertEquals(1, result.items().size());
        Assertions.assertFalse(result.items().getFirst().getRecommendationReasons()
                .contains("活动描述与个人偏好较接近"));
    }

    private User createUser(String city, String tags) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = new User();
        user.setUsername("hybrid_" + suffix);
        user.setUserAccount("hybrid_" + suffix);
        user.setUserPassword(new BCryptPasswordEncoder().encode("Password123"));
        user.setPlanetCode(suffix.substring(0, 5));
        user.setUserRole(0);
        user.setUserStatus(0);
        user.setCity(city);
        user.setTags(tags);
        user.setLastActiveTime(new Date());
        Assertions.assertTrue(userService.save(user));
        userIds.add(user.getId());
        return user;
    }

    private Team createTeam(User owner, String name, String description) {
        Team team = new Team();
        team.setName(name);
        team.setDescription(description);
        team.setActivityCategory(1);
        team.setActivityType("羽毛球");
        team.setCity("西安");
        team.setDistrict("雁塔区");
        team.setMaxNum(4);
        team.setStatus(0);
        team.setStartTime(new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3)));
        team.setExpireTime(new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(4)));
        long teamId = teamService.addTeam(team, owner);
        teamIds.add(teamId);
        return teamService.getById(teamId);
    }

    private void insertProfile(long userId, String matchText, int version) {
        jdbcTemplate.update("""
                insert into ai_user_profile
                (userId, profileText, matchProfileText, interactionProfileText, profileVersion,
                 sourceHash, model, promptVersion, status, generatedAt, isDelete)
                values (?, ?, ?, ?, ?, ?, ?, ?, 1, now(), 0)
                """, userId, matchText + "\nAI 交互偏好：简洁", matchText, "简洁直接", version,
                "0".repeat(64), "test-model", "test-prompt-v1");
    }

    private void insertProfileAndEmbedding(long userId, String matchText, int version, float[] vector) {
        insertProfile(userId, matchText, version);
        jdbcTemplate.update("""
                insert into ai_user_profile_embedding
                (userId, profileVersion, matchTextHash, embeddingModel, dimensions,
                 vectorJson, status, generatedAt, isDelete)
                values (?, ?, ?, ?, ?, ?, 1, now(), 0)
                """, userId, version, textHashService.sha256(matchText), "test-embedding-model",
                vector.length, embeddingCodec.serialize(embeddingCodec.normalize(vector)));
    }

    private void insertTeamEmbedding(Team team, int version, float[] vector) {
        String retrievalText = teamTextBuilder.build(team);
        jdbcTemplate.update("""
                insert into ai_team_embedding
                (teamId, contentVersion, contentHash, embeddingModel, dimensions,
                 vectorJson, status, generatedAt, isDelete)
                values (?, ?, ?, ?, ?, ?, 1, now(), 0)
                """, team.getId(), version, textHashService.sha256(retrievalText), "test-embedding-model",
                vector.length, embeddingCodec.serialize(embeddingCodec.normalize(vector)));
    }

    private void createTeamEmbeddingTableIfMissing() {
        jdbcTemplate.execute("""
                create table if not exists ai_team_embedding
                (
                    id bigint auto_increment primary key,
                    teamId bigint not null,
                    contentVersion int not null,
                    contentHash char(64) not null,
                    embeddingModel varchar(128) not null,
                    dimensions int not null,
                    vectorJson mediumtext not null,
                    status tinyint default 1 not null,
                    generatedAt datetime not null,
                    createTime datetime default CURRENT_TIMESTAMP null,
                    updateTime datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
                    isDelete tinyint default 0 not null,
                    unique key uk_ai_team_embedding_team_version (teamId, contentVersion),
                    key idx_ai_team_embedding_team_status (teamId, status)
                )
                """);
    }
}
