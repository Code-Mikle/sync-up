package com.mikle.syncup.ai;

import com.mikle.syncup.ai.agent.AiAgentToolContext;
import com.mikle.syncup.ai.agent.AiAssistantTools;
import com.mikle.syncup.ai.agent.impl.AiAssistantAgentServiceImpl;
import com.mikle.syncup.ai.config.AiAgentProperties;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.ai.model.vo.AiChatResponseVO;
import com.mikle.syncup.ai.service.WorkingMemoryService;
import com.mikle.syncup.model.domain.User;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantAgentServiceTest {

    @Mock private WorkingMemoryService workingMemoryService;
    @Mock private ChatModel chatModel;

    private AiAgentProperties properties;
    private AiAgentToolContext toolContext;
    private AiAssistantAgentServiceImpl agentService;

    @BeforeEach
    void setUp() {
        properties = new AiAgentProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        properties.setMaxInputLength(1000);
        properties.setMaxToolCalls(3);
        toolContext = new AiAgentToolContext();
        agentService = new AiAssistantAgentServiceImpl();
        ReflectionTestUtils.setField(agentService, "aiAgentProperties", properties);
        ReflectionTestUtils.setField(agentService, "aiAssistantTools", new AiAssistantTools());
        ReflectionTestUtils.setField(agentService, "aiAgentToolContext", toolContext);
        ReflectionTestUtils.setField(agentService, "workingMemoryService", workingMemoryService);
        ReflectionTestUtils.setField(agentService, "chatModel", chatModel);
    }

    @Test
    void chat_agentDisabled_shouldReturnEmptyWithoutBuildingContextOrCallingModel() {
        properties.setEnabled(false);

        Optional<AiChatResponseVO> response = agentService.chat("你好", session(), user());

        Assertions.assertTrue(response.isEmpty());
        verifyNoInteractions(workingMemoryService, chatModel);
        assertContextCleared();
    }

    @Test
    void chat_invalidInput_shouldReturnEmptyBeforeBuildingModelContext() {
        Assertions.assertTrue(agentService.chat("   ", session(), user()).isEmpty());
        Assertions.assertTrue(agentService.chat("a".repeat(1001), session(), user()).isEmpty());

        verifyNoInteractions(workingMemoryService, chatModel);
        assertContextCleared();
    }

    @Test
    void chat_modelReturnsText_shouldReturnResponseAndClearContext() {
        AiChatSession session = session();
        User user = user();
        when(workingMemoryService.buildModelContext(session, user, "找羽毛球搭子"))
                .thenReturn("测试模型上下文");
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response("找到两个合适的搭子。"));

        Optional<AiChatResponseVO> result = agentService.chat("找羽毛球搭子", session, user);

        Assertions.assertTrue(result.isPresent());
        AiChatResponseVO value = result.orElseThrow();
        Assertions.assertEquals("找到两个合适的搭子。", value.getReply());
        Assertions.assertEquals(session.getSessionKey(), value.getSessionId());
        Assertions.assertEquals("找羽毛球搭子", value.getIntent().getSourceText());
        Assertions.assertFalse(value.getIntent().isTeamRelated());
        verify(chatModel).chat(any(ChatRequest.class));
        assertContextCleared();
    }

    @Test
    void chat_normalModelFailure_shouldFallbackWithoutRetryAndClearContext() {
        prepareContext();
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("provider timeout"));

        Optional<AiChatResponseVO> result = agentService.chat("你好", session(), user());

        Assertions.assertTrue(result.isEmpty());
        verify(chatModel).chat(any(ChatRequest.class));
        assertContextCleared();
    }

    @Test
    void chat_invalidToolArguments_shouldReturnSpecificErrorToModelAndAllowCorrection() {
        prepareContext();
        ToolExecutionRequest invalidRequest = ToolExecutionRequest.builder()
                .id("tool-call-1")
                .name("search_teams")
                .arguments("{\"activityCategory\":\"not-a-number\"}")
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(toolRequestResponse(invalidRequest))
                .thenReturn(response("参数修正后成功"));

        Optional<AiChatResponseVO> result = agentService.chat("你好", session(), user());

        Assertions.assertEquals("参数修正后成功", result.orElseThrow().getReply());
        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(2)).chat(requestCaptor.capture());
        ToolExecutionResultMessage errorMessage = requestCaptor.getAllValues().get(1).messages().stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .findFirst()
                .orElseThrow();
        Assertions.assertAll(
                () -> Assertions.assertEquals("search_teams", errorMessage.toolName()),
                () -> Assertions.assertEquals(Boolean.TRUE, errorMessage.isError()),
                () -> Assertions.assertTrue(errorMessage.text().contains("工具 'search_teams' 的参数无效")),
                () -> Assertions.assertTrue(errorMessage.text().contains("严格按照该工具的参数定义修正"))
        );
        assertContextCleared();
    }

    private void prepareContext() {
        when(workingMemoryService.buildModelContext(any(AiChatSession.class), any(User.class), any()))
                .thenReturn("测试模型上下文");
    }

    private ChatResponse response(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }

    private ChatResponse toolRequestResponse(ToolExecutionRequest request) {
        return ChatResponse.builder().aiMessage(AiMessage.from(List.of(request))).build();
    }

    private AiChatSession session() {
        AiChatSession session = new AiChatSession();
        session.setId(2001L);
        session.setSessionKey("session-1");
        return session;
    }

    private User user() {
        User user = new User();
        user.setId(1001L);
        return user;
    }

    private void assertContextCleared() {
        Assertions.assertThrows(IllegalStateException.class, toolContext::getRequired);
    }

}
