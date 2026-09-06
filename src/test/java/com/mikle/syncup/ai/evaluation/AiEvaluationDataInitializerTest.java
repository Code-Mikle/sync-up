package com.mikle.syncup.ai.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.mapper.AiTeamEmbeddingMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileEmbeddingMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileMapper;
import com.mikle.syncup.ai.model.entity.AiTeamEmbedding;
import com.mikle.syncup.ai.model.entity.AiUserProfileEmbedding;
import com.mikle.syncup.ai.model.entity.AiUserProfileEntity;
import com.mikle.syncup.ai.model.enums.ProfileStatus;
import com.mikle.syncup.ai.model.schema.GeneratedEmbedding;
import com.mikle.syncup.ai.model.schema.GeneratedUserProfile;
import com.mikle.syncup.ai.service.ProfileEmbeddingCodec;
import com.mikle.syncup.ai.service.ProfileEmbeddingGenerator;
import com.mikle.syncup.ai.service.TeamRetrievalTextBuilder;
import com.mikle.syncup.ai.service.TextHashService;
import com.mikle.syncup.ai.service.UserProfileTextAssembler;
import com.mikle.syncup.mapper.TagMapper;
import com.mikle.syncup.mapper.TeamMapper;
import com.mikle.syncup.mapper.UserMapper;
import com.mikle.syncup.mapper.UserTeamMapper;
import com.mikle.syncup.model.domain.Tag;
import com.mikle.syncup.model.domain.Team;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.domain.UserTeam;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 显式执行的 AI 评测数据初始化入口。
 *
 * <p>默认测试不会运行本类。执行时必须同时指定：
 * {@code -Dai.eval.data.enabled=true -Dai.eval.data.action=reset|clean|verify}。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "ai.eval.data.enabled", matches = "true")
class AiEvaluationDataInitializerTest {

    private static final String TEST_DATABASE = "sync_up_test";
    private static final String DATASET_MODEL = "evaluation-seed";
    private static final String DATASET_PROMPT_VERSION = "evaluation-seed-v1";
    private static final ZoneId EVALUATION_ZONE = ZoneId.of("Asia/Shanghai");

    private final AiEvaluationDatasetLoader loader = new AiEvaluationDatasetLoader();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Resource private DataSource dataSource;
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private TransactionTemplate transactionTemplate;
    @Resource private ObjectMapper objectMapper;
    @Resource private UserMapper userMapper;
    @Resource private TeamMapper teamMapper;
    @Resource private UserTeamMapper userTeamMapper;
    @Resource private TagMapper tagMapper;
    @Resource private AiUserProfileMapper profileMapper;
    @Resource private AiUserProfileEmbeddingMapper profileEmbeddingMapper;
    @Resource private AiTeamEmbeddingMapper teamEmbeddingMapper;
    @Resource private UserProfileTextAssembler profileTextAssembler;
    @Resource private ProfileEmbeddingGenerator embeddingGenerator;
    @Resource private ProfileEmbeddingCodec embeddingCodec;
    @Resource private TeamRetrievalTextBuilder teamTextBuilder;
    @Resource private TextHashService textHashService;

    @Test
    void resetOrCleanEvaluationData() throws Exception {
        assertTestDatabase();
        String action = System.getProperty("ai.eval.data.action", "reset").trim().toLowerCase(Locale.ROOT);
        assertTrue(Set.of("reset", "clean", "verify").contains(action),
                "ai.eval.data.action must be reset, clean or verify");

        if (!"verify".equals(action)) {
            transactionTemplate.executeWithoutResult(status -> {
                cleanEvaluationData();
                if ("reset".equals(action)) {
                    seedEvaluationData();
                }
            });
        }

        if ("reset".equals(action) && embeddingsRequested()) {
            generateEmbeddings();
        }
        verifyResult(action);
    }

    private void seedEvaluationData() {
        var usersDocument = loader.users();
        Map<String, Long> tagIdsByName = loadControlledTagIds(usersDocument.users());
        Map<String, Long> userIdsByAccount = insertUsers(usersDocument, tagIdsByName);
        insertProfiles(userIdsByAccount);
        insertTeams(userIdsByAccount);
    }

