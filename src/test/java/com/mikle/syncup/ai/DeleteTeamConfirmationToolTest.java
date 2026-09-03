package com.mikle.syncup.ai;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.vo.AiTeamDeleteConfirmationVO;
import com.mikle.syncup.ai.tool.DeleteTeamConfirmationTool;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.Team;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.domain.UserTeam;
import com.mikle.syncup.service.TeamService;
import com.mikle.syncup.service.UserTeamService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteTeamConfirmationToolTest {

    @InjectMocks
    private DeleteTeamConfirmationTool tool;

    @Mock
    private TeamService teamService;

    @Mock
    private UserTeamService userTeamService;

    @Test
    void execute_ownedTeam_shouldReturnConfirmationWithoutDeletingTeam() {
        User owner = user(1001L);
        Team team = team(2001L, owner.getId());
        when(teamService.getById(team.getId())).thenReturn(team);
        when(userTeamService.count(org.mockito.ArgumentMatchers.<Wrapper<UserTeam>>any())).thenReturn(3L);

        AiToolResult result = tool.execute(intent(team.getId()), owner);

        AiTeamDeleteConfirmationVO confirmation =
                Assertions.assertInstanceOf(AiTeamDeleteConfirmationVO.class, result.getData());
        Assertions.assertAll(
                () -> Assertions.assertTrue(result.isSuccess()),
                () -> Assertions.assertEquals("draft", result.getType()),
                () -> Assertions.assertEquals(team.getId(), confirmation.getTeamId()),
                () -> Assertions.assertEquals(team.getName(), confirmation.getName()),
                () -> Assertions.assertEquals(team.getActivityType(), confirmation.getActivityType()),
                () -> Assertions.assertEquals(team.getCity(), confirmation.getCity()),
                () -> Assertions.assertEquals(3, confirmation.getHasJoinNum()),
                () -> Assertions.assertTrue(confirmation.getWarning().contains("删除"))
        );
        verify(teamService, never()).deleteTeam(anyLong(), any(User.class));
    }

    @Test
    void execute_teamOwnedByAnotherUser_shouldReturnFailure() {
        User currentUser = user(1001L);
        Team team = team(2001L, 1002L);
        when(teamService.getById(team.getId())).thenReturn(team);

        AiToolResult result = tool.execute(intent(team.getId()), currentUser);

        Assertions.assertFalse(result.isSuccess());
        Assertions.assertNull(result.getData());
        Assertions.assertTrue(result.getSummary().contains("只能删除自己创建的队伍"));
        verify(userTeamService, never()).count(org.mockito.ArgumentMatchers.<Wrapper<UserTeam>>any());
        verify(teamService, never()).deleteTeam(anyLong(), any(User.class));
    }

    @Test
    void execute_missingTeamId_shouldReturnFailureWithoutQueryingDatabase() {
        AiToolResult nullIntentResult = tool.execute(null, user(1001L));
        AiToolResult invalidIdResult = tool.execute(intent(0L), user(1001L));

        Assertions.assertFalse(nullIntentResult.isSuccess());
        Assertions.assertFalse(invalidIdResult.isSuccess());
        Assertions.assertTrue(nullIntentResult.getSummary().contains("明确要删除的队伍"));
        verify(teamService, never()).getById(any(Long.class));
    }

    @Test
    void execute_teamDoesNotExist_shouldReturnFailure() {
        when(teamService.getById(2001L)).thenReturn(null);

        AiToolResult result = tool.execute(intent(2001L), user(1001L));

        Assertions.assertFalse(result.isSuccess());
        Assertions.assertNull(result.getData());
        Assertions.assertTrue(result.getSummary().contains("没有找到"));
        verify(teamService, never()).deleteTeam(anyLong(), any(User.class));
    }

    @Test
    void execute_withoutLoginUser_shouldRejectRequest() {
        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> tool.execute(intent(2001L), null)
        );

        Assertions.assertEquals(ErrorCode.NOT_LOGIN.getCode(), exception.getCode());
        verify(teamService, never()).getById(any(Long.class));
    }

    private TeamIntent intent(long teamId) {
        TeamIntent intent = new TeamIntent();
        intent.setTeamId(teamId);
        return intent;
    }

    private User user(long userId) {
        User user = new User();
        user.setId(userId);
        return user;
    }

    private Team team(long teamId, long ownerId) {
        Team team = new Team();
        team.setId(teamId);
        team.setUserId(ownerId);
        team.setName("周末羽毛球");
        team.setDescription("新手友好");
        team.setActivityCategory(1);
        team.setActivityType("羽毛球");
        team.setCity("西安");
        team.setDistrict("雁塔区");
        team.setStartTime(new Date(System.currentTimeMillis() + 86_400_000L));
        team.setMaxNum(4);
        return team;
    }

}
