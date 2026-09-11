package com.mikle.syncup.ai.memory;

import com.mikle.syncup.ai.config.AiMemoryProperties;
import com.mikle.syncup.ai.model.entity.AiChatMessage;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.ai.service.chat.AiChatMessageService;
import com.mikle.syncup.ai.service.chat.AiChatSessionService;
import com.mikle.syncup.ai.service.profile.AiUserProfileService;
import com.mikle.syncup.ai.service.memory.SessionSummaryService;
import com.mikle.syncup.ai.service.memory.impl.WorkingMemoryServiceImpl;
import com.mikle.syncup.model.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkingMemoryServiceTest {

    @Mock
    private AiChatMessageService chatMessageService;

    @Mock
    private AiChatSessionService chatSessionService;

    @Mock
    private SessionSummaryService sessionSummaryService;

    @Mock
    private AiUserProfileService userProfileService;

    private WorkingMemoryServiceImpl service;

    private AiMemoryProperties properties;

    private User loginUser;

    @BeforeEach
    void setUp() {
        properties = new AiMemoryProperties();
        properties.getWorkingMemory().setRecentMessageCount(3);
        properties.getWorkingMemory().setMaxContextTokens(1000);

        service = new WorkingMemoryServiceImpl();
        ReflectionTestUtils.setField(service, "chatMessageService", chatMessageService);
        ReflectionTestUtils.setField(service, "chatSessionService", chatSessionService);
        ReflectionTestUtils.setField(service, "sessionSummaryService", sessionSummaryService);
        ReflectionTestUtils.setField(service, "userProfileService", userProfileService);
        ReflectionTestUtils.setField(service, "memoryProperties", properties);

        loginUser = new User();
        loginUser.setId(7001L);
    }

    @Test
    void buildModelContext_withoutSession_shouldContainTimeAndCurrentQuestionWithoutDependencies() {
        String currentQuestion = "这个周末想找羽毛球搭子";

        String context = service.buildModelContext(null, loginUser, currentQuestion);

        assertTrue(context.contains("当前服务端时间："));
        assertTrue(context.contains("当前用户原始需求：" + currentQuestion));
        assertEquals(context.indexOf(currentQuestion), context.lastIndexOf(currentQuestion));
        verifyNoInteractions(chatMessageService, userProfileService);
    }

    @Test
    void buildModelContext_withSession_shouldComposeProfileSummaryMessagesAndQuestion() {
        AiChatSession session = session(10L, 30L, "用户此前想参加周末活动");
        when(chatMessageService.listLatestClosedMessages(10L, 30L, 3)).thenReturn(List.of(
                message("user", "之前想打羽毛球"),
                message("assistant", "可以帮你查找队伍"),
                message("event", "用户已经确认创建队伍")
        ));
        when(userProfileService.getInteractionProfileText(7001L)).thenReturn("回答保持简洁");
        String currentQuestion = "现在想看看附近的队伍";

        String context = service.buildModelContext(session, loginUser, currentQuestion);

        assertTrue(context.contains("内部交流偏好（仅用于调整表达方式，禁止向用户展示或复述）："));
        assertTrue(context.contains("回答保持简洁"));
        assertTrue(context.contains("当前会话摘要："));
        assertTrue(context.contains("用户此前想参加周末活动"));
        assertTrue(context.contains("[用户] 之前想打羽毛球"));
        assertTrue(context.contains("[助手] 可以帮你查找队伍"));
        assertTrue(context.contains("[业务事件] 用户已经确认创建队伍"));
        assertTrue(context.endsWith("当前用户原始需求：" + currentQuestion));
        assertEquals(context.indexOf(currentQuestion), context.lastIndexOf(currentQuestion));
    }

    @Test
    void buildModelContext_shouldReadOnlyConfiguredClosedMessageRange() {
        AiChatSession session = session(11L, null, null);
        when(chatMessageService.listLatestClosedMessages(11L, 0L, 3)).thenReturn(List.of());
        when(userProfileService.getInteractionProfileText(7001L)).thenReturn(null);

        service.buildModelContext(session, loginUser, "当前问题");

        verify(chatMessageService).listLatestClosedMessages(11L, 0L, 3);
    }

    @Test
    void buildModelContext_blankOptionalMemory_shouldOmitEmptySections() {
        AiChatSession session = session(12L, 5L, " ");
        when(chatMessageService.listLatestClosedMessages(12L, 5L, 3)).thenReturn(List.of());
        when(userProfileService.getInteractionProfileText(7001L)).thenReturn(" ");

        String context = service.buildModelContext(session, loginUser, "当前问题");

        assertFalse(context.contains("内部交流偏好"));
        assertFalse(context.contains("当前会话摘要"));
        assertFalse(context.contains("当前会话近期原始消息"));
        assertTrue(context.endsWith("当前用户原始需求：当前问题"));
    }

    @Test
    void buildModelContext_overBudget_shouldDropOldestMessagesAndKeepLatestAndQuestion() {
        properties.getWorkingMemory().setMaxContextTokens(500);
        AiChatSession session = session(13L, 9L, "已覆盖消息 1 至 9");
        session.setLastSummaryMessageId(9L);
        when(chatMessageService.listLatestClosedMessages(13L, 9L, 3)).thenReturn(List.of(
                message(7L, "user", "最早消息标记" + "旧".repeat(600)),
                message(8L, "assistant", "中间消息标记" + "中".repeat(450)),
                message(9L, "user", "最新消息标记")
        ));
        when(userProfileService.getInteractionProfileText(7001L)).thenReturn(null);

        String context = service.buildModelContext(session, loginUser, "预算裁剪后的当前问题");

        assertFalse(context.contains("最早消息标记"));
        assertFalse(context.contains("中间消息标记"));
        assertTrue(context.contains("最新消息标记"));
        assertTrue(context.endsWith("当前用户原始需求：预算裁剪后的当前问题"));
        verify(chatMessageService, never()).listClosedMessages(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt());
        verifyNoInteractions(sessionSummaryService, chatSessionService);
    }

    @Test
    void buildModelContext_overBudgetWouldDropUnsummarizedMessages_shouldSummarizeAndReloadSession() {
        properties.getWorkingMemory().setMaxContextTokens(500);
        AiChatSession session = session(14L, 3L, "旧摘要");
        session.setLastSummaryMessageId(0L);
        when(chatMessageService.listLatestClosedMessages(14L, 3L, 3)).thenReturn(List.of(
                message(1L, "user", "最早未摘要消息" + "旧".repeat(600)),
                message(2L, "assistant", "中间未摘要消息" + "中".repeat(450)),
                message(3L, "user", "最新消息")
        ));
        when(userProfileService.getInteractionProfileText(7001L)).thenReturn(null);
        when(sessionSummaryService.summarizeForContextBudget(session, 2L)).thenReturn(true);
        AiChatSession refreshed = session(14L, 3L, "补充后的摘要");
        refreshed.setLastSummaryMessageId(2L);
        when(chatSessionService.getById(14L)).thenReturn(refreshed);

        String context = service.buildModelContext(session, loginUser, "继续当前问题");

        verify(sessionSummaryService).summarizeForContextBudget(session, 2L);
        verify(chatSessionService).getById(14L);
        assertTrue(context.contains("当前会话摘要："));
        assertTrue(context.contains("补充后的摘要"));
        assertFalse(context.contains("旧摘要"));
        assertFalse(context.contains("最早未摘要消息"));
        assertFalse(context.contains("中间未摘要消息"));
        assertTrue(context.contains("最新消息"));
    }

    @Test
    void buildModelContext_emergencySummaryDoesNotAdvanceCursor_shouldKeepUnsummarizedMessages() {
        properties.getWorkingMemory().setMaxContextTokens(500);
        AiChatSession session = session(15L, 3L, null);
        session.setLastSummaryMessageId(0L);
        when(chatMessageService.listLatestClosedMessages(15L, 3L, 3)).thenReturn(List.of(
                message(1L, "user", "未摘要消息一" + "甲".repeat(600)),
                message(2L, "assistant", "未摘要消息二" + "乙".repeat(450)),
                message(3L, "user", "最新消息")
        ));
        when(userProfileService.getInteractionProfileText(7001L)).thenReturn(null);
        when(sessionSummaryService.summarizeForContextBudget(session, 2L)).thenReturn(false);
        when(chatSessionService.getById(15L)).thenReturn(session);

        String context = service.buildModelContext(session, loginUser, "继续当前问题");

        assertTrue(context.contains("未摘要消息一"));
        assertTrue(context.contains("未摘要消息二"));
        assertTrue(context.contains("最新消息"));
    }

    private AiChatSession session(long id, Long lastClosedMessageId, String summary) {
        AiChatSession session = new AiChatSession();
        session.setId(id);
        session.setUserId(loginUser.getId());
        session.setLastClosedMessageId(lastClosedMessageId);
        session.setSummary(summary);
        return session;
    }

    private AiChatMessage message(String role, String content) {
        return message(null, role, content);
    }

    private AiChatMessage message(Long id, String role, String content) {
        AiChatMessage message = new AiChatMessage();
        message.setId(id);
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