    private Map<String, Long> loadControlledTagIds(List<AiEvaluationDatasetLoader.UserSeed> users) {
        Set<String> requiredNames = new LinkedHashSet<>();
        users.forEach(user -> requiredNames.addAll(user.tags()));
        List<Tag> tags = tagMapper.selectList(new QueryWrapper<Tag>()
                .in("name", requiredNames)
                .eq("status", 1));
        Map<String, Long> result = new LinkedHashMap<>();
        tags.forEach(tag -> result.put(tag.getName(), tag.getId()));
        Set<String> missing = new LinkedHashSet<>(requiredNames);
        missing.removeAll(result.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("test database is missing controlled tags: " + missing);
        }
        return result;
    }

    private Map<String, Long> insertUsers(AiEvaluationDatasetLoader.UsersDocument document,
                                           Map<String, Long> tagIdsByName) {
        Map<String, Long> ids = new LinkedHashMap<>();
        String encodedPassword = passwordEncoder.encode(document.defaults().userPassword());
        Date now = new Date();
        for (var seed : document.users()) {
            User user = new User();
            user.setUsername(seed.username());
            user.setUserAccount(seed.userAccount());
            user.setGender(seed.gender());
            user.setUserPassword(encodedPassword);
            user.setCity(seed.city());
            user.setTagIds(writeJson(seed.tags().stream().map(tagIdsByName::get).toList()));
            user.setProfile(seed.profile());
            user.setUserStatus(document.defaults().userStatus());
            user.setUserRole(document.defaults().userRole());
            user.setLastActiveTime(now);
            user.setIsDelete(0);
            if (userMapper.insert(user) != 1) {
                throw new IllegalStateException("cannot insert evaluation user: " + seed.userAccount());
            }
            ids.put(seed.userAccount(), user.getId());
        }
        return ids;
    }

    private void insertProfiles(Map<String, Long> userIdsByAccount) {
        Date now = new Date();
        for (var seed : loader.profiles().profiles()) {
            GeneratedUserProfile generated = generatedProfile(seed);
            String fullText = profileTextAssembler.renderFull(generated);
            String matchText = profileTextAssembler.renderMatch(generated);
            String interactionText = profileTextAssembler.renderInteraction(generated);

            AiUserProfileEntity profile = new AiUserProfileEntity();
            profile.setUserId(requiredId(userIdsByAccount, seed.userAccount()));
            profile.setActivityPreferenceText(seed.activityPreferenceText());
            profile.setSocialPersonalityText(seed.socialPersonalityText());
            profile.setPartnerPreferenceText(seed.partnerPreferenceText());
            profile.setActivityConstraintHabitText(seed.activityConstraintHabitText());
            profile.setAiInteractionPreferenceText(seed.aiInteractionPreferenceText());
            profile.setProfileText(fullText);
            profile.setMatchProfileText(matchText);
            profile.setInteractionProfileText(interactionText);
            profile.setProfileVersion(1);
            profile.setEvidenceDigest(textHashService.sha256(DATASET_PROMPT_VERSION + "|" + seed.userAccount()));
            profile.setModel(DATASET_MODEL);
            profile.setPromptVersion(DATASET_PROMPT_VERSION);
            profile.setStatus(ProfileStatus.ACTIVE.name());
            profile.setGeneratedAt(now);
            profile.setIsDelete(0);
            if (profileMapper.insert(profile) != 1) {
                throw new IllegalStateException("cannot insert evaluation profile: " + seed.userAccount());
            }
        }
    }

