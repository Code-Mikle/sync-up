package com.mikle.syncup.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mikle.syncup.ai.model.dto.AiTeamDetailsRequest;
import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.service.AiChatService;
import com.mikle.syncup.ai.service.AiMemoryPipelineService;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.mapper.TeamMapper;
import com.mikle.syncup.mapper.UserMapper;
import com.mikle.syncup.mapper.UserTeamMapper;
import com.mikle.syncup.model.domain.Team;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.domain.UserTeam;
import com.mikle.syncup.service.TeamService;
import com.mikle.syncup.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class AiChatServiceDeleteTest {

    @Resource
    private AiChatService aiChatService;

    @Resource
    private TeamService teamService;

    @MockitoSpyBean
    private UserService userService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private UserTeamMapper userTeamMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private DataSource dataSource;

    @MockitoBean
    private AiMemoryPipelineService memoryPipelineService;

    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void ensureUsingTestDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            Assertions.assertEquals(
                    "sync_up_test",
                    connection.getCatalog(),
                    "当前连接的不是 sync_up_test，已停止测试，避免污染开发数据库"
            );
        }
    }

    @AfterEach
    void cleanup() {
        for (Long userId : userIds) {
            jdbcTemplate.update("delete from ai_tool_call_log where userId = ?", userId);
            jdbcTemplate.update("delete from ai_episode_extraction_task where userId = ?", userId);
            jdbcTemplate.update("delete from ai_chat_message where userId = ?", userId);
            jdbcTemplate.update("delete from ai_chat_session where userId = ?", userId);
            jdbcTemplate.update("delete from ai_team_draft where userId = ?", userId);
            userTeamMapper.deleteByTeamCreatorUserIdPhysically(userId);
            userTeamMapper.deleteByUserIdPhysically(userId);
            teamMapper.deleteByUserIdPhysically(userId);
            userMapper.deleteByIdPhysically(userId);
        }
        userIds.clear();
    }

    @Test
    void deleteTeam_ownedTeam_shouldDeleteTeamAndRecordEvent() {
        User owner = createUser();
        User member = createUser();
        long teamId = createTeam(owner);
        createMembership(member.getId(), teamId);
        String sessionId = unique("delete-session");
        HttpServletRequest request = requestFor(owner);

        AiToolResult result = aiChatService.deleteTeam(teamId, details(sessionId), request);

        Map<?, ?> data = Assertions.assertInstanceOf(Map.class, result.getData());
        Assertions.assertAll(
                () -> Assertions.assertTrue(result.isSuccess()),
                () -> Assertions.assertEquals(Boolean.TRUE, data.get("deleted")),
                () -> Assertions.assertEquals(teamId, ((Number) data.get("teamId")).longValue()),
                () -> Assertions.assertEquals(0L, countPhysicalTeams(teamId)),
                () -> Assertions.assertEquals(0L, countTeamMemberships(teamId)),
                () -> Assertions.assertEquals(1L, countToolLogs(owner.getId(), teamId, "success")),
                () -> Assertions.assertEquals(1L, countDeleteEvents(owner.getId(), sessionId, teamId))
        );
        verify(memoryPipelineService).onChatTurnCompleted(
                org.mockito.ArgumentMatchers.eq(owner.getId()), any(), anyLong());
    }

    @Test
    void deleteTeam_teamOwnedByAnotherUser_shouldRejectWithoutChangingTeam() {
        User owner = createUser();
        User anotherUser = createUser();
        long teamId = createTeam(owner);
        long originalMemberships = countTeamMemberships(teamId);
        HttpServletRequest request = requestFor(anotherUser);

        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> aiChatService.deleteTeam(teamId, details(unique("delete-session")), request)
        );

        Assertions.assertEquals(ErrorCode.NO_AUTH.getCode(), exception.getCode());
        Assertions.assertEquals(1L, countPhysicalTeams(teamId));
        Assertions.assertEquals(originalMemberships, countTeamMemberships(teamId));
        Assertions.assertEquals(1L, countToolLogs(anotherUser.getId(), teamId, "failed"));
        Assertions.assertEquals(0L, countAllDeleteEvents(anotherUser.getId()));
        verify(memoryPipelineService, never()).onChatTurnCompleted(anyLong(), any(), anyLong());
    }

    @Test
    void deleteTeam_confirmedTwice_shouldRecordOneEventAndRejectSecondRequest() {
        User owner = createUser();
        long teamId = createTeam(owner);
        String sessionId = unique("delete-session");
        HttpServletRequest request = requestFor(owner);

        aiChatService.deleteTeam(teamId, details(sessionId), request);
        BusinessException second = Assertions.assertThrows(
                BusinessException.class,
                () -> aiChatService.deleteTeam(teamId, details(sessionId), request)
        );

        Assertions.assertEquals(ErrorCode.NULL_ERROR.getCode(), second.getCode());
        Assertions.assertEquals(0L, countPhysicalTeams(teamId));
        Assertions.assertEquals(0L, countTeamMemberships(teamId));
        Assertions.assertEquals(1L, countToolLogs(owner.getId(), teamId, "success"));
        Assertions.assertEquals(1L, countToolLogs(owner.getId(), teamId, "failed"));
        Assertions.assertEquals(1L, countDeleteEvents(owner.getId(), sessionId, teamId));
        verify(memoryPipelineService).onChatTurnCompleted(
                org.mockito.ArgumentMatchers.eq(owner.getId()), any(), anyLong());
    }

    @Test
    void deleteTeam_invalidTeamId_shouldRejectWithoutCreatingSessionOrEvent() {
        User owner = createUser();
        String sessionId = unique("delete-session");
        HttpServletRequest request = requestFor(owner);

        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> aiChatService.deleteTeam(0L, details(sessionId), request)
        );

        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        Assertions.assertEquals(1L, countToolLogs(owner.getId(), 0L, "failed"));
        Assertions.assertEquals(0L, countSessions(owner.getId(), sessionId));
        Assertions.assertEquals(0L, countAllDeleteEvents(owner.getId()));
        verify(memoryPipelineService, never()).onChatTurnCompleted(anyLong(), any(), anyLong());
    }

    private User createUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = new User();
        user.setUsername("delete_test");
        user.setUserAccount("delete_" + suffix);
        user.setUserPassword("Password123");
        user.setUserRole(0);
        user.setUserStatus(0);
        Assertions.assertTrue(userService.save(user));
        userIds.add(user.getId());
        return user;
    }

    private long createTeam(User owner) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        Team team = new Team();
        team.setName("AI删除测试" + suffix);
        team.setDescription("用于验证 AI 删除确认入口");
        team.setActivityCategory(1);
        team.setActivityType("羽毛球");
        team.setCity("西安");
        team.setDistrict("雁塔区");
        team.setMaxNum(4);
        team.setStartTime(new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(2)));
        team.setExpireTime(new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3)));
        team.setDurationMinutes(120);
        team.setBudgetPerPerson(new BigDecimal("50.00"));
        team.setSkillLevel("入门");
        return teamService.addTeam(team, owner);
    }

    private void createMembership(long userId, long teamId) {
        UserTeam relation = new UserTeam();
        relation.setUserId(userId);
        relation.setTeamId(teamId);
        relation.setJoinTime(new Date());
        Assertions.assertEquals(1, userTeamMapper.insert(relation));
    }

    private HttpServletRequest requestFor(User loginUser) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        doReturn(loginUser).when(userService).getLoginUser(request);
        return request;
    }

    private AiTeamDetailsRequest details(String sessionId) {
        AiTeamDetailsRequest details = new AiTeamDetailsRequest();
        details.setSessionId(sessionId);
        return details;
    }

    private long countPhysicalTeams(long teamId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from team where id = ?",
                Long.class,
                teamId
        );
        return count == null ? 0 : count;
    }

    private long countTeamMemberships(long teamId) {
        return userTeamMapper.selectCount(new QueryWrapper<UserTeam>().eq("teamId", teamId));
    }

    private long countToolLogs(long userId, long teamId, String status) {
        Long count = jdbcTemplate.queryForObject("""
                        select count(*) from ai_tool_call_log
                        where userId = ? and toolName = 'delete_team' and status = ?
                          and argumentsSummary like ?
                        """,
                Long.class,
                userId,
                status,
                "%teamId=" + teamId + "%"
        );
        return count == null ? 0 : count;
    }

    private long countDeleteEvents(long userId, String sessionId, long teamId) {
        Long count = jdbcTemplate.queryForObject("""
                        select count(*)
                        from ai_chat_message message
                        inner join ai_chat_session session on session.id = message.chatSessionId
                        where message.userId = ? and session.sessionKey = ? and message.role = 'event'
                          and message.content like ?
                        """,
                Long.class,
                userId,
                sessionId,
                "%teamId=" + teamId + "%"
        );
        return count == null ? 0 : count;
    }

    private long countAllDeleteEvents(long userId) {
        Long count = jdbcTemplate.queryForObject("""
                        select count(*) from ai_chat_message
                        where userId = ? and role = 'event' and content like '用户已确认删除队伍%'
                        """,
                Long.class,
                userId
        );
        return count == null ? 0 : count;
    }

    private long countSessions(long userId, String sessionId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from ai_chat_session where userId = ? and sessionKey = ?",
                Long.class,
                userId,
                sessionId
        );
        return count == null ? 0 : count;
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
