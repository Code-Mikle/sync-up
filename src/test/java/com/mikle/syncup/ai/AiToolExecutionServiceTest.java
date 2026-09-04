package com.mikle.syncup.ai;

import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.service.AiToolCallLogService;
import com.mikle.syncup.ai.service.impl.AiToolExecutionServiceImpl;
import com.mikle.syncup.ai.tool.AiToolRegistry;
import com.mikle.syncup.model.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiToolExecutionServiceTest {

    @Mock
    private AiToolRegistry registry;

    @Mock
    private AiToolCallLogService logService;

    private AiToolExecutionServiceImpl service;

    private User loginUser;

    @BeforeEach
    void setUp() {
        service = new AiToolExecutionServiceImpl();
        ReflectionTestUtils.setField(service, "aiToolRegistry", registry);
        ReflectionTestUtils.setField(service, "aiToolCallLogService", logService);
        loginUser = new User();
        loginUser.setId(2001L);
    }

    @Test
    void execute_toolSucceeds_shouldReturnSameResultAndRecordSuccess() {
        TeamIntent intent = new TeamIntent();
        intent.setCity("西安");
        AiToolResult expected = AiToolResult.success("search_teams", "read", "找到队伍", List.of());
        when(registry.execute("search_teams", intent, loginUser)).thenReturn(expected);

        AiToolResult actual = service.execute("search_teams", intent, loginUser, "session-1");

        assertSame(expected, actual);
        InOrder order = inOrder(registry, logService);
        order.verify(registry).execute("search_teams", intent, loginUser);
        order.verify(logService).recordToolCall(
                eq("session-1"), eq(loginUser), eq("search_teams"), eq("success"),
                contains("city=西安"), contains("success=true"), isNull(), anyLong()
        );
    }

    @Test
    void execute_toolReturnsBusinessFailure_shouldReturnFailureAndRecordFailed() {
        TeamIntent intent = new TeamIntent();
        AiToolResult expected = AiToolResult.failure("search_teams", "read", "筛选条件不完整");
        when(registry.execute("search_teams", intent, loginUser)).thenReturn(expected);

        AiToolResult actual = service.execute("search_teams", intent, loginUser, "session-2");

        assertSame(expected, actual);
        verify(logService).recordToolCall(
                eq("session-2"), eq(loginUser), eq("search_teams"), eq("failed"),
                contains("createTeamRequested=false"), contains("success=false"),
                eq("筛选条件不完整"), anyLong()
        );
    }

    @Test
    void execute_toolThrows_shouldRecordFailureAndRethrowSameException() {
        TeamIntent intent = new TeamIntent();
        RuntimeException failure = new IllegalStateException("tool unavailable");
        when(registry.execute("search_teams", intent, loginUser)).thenThrow(failure);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> service.execute("search_teams", intent, loginUser, "session-3")
        );

        assertSame(failure, actual);
        verify(logService).recordToolCall(
                eq("session-3"), eq(loginUser), eq("search_teams"), eq("failed"),
                contains("createTeamRequested=false"), isNull(), eq("tool unavailable"), anyLong()
        );
    }

    @Test
    void execute_teamIntentContainsPassword_shouldAuditOnlyPasswordPresence() {
        TeamIntent intent = new TeamIntent();
        intent.setSourceText("加入队伍，密码是 source-secret");
        intent.setTeamPassword("raw-secret-password");
        AiToolResult result = AiToolResult.success("team_details", "read", "ok", null);
        when(registry.execute("team_details", intent, loginUser)).thenReturn(result);
        ArgumentCaptor<String> argumentsSummary = ArgumentCaptor.forClass(String.class);

        service.execute("team_details", intent, loginUser, "session-4");

        verify(logService).recordToolCall(
                eq("session-4"), eq(loginUser), eq("team_details"), eq("success"),
                argumentsSummary.capture(), contains("success=true"), isNull(), anyLong()
        );
        assertTrue(argumentsSummary.getValue().contains("hasTeamPassword=true"));
        assertFalse(argumentsSummary.getValue().contains("raw-secret-password"));
        assertFalse(argumentsSummary.getValue().contains("source-secret"));
    }
}
