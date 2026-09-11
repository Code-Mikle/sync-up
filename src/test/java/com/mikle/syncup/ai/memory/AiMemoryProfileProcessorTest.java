package com.mikle.syncup.ai.memory;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mikle.syncup.ai.config.AiMemoryProperties;
import com.mikle.syncup.ai.mapper.AiProfileUpdateTaskMapper;
import com.mikle.syncup.ai.mapper.AiUserEpisodeMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileEmbeddingMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileRevisionMapper;
import com.mikle.syncup.ai.model.entity.AiProfileUpdateTask;
import com.mikle.syncup.ai.model.entity.AiUserEpisode;
import com.mikle.syncup.ai.model.entity.AiUserProfileEmbedding;
import com.mikle.syncup.ai.model.entity.AiUserProfileEntity;
import com.mikle.syncup.ai.model.entity.AiUserProfileRevision;
import com.mikle.syncup.ai.model.enums.EpisodePriority;
import com.mikle.syncup.ai.model.enums.EpisodeSignalType;
import com.mikle.syncup.ai.model.enums.EpisodeStatus;
import com.mikle.syncup.ai.model.enums.MemorySourceType;
import com.mikle.syncup.ai.model.enums.MemoryTaskStatus;
import com.mikle.syncup.ai.model.enums.ProfileStatus;
import com.mikle.syncup.ai.model.enums.ProfileType;
import com.mikle.syncup.ai.model.enums.ProfileUpdateTriggerType;
import com.mikle.syncup.ai.model.schema.GeneratedEmbedding;
import com.mikle.syncup.ai.service.memory.AiMemoryTaskProcessorService;
import com.mikle.syncup.ai.service.profile.AiProfileUpdateTaskService;
import com.mikle.syncup.ai.service.profile.ProfileDimensionGenerator;
import com.mikle.syncup.ai.service.embedding.ProfileEmbeddingGenerator;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AiMemoryProfileProcessorTest {

    private static final String UNKNOWN = "暂未观察到明确偏好";

    private final List<Long> testUserIds = new ArrayList<>();

    @Resource private AiMemoryTaskProcessorService processor;
    @Resource private AiMemoryProperties properties;
    @Resource private AiProfileUpdateTaskMapper taskMapper;
    @Resource private AiUserEpisodeMapper episodeMapper;
    @Resource private AiUserProfileMapper profileMapper;
    @Resource private AiUserProfileEmbeddingMapper embeddingMapper;
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private DataSource dataSource;

    @MockitoBean private ProfileDimensionGenerator dimensionGenerator;
    @MockitoBean private ProfileEmbeddingGenerator embeddingGenerator;
    @MockitoBean private AiProfileUpdateTaskService profileTaskService;
    @MockitoSpyBean private AiUserProfileRevisionMapper revisionMapper;

    @BeforeEach
    void setUp() throws SQLException {
        assertTestDatabase();
        cleanupMemoryTables();
        properties.getProfileUpdate().setBatchSize(20);
        properties.getProfileUpdate().setMaxRetries(3);
        properties.getProfileUpdate().setProcessingTimeoutMinutes(10);
        when(dimensionGenerator.isAvailable()).thenReturn(true);
        when(dimensionGenerator.modelName()).thenReturn("profile-test-model");
        when(dimensionGenerator.promptVersion()).thenReturn("profile-prompt-v1");
        when(embeddingGenerator.isAvailable()).thenReturn(false);
    }

    @AfterEach
    void cleanup() {
        cleanupMemoryTables();
    }

    @Test
    void process_singleDimension_shouldPreserveOtherDimensionsAndWriteRevision() {
        long userId = uniquePositiveLong();
        AiUserProfileEntity original = insertProfile(userId, 3);
        AiUserEpisode evidence = insertEpisode(userId, ProfileType.ACTIVITY_PREFERENCE,
                EpisodeSignalType.EXPLICIT, EpisodeStatus.PENDING, "喜欢周末羽毛球", "[]");
        AiProfileUpdateTask task = insertTask(userId, ProfileType.ACTIVITY_PREFERENCE, 3,
                ProfileUpdateTriggerType.COUNT);
        when(dimensionGenerator.generate(eq(ProfileType.ACTIVITY_PREFERENCE),
                eq(original.getActivityPreferenceText()), anyList()))
                .thenReturn("偏好周末参加羽毛球活动");

        int processed = processor.processProfileUpdateTasks();

        AiUserProfileEntity updated = profileFor(userId);
        AiUserEpisode consolidated = episodeMapper.selectById(evidence.getId());
        AiUserProfileRevision revision = oneRevision(userId);
        assertAll(
                () -> assertEquals(1, processed),
                () -> assertEquals(4, updated.getProfileVersion()),
                () -> assertEquals("偏好周末参加羽毛球活动", updated.getActivityPreferenceText()),
                () -> assertEquals(original.getSocialPersonalityText(), updated.getSocialPersonalityText()),
                () -> assertEquals(original.getPartnerPreferenceText(), updated.getPartnerPreferenceText()),
                () -> assertEquals(original.getActivityConstraintHabitText(), updated.getActivityConstraintHabitText()),
                () -> assertEquals(original.getAiInteractionPreferenceText(), updated.getAiInteractionPreferenceText()),
                () -> assertEquals(ProfileStatus.ACTIVE.name(), updated.getStatus()),
                () -> assertEquals(EpisodeStatus.CONSOLIDATED.name(), consolidated.getStatus()),
                () -> assertEquals(4, consolidated.getConsolidatedProfileVersion()),
                () -> assertEquals(MemoryTaskStatus.SUCCESS.name(), taskMapper.selectById(task.getId()).getStatus()),
                () -> assertNotNull(revision),
                () -> assertEquals(3, revision.getFromProfileVersion()),
                () -> assertEquals(4, revision.getToProfileVersion()),
                () -> assertTrue(revision.getEvidenceEpisodeIds().contains(evidence.getId().toString()))
        );
    }

    @Test
    void process_correctionEpisode_shouldInvalidateSupersededEvidence() {
        long userId = uniquePositiveLong();
        AiUserEpisode oldEvidence = insertEpisode(userId, ProfileType.PARTNER_PREFERENCE,
                EpisodeSignalType.EXPLICIT, EpisodeStatus.CONSOLIDATED, "偏好热闹的大型队伍", "[]");
        AiUserEpisode correction = insertEpisode(userId, ProfileType.PARTNER_PREFERENCE,
                EpisodeSignalType.CORRECTION, EpisodeStatus.PENDING, "更偏好三到四人的小队",
                "[" + oldEvidence.getId() + "]");
        insertTask(userId, ProfileType.PARTNER_PREFERENCE, 0, ProfileUpdateTriggerType.IMMEDIATE);
        when(dimensionGenerator.generate(eq(ProfileType.PARTNER_PREFERENCE), eq(UNKNOWN), anyList()))
                .thenReturn("偏好三到四人、沟通直接的搭子");

        processor.processProfileUpdateTasks();

        assertAll(
                () -> assertEquals(EpisodeStatus.INVALID.name(),
                        episodeMapper.selectById(oldEvidence.getId()).getStatus()),
                () -> assertEquals(EpisodeStatus.CONSOLIDATED.name(),
                        episodeMapper.selectById(correction.getId()).getStatus()),
                () -> assertEquals("偏好三到四人、沟通直接的搭子",
                        profileFor(userId).getPartnerPreferenceText())
        );
    }

    @Test
    void process_staleExpectedVersion_shouldSupersedeTaskAndRequestRebuild() {
        long userId = uniquePositiveLong();
        insertProfile(userId, 2);
        insertEpisode(userId, ProfileType.SOCIAL_PERSONALITY,
                EpisodeSignalType.INFERRED, EpisodeStatus.PENDING, "交流时比较慢热", "[]");
        AiProfileUpdateTask task = insertTask(userId, ProfileType.SOCIAL_PERSONALITY, 1,
                ProfileUpdateTriggerType.COUNT);

        processor.processProfileUpdateTasks();

        assertAll(
                () -> assertEquals(MemoryTaskStatus.SUPERSEDED.name(),
                        taskMapper.selectById(task.getId()).getStatus()),
                () -> assertEquals(2, profileFor(userId).getProfileVersion()),
                () -> assertNull(oneRevision(userId))
        );
        verify(dimensionGenerator, never()).generate(any(), anyString(), anyList());
        verify(profileTaskService).enqueueIfNecessary(
                userId, ProfileType.SOCIAL_PERSONALITY, ProfileUpdateTriggerType.REBUILD, true);
    }

    @Test
    void process_newEmbedding_shouldDeactivateOldVersionAndActivateNormalizedVector() {
        long userId = uniquePositiveLong();
        insertProfile(userId, 1);
        insertEpisode(userId, ProfileType.ACTIVITY_PREFERENCE,
                EpisodeSignalType.EXPLICIT, EpisodeStatus.PENDING, "喜欢徒步", "[]");
        insertTask(userId, ProfileType.ACTIVITY_PREFERENCE, 1, ProfileUpdateTriggerType.COUNT);
        AiUserProfileEmbedding oldEmbedding = insertEmbedding(userId, 1, "old-hash", 1);
        when(dimensionGenerator.generate(eq(ProfileType.ACTIVITY_PREFERENCE), anyString(), anyList()))
                .thenReturn("喜欢周末轻量徒步");
        when(embeddingGenerator.isAvailable()).thenReturn(true);
        when(embeddingGenerator.generate(anyString()))
                .thenReturn(new GeneratedEmbedding("embedding-test-model", new float[]{3F, 4F}));

        processor.processProfileUpdateTasks();

        List<AiUserProfileEmbedding> embeddings = embeddingMapper.selectList(
                new QueryWrapper<AiUserProfileEmbedding>().eq("userId", userId).orderByAsc("profileVersion"));
        assertAll(
                () -> assertEquals(2, embeddings.size()),
                () -> assertEquals(0, embeddingMapper.selectById(oldEmbedding.getId()).getStatus()),
                () -> assertEquals(2, embeddings.getLast().getProfileVersion()),
                () -> assertEquals(1, embeddings.getLast().getStatus()),
                () -> assertEquals("embedding-test-model", embeddings.getLast().getEmbeddingModel()),
                () -> assertEquals(2, embeddings.getLast().getDimensions()),
                () -> assertEquals("[0.6,0.8]", embeddings.getLast().getVectorJson())
        );
    }

    @Test
    void process_revisionInsertFails_shouldRollbackProfileEpisodeAndEmbeddingChanges() {
        long userId = uniquePositiveLong();
        AiUserProfileEntity original = insertProfile(userId, 1);
        AiUserEpisode evidence = insertEpisode(userId, ProfileType.ACTIVITY_CONSTRAINT_HABIT,
                EpisodeSignalType.EXPLICIT, EpisodeStatus.PENDING, "只能周末参加活动", "[]");
        AiProfileUpdateTask task = insertTask(userId, ProfileType.ACTIVITY_CONSTRAINT_HABIT, 1,
                ProfileUpdateTriggerType.COUNT);
        AiUserProfileEmbedding oldEmbedding = insertEmbedding(userId, 1, "old-hash", 1);
        when(dimensionGenerator.generate(eq(ProfileType.ACTIVITY_CONSTRAINT_HABIT), anyString(), anyList()))
                .thenReturn("通常只能在周末参加活动");
        when(embeddingGenerator.isAvailable()).thenReturn(true);
        when(embeddingGenerator.generate(anyString()))
                .thenReturn(new GeneratedEmbedding("embedding-test-model", new float[]{1F, 0F}));
        doThrow(new IllegalStateException("revision storage failed"))
                .when(revisionMapper).insert(any(AiUserProfileRevision.class));

        processor.processProfileUpdateTasks();

        AiProfileUpdateTask retriable = taskMapper.selectById(task.getId());
        assertAll(
                () -> assertEquals(1, profileFor(userId).getProfileVersion()),
                () -> assertEquals(original.getActivityConstraintHabitText(),
                        profileFor(userId).getActivityConstraintHabitText()),
                () -> assertEquals(EpisodeStatus.PENDING.name(),
                        episodeMapper.selectById(evidence.getId()).getStatus()),
                () -> assertEquals(1, embeddingMapper.selectById(oldEmbedding.getId()).getStatus()),
                () -> assertEquals(1L, embeddingMapper.selectCount(
                        new QueryWrapper<AiUserProfileEmbedding>().eq("userId", userId))),
                () -> assertNull(oneRevision(userId)),
                () -> assertEquals(MemoryTaskStatus.PENDING.name(), retriable.getStatus()),
                () -> assertEquals(1, retriable.getRetryCount()),
                () -> assertEquals("revision storage failed", retriable.getLastError())
        );
    }

    private AiUserProfileEntity insertProfile(long userId, int version) {
        AiUserProfileEntity profile = new AiUserProfileEntity();
        profile.setUserId(userId);
        profile.setActivityPreferenceText("原活动偏好");
        profile.setSocialPersonalityText("原社交倾向");
        profile.setPartnerPreferenceText("原搭子偏好");
        profile.setActivityConstraintHabitText("原活动约束");
        profile.setAiInteractionPreferenceText("原交互偏好");
        profile.setProfileText("原完整画像");
        profile.setMatchProfileText("原匹配画像");
        profile.setInteractionProfileText("原交互画像");
        profile.setProfileVersion(version);
        profile.setEvidenceDigest("a".repeat(64));
        profile.setModel("old-model");
        profile.setPromptVersion("old-prompt");
        profile.setStatus(ProfileStatus.ACTIVE.name());
        profile.setGeneratedAt(new Date());
        profileMapper.insert(profile);
        return profile;
    }

    private AiUserEpisode insertEpisode(long userId, ProfileType type, EpisodeSignalType signal,
                                        EpisodeStatus status, String content, String supersededIds) {
        AiUserEpisode episode = new AiUserEpisode();
        episode.setUserId(userId);
        episode.setProfileType(type.name());
        episode.setContent(content);
        episode.setSourceType(MemorySourceType.CHAT_MESSAGE.name());
        episode.setSourceMessageIds("[]");
        episode.setSignalType(signal.name());
        episode.setPriority(EpisodePriority.NORMAL.name());
        episode.setEvidenceGroupKey("message:" + UUID.randomUUID());
        episode.setDedupeHash(UUID.randomUUID().toString().replace("-", "") + "0".repeat(32));
        episode.setSupersededEpisodeIds(supersededIds);
        episode.setStatus(status.name());
        episode.setObservedAt(new Date());
        episodeMapper.insert(episode);
        return episode;
    }

    private AiProfileUpdateTask insertTask(long userId, ProfileType type, int expectedVersion,
                                           ProfileUpdateTriggerType triggerType) {
        AiProfileUpdateTask task = new AiProfileUpdateTask();
        task.setUserId(userId);
        task.setProfileType(type.name());
        task.setTriggerType(triggerType.name());
        task.setTargetEvidenceDigest(UUID.randomUUID().toString().replace("-", "") + "0".repeat(32));
        task.setExpectedProfileVersion(expectedVersion);
        task.setStatus(MemoryTaskStatus.PENDING.name());
        task.setRetryCount(0);
        task.setNextRetryAt(new Date(System.currentTimeMillis() - 1000));
        taskMapper.insert(task);
        return task;
    }

    private AiUserProfileEmbedding insertEmbedding(long userId, int version, String hash, int status) {
        AiUserProfileEmbedding embedding = new AiUserProfileEmbedding();
        embedding.setUserId(userId);
        embedding.setProfileVersion(version);
        embedding.setMatchTextHash(hash.length() == 64 ? hash : "b".repeat(64));
        embedding.setEmbeddingModel("old-embedding-model");
        embedding.setDimensions(2);
        embedding.setVectorJson("[1.0,0.0]");
        embedding.setStatus(status);
        embedding.setGeneratedAt(new Date());
        embeddingMapper.insert(embedding);
        return embedding;
    }

    private AiUserProfileEntity profileFor(long userId) {
        return profileMapper.selectOne(new QueryWrapper<AiUserProfileEntity>()
                .eq("userId", userId).last("limit 1"));
    }

    private AiUserProfileRevision oneRevision(long userId) {
        return revisionMapper.selectOne(new QueryWrapper<AiUserProfileRevision>()
                .eq("userId", userId).last("limit 1"));
    }

    private void assertTestDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("sync_up_test", connection.getCatalog(),
                    "当前连接的不是 sync_up_test，已停止测试，避免污染开发数据库");
        }
    }

    private void cleanupMemoryTables() {
        for (Long userId : testUserIds) {
            jdbcTemplate.update("delete from ai_user_profile_revision where userId = ?", userId);
            jdbcTemplate.update("delete from ai_user_profile_embedding where userId = ?", userId);
            jdbcTemplate.update("delete from ai_profile_update_task where userId = ?", userId);
            jdbcTemplate.update("delete from ai_user_episode where userId = ?", userId);
            jdbcTemplate.update("delete from ai_episode_extraction_task where userId = ?", userId);
            jdbcTemplate.update("delete from ai_chat_message where userId = ?", userId);
            jdbcTemplate.update("delete from ai_chat_session where userId = ?", userId);
            jdbcTemplate.update("delete from ai_user_profile where userId = ?", userId);
        }
        testUserIds.clear();
    }

    private long uniquePositiveLong() {
        long userId = Math.abs(UUID.randomUUID().getMostSignificantBits() >>> 1);
        testUserIds.add(userId);
        return userId;
    }
}
