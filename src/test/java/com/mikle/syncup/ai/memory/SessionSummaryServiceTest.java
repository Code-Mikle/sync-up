package com.mikle.syncup.ai.memory;

import com.mikle.syncup.ai.config.AiMemoryProperties;
import com.mikle.syncup.ai.mapper.AiChatSessionMapper;
import com.mikle.syncup.ai.model.entity.AiChatMessage;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.ai.service.AiChatMessageService;
import com.mikle.syncup.ai.service.AiChatSessionService;
import com.mikle.syncup.ai.service.SessionSummaryGenerator;
import com.mikle.syncup.ai.service.impl.SessionSummaryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionSummaryServiceTest {

    @Mock
    private AiChatSessionService chatSessionService;

    @Mock
    private AiChatSessionMapper chatSessionMapper;

    @Mock
    private AiChatMessageService chatMessageService;

    @Mock
    private SessionSummaryGenerator summaryGenerator;

    private SessionSummaryServiceImpl service;

    private AiMemoryProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AiMemoryProperties();
        properties.getWorkingMemory().setRecentMessageCount(2);
        properties.getWorkingMemory().setSummaryBatchSize(2);
        properties.getWorkingMemory().setMaxContextTokens(1000);
        properties.getWorkingMemory().setSummaryInputMaxTokens(1000);
        properties.getWorkingMemory().setSummaryMaxChars(200);

        service = new SessionSummaryServiceImpl();
        ReflectionTestUtils.setField(service, "chatSessionService", chatSessionService);
        ReflectionTestUtils.setField(service, "chatSessionMapper", chatSessionMapper);
        ReflectionTestUtils.setField(service, "chatMessageService", chatMessageService);
        ReflectionTestUtils.setField(service, "summaryGenerator", summaryGenerator);
        ReflectionTestUtils.setField(service, "memoryProperties", properties);
    }

    @Test
    void summarizeAsyncIfNecessary_sessionBelongsToAnotherUser_shouldIgnoreRequest() {
        when(chatSessionService.getById(10L)).thenReturn(session(10L, 8002L, 0L, 5L, null));

        service.summarizeAsyncIfNecessary(8001L, 10L);

        verifyNoInteractions(summaryGenerator, chatMessageService, chatSessionMapper);
    }

    @Test
    void summarizeIfNecessary_generatorUnavailable_shouldReturnFalseWithoutReadingSession() {
        when(summaryGenerator.isAvailable()).thenReturn(false);

        boolean updated = service.summarizeIfNecessary(session(10L, 8001L, 0L, 5L, null));

        assertFalse(updated);
        verifyNoInteractions(chatSessionService, chatMessageService, chatSessionMapper);
    }

    @Test
    void summarizeIfNecessary_belowCountAndTokenThreshold_shouldNotGenerateSummary() {
        AiChatSession current = session(10L, 8001L, 0L, 3L, null);
        when(summaryGenerator.isAvailable()).thenReturn(true);
        when(chatSessionService.getById(10L)).thenReturn(current);
        when(chatMessageService.listClosedMessages(10L, 0L, 3L, 104)).thenReturn(List.of(
                message(1L, "user", "消息一"),
                message(2L, "assistant", "消息二"),
                message(3L, "user", "消息三")
        ));

        boolean updated = service.summarizeIfNecessary(current);

        assertFalse(updated);
        verify(summaryGenerator, never()).summarize(any());
        verify(chatSessionMapper, never()).updateSummaryCas(
                anyLong(), anyLong(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void summarizeIfNecessary_firstSummary_shouldSummarizeOldMessagesAndKeepRecentRawMessages() {
        AiChatSession current = session(10L, 8001L, 0L, 5L, null);
        List<AiChatMessage> messages = List.of(
                message(1L, "user", "第一条"),
                message(2L, "assistant", "第二条"),
                message(3L, "event", "第三条"),
                message(4L, "user", "第四条保留原文"),
                message(5L, "assistant", "第五条保留原文")
        );
        prepareSuccessfulGeneration(current, messages, "第一轮摘要", 1);
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);

        boolean updated = service.summarizeIfNecessary(current);

        assertTrue(updated);
        verify(summaryGenerator).summarize(input.capture());
        assertTrue(input.getValue().contains("旧摘要：" + System.lineSeparator() + "暂无"));
        assertTrue(input.getValue().contains("[用户] 第一条"));
        assertTrue(input.getValue().contains("[助手] 第二条"));
        assertTrue(input.getValue().contains("[业务事件] 第三条"));
        assertFalse(input.getValue().contains("第四条保留原文"));
        assertFalse(input.getValue().contains("第五条保留原文"));
        verify(chatSessionMapper).updateSummaryCas(
                eq(10L), eq(0L), eq(3L), eq("第一轮摘要"),
                eq("summary-model"), eq("summary-prompt-v1"), any(Date.class));
    }

    @Test
    void summarizeIfNecessary_incrementalSummary_shouldIncludeOldSummaryAndOnlyNewBatch() {
        AiChatSession current = session(10L, 8001L, 3L, 7L, "已有摘要内容");
        List<AiChatMessage> messages = List.of(
                message(4L, "user", "新增第四条"),
                message(5L, "assistant", "新增第五条"),
                message(6L, "user", "第六条保留原文"),
                message(7L, "assistant", "第七条保留原文")
        );
        prepareSuccessfulGeneration(current, messages, "增量摘要", 1);
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);

        boolean updated = service.summarizeIfNecessary(current);

        assertTrue(updated);
        verify(summaryGenerator).summarize(input.capture());
        assertTrue(input.getValue().contains("已有摘要内容"));
        assertTrue(input.getValue().contains("新增第四条"));
        assertTrue(input.getValue().contains("新增第五条"));
        assertFalse(input.getValue().contains("第六条保留原文"));
        assertFalse(input.getValue().contains("第七条保留原文"));
        verify(chatSessionMapper).updateSummaryCas(
                eq(10L), eq(3L), eq(5L), eq("增量摘要"),
                eq("summary-model"), eq("summary-prompt-v1"), any(Date.class));
    }

    @Test
    void summarizeIfNecessary_sensitiveAndLongSummary_shouldMaskAndTruncateBeforeSaving() {
        properties.getWorkingMemory().setSummaryMaxChars(120);
        AiChatSession current = session(10L, 8001L, 0L, 4L, null);
        List<AiChatMessage> messages = fourMessages();
        String generated = "password=Secret123 token:abc-token tester@example.com 13812345678 "
                + "摘要正文".repeat(80);
        prepareSuccessfulGeneration(current, messages, generated, 1);
        ArgumentCaptor<String> savedSummary = ArgumentCaptor.forClass(String.class);

        boolean updated = service.summarizeIfNecessary(current);

        assertTrue(updated);
        verify(chatSessionMapper).updateSummaryCas(
                eq(10L), eq(0L), eq(2L), savedSummary.capture(),
                eq("summary-model"), eq("summary-prompt-v1"), any(Date.class));
        assertEquals(120, savedSummary.getValue().length());
        assertFalse(savedSummary.getValue().contains("Secret123"));
        assertFalse(savedSummary.getValue().contains("abc-token"));
        assertFalse(savedSummary.getValue().contains("tester@example.com"));
        assertFalse(savedSummary.getValue().contains("13812345678"));
        assertTrue(savedSummary.getValue().contains("password=***"));
        assertTrue(savedSummary.getValue().contains("token=***"));
        assertTrue(savedSummary.getValue().contains("***@***"));
        assertTrue(savedSummary.getValue().contains("1**********"));
    }

    @Test
    void summarizeIfNecessary_generatorThrows_shouldPropagateWithoutAdvancingCursor() {
        AiChatSession current = session(10L, 8001L, 0L, 4L, "旧摘要");
        when(summaryGenerator.isAvailable()).thenReturn(true);
        when(chatSessionService.getById(10L)).thenReturn(current);
        when(chatMessageService.listClosedMessages(10L, 0L, 4L, 104)).thenReturn(fourMessages());
        IllegalStateException failure = new IllegalStateException("model timeout");
        when(summaryGenerator.summarize(any())).thenThrow(failure);

        IllegalStateException actual = assertThrows(
                IllegalStateException.class,
                () -> service.summarizeIfNecessary(current)
        );

        assertSame(failure, actual);
        verify(chatSessionMapper, never()).updateSummaryCas(
                anyLong(), anyLong(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void summarizeIfNecessary_casConflict_shouldReturnFalseInsteadOfOverwritingNewSummary() {
        AiChatSession current = session(10L, 8001L, 0L, 4L, null);
        prepareSuccessfulGeneration(current, fourMessages(), "竞争产生的旧结果", 0);

        boolean updated = service.summarizeIfNecessary(current);

        assertFalse(updated);
        verify(chatSessionMapper).updateSummaryCas(
                eq(10L), eq(0L), eq(2L), eq("竞争产生的旧结果"),
                eq("summary-model"), eq("summary-prompt-v1"), any(Date.class));
    }

    @Test
    void summarizeForContextBudget_belowNormalThreshold_shouldSummarizeThroughTargetMessage() {
        AiChatSession current = session(10L, 8001L, 0L, 3L, null);
        when(summaryGenerator.isAvailable()).thenReturn(true);
        when(chatSessionService.getById(10L)).thenReturn(current);
        when(chatMessageService.listClosedMessages(10L, 0L, 1L, 104)).thenReturn(List.of(
                message(1L, "user", "即将被上下文裁剪的消息")
        ));
        when(summaryGenerator.summarize(any())).thenReturn("预算兜底摘要");
        when(summaryGenerator.modelName()).thenReturn("summary-model");
        when(summaryGenerator.promptVersion()).thenReturn("summary-prompt-v1");
        when(chatSessionMapper.updateSummaryCas(
                eq(10L), eq(0L), eq(1L), eq("预算兜底摘要"),
                eq("summary-model"), eq("summary-prompt-v1"), any(Date.class)))
                .thenReturn(1);

        boolean updated = service.summarizeForContextBudget(current, 1L);

        assertTrue(updated);
        verify(summaryGenerator).summarize(any());
        verify(chatSessionMapper).updateSummaryCas(
                eq(10L), eq(0L), eq(1L), eq("预算兜底摘要"),
                eq("summary-model"), eq("summary-prompt-v1"), any(Date.class));
    }

    private void prepareSuccessfulGeneration(AiChatSession current,
                                             List<AiChatMessage> messages,
                                             String generated,
                                             int casResult) {
        when(summaryGenerator.isAvailable()).thenReturn(true);
        when(chatSessionService.getById(current.getId())).thenReturn(current);
        when(chatMessageService.listClosedMessages(
                current.getId(), current.getLastSummaryMessageId(), current.getLastClosedMessageId(), 104))
                .thenReturn(messages);
        when(summaryGenerator.summarize(any())).thenReturn(generated);
        when(summaryGenerator.modelName()).thenReturn("summary-model");
        when(summaryGenerator.promptVersion()).thenReturn("summary-prompt-v1");
        when(chatSessionMapper.updateSummaryCas(
                eq(current.getId()), eq(current.getLastSummaryMessageId()), anyLong(),
                any(), eq("summary-model"), eq("summary-prompt-v1"), any(Date.class)))
                .thenReturn(casResult);
    }

    private List<AiChatMessage> fourMessages() {
        return List.of(
                message(1L, "user", "第一条"),
                message(2L, "assistant", "第二条"),
                message(3L, "user", "第三条保留原文"),
                message(4L, "assistant", "第四条保留原文")
        );
    }

    private AiChatSession session(long id,
                                  long userId,
                                  long summaryCursor,
                                  long closedCursor,
                                  String summary) {
        AiChatSession session = new AiChatSession();
        session.setId(id);
        session.setUserId(userId);
        session.setLastSummaryMessageId(summaryCursor);
        session.setLastClosedMessageId(closedCursor);
        session.setSummary(summary);
        return session;
    }

    private AiChatMessage message(long id, String role, String content) {
        AiChatMessage message = new AiChatMessage();
        message.setId(id);
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