    private void insertTeams(Map<String, Long> userIdsByAccount) {
        var document = loader.teams();
        Map<String, Long> teamIdsByCode = new LinkedHashMap<>();
        Map<String, AiEvaluationDatasetLoader.TeamSeed> seedsByCode = new LinkedHashMap<>();
        LocalDate baseDate = LocalDate.now(EVALUATION_ZONE);
        for (var seed : document.teams()) {
            Team team = new Team();
            team.setName(seed.name());
            team.setDescription(seed.description());
            team.setActivityCategory(seed.activityCategory());
            team.setMaxNum(seed.maxNum());
            team.setExpireTime(toDate(baseDate.plusDays(valueOrDefault(
                    seed.expireAfterDays(), document.defaults().expireAfterDays())).atTime(23, 59)));
            team.setActivityType(seed.activityType());
            team.setCity(seed.city());
            team.setDistrict(seed.district());
            team.setStartTime(toDate(baseDate.plusDays(seed.startAfterDays())
                    .atTime(LocalTime.parse(seed.startTimeOfDay()))));
            team.setDurationMinutes(valueOrDefault(seed.durationMinutes(), document.defaults().durationMinutes()));
            team.setBudgetPerPerson(seed.budgetPerPerson() == null ? BigDecimal.ZERO : seed.budgetPerPerson());
            team.setSkillLevel(seed.skillLevel());
            team.setUserId(requiredId(userIdsByAccount, seed.ownerAccount()));
            team.setStatus(valueOrDefault(seed.status(), document.defaults().status()));
            team.setIsDelete(0);
            if (teamMapper.insert(team) != 1) {
                throw new IllegalStateException("cannot insert evaluation team: " + seed.code());
            }
            teamIdsByCode.put(seed.code(), team.getId());
            seedsByCode.put(seed.code(), seed);
        }

        Date now = new Date();
        for (Map.Entry<String, AiEvaluationDatasetLoader.TeamSeed> entry : seedsByCode.entrySet()) {
            Long teamId = teamIdsByCode.get(entry.getKey());
            for (String memberAccount : entry.getValue().memberAccounts()) {
                UserTeam relation = new UserTeam();
                relation.setUserId(requiredId(userIdsByAccount, memberAccount));
                relation.setTeamId(teamId);
                relation.setJoinTime(now);
                relation.setIsDelete(0);
                if (userTeamMapper.insert(relation) != 1) {
                    throw new IllegalStateException("cannot insert member " + memberAccount
                            + " for evaluation team " + entry.getKey());
                }
            }
        }
    }

    private void generateEmbeddings() {
        if (!embeddingGenerator.isAvailable()) {
            throw new IllegalStateException("embedding generation was requested, but provider configuration is unavailable");
        }
        List<AiUserProfileEntity> profiles = profileMapper.selectList(new QueryWrapper<AiUserProfileEntity>()
                .in("userId", evaluationUserIds()).orderByAsc("userId"));
        for (AiUserProfileEntity profile : profiles) {
            GeneratedEmbedding generated = embeddingGenerator.generate(profile.getMatchProfileText());
            float[] normalized = embeddingCodec.normalize(generated.vector());
            AiUserProfileEmbedding embedding = new AiUserProfileEmbedding();
            embedding.setUserId(profile.getUserId());
            embedding.setProfileVersion(profile.getProfileVersion());
            embedding.setMatchTextHash(textHashService.sha256(profile.getMatchProfileText()));
            embedding.setEmbeddingModel(generated.model());
            embedding.setDimensions(normalized.length);
            embedding.setVectorJson(embeddingCodec.serialize(normalized));
            embedding.setStatus(1);
            embedding.setGeneratedAt(new Date());
            embedding.setIsDelete(0);
            profileEmbeddingMapper.insert(embedding);
        }

        List<Team> teams = teamMapper.selectBatchIds(evaluationTeamIds());
        for (Team team : teams) {
            String retrievalText = teamTextBuilder.build(team);
            GeneratedEmbedding generated = embeddingGenerator.generate(retrievalText);
            float[] normalized = embeddingCodec.normalize(generated.vector());
            AiTeamEmbedding embedding = new AiTeamEmbedding();
            embedding.setTeamId(team.getId());
            embedding.setContentVersion(1);
            embedding.setContentHash(textHashService.sha256(retrievalText));
            embedding.setEmbeddingModel(generated.model());
            embedding.setDimensions(normalized.length);
            embedding.setVectorJson(embeddingCodec.serialize(normalized));
            embedding.setStatus(1);
            embedding.setGeneratedAt(new Date());
            embedding.setIsDelete(0);
            teamEmbeddingMapper.insert(embedding);
        }
    }

