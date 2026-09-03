package com.mikle.syncup.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mikle.syncup.ai.exception.DraftExpiredException;
import com.mikle.syncup.ai.mapper.AiTeamDraftMapper;
import com.mikle.syncup.ai.model.entity.AiTeamDraft;
import com.mikle.syncup.ai.model.vo.AiTeamDraftConfirmResponse;
import com.mikle.syncup.ai.model.vo.TeamDraftVO;
import com.mikle.syncup.ai.service.AiMemoryPipelineService;
import com.mikle.syncup.ai.service.AiTeamDraftService;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.mapper.TeamMapper;
import com.mikle.syncup.mapper.UserMapper;
import com.mikle.syncup.mapper.UserTeamMapper;
import com.mikle.syncup.model.domain.Team;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.domain.UserTeam;
import com.mikle.syncup.service.UserService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class AiTeamDraftServiceTest {

    @Resource
    private AiTeamDraftService aiTeamDraftService;

    @Resource
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

    @MockitoSpyBean
    private AiTeamDraftMapper aiTeamDraftMapper;

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
    void saveDraft_validDraft_shouldPersistPendingDraftWithoutCreatingTeam() {
        User owner = createUser();
        String sessionId = unique("session");
        TeamDraftVO source = validDraft(sessionId, owner);

        TeamDraftVO saved = aiTeamDraftService.saveDraft(source, owner, sessionId);

        AiTeamDraft entity = findDraft(saved.getDraftId());
        Assertions.assertAll(
                () -> Assertions.assertNotNull(entity),
                () -> Assertions.assertEquals(owner.getId(), entity.getUserId()),
                () -> Assertions.assertEquals(sessionId, entity.getSessionId()),
                () -> Assertions.assertEquals(0, entity.getStatus()),
                () -> Assertions.assertNull(entity.getConfirmedTeamId()),
                () -> Assertions.assertEquals(0L, countTeams(owner.getId())),
                () -> Assertions.assertEquals(0L, countMemberships(owner.getId()))
        );
    }

    @Test
    void confirmDraft_ownedPendingDraft_shouldCreateOneTeamAndMarkDraftConfirmed() {
        User owner = createUser();
        String sessionId = unique("session");
        TeamDraftVO draft = saveValidDraft(owner, sessionId);

        AiTeamDraftConfirmResponse response = aiTeamDraftService.confirmDraft(draft.getDraftId(), owner);

        AiTeamDraft confirmed = findDraft(draft.getDraftId());
        Team team = teamMapper.selectById(response.getTeamId());
        Assertions.assertAll(
                () -> Assertions.assertEquals("confirmed", response.getStatus()),
                () -> Assertions.assertEquals(draft.getDraftId(), response.getDraftId()),
                () -> Assertions.assertNotNull(team),
                () -> Assertions.assertEquals(owner.getId(), team.getUserId()),
                () -> Assertions.assertEquals(draft.getName(), team.getName()),
                () -> Assertions.assertEquals(draft.getActivityCategory(), team.getActivityCategory()),
                () -> Assertions.assertEquals(draft.getCity(), team.getCity()),
                () -> Assertions.assertEquals(draft.getBudgetPerPerson(), team.getBudgetPerPerson()),
                () -> Assertions.assertEquals(1, confirmed.getStatus()),
                () -> Assertions.assertEquals(response.getTeamId(), confirmed.getConfirmedTeamId()),
                () -> Assertions.assertNotNull(confirmed.getConfirmedAt()),
                () -> Assertions.assertEquals(1L, countTeamMemberships(response.getTeamId())),
                () -> Assertions.assertEquals(1L, countAuditLogs(draft.getDraftId(), "success")),
                () -> Assertions.assertEquals(1L, countEvents(owner.getId(), sessionId))
        );
        verify(memoryPipelineService).onChatTurnCompleted(
                org.mockito.ArgumentMatchers.eq(owner.getId()), any(), anyLong());
    }

    @Test
    void confirmDraft_ownedByAnotherUser_shouldRejectWithoutCreatingTeam() {
        User owner = createUser();
        User anotherUser = createUser();
        TeamDraftVO draft = saveValidDraft(owner, unique("session"));

        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> aiTeamDraftService.confirmDraft(draft.getDraftId(), anotherUser)
        );

        Assertions.assertEquals(ErrorCode.NO_AUTH.getCode(), exception.getCode());
        Assertions.assertEquals(0, findDraft(draft.getDraftId()).getStatus());
        Assertions.assertEquals(0L, countTeams(owner.getId()));
        Assertions.assertEquals(0L, countTeams(anotherUser.getId()));
        Assertions.assertEquals(1L, countAuditLogs(draft.getDraftId(), "failed"));
    }

    @Test
    void confirmDraft_expiredDraft_shouldPersistExpiredStatusWithoutCreatingTeam() {
        User owner = createUser();
        TeamDraftVO draft = validDraft(unique("session"), owner);
        draft.setExpiresAt(new Date(System.currentTimeMillis() - 60_000));
        aiTeamDraftService.saveDraft(draft, owner, draft.getSessionId());

        Assertions.assertThrows(
                DraftExpiredException.class,
                () -> aiTeamDraftService.confirmDraft(draft.getDraftId(), owner)
        );

        Assertions.assertEquals(2, findDraft(draft.getDraftId()).getStatus());
        Assertions.assertEquals(0L, countTeams(owner.getId()));
        Assertions.assertEquals(1L, countAuditLogs(draft.getDraftId(), "failed"));
    }

    @Test
    void confirmDraft_confirmedTwice_shouldCreateOnlyOneTeam() {
        User owner = createUser();
        TeamDraftVO draft = saveValidDraft(owner, unique("session"));

        AiTeamDraftConfirmResponse first = aiTeamDraftService.confirmDraft(draft.getDraftId(), owner);
        BusinessException second = Assertions.assertThrows(
                BusinessException.class,
                () -> aiTeamDraftService.confirmDraft(draft.getDraftId(), owner)
        );

        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), second.getCode());
        Assertions.assertEquals(1L, countTeams(owner.getId()));
        Assertions.assertEquals(1L, countTeamMemberships(first.getTeamId()));
        Assertions.assertEquals(first.getTeamId(), findDraft(draft.getDraftId()).getConfirmedTeamId());
    }

    @Test
    void confirmDraft_concurrentRequests_shouldCreateOnlyOneTeam() throws Exception {
        User owner = createUser();
        TeamDraftVO draft = saveValidDraft(owner, unique("session"));
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger alreadyConfirmedCount = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();

        try {
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        aiTeamDraftService.confirmDraft(draft.getDraftId(), owner);
                        successCount.incrementAndGet();
                    } catch (BusinessException exception) {
                        if (exception.getCode() == ErrorCode.PARAMS_ERROR.getCode()
                                && "draft has already been confirmed".equals(exception.getDescription())) {
                            alreadyConfirmedCount.incrementAndGet();
                        } else {
                            unexpectedErrors.add(exception);
                        }
                    } catch (Throwable throwable) {
                        unexpectedErrors.add(throwable);
                    } finally {
                        done.countDown();
                    }
                });
            }

            Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS), "workers were not ready in time");
            start.countDown();
            Assertions.assertTrue(done.await(30, TimeUnit.SECONDS), "concurrent confirmation timed out");

            AiTeamDraft confirmed = findDraft(draft.getDraftId());
            Assertions.assertAll(
                    () -> Assertions.assertEquals(1, successCount.get()),
                    () -> Assertions.assertEquals(1, alreadyConfirmedCount.get()),
                    () -> Assertions.assertTrue(unexpectedErrors.isEmpty(),
                            () -> "unexpected errors: " + unexpectedErrors),
                    () -> Assertions.assertEquals(1L, countTeams(owner.getId())),
                    () -> Assertions.assertEquals(1, confirmed.getStatus()),
                    () -> Assertions.assertNotNull(confirmed.getConfirmedTeamId()),
                    () -> Assertions.assertEquals(1L, countTeamMemberships(confirmed.getConfirmedTeamId()))
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void confirmDraft_whenDraftStatusUpdateFails_shouldRollbackCreatedTeam() {
        User owner = createUser();
        TeamDraftVO draft = saveValidDraft(owner, unique("session"));

        doReturn(0)
                .when(aiTeamDraftMapper)
                .updateById(ArgumentMatchers.<AiTeamDraft>argThat(
                        update -> update != null && Integer.valueOf(1).equals(update.getStatus())
                        )
                );

        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> aiTeamDraftService.confirmDraft(draft.getDraftId(), owner)
        );

        Assertions.assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), exception.getCode());
        AiTeamDraft pending = findDraft(draft.getDraftId());
        Assertions.assertEquals(0, pending.getStatus());
        Assertions.assertNull(pending.getConfirmedTeamId());
        Assertions.assertEquals(0L, countTeams(owner.getId()));
        Assertions.assertEquals(0L, countMemberships(owner.getId()));
    }

    private User createUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = new User();
        user.setUsername("draft_test");
        user.setUserAccount("draft_" + suffix);
        user.setUserPassword("Password123");
        user.setUserRole(0);
        user.setUserStatus(0);
        Assertions.assertTrue(userService.save(user));
        userIds.add(user.getId());
        return user;
    }

    private TeamDraftVO saveValidDraft(User owner, String sessionId) {
        TeamDraftVO draft = validDraft(sessionId, owner);
        return aiTeamDraftService.saveDraft(draft, owner, sessionId);
    }

    private TeamDraftVO validDraft(String sessionId, User owner) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        TeamDraftVO draft = new TeamDraftVO();
        draft.setDraftId("draft-" + suffix + "-" + owner.getId());
        draft.setSessionId(sessionId);
        draft.setName("AI羽毛球" + suffix);
        draft.setDescription("新手友好的周末活动");
        draft.setMaxNum(4);
        draft.setActivityCategory(1);
        draft.setActivityType("羽毛球");
        draft.setCity("西安");
        draft.setDistrict("雁塔区");
        draft.setStartTime(new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(2)));
        draft.setDurationMinutes(120);
        draft.setBudgetPerPerson(new BigDecimal("50.00"));
        draft.setSkillLevel("入门");
        draft.setExpiresAt(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30)));
        return draft;
    }

    private AiTeamDraft findDraft(String draftId) {
        return aiTeamDraftMapper.selectOne(new QueryWrapper<AiTeamDraft>()
                .eq("draftId", draftId)
                .last("limit 1"));
    }

    private long countTeams(long userId) {
        return teamMapper.selectCount(new QueryWrapper<Team>()
                .eq("userId", userId));
    }

    private long countMemberships(long userId) {
        return userTeamMapper.selectCount(new QueryWrapper<UserTeam>()
                .eq("userId", userId));
    }

    private long countTeamMemberships(long teamId) {
        return userTeamMapper.selectCount(new QueryWrapper<UserTeam>()
                .eq("teamId", teamId));
    }

    private long countAuditLogs(String draftId, String status) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from ai_tool_call_log where relatedDraftId = ? and status = ?",
                Long.class,
                draftId,
                status
        );
        return count == null ? 0 : count;
    }

    private long countEvents(long userId, String sessionId) {
        Long count = jdbcTemplate.queryForObject("""
                        select count(*)
                        from ai_chat_message message
                        inner join ai_chat_session session on session.id = message.chatSessionId
                        where message.userId = ? and session.sessionKey = ? and message.role = 'event'
                        """,
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
