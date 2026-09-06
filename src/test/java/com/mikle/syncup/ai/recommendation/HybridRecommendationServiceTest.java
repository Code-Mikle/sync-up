package com.mikle.syncup.ai.recommendation;

import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.agent.UserIntent;
import com.mikle.syncup.ai.model.schema.GeneratedEmbedding;
import com.mikle.syncup.ai.model.vo.AiUserRecommendation;
import com.mikle.syncup.ai.model.vo.HybridRecommendationResult;
import com.mikle.syncup.ai.service.HybridRecommendationService;
import com.mikle.syncup.ai.service.ProfileEmbeddingGenerator;
import com.mikle.syncup.ai.service.ProfileEmbeddingCodec;
import com.mikle.syncup.ai.service.TeamRetrievalTextBuilder;
import com.mikle.syncup.ai.service.TextHashService;
import com.mikle.syncup.model.domain.Team;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.vo.TeamUserVO;
import com.mikle.syncup.service.TeamService;
import com.mikle.syncup.service.UserService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles(profiles = "test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HybridRecommendationServiceTest {

    @Resource
    private HybridRecommendationService recommendationService;

    @Resource
    private UserService userService;

    @Resource
    private TeamService teamService;

    @Resource
    private TeamRetrievalTextBuilder teamTextBuilder;

    @Resource
    private TextHashService textHashService;

    @Resource
    private ProfileEmbeddingCodec embeddingCodec;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    protected DataSource dataSource;

    @MockitoBean
    private ProfileEmbeddingGenerator embeddingGenerator;

    @BeforeEach
    protected void ensureUsingTestDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String databaseName = connection.getCatalog();

            Assertions.assertEquals(
                    "sync_up_test",
                    databaseName,
                    "当前连接的不是 sync_up_test，已停止测试，避免污染开发数据库"
            );
        }
    }

    @BeforeEach
    void prepareEmbeddingGenerator() {
        when(embeddingGenerator.isAvailable()).thenReturn(true);
        when(embeddingGenerator.generate(anyString())).thenReturn(
                new GeneratedEmbedding("test-embedding-model", new float[]{1F, 0F}));
    }

    @AfterAll
    void shouldNotLeaveHybridTestUsers() {
        Long remaining = jdbcTemplate.queryForObject(
                "select count(*) from user where left(username, 7) = 'hybrid_'",
                Long.class
        );
        Assertions.assertEquals(0L, remaining, "AI recommendation tests should not leave test users");
    }

    @Test
    @Transactional
    void recommendUsers_shouldHardFilterAndRankByProfileEmbedding() {
        User current = createUser("西安", "[107]");
        User best = createUser("西安", "[107]");
        User second = createUser("西安", "[107]");
        User wrongCity = createUser("北京", "[107]");
        insertProfile(current.getId(), "喜欢轻松羽毛球和小范围交流", 1);
        insertProfileAndEmbedding(best.getId(), "喜欢轻松羽毛球和小范围交流", 1, new float[]{1F, 0F});
        insertProfileAndEmbedding(second.getId(), "喜欢竞技羽毛球", 1, new float[]{0F, 1F});
        insertProfileAndEmbedding(wrongCity.getId(), "喜欢轻松羽毛球", 1, new float[]{1F, 0F});

        UserIntent intent = new UserIntent();
        intent.setProfile("这个周末想打羽毛球，想找轻松一点的搭子");
        intent.setTagIds(List.of(107L));
        intent.setCity("西安");

        HybridRecommendationResult<AiUserRecommendation> result =
                recommendationService.recommendUsers(intent, current, 3);

        Assertions.assertFalse(result.degraded());
        Assertions.assertEquals(2, result.candidateCount());
        Assertions.assertEquals(1, result.items().size());
        Assertions.assertEquals(best.getId(), result.items().getFirst().getId());
        Assertions.assertTrue(result.items().getFirst().getReasons().contains("活动与社交偏好较接近"));
        Assertions.assertTrue(result.items().stream().noneMatch(item -> item.getId().equals(wrongCity.getId())));
    }

    @Test
    @Transactional
    void recommendUsers_embeddingFailure_shouldFallbackToStructuredTags() {
        User current = createUser("西安", "[403]", 0);
        User expected = createUser("西安", "[403]", 1);
        createUser("北京", "[403]", 1);
        createUser("西安", "[403]", 0);
        createUser("西安", "[107]", 1);
        when(embeddingGenerator.generate(anyString())).thenThrow(new IllegalStateException("embedding timeout"));

        UserIntent intent = new UserIntent();
        intent.setProfile("想找桌游搭子");
        intent.setTagIds(List.of(403L));
        intent.setCity("西安");
        intent.setGender(1);

        HybridRecommendationResult<AiUserRecommendation> result =
                recommendationService.recommendUsers(intent, current, 3);

        Assertions.assertTrue(result.degraded());
        Assertions.assertEquals(1, result.candidateCount());
        Assertions.assertEquals(List.of(expected.getId()),
                result.items().stream().map(AiUserRecommendation::getId).toList());
    }

    @Test
    @Transactional
    void recommendUsers_explicitCityGenderAndTag_shouldApplyAllHardConditions() {
        User current = createUser("西安", "[107]", 0);
        User expected = createUser("北京", "[107]", 1);
        createUser("西安", "[107]", 1);
        createUser("北京", "[107]", 0);
        createUser("北京", "[403]", 1);

        UserIntent intent = new UserIntent();
        intent.setProfile("想找北京的羽毛球搭子");
        intent.setCity("北京");
        intent.setGender(1);
        intent.setTagIds(List.of(107L));

        HybridRecommendationResult<AiUserRecommendation> result =
                recommendationService.recommendUsers(intent, current, 10);

        Assertions.assertEquals(1, result.candidateCount());
        Assertions.assertEquals(List.of(expected.getId()),
                result.items().stream().map(AiUserRecommendation::getId).toList());
        Assertions.assertTrue(result.items().stream().noneMatch(item -> item.getId().equals(current.getId())));
    }

    @Test
    @Transactional
    void recommendUsers_cityNotSpecified_shouldUseCurrentUsersCity() {
        User current = createUser("西安", "[]");
        User local = createUser("西安", "[]");
        createUser("北京", "[]");

        UserIntent intent = new UserIntent();
        intent.setProfile("想找周末搭子");

        HybridRecommendationResult<AiUserRecommendation> result =
                recommendationService.recommendUsers(intent, current, 10);

        Assertions.assertEquals(1, result.candidateCount());
        Assertions.assertEquals(List.of(local.getId()),
                result.items().stream().map(AiUserRecommendation::getId).toList());
    }

    @Test
    @Transactional
    void recommendUsers_noCandidateMatchesExplicitCity_shouldReturnEmptyWithoutRelaxingCondition() {
        User current = createUser("西安", "[]");
        createUser("北京", "[]");
        UserIntent intent = new UserIntent();
        intent.setProfile("想找上海的活动搭子");
        intent.setCity("上海");

        HybridRecommendationResult<AiUserRecommendation> result =
                recommendationService.recommendUsers(intent, current, 10);

        Assertions.assertTrue(result.degraded());
        Assertions.assertEquals(0, result.candidateCount());
        Assertions.assertTrue(result.items().isEmpty());
    }

    @Test
    @Transactional
    void recommendUsers_profileAndEmbeddingVersionsMismatch_shouldSkipSemanticScoreAndHideInternalProfile() {
        User current = createUser("西安", "[107]");
        User candidate = createUser("西安", "[107]");
        insertProfile(candidate.getId(), "INTERNAL_MATCH_PROFILE_SECRET", 2);
        insertUserEmbedding(candidate.getId(), "INTERNAL_MATCH_PROFILE_SECRET", 1, new float[]{1F, 0F});

        UserIntent intent = new UserIntent();
        intent.setProfile("想找羽毛球搭子");
        intent.setTagIds(List.of(107L));
        intent.setCity("西安");

        HybridRecommendationResult<AiUserRecommendation> result =
                recommendationService.recommendUsers(intent, current, 3);

        Assertions.assertTrue(result.degraded());
        Assertions.assertEquals(1, result.items().size());
        Assertions.assertEquals(candidate.getId(), result.items().getFirst().getId());
        Assertions.assertEquals("公开个人简介", result.items().getFirst().getProfile());
        Assertions.assertFalse(result.items().getFirst().getReasons().contains("活动与社交偏好较接近"));
        Assertions.assertTrue(result.items().getFirst().getReasons().stream()
                .noneMatch(reason -> reason.contains("INTERNAL_MATCH_PROFILE_SECRET")));
    }

    @Test
    @Transactional
    void recommendTeams_shouldRankValidCurrentTeamEmbedding() {
        User current = createUser("西安", "[107]");
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
    @Transactional
    void recommendTeams_staleEmbedding_shouldFallbackWithoutUsingOldVector() {
        User current = createUser("西安", "[403]");
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

    @Test
    @Transactional
    void recommendTeams_shouldExcludePrivateExpiredFullAndInsufficientCapacityTeams() {
        User current = createUser("西安", "[107]");
        Team valid = createTeam(current, "可加入羽毛球局", "还有充足名额");
        Team privateTeam = createTeam(current, "私有羽毛球局", "不应被推荐");
        Team expired = createTeam(current, "过期羽毛球局", "不应被推荐");
        Team full = createTeam(current, "满员羽毛球局", "不应被推荐");
        Team insufficient = createTeam(current, "名额不足羽毛球局", "只剩一个名额");
        jdbcTemplate.update("update team set status = 1 where id = ?", privateTeam.getId());
        jdbcTemplate.update("update team set expireTime = ? where id = ?",
                new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)), expired.getId());
        addMembersUntilFull(full);
        jdbcTemplate.update("update team set maxNum = 2 where id = ?", insufficient.getId());

        TeamIntent intent = new TeamIntent();
        intent.setActivityCategory(1);
        intent.setActivityType("羽毛球");
        intent.setCity("西安");
        intent.setMemberCount(2);

        HybridRecommendationResult<TeamUserVO> result =
                recommendationService.recommendTeams(intent, current, 10);

        Assertions.assertEquals(1, result.candidateCount());
        Assertions.assertEquals(List.of(valid.getId()),
                result.items().stream().map(TeamUserVO::getId).toList());
        Assertions.assertEquals(3, result.items().getFirst().getMaxNum()
                - result.items().getFirst().getHasJoinNum());
    }

    @Test
    @Transactional
    void recommendTeams_noTeamMatchesExplicitCity_shouldReturnEmptyWithoutRelaxingCondition() {
        User current = createUser("西安", "[107]");
        Team beijingTeam = createTeam(current, "北京羽毛球局", "城市不符合请求");
        jdbcTemplate.update("update team set city = '北京' where id = ?", beijingTeam.getId());
        TeamIntent intent = new TeamIntent();
        intent.setActivityCategory(1);
        intent.setActivityType("羽毛球");
        intent.setCity("西安");

        HybridRecommendationResult<TeamUserVO> result =
                recommendationService.recommendTeams(intent, current, 10);

        Assertions.assertTrue(result.degraded());
        Assertions.assertEquals(0, result.candidateCount());
        Assertions.assertTrue(result.items().isEmpty());
    }

    private User createUser(String city, String tagIds) {
        return createUser(city, tagIds, null);
    }

    private User createUser(String city, String tagIds, Integer gender) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = new User();
        user.setUsername("hybrid_" + suffix);
        user.setUserAccount("hybrid_" + suffix);
        user.setUserPassword(new BCryptPasswordEncoder().encode("Password123"));
        user.setUserRole(0);
        user.setUserStatus(0);
        user.setCity(city);
        user.setGender(gender);
        user.setTagIds(tagIds);
        user.setProfile("公开个人简介");
        user.setLastActiveTime(new Date());
        Assertions.assertTrue(userService.save(user));
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
        return teamService.getById(teamId);
    }

    private void insertProfile(long userId, String matchText, int version) {
        jdbcTemplate.update("""
                insert into ai_user_profile
                (userId, activityPreferenceText, socialPersonalityText, partnerPreferenceText,
                 activityConstraintHabitText, aiInteractionPreferenceText, profileText, matchProfileText,
                 interactionProfileText, profileVersion, evidenceDigest, model, promptVersion, status, generatedAt, isDelete)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', now(), 0)
                """, userId, matchText, "偏好小范围交流", "偏好轻松搭子", "暂未观察到明确限制", "简洁直接",
                matchText + "\nAI 交互偏好：简洁", matchText, "简洁直接", version,
                "0".repeat(64), "test-model", "test-prompt-v1");
    }

    private void insertProfileAndEmbedding(long userId, String matchText, int version, float[] vector) {
        insertProfile(userId, matchText, version);
        insertUserEmbedding(userId, matchText, version, vector);
    }

    private void insertUserEmbedding(long userId, String matchText, int version, float[] vector) {
        jdbcTemplate.update("""
                insert into ai_user_profile_embedding
                (userId, profileVersion, matchTextHash, embeddingModel, dimensions,
                 vectorJson, status, generatedAt, isDelete)
                values (?, ?, ?, ?, ?, ?, 1, now(), 0)
                """, userId, version, textHashService.sha256(matchText), "test-embedding-model",
                vector.length, embeddingCodec.serialize(embeddingCodec.normalize(vector)));
    }

    private void addMembersUntilFull(Team team) {
        int existingMembers = 1;
        for (int index = existingMembers; index < team.getMaxNum(); index++) {
            User member = createUser("西安", "[]");
            jdbcTemplate.update("""
                    insert into user_team (userId, teamId, joinTime, isDelete)
                    values (?, ?, now(), 0)
                    """, member.getId(), team.getId());
        }
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

}
