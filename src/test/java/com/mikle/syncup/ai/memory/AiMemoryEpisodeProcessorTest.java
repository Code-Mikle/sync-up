package com.mikle.syncup.ai.memory;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mikle.syncup.ai.config.AiMemoryProperties;
import com.mikle.syncup.ai.mapper.AiChatMessageMapper;
import com.mikle.syncup.ai.mapper.AiChatSessionMapper;
import com.mikle.syncup.ai.mapper.AiEpisodeExtractionTaskMapper;
import com.mikle.syncup.ai.mapper.AiUserEpisodeMapper;
import com.mikle.syncup.ai.model.entity.AiChatMessage;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.ai.model.entity.AiEpisodeExtractionTask;
import com.mikle.syncup.ai.model.entity.AiUserEpisode;
import com.mikle.syncup.ai.model.enums.EpisodePriority;
import com.mikle.syncup.ai.model.enums.EpisodeSignalType;
import com.mikle.syncup.ai.model.enums.EpisodeStatus;
import com.mikle.syncup.ai.model.enums.MemorySourceType;
import com.mikle.syncup.ai.model.enums.MemoryTaskStatus;
import com.mikle.syncup.ai.model.enums.ProfileType;
import com.mikle.syncup.ai.model.enums.ProfileUpdateTriggerType;
import com.mikle.syncup.ai.model.schema.GeneratedEpisode;
import com.mikle.syncup.ai.model.schema.GeneratedEpisodeExtraction;
import com.mikle.syncup.ai.service.AiMemoryPipelineService;
import com.mikle.syncup.ai.service.AiMemoryTaskProcessorService;
import com.mikle.syncup.ai.service.AiProfileUpdateTaskService;
import com.mikle.syncup.ai.service.EpisodeExtractor;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AiMemoryEpisodeProcessorTest {

    private final List<Long> testUserIds = new ArrayList<>();

    @Resource private AiMemoryTaskProcessorService processor;
    @Resource private AiMemoryProperties properties;
    @Resource private AiChatSessionMapper sessionMapper;
    @Resource private AiChatMessageMapper messageMapper;
    @Resource private AiEpisodeExtractionTaskMapper taskMapper;
    @Resource private AiUserEpisodeMapper episodeMapper;
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private DataSource dataSource;

    @MockitoBean private EpisodeExtractor episodeExtractor;
    @MockitoBean private AiProfileUpdateTaskService profileTaskService;
    @MockitoBean private AiMemoryPipelineService memoryPipelineService;

    @BeforeEach
    void setUp() throws SQLException {
        assertTestDatabase();
        cleanupMemoryTables();
        properties.getEpisode().setBatchSize(20);
        properties.getEpisode().setMaxRetries(3);
        properties.getEpisode().setProcessingTimeoutMinutes(10);
        when(episodeExtractor.isAvailable()).thenReturn(true);
        when(episodeExtractor.modelName()).thenReturn("episode-test-model");
        when(episodeExtractor.promptVersion()).thenReturn("episode-prompt-v1");
    }

    @AfterEach
    void cleanup() {
        cleanupMemoryTables();
    }

    @Test
    void process_validEpisode_shouldSaveEpisodeAdvanceCursorAndCompleteTask() {
        ChatFixture fixture = createChatFixture();
        when(episodeExtractor.extract(anyString())).thenReturn(extraction(
                episode("周末喜欢打羽毛球", fixture.userMessageId(), EpisodeSignalType.EXPLICIT)));

        int processed = processor.processEpisodeExtractionTasks();

        AiEpisodeExtractionTask task = taskMapper.selectById(fixture.taskId());
        AiChatSession session = sessionMapper.selectById(fixture.sessionId());
        AiUserEpisode saved = oneEpisode(fixture.userId());
        assertAll(
                () -> assertEquals(1, processed),
                () -> assertEquals(MemoryTaskStatus.SUCCESS.name(), task.getStatus()),
                () -> assertEquals("episode-test-model", task.getModel()),
                () -> assertEquals(fixture.lastMessageId(), session.getLastEpisodeExtractedMessageId()),
                () -> assertNotNull(saved),
                () -> assertEquals("周末喜欢打羽毛球", saved.getContent()),
                () -> assertEquals(EpisodeStatus.PENDING.name(), saved.getStatus()),
                () -> assertEquals("[" + fixture.userMessageId() + "]", saved.getSourceMessageIds())
        );
        verify(profileTaskService).enqueueIfNecessary(
                fixture.userId(), ProfileType.ACTIVITY_PREFERENCE,
                ProfileUpdateTriggerType.COUNT, false);
        verify(memoryPipelineService).createNextChatExtractionTaskIfNecessary(
                fixture.userId(), fixture.sessionId());
    }

    @Test
    void process_emptyExtraction_shouldStillCompleteTaskAndAdvanceCursor() {
        ChatFixture fixture = createChatFixture();
        when(episodeExtractor.extract(anyString())).thenReturn(new GeneratedEpisodeExtraction());

        processor.processEpisodeExtractionTasks();

        assertAll(
                () -> assertEquals(MemoryTaskStatus.SUCCESS.name(),
                        taskMapper.selectById(fixture.taskId()).getStatus()),
                () -> assertEquals(fixture.lastMessageId(),
                        sessionMapper.selectById(fixture.sessionId()).getLastEpisodeExtractedMessageId()),
                () -> assertEquals(0L, episodeMapper.selectCount(
                        new QueryWrapper<AiUserEpisode>().eq("userId", fixture.userId())))
        );
        verify(profileTaskService, never()).enqueueIfNecessary(
                eq(fixture.userId()), eq(ProfileType.ACTIVITY_PREFERENCE),
                eq(ProfileUpdateTriggerType.COUNT), eq(false));
    }

    @Test
    void process_episodeReferencesAssistantMessage_shouldRejectWholeBatchAndKeepCursor() {
        ChatFixture fixture = createChatFixture();
        GeneratedEpisode valid = episode("喜欢徒步", fixture.userMessageId(), EpisodeSignalType.EXPLICIT);
        GeneratedEpisode invalid = episode("偏好安静交流", fixture.lastMessageId(), EpisodeSignalType.INFERRED);
        when(episodeExtractor.extract(anyString())).thenReturn(extraction(valid, invalid));

        processor.processEpisodeExtractionTasks();

        AiEpisodeExtractionTask task = taskMapper.selectById(fixture.taskId());
        assertAll(
                () -> assertEquals(MemoryTaskStatus.PENDING.name(), task.getStatus()),
                () -> assertEquals(1, task.getRetryCount()),
                () -> assertTrue(task.getLastError().contains("source messages")),
                () -> assertEquals(0L,
                        sessionMapper.selectById(fixture.sessionId()).getLastEpisodeExtractedMessageId()),
                () -> assertEquals(0L, episodeMapper.selectCount(
                        new QueryWrapper<AiUserEpisode>().eq("userId", fixture.userId())))
        );
    }

    @Test
    void process_sensitiveEpisode_shouldRejectWithoutPersistingSensitiveText() {
        ChatFixture fixture = createChatFixture();
        when(episodeExtractor.extract(anyString())).thenReturn(extraction(
                episode("联系方式 tester@example.com", fixture.userMessageId(), EpisodeSignalType.EXPLICIT)));

        processor.processEpisodeExtractionTasks();

        AiEpisodeExtractionTask task = taskMapper.selectById(fixture.taskId());
        assertAll(
                () -> assertEquals(MemoryTaskStatus.PENDING.name(), task.getStatus()),
                () -> assertEquals(1, task.getRetryCount()),
                () -> assertTrue(task.getLastError().contains("sensitive data")),
                () -> assertEquals(0L, episodeMapper.selectCount(
                        new QueryWrapper<AiUserEpisode>().eq("userId", fixture.userId())))
        );
    }

    @Test
    void process_staleSessionCursor_shouldSupersedeTaskWithoutPersistingResult() {
        ChatFixture fixture = createChatFixture();
        AiChatSession session = sessionMapper.selectById(fixture.sessionId());
        session.setLastEpisodeExtractedMessageId(fixture.userMessageId());
        sessionMapper.updateById(session);
        when(episodeExtractor.extract(anyString())).thenReturn(extraction(
                episode("这个结果已经过期", fixture.userMessageId(), EpisodeSignalType.INFERRED)));

        processor.processEpisodeExtractionTasks();

        assertAll(
                () -> assertEquals(MemoryTaskStatus.SUPERSEDED.name(),
                        taskMapper.selectById(fixture.taskId()).getStatus()),
                () -> assertEquals(0L, episodeMapper.selectCount(
                        new QueryWrapper<AiUserEpisode>().eq("userId", fixture.userId())))
        );
        verify(episodeExtractor).extract(anyString());
    }

    @Test
    void process_repeatedExtractorFailure_shouldEventuallyMarkTaskFailed() {
        ChatFixture fixture = createChatFixture();
        properties.getEpisode().setMaxRetries(2);
        when(episodeExtractor.extract(anyString())).thenThrow(new IllegalStateException("model timeout"));

        processor.processEpisodeExtractionTasks();
        makeTaskDueNow(fixture.taskId());
        processor.processEpisodeExtractionTasks();

        AiEpisodeExtractionTask task = taskMapper.selectById(fixture.taskId());
        assertAll(
                () -> assertEquals(MemoryTaskStatus.FAILED.name(), task.getStatus()),
                () -> assertEquals(2, task.getRetryCount()),
                () -> assertEquals("model timeout", task.getLastError()),
                () -> assertEquals(0L,
                        sessionMapper.selectById(fixture.sessionId()).getLastEpisodeExtractedMessageId())
        );
    }

    @Test
    void process_timedOutProcessingTask_shouldRecoverAndProcessIt() {
        ChatFixture fixture = createChatFixture();
        jdbcTemplate.update("update ai_episode_extraction_task set status = ?, updateTime = ? where id = ?",
                MemoryTaskStatus.PROCESSING.name(),
                Date.from(Instant.now().minus(30, ChronoUnit.MINUTES)), fixture.taskId());
        when(episodeExtractor.extract(anyString())).thenReturn(new GeneratedEpisodeExtraction());

        int processed = processor.processEpisodeExtractionTasks();

        assertAll(
                () -> assertEquals(1, processed),
                () -> assertEquals(MemoryTaskStatus.SUCCESS.name(),
                        taskMapper.selectById(fixture.taskId()).getStatus()),
                () -> assertEquals(fixture.lastMessageId(),
                        sessionMapper.selectById(fixture.sessionId()).getLastEpisodeExtractedMessageId())
        );
    }

    private ChatFixture createChatFixture() {
        long userId = uniquePositiveLong();
        AiChatSession session = new AiChatSession();
        session.setUserId(userId);
        session.setSessionKey("episode-test-" + UUID.randomUUID());
        session.setLastSummaryMessageId(0L);
        session.setSummaryVersion(0);
        session.setLastClosedMessageId(0L);
        session.setLastEpisodeExtractedMessageId(0L);
        sessionMapper.insert(session);

        AiChatMessage userMessage = message(userId, session.getId(), "user", "我周末喜欢打羽毛球");
        AiChatMessage assistantMessage = message(userId, session.getId(), "assistant", "已记住你的偏好");
        messageMapper.insert(userMessage);
        messageMapper.insert(assistantMessage);

        session.setLastClosedMessageId(assistantMessage.getId());
        sessionMapper.updateById(session);

        AiEpisodeExtractionTask task = new AiEpisodeExtractionTask();
        task.setUserId(userId);
        task.setChatSessionId(session.getId());
        task.setSourceType(MemorySourceType.CHAT_MESSAGE.name());
        task.setFromMessageIdExclusive(0L);
        task.setToMessageIdInclusive(assistantMessage.getId());
        task.setStatus(MemoryTaskStatus.PENDING.name());
        task.setRetryCount(0);
        task.setNextRetryAt(new Date(System.currentTimeMillis() - 1000));
        taskMapper.insert(task);
        return new ChatFixture(userId, session.getId(), userMessage.getId(), assistantMessage.getId(), task.getId());
    }

    private AiChatMessage message(long userId, long sessionId, String role, String content) {
        AiChatMessage message = new AiChatMessage();
        message.setUserId(userId);
        message.setChatSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setVisible(1);
        message.setCreateTime(new Date());
        return message;
    }

    private GeneratedEpisode episode(String content, long sourceMessageId, EpisodeSignalType signalType) {
        GeneratedEpisode episode = new GeneratedEpisode();
        episode.setProfileType(ProfileType.ACTIVITY_PREFERENCE.name());
        episode.setContent(content);
        episode.setSourceMessageIds(List.of(sourceMessageId));
        episode.setSignalType(signalType.name());
        episode.setPriority(EpisodePriority.NORMAL.name());
        episode.setSupersededEpisodeIds(List.of());
        return episode;
    }

    private GeneratedEpisodeExtraction extraction(GeneratedEpisode... episodes) {
        GeneratedEpisodeExtraction extraction = new GeneratedEpisodeExtraction();
        extraction.setEpisodes(List.of(episodes));
        return extraction;
    }

    private AiUserEpisode oneEpisode(long userId) {
        return episodeMapper.selectOne(new QueryWrapper<AiUserEpisode>()
                .eq("userId", userId).last("limit 1"));
    }

    private void makeTaskDueNow(long taskId) {
        jdbcTemplate.update("update ai_episode_extraction_task set nextRetryAt = ? where id = ?",
                new Date(System.currentTimeMillis() - 1000), taskId);
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
        }
        testUserIds.clear();
    }

    private long uniquePositiveLong() {
        long userId = Math.abs(UUID.randomUUID().getMostSignificantBits() >>> 1);
        testUserIds.add(userId);
        return userId;
    }

    private record ChatFixture(long userId, long sessionId, long userMessageId,
                               long lastMessageId, long taskId) { }
}