    private void cleanEvaluationData() {
        List<Long> userIds = evaluationUserIds();
        Set<Long> teamIds = new LinkedHashSet<>(evaluationTeamIds());
        teamIds.addAll(selectIdsByForeignKey("team", "userId", userIds));

        deleteByIds("ai_tool_call_log", "relatedTeamId", teamIds);
        deleteByIds("ai_team_draft", "confirmedTeamId", teamIds);
        deleteByIds("ai_team_embedding", "teamId", teamIds);
        deleteByIds("user_team", "teamId", teamIds);
        deleteByIds("user_team", "userId", userIds);
        deleteByIds("team", "id", teamIds);

        deleteByIds("ai_chat_message", "userId", userIds);
        deleteByIds("ai_user_episode", "userId", userIds);
        deleteByIds("ai_episode_extraction_task", "userId", userIds);
        deleteByIds("ai_profile_update_task", "userId", userIds);
        deleteByIds("ai_user_profile_revision", "userId", userIds);
        deleteByIds("ai_user_profile_embedding", "userId", userIds);
        deleteByIds("ai_user_profile", "userId", userIds);
        deleteByIds("ai_team_draft", "userId", userIds);
        deleteByIds("ai_tool_call_log", "userId", userIds);
        deleteByIds("ai_chat_session", "userId", userIds);

        assertNoRows("ai_team_embedding", "teamId", teamIds);
        assertNoRows("user_team", "teamId", teamIds);
        assertNoRows("user_team", "userId", userIds);
        assertNoRows("team", "id", teamIds);
        assertNoRows("ai_user_profile_embedding", "userId", userIds);
        assertNoRows("ai_user_profile", "userId", userIds);
        assertNoRows("ai_chat_message", "userId", userIds);
        assertNoRows("ai_chat_session", "userId", userIds);
        deleteByIds("user", "id", userIds);
    }

    private void verifyResult(String action) {
        boolean dataExpected = !"clean".equals(action);
        long expectedUsers = dataExpected ? loader.users().users().size() : 0;
        long expectedProfiles = dataExpected ? loader.profiles().profiles().size() : 0;
        long expectedTeams = dataExpected ? loader.teams().teams().size() : 0;
        assertEquals(expectedUsers, scalar("select count(*) from `user` where left(userAccount, 5) = 'eval_'"));
        assertEquals(expectedProfiles, scalar("select count(*) from ai_user_profile where userId in "
                + "(select id from `user` where left(userAccount, 5) = 'eval_')"));
        assertEquals(expectedTeams, scalar("select count(*) from team where left(name, 9) = '[AI-EVAL]'"));
        if (dataExpected && embeddingsRequested()) {
            assertEquals(expectedProfiles, scalar("select count(*) from ai_user_profile_embedding where status = 1 "
                    + "and userId in (select id from `user` where left(userAccount, 5) = 'eval_')"));
            assertEquals(expectedTeams, scalar("select count(*) from ai_team_embedding where status = 1 "
                    + "and teamId in (select id from team where left(name, 9) = '[AI-EVAL]')"));
            assertEmbeddingMetadataAndVectors();
        }
    }

