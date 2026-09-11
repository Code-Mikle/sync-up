package com.mikle.syncup.ai.memory;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mikle.syncup.ai.mapper.AiChatMessageMapper;
import com.mikle.syncup.ai.mapper.AiChatSessionMapper;
import com.mikle.syncup.ai.mapper.AiEpisodeExtractionTaskMapper;
import com.mikle.syncup.ai.mapper.AiUserEpisodeMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileEmbeddingMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileMapper;
import com.mikle.syncup.ai.model.entity.AiChatMessage;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.ai.model.entity.AiEpisodeExtractionTask;
import com.mikle.syncup.ai.model.entity.AiUserEpisode;
import com.mikle.syncup.ai.model.entity.AiUserProfileEmbedding;
import com.mikle.syncup.ai.model.entity.AiUserProfileEntity;
import com.mikle.syncup.ai.model.enums.EpisodePriority;
import com.mikle.syncup.ai.model.enums.EpisodeSignalType;
import com.mikle.syncup.ai.model.enums.EpisodeStatus;
import com.mikle.syncup.ai.model.enums.MemorySourceType;
import com.mikle.syncup.ai.model.enums.MemoryTaskStatus;
import com.mikle.syncup.ai.model.enums.ProfileStatus;
import com.mikle.syncup.ai.model.enums.ProfileType;
import com.mikle.syncup.ai.model.enums.ProfileUpdateTriggerType;
import com.mikle.syncup.ai.service.chat.AiChatMessageService;
import com.mikle.syncup.ai.service.memory.AiMemoryPipelineService;
import com.mikle.syncup.ai.service.profile.AiProfileUpdateTaskService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class AiMemorySourceLifecycleTest {

    private final List<Long> testUserIds = new ArrayList<>();

    @Resource private AiMemoryPipelineService memoryPipelineService;
    @Resource private AiChatMessageService chatMessageService;
    @Resource private AiChatSessionMapper sessionMapper;
    @Resource private AiChatMessageMapper messageMapper;
    @Resource private AiEpisodeExtractionTaskMapper extractionTaskMapper;
    @Resource private AiUserEpisodeMapper episodeMapper;
    @Resource private AiUserProfileMapper profileMapper;
    @Resource private AiUserProfileEmbeddingMapper embeddingMapper;
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private DataSource dataSource;

    @MockitoBean private AiProfileUpdateTaskService profileTaskService;

    @BeforeEach
    void setUp() throws SQLException {
        assertTestDatabase();
        cleanupMemoryTables();
    }

    @AfterEach
    void cleanup() {
        cleanupMemoryTables();
    }

    @Test
    void selfIntroductionChanged_shouldInvalidateOldSourceAndCreateNewExtractionTask() {
        long userId = uniquePositiveLong();
        AiEpisodeExtractionTask oldTask = insertSelfIntroductionTask(userId, "旧自我介绍");
        AiUserEpisode oldEpisode = insertEpisode(userId, MemorySourceType.SELF_INTRODUCTION, null, "[]");
        insertProfile(userId);
        AiUserProfileEmbedding oldEmbedding = insertEmbedding(userId);

        memoryPipelineService.onSelfIntroductionChanged(userId, "  喜欢周末徒步和羽毛球  ");

        List<AiEpisodeExtractionTask> tasks = extractionTaskMapper.selectList(
                new QueryWrapper<AiEpisodeExtractionTask>().eq("userId", userId).orderByAsc("id"));
        AiEpisodeExtractionTask newTask = tasks.getLast();
        assertAll(
                () -> assertEquals(2, tasks.size()),
                () -> assertEquals(MemoryTaskStatus.SUPERSEDED.name(),
                        extractionTaskMapper.selectById(oldTask.getId()).getStatus()),
                () -> assertEquals(EpisodeStatus.INVALID.name(),
                        episodeMapper.selectById(oldEpisode.getId()).getStatus()),
                () -> assertEquals(ProfileStatus.REBUILD_REQUIRED.name(), profileFor(userId).getStatus()),
                () -> assertEquals(0, embeddingMapper.selectById(oldEmbedding.getId()).getStatus()),
                () -> assertEquals(MemoryTaskStatus.PENDING.name(), newTask.getStatus()),
                () -> assertEquals(MemorySourceType.SELF_INTRODUCTION.name(), newTask.getSourceType()),
                () -> assertEquals("喜欢周末徒步和羽毛球", newTask.getSourceText()),
                () -> assertNotNull(newTask.getSourceReferenceId())
        );
        verify(profileTaskService).supersedeActiveTasks(userId);
        verify(profileTaskService, never()).enqueueRebuildAll(
                userId, ProfileUpdateTriggerType.SOURCE_DELETED);
    }

    @Test
    void selfIntroductionCleared_shouldInvalidateOldSourceAndEnqueueRebuildWithoutExtraction() {
        long userId = uniquePositiveLong();
        AiEpisodeExtractionTask oldTask = insertSelfIntroductionTask(userId, "旧自我介绍");
        AiUserEpisode oldEpisode = insertEpisode(userId, MemorySourceType.SELF_INTRODUCTION, null, "[]");
        insertProfile(userId);
        AiUserProfileEmbedding oldEmbedding = insertEmbedding(userId);

        memoryPipelineService.onSelfIntroductionChanged(userId, "   ");

        assertAll(
                () -> assertEquals(1L, extractionTaskMapper.selectCount(
                        new QueryWrapper<AiEpisodeExtractionTask>().eq("userId", userId))),
                () -> assertEquals(MemoryTaskStatus.SUPERSEDED.name(),
                        extractionTaskMapper.selectById(oldTask.getId()).getStatus()),
                () -> assertEquals(EpisodeStatus.INVALID.name(),
                        episodeMapper.selectById(oldEpisode.getId()).getStatus()),
                () -> assertEquals(ProfileStatus.REBUILD_REQUIRED.name(), profileFor(userId).getStatus()),
                () -> assertEquals(0, embeddingMapper.selectById(oldEmbedding.getId()).getStatus())
        );
        verify(profileTaskService).supersedeActiveTasks(userId);
        verify(profileTaskService).enqueueRebuildAll(
                userId, ProfileUpdateTriggerType.SOURCE_DELETED);
    }

    @Test
    void expiredChatMessageCleanup_shouldInvalidateDerivedMemoryAndKeepUnexpiredMessage() {
        long userId = uniquePositiveLong();
        AiChatSession session = insertSession(userId);
        AiChatMessage expired = insertMessage(userId, session.getId(),
                new Date(System.currentTimeMillis() - 60_000));
        AiChatMessage unexpired = insertMessage(userId, session.getId(),
                new Date(System.currentTimeMillis() + 86_400_000));
        AiUserEpisode episode = insertEpisode(userId, MemorySourceType.CHAT_MESSAGE,
                session.getId(), "[" + expired.getId() + "]");
        AiEpisodeExtractionTask task = insertChatExtractionTask(userId, session.getId(), unexpired.getId());
        insertProfile(userId);
        AiUserProfileEmbedding embedding = insertEmbedding(userId);

        int deleted = chatMessageService.deleteExpiredPhysically();

        AiChatSession resetSession = sessionMapper.selectById(session.getId());
        assertAll(
                () -> assertEquals(1, deleted),
                () -> assertNull(messageMapper.selectById(expired.getId())),
                () -> assertNotNull(messageMapper.selectById(unexpired.getId())),
                () -> assertEquals(EpisodeStatus.INVALID.name(),
                        episodeMapper.selectById(episode.getId()).getStatus()),
                () -> assertEquals(MemoryTaskStatus.SUPERSEDED.name(),
                        extractionTaskMapper.selectById(task.getId()).getStatus()),
                () -> assertNull(resetSession.getSummary()),
                () -> assertEquals(0L, resetSession.getLastSummaryMessageId()),
                () -> assertNull(resetSession.getSummaryModel()),
                () -> assertEquals(ProfileStatus.REBUILD_REQUIRED.name(), profileFor(userId).getStatus()),
                () -> assertEquals(0, embeddingMapper.selectById(embedding.getId()).getStatus())
        );
        verify(profileTaskService).supersedeActiveTasks(userId);
        verify(profileTaskService).enqueueRebuildAll(
                userId, ProfileUpdateTriggerType.SOURCE_DELETED);
    }

    private AiEpisodeExtractionTask insertSelfIntroductionTask(long userId, String text) {
        AiEpisodeExtractionTask task = new AiEpisodeExtractionTask();
        task.setUserId(userId);
        task.setSourceType(MemorySourceType.SELF_INTRODUCTION.name());
        task.setSourceText(text);
        task.setSourceReferenceId("self-intro:old-" + UUID.randomUUID());
        task.setStatus(MemoryTaskStatus.PENDING.name());
        task.setRetryCount(0);
        task.setNextRetryAt(new Date());
        extractionTaskMapper.insert(task);
        return task;
    }

    private AiEpisodeExtractionTask insertChatExtractionTask(long userId, long sessionId, long toMessageId) {
        AiEpisodeExtractionTask task = new AiEpisodeExtractionTask();
        task.setUserId(userId);
        task.setChatSessionId(sessionId);
        task.setSourceType(MemorySourceType.CHAT_MESSAGE.name());
        task.setFromMessageIdExclusive(0L);
        task.setToMessageIdInclusive(toMessageId);
        task.setStatus(MemoryTaskStatus.PENDING.name());
        task.setRetryCount(0);
        task.setNextRetryAt(new Date());
        extractionTaskMapper.insert(task);
        return task;
    }

    private AiUserEpisode insertEpisode(long userId, MemorySourceType sourceType,
                                        Long sourceSessionId, String sourceMessageIds) {
        AiUserEpisode episode = new AiUserEpisode();
        episode.setUserId(userId);
        episode.setProfileType(ProfileType.ACTIVITY_PREFERENCE.name());
        episode.setContent("旧的活动偏好证据");
        episode.setSourceType(sourceType.name());
        episode.setSourceSessionId(sourceSessionId);
        episode.setSourceMessageIds(sourceMessageIds);
        episode.setSourceReferenceId("source-" + UUID.randomUUID());
        episode.setSignalType(EpisodeSignalType.EXPLICIT.name());
        episode.setPriority(EpisodePriority.NORMAL.name());
        episode.setEvidenceGroupKey("group-" + UUID.randomUUID());
        episode.setDedupeHash(UUID.randomUUID().toString().replace("-", "") + "0".repeat(32));
        episode.setSupersededEpisodeIds("[]");
        episode.setStatus(EpisodeStatus.CONSOLIDATED.name());
        episode.setConsolidatedProfileVersion(1);
        episode.setObservedAt(new Date());
        episodeMapper.insert(episode);
        return episode;
    }

    private AiChatSession insertSession(long userId) {
        AiChatSession session = new AiChatSession();
        session.setUserId(userId);
        session.setSessionKey("lifecycle-test-" + UUID.randomUUID());
        session.setSummary("旧会话摘要");
        session.setLastSummaryMessageId(100L);
        session.setSummaryVersion(1);
        session.setSummaryUpdatedAt(new Date());
        session.setSummaryModel("summary-model");
        session.setSummaryPromptVersion("summary-prompt-v1");
        session.setLastClosedMessageId(100L);
        session.setLastEpisodeExtractedMessageId(0L);
        sessionMapper.insert(session);
        return session;
    }

    private AiChatMessage insertMessage(long userId, long sessionId, Date retentionExpireAt) {
        AiChatMessage message = new AiChatMessage();
        message.setUserId(userId);
        message.setChatSessionId(sessionId);
        message.setRole("user");
        message.setContent("聊天消息");
        message.setVisible(1);
        message.setRetentionExpireAt(retentionExpireAt);
        messageMapper.insert(message);
        return message;
    }

    private AiUserProfileEntity insertProfile(long userId) {
        AiUserProfileEntity profile = new AiUserProfileEntity();
        profile.setUserId(userId);
        profile.setActivityPreferenceText("活动偏好");
        profile.setSocialPersonalityText("社交倾向");
        profile.setPartnerPreferenceText("搭子偏好");
        profile.setActivityConstraintHabitText("活动约束");
        profile.setAiInteractionPreferenceText("交互偏好");
        profile.setProfileText("完整画像");
        profile.setMatchProfileText("匹配画像");
        profile.setInteractionProfileText("交互画像");
        profile.setProfileVersion(1);
        profile.setEvidenceDigest("a".repeat(64));
        profile.setModel("profile-model");
        profile.setPromptVersion("profile-prompt-v1");
        profile.setStatus(ProfileStatus.ACTIVE.name());
        profile.setGeneratedAt(new Date());
        profileMapper.insert(profile);
        return profile;
    }

    private AiUserProfileEmbedding insertEmbedding(long userId) {
        AiUserProfileEmbedding embedding = new AiUserProfileEmbedding();
        embedding.setUserId(userId);
        embedding.setProfileVersion(1);
        embedding.setMatchTextHash("b".repeat(64));
        embedding.setEmbeddingModel("embedding-model");
        embedding.setDimensions(2);
        embedding.setVectorJson("[1.0,0.0]");
        embedding.setStatus(1);
        embedding.setGeneratedAt(new Date());
        embeddingMapper.insert(embedding);
        return embedding;
    }

    private AiUserProfileEntity profileFor(long userId) {
        return profileMapper.selectOne(new QueryWrapper<AiUserProfileEntity>()
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
