package com.mikle.syncup.ai;

import com.mikle.syncup.ai.agent.AiAssistantAgentService;
import com.mikle.syncup.ai.model.dto.AiChatRequest;
import com.mikle.syncup.ai.model.entity.AiChatMessage;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.ai.model.vo.AiChatResponseVO;
import com.mikle.syncup.ai.service.AiChatMessageService;
import com.mikle.syncup.ai.service.AiChatSessionService;
import com.mikle.syncup.ai.service.AiMemoryPipelineService;
import com.mikle.syncup.ai.service.AiToolExecutionService;
import com.mikle.syncup.ai.service.impl.AiChatServiceImpl;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    @Mock private UserService userService;
    @Mock private AiAssistantAgentService agentService;
    @Mock private AiChatMessageService messageService;
    @Mock private AiChatSessionService sessionService;
    @Mock private AiMemoryPipelineService memoryPipelineService;
    @Mock private AiToolExecutionService toolExecutionService;
    @Mock private HttpServletRequest httpRequest;

    private AiChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        chatService = new AiChatServiceImpl();
        ReflectionTestUtils.setField(chatService, "userService", userService);
        ReflectionTestUtils.setField(chatService, "aiAssistantAgentService", agentService);
        ReflectionTestUtils.setField(chatService, "aiChatMessageService", messageService);
        ReflectionTestUtils.setField(chatService, "aiChatSessionService", sessionService);
        ReflectionTestUtils.setField(chatService, "memoryPipelineService", memoryPipelineService);
        ReflectionTestUtils.setField(chatService, "aiToolExecutionService", toolExecutionService);
    }

    @Test
    void chat_agentReturnsResponse_shouldSaveBothMessagesAndCloseTurnInOrder() {
        User user = user(1001L);
        AiChatSession session = session(2001L, "session-1");
        AiChatMessage assistantMessage = message(3001L);
        AiChatResponseVO agentResponse = new AiChatResponseVO();
        agentResponse.setReply("找到两个合适的队伍。");
        when(userService.getLoginUser(httpRequest)).thenReturn(user);
        when(sessionService.getOrCreate(user.getId(), "session-1")).thenReturn(session);
        when(agentService.chat("找羽毛球搭子", session, user))
                .thenReturn(Optional.of(agentResponse));
        when(messageService.saveAssistantMessage(user, session, agentResponse.getReply(), agentResponse))
                .thenReturn(assistantMessage);

        AiChatResponseVO response = chatService.chat(request("找羽毛球搭子", "  session-1  "), httpRequest);

        Assertions.assertSame(agentResponse, response);
        Assertions.assertEquals("session-1", response.getSessionId());
        InOrder order = inOrder(userService, sessionService, messageService, agentService, memoryPipelineService);
        order.verify(userService).getLoginUser(httpRequest);
        order.verify(sessionService).getOrCreate(user.getId(), "session-1");
        order.verify(messageService).saveUserMessage(user, session, "找羽毛球搭子");
        order.verify(agentService).chat("找羽毛球搭子", session, user);
        order.verify(messageService).saveAssistantMessage(user, session, agentResponse.getReply(), agentResponse);
        order.verify(memoryPipelineService).onChatTurnCompleted(user.getId(), session, assistantMessage.getId());
    }

    @Test
    void chat_withoutSessionId_shouldGenerateOneConsistentUuid() {
        User user = user(1001L);
        AiChatMessage assistantMessage = message(3001L);
        when(userService.getLoginUser(httpRequest)).thenReturn(user);
        when(sessionService.getOrCreate(eq(user.getId()), anyString())).thenAnswer(invocation -> {
            String generatedSessionId = invocation.getArgument(1);
            return session(2001L, generatedSessionId);
        });
        when(agentService.chat(eq("你好"), any(AiChatSession.class), eq(user))).thenReturn(Optional.empty());
        when(messageService.saveAssistantMessage(eq(user), any(AiChatSession.class), anyString(), any()))
                .thenReturn(assistantMessage);

        AiChatResponseVO response = chatService.chat(request("你好", null), httpRequest);

        Assertions.assertDoesNotThrow(() -> UUID.fromString(response.getSessionId()));
        verify(sessionService).getOrCreate(user.getId(), response.getSessionId());
        verify(messageService).saveUserMessage(
                eq(user),
                ArgumentMatchers.argThat(value -> response.getSessionId().equals(value.getSessionKey())),
                eq("你好")
        );
    }

    @Test
    void chat_agentUnavailable_shouldSaveFallbackAssistantMessageAndCloseTurn() {
        User user = user(1001L);
        AiChatSession session = session(2001L, "session-1");
        AiChatMessage assistantMessage = message(3001L);
        when(userService.getLoginUser(httpRequest)).thenReturn(user);
        when(sessionService.getOrCreate(user.getId(), "session-1")).thenReturn(session);
        when(agentService.chat("你好", session, user)).thenReturn(Optional.empty());
        when(messageService.saveAssistantMessage(eq(user), eq(session), anyString(), any()))
                .thenReturn(assistantMessage);

        AiChatResponseVO response = chatService.chat(request("你好", "session-1"), httpRequest);

        Assertions.assertEquals("AI 助手暂时不可用，请稍后再试。", response.getReply());
        Assertions.assertFalse(response.isNeedClarification());
        verify(messageService).saveUserMessage(user, session, "你好");
        verify(messageService).saveAssistantMessage(user, session, response.getReply(), response);
        verify(memoryPipelineService).onChatTurnCompleted(user.getId(), session, assistantMessage.getId());
    }

    @Test
    void chat_blankMessage_shouldRejectBeforeAnyDependencyCall() {
        BusinessException nullRequest = Assertions.assertThrows(
                BusinessException.class,
                () -> chatService.chat(null, httpRequest)
        );
        BusinessException blankMessage = Assertions.assertThrows(
                BusinessException.class,
                () -> chatService.chat(request("   ", "session-1"), httpRequest)
        );

        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), nullRequest.getCode());
        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), blankMessage.getCode());
        verifyNoInteractions(userService, sessionService, messageService, agentService, memoryPipelineService);
    }

    @Test
    void chat_messageOverLimit_shouldRejectBeforeLoginAndPersistence() {
        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> chatService.chat(request("a".repeat(501), "session-1"), httpRequest)
        );

        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        verify(userService, never()).getLoginUser(any());
        verifyNoInteractions(sessionService, messageService, agentService, memoryPipelineService);
    }

    private AiChatRequest request(String message, String sessionId) {
        AiChatRequest request = new AiChatRequest();
        request.setMessage(message);
        request.setSessionId(sessionId);
        return request;
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private AiChatSession session(long id, String sessionKey) {
        AiChatSession session = new AiChatSession();
        session.setId(id);
        session.setSessionKey(sessionKey);
        return session;
    }

    private AiChatMessage message(long id) {
        AiChatMessage message = new AiChatMessage();
        message.setId(id);
        return message;
    }
}