    private void assertEmbeddingMetadataAndVectors() {
        String expectedModel = embeddingGenerator.modelName();
        List<Map<String, Object>> profileRows = jdbcTemplate.queryForList(
                "select e.embeddingModel, e.dimensions, e.vectorJson, e.profileVersion "
                        + "from ai_user_profile_embedding e join `user` u on u.id = e.userId "
                        + "where left(u.userAccount, 5) = 'eval_' and e.status = 1");
        List<Map<String, Object>> teamRows = jdbcTemplate.queryForList(
                "select e.embeddingModel, e.dimensions, e.vectorJson, e.contentVersion "
                        + "from ai_team_embedding e join team t on t.id = e.teamId "
                        + "where left(t.name, 9) = '[AI-EVAL]' and e.status = 1");
        Integer commonDimensions = null;
        for (Map<String, Object> row : concat(profileRows, teamRows)) {
            assertEquals(expectedModel, row.get("embeddingModel"), "embedding model must match configuration");
            int dimensions = ((Number) row.get("dimensions")).intValue();
            assertTrue(dimensions > 0, "embedding dimensions must be positive");
            if (commonDimensions == null) {
                commonDimensions = dimensions;
            } else {
                assertEquals(commonDimensions, dimensions, "all evaluation vectors must have the same dimensions");
            }
            float[] vector = embeddingCodec.deserialize((String) row.get("vectorJson"));
            assertEquals(dimensions, vector.length, "stored dimensions must match vector length");
        }
        profileRows.forEach(row -> assertEquals(1, ((Number) row.get("profileVersion")).intValue()));
        teamRows.forEach(row -> assertEquals(1, ((Number) row.get("contentVersion")).intValue()));
    }

    private List<Long> evaluationUserIds() {
        return jdbcTemplate.queryForList(
                "select id from `user` where left(userAccount, 5) = 'eval_'", Long.class);
    }

    private List<Long> evaluationTeamIds() {
        return jdbcTemplate.queryForList(
                "select id from team where left(name, 9) = '[AI-EVAL]'", Long.class);
    }

    private List<Long> selectIdsByForeignKey(String table, String column, List<Long> values) {
        if (values.isEmpty()) {
            return List.of();
        }
        String sql = "select id from " + table + " where " + column + " in (" + placeholders(values.size()) + ")";
        return jdbcTemplate.queryForList(sql, Long.class, values.toArray());
    }

    private void deleteByIds(String table, String column, Iterable<Long> values) {
        List<Long> ids = new ArrayList<>();
        values.forEach(ids::add);
        if (ids.isEmpty()) {
            return;
        }
        String sql = "delete from `" + table + "` where `" + column + "` in (" + placeholders(ids.size()) + ")";
        jdbcTemplate.update(sql, ids.toArray());
    }

    private void assertNoRows(String table, String column, Iterable<Long> values) {
        List<Long> ids = new ArrayList<>();
        values.forEach(ids::add);
        if (ids.isEmpty()) {
            return;
        }
        String sql = "select count(*) from `" + table + "` where `" + column + "` in ("
                + placeholders(ids.size()) + ")";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, ids.toArray());
        assertEquals(0L, count == null ? 0L : count, "cleanup left rows in " + table);
    }

    private List<Map<String, Object>> concat(List<Map<String, Object>> first,
                                              List<Map<String, Object>> second) {
        List<Map<String, Object>> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private long scalar(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private void assertTestDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals(TEST_DATABASE, connection.getCatalog(),
                    "当前连接的不是 sync_up_test，已停止评测数据操作");
        }
    }

    private GeneratedUserProfile generatedProfile(AiEvaluationDatasetLoader.ProfileSeed seed) {
        return new GeneratedUserProfile(
                seed.activityPreferenceText(), seed.socialPersonalityText(), seed.partnerPreferenceText(),
                seed.activityConstraintHabitText(), seed.aiInteractionPreferenceText());
    }

    private long requiredId(Map<String, Long> idsByBusinessKey, String businessKey) {
        Long id = idsByBusinessKey.get(businessKey);
        if (id == null) {
            throw new IllegalStateException("missing evaluation business key: " + businessKey);
        }
        return id;
    }

    private int valueOrDefault(Integer value, Integer defaultValue) {
        if (value == null && defaultValue == null) {
            throw new IllegalStateException("evaluation seed and defaults both omit a required value");
        }
        return value == null ? defaultValue : value;
    }

    private Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(EVALUATION_ZONE).toInstant());
    }

    private boolean embeddingsRequested() {
        return Boolean.parseBoolean(System.getProperty("ai.eval.embedding.enabled", "false"));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize evaluation seed value", e);
        }
    }
}
