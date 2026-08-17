package com.mikle.syncup.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.agent.AiAgentToolContext;
import com.mikle.syncup.ai.agent.AiAssistantTools;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.vo.AiUiBlockVO;
import com.mikle.syncup.ai.service.AiToolExecutionService;
import com.mikle.syncup.ai.tool.GetMyProfileTool;
import com.mikle.syncup.model.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantToolsPresentationTest {

    @Mock
    private AiToolExecutionService aiToolExecutionService;

    private AiAgentToolContext context;

    private AiAssistantTools tools;

    @BeforeEach
    void setUp() {
        context = new AiAgentToolContext();
        tools = new AiAssistantTools();
        ReflectionTestUtils.setField(tools, "aiAgentToolContext", context);
        ReflectionTestUtils.setField(tools, "aiToolExecutionService", aiToolExecutionService);
        ReflectionTestUtils.setField(tools, "objectMapper", new ObjectMapper());

        User user = new User();
        user.setId(10001L);
        context.start("presentation-test", user);

        AiToolResult profileResult = AiToolResult.success(
                GetMyProfileTool.TOOL_NAME,
                "read",
                "profile loaded",
                Map.of("id", 10001L, "username", "tester", "gender", 1)
        );
        when(aiToolExecutionService.execute(
                eq(GetMyProfileTool.TOOL_NAME),
                any(TeamIntent.class),
                eq(user),
                eq("presentation-test")
        )).thenReturn(profileResult);
    }

    @AfterEach
    void tearDown() {
        context.clear();
    }

    @Test
    void getMyProfile_shouldOnlyProvideDataForTextReply() {
        tools.getMyProfile();

        assertTrue(context.snapshot().getUiBlocks().isEmpty());
    }

    @Test
    void showMyProfileCard_shouldCreateExplicitProfileCardBlock() {
        tools.showMyProfileCard();

        assertEquals(1, context.snapshot().getUiBlocks().size());
        assertEquals(AiUiBlockVO.PROFILE_CARD, context.snapshot().getUiBlocks().get(0).getType());
    }
}
