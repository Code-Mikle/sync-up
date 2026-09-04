package com.mikle.syncup.ai;

import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.agent.UserIntent;
import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.tool.AiTool;
import com.mikle.syncup.ai.tool.AiToolRegistry;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiToolRegistryTest {

    @Mock
    private AiTool<TeamIntent> teamTool;

    private AiToolRegistry registry;

    @BeforeEach
    void setUp() {
        when(teamTool.name()).thenReturn("search_teams");
        registry = new AiToolRegistry(List.of(teamTool));
    }

    @Test
    void execute_registeredToolWithMatchingIntent_shouldDelegateAndReturnResult() {
        TeamIntent intent = new TeamIntent();
        User loginUser = user(1001L);
        AiToolResult expected = AiToolResult.success("search_teams", "read", "found", List.of());
        when(teamTool.intentType()).thenReturn(TeamIntent.class);
        when(teamTool.execute(intent, loginUser)).thenReturn(expected);

        AiToolResult actual = registry.execute("search_teams", intent, loginUser);

        assertSame(expected, actual);
        verify(teamTool).execute(intent, loginUser);
    }

    @Test
    void execute_unknownTool_shouldRejectBeforeCallingAnyTool() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> registry.execute("delete_everything", new TeamIntent(), user(1001L))
        );

        assertEquals(ErrorCode.NO_AUTH.getCode(), exception.getCode());
        verify(teamTool, never()).execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void execute_intentTypeDoesNotMatchTool_shouldRejectBeforeCallingTool() {
        when(teamTool.intentType()).thenReturn(TeamIntent.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> registry.execute("search_teams", new UserIntent(), user(1001L))
        );

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        verify(teamTool, never()).execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
