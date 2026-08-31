package com.mikle.syncup.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.mapper.TeamMapper;
import com.mikle.syncup.mapper.UserMapper;
import com.mikle.syncup.mapper.UserTeamMapper;
import com.mikle.syncup.model.domain.Team;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.domain.UserTeam;
import com.mikle.syncup.model.dto.TeamQuery;
import com.mikle.syncup.model.enums.TeamStatusEnum;
import com.mikle.syncup.model.request.TeamJoinRequest;
import com.mikle.syncup.model.request.TeamQuitRequest;
import com.mikle.syncup.model.vo.TeamUserVO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@SpringBootTest
@ActiveProfiles(profiles = "test")
class TeamServiceTest {

    @Resource
    private TeamService teamService;

    @Resource
    private UserService userService;

    @Resource
    private UserTeamService userTeamService;

    @Autowired
    @Resource
    private TeamMapper teamMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserTeamMapper userTeamMapper;

    @Resource
    private DataSource dataSource;

    @BeforeEach
    void ensureUsingTestDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String databaseName = connection.getCatalog();

            Assertions.assertEquals(
                    "sync_up_test",
                    databaseName,
                    "当前连接的不是 sync_up_test，已停止测试，避免污染开发数据库"
            );
        }
    }

    @Test
    @Transactional
    void joinTeam_publicTeam_shouldCreateMembership() {
        // Arrange
        User creator = createTestUser();
        User member = createTestUser();
        long teamId = createPublicTeam(creator.getId());

        TeamJoinRequest request = new TeamJoinRequest();
        request.setTeamId(teamId);

        // Act
        boolean joined = teamService.joinTeam(request, member);

        // Assert
        Assertions.assertTrue(joined);

        long relationCount = userTeamService.lambdaQuery()
                .eq(UserTeam::getTeamId, teamId)
                .eq(UserTeam::getUserId, member.getId())
                .count();

        Assertions.assertEquals(1L, relationCount);
    }

    @Test
    @Transactional
    void joinTeam_duplicateJoin_shouldBeRejected() {
        // Arrange
        User creator = createTestUser();
        User member = createTestUser();
        long teamId = createTeam(creator, 5);

        TeamJoinRequest joinRequest = new TeamJoinRequest();
        joinRequest.setTeamId(teamId);

        // Act: First time joining
        boolean firstJoinResult = teamService.joinTeam(joinRequest, member);

        // Assert: Successfully joined for the first time
        Assertions.assertTrue(firstJoinResult);

        // Act: The same user attempts to join again
        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> teamService.joinTeam(joinRequest, member)
        );

        // Assert: verify that the failure reason is indeed duplicate joining
        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        Assertions.assertEquals("user already joined this team", exception.getDescription());

        // Assert: only one relationship record between the user and the team can exist in the database
        Long memberShipCount = userTeamService.lambdaQuery()
                .eq(UserTeam::getUserId, creator.getId())
                .eq(UserTeam::getTeamId, teamId)
                .count();

        Assertions.assertEquals(1, memberShipCount);

    }

    @Test
    @Transactional
    void joinTeam_whenTeamIsFull_shouldBeRejected() {
        // Arrange
        User creator = createTestUser();
        User member = createTestUser();
        long teamId = createTeam(creator, 1);

        TeamJoinRequest joinRequest = new TeamJoinRequest();
        joinRequest.setTeamId(teamId);

        // Act: attempts to join
        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> teamService.joinTeam(joinRequest, member)
        );

        // Assert: failed to join
        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        Assertions.assertEquals("team is full", exception.getDescription());

        // Assert: the team size does not exceed the maximum limit
        Long count = userTeamService.lambdaQuery()
                .eq(UserTeam::getTeamId, teamId)
                .count();

        Assertions.assertEquals(1L, count);

        // Assert: no relationship record is created for the rejected user
        Long rejectedMemberCount = userTeamService.lambdaQuery()
                .eq(UserTeam::getTeamId, teamId)
                .eq(UserTeam::getUserId, member.getId())
                .count();

        Assertions.assertEquals(0L, rejectedMemberCount);
    }

    @Test
    @Transactional
    void joinTeam_whenTeamIsExpired_shouldBeRejected() {
        // Arrange
        User creator = createTestUser();
        User member = createTestUser();
        long teamId = createTeam(creator, 5);

        // sets the team as expired one minute ago
        Team expiredTeam = new Team();
        expiredTeam.setId(teamId);
        expiredTeam.setExpireTime(new Date(System.currentTimeMillis() - 60_000));
        boolean updated = teamService.updateById(expiredTeam);
        Assertions.assertTrue(updated, "test team should be marked as expired");

        TeamJoinRequest joinRequest = new TeamJoinRequest();
        joinRequest.setTeamId(teamId);

        // Act: attempts to join an expired team
        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> teamService.joinTeam(joinRequest, member)
        );

        // Assert: failed to join
        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        Assertions.assertEquals("team has expired", exception.getDescription());

        // Assert: the team size does not exceed the maximum limit
        Long count = userTeamService.lambdaQuery()
                .eq(UserTeam::getTeamId, teamId)
                .count();

        Assertions.assertEquals(1L, count);

        // Assert: no relationship record is created for the rejected user
        Long rejectedMemberCount = userTeamService.lambdaQuery()
                .eq(UserTeam::getTeamId, teamId)
                .eq(UserTeam::getUserId, member.getId())
                .count();

        Assertions.assertEquals(0L, rejectedMemberCount);
    }

    @Test
    void joinTeam_whenUsersCompeteForLastSlot_shouldAllowOnlyOneMember() throws Exception {
        int candidateCount = 10;

        User creator = null;
        List<User> candidates = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(candidateCount);

        try {
            // Arrange：最大人数为 2，队长已经占用一个名额
            creator = createTestUser();
            long teamId = createTeam(creator, 2);

            for (int i = 0; i < candidateCount; i++) {
                candidates.add(createTestUser());
            }

            CountDownLatch ready = new CountDownLatch(candidateCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(candidateCount);

            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger fullRejectedCount = new AtomicInteger();
            Queue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();

            for (User candidate : candidates) {
                pool.submit(() -> {
                    ready.countDown();

                    try {
                        // 等待所有线程准备好后统一开始
                        start.await();

                        TeamJoinRequest joinRequest = new TeamJoinRequest();
                        joinRequest.setTeamId(teamId);

                        boolean joined = teamService.joinTeam(
                                joinRequest,
                                candidate
                        );

                        if (joined) {
                            successCount.incrementAndGet();
                        } else {
                            unexpectedErrors.add(
                                    new AssertionError(
                                            "joinTeam returned false without an exception"
                                    )
                            );
                        }
                    } catch (BusinessException e) {
                        if (e.getCode() == ErrorCode.PARAMS_ERROR.getCode()
                                && "team is full".equals(e.getDescription())) {
                            fullRejectedCount.incrementAndGet();
                        } else {
                            unexpectedErrors.add(e);
                        }
                    } catch (Throwable e) {
                        unexpectedErrors.add(e);
                    } finally {
                        done.countDown();
                    }
                });
            }

            // 等待所有工作线程进入准备状态
            Assertions.assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "worker threads were not ready in time"
            );

            // 同时释放所有线程
            start.countDown();

            Assertions.assertTrue(
                    done.await(30, TimeUnit.SECONDS),
                    "concurrent join requests did not finish in time"
            );

            // Assert：不能出现数据库异常、锁超时等意外错误
            Assertions.assertTrue(
                    unexpectedErrors.isEmpty(),
                    () -> "unexpected errors: "
                            + unexpectedErrors.stream()
                            .map(error -> error.getClass().getSimpleName()
                                    + ": " + error.getMessage())
                            .collect(Collectors.joining("; "))
            );

            // Assert：只有一个候选用户加入成功
            Assertions.assertEquals(
                    1,
                    successCount.get(),
                    "only one candidate should get the last slot"
            );

            // Assert：其他候选用户都因为队伍已满而被拒绝
            Assertions.assertEquals(
                    candidateCount - 1,
                    fullRejectedCount.get(),
                    "remaining candidates should be rejected because the team is full"
            );

            // Assert：队伍最终只有队长和一个新成员
            long totalMembershipCount = userTeamService.lambdaQuery()
                    .eq(UserTeam::getTeamId, teamId)
                    .count();

            Assertions.assertEquals(
                    2L,
                    totalMembershipCount,
                    "team membership must not exceed maxNum"
            );

            // Assert：候选用户中恰好只有一个产生了关系记录
            List<Long> candidateIds = candidates.stream()
                    .map(User::getId)
                    .toList();

            long joinedCandidateCount = userTeamService.lambdaQuery()
                    .eq(UserTeam::getTeamId, teamId)
                    .in(UserTeam::getUserId, candidateIds)
                    .count();

            Assertions.assertEquals(
                    1L,
                    joinedCandidateCount,
                    "exactly one candidate membership should exist"
            );
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);

            // 队长清理会先删除其创建的队伍及队伍关系
            cleanupUserAndTeams(creator);

            for (User candidate : candidates) {
                cleanupUserAndTeams(candidate);
            }
        }
    }

    @Test
    @Transactional
    void joinTeam_whenTeamIsPrivate_shouldBeRejected() {
        // Arrange
        User creator = createTestUser();
        User member = createTestUser();
        long teamId = createTeam(creator, 5);

        // sets the team as private
        Team privateTeam = new Team();
        privateTeam.setId(teamId);
        privateTeam.setStatus(TeamStatusEnum.PRIVATE.getValue());
        int updated = teamMapper.updateById(privateTeam);
        Assertions.assertEquals(1, updated);

        TeamJoinRequest teamJoinRequest = new TeamJoinRequest();
        teamJoinRequest.setTeamId(teamId);

        // Act: attempts to join the private team
        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> teamService.joinTeam(teamJoinRequest, member)
        );

        // Assert: failed to join
        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        Assertions.assertEquals("cannot join a private team", exception.getDescription());

        // Assert: no relationship record is created for the rejected user
        Long rejectedMemberCount = userTeamService.lambdaQuery()
                .eq(UserTeam::getTeamId, teamId)
                .eq(UserTeam::getUserId, member.getId())
                .count();

        Assertions.assertEquals(0L, rejectedMemberCount);
    }

    @Test
    @Transactional
    void joinTeam_whenEncryptedTeamWithCorrectPassword_shouldSucceed() {
        // Arrange
        User creator = createTestUser();
        User member = createTestUser();
        long teamId = createTeam(creator, 5);

        // sets the team as encrypted
        Team encryptedTeam = new Team();
        encryptedTeam.setId(teamId);
        encryptedTeam.setPassword("12345678");
        encryptedTeam.setStatus(TeamStatusEnum.SECRET.getValue());
        int updated = teamMapper.updateById(encryptedTeam);
        Assertions.assertEquals(1, updated);

        TeamJoinRequest teamJoinRequest = new TeamJoinRequest();
        teamJoinRequest.setTeamId(teamId);
        teamJoinRequest.setPassword("12345678");

        // Act: attempts to join the encrypted team
        boolean joined = teamService.joinTeam(teamJoinRequest, member);

        // Assert: successfully joined
        Assertions.assertTrue(joined);

        Long count = userTeamService.lambdaQuery()
                .eq(UserTeam::getTeamId, teamId)
                .eq(UserTeam::getUserId, member.getId())
                .count();
        Assertions.assertEquals(1L, count);
    }

    @Test
    @Transactional
    void joinTeam_whenEncryptedTeamWithWrongPassword_shouldFail() {
        // Arrange
        User creator = createTestUser();
        User member = createTestUser();
        long teamId = createTeam(creator, 5);

        // sets the team as encrypted
        Team encryptedTeam = new Team();
        encryptedTeam.setId(teamId);
        encryptedTeam.setPassword("12345678");
        encryptedTeam.setStatus(TeamStatusEnum.SECRET.getValue());
        int updated = teamMapper.updateById(encryptedTeam);
        Assertions.assertEquals(1, updated);

        TeamJoinRequest teamJoinRequest = new TeamJoinRequest();
        teamJoinRequest.setTeamId(teamId);
        teamJoinRequest.setPassword("31223666");

        // Act: attempts to join the encrypted team
        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> teamService.joinTeam(teamJoinRequest, member)
        );

        // Assert: failed to join
        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        Assertions.assertEquals("wrong password", exception.getDescription());

        // Assert:
        Long count = userTeamService.lambdaQuery()
                .eq(UserTeam::getTeamId, teamId)
                .eq(UserTeam::getUserId, member.getId())
                .count();
        Assertions.assertEquals(0L, count);
    }

    @Test
    @Transactional
    void joinTeam_whenUserRejoinsAfterQuit_shouldSucceed() {
        // Arrange
        User creator = createTestUser();
        User member = createTestUser();
        long teamId = createTeam(creator, 2);

        TeamJoinRequest joinRequest = new TeamJoinRequest();
        joinRequest.setTeamId(teamId);

        boolean firstJoinResult = teamService.joinTeam(joinRequest, member);
        Assertions.assertTrue(firstJoinResult);

        TeamQuitRequest quitRequest = new TeamQuitRequest();
        quitRequest.setTeamId(teamId);

        // Act：先退出
        boolean quitResult = teamService.quitTeam(quitRequest, member);
        Assertions.assertTrue(quitResult);

        long relationCountAfterQuit = userTeamService.lambdaQuery()
                .eq(UserTeam::getTeamId, teamId)
                .eq(UserTeam::getUserId, member.getId())
                .count();

        Assertions.assertEquals(0L, relationCountAfterQuit);

        // Act：退出后重新加入
        boolean rejoinResult = teamService.joinTeam(joinRequest, member);

        // Assert
        Assertions.assertTrue(rejoinResult);

        long relationCountAfterRejoin = userTeamService.lambdaQuery()
                .eq(UserTeam::getTeamId, teamId)
                .eq(UserTeam::getUserId, member.getId())
                .count();

        Assertions.assertEquals(1L, relationCountAfterRejoin);

        // 队伍最终有队长和重新加入的成员
        long totalMembershipCount = userTeamService.lambdaQuery()
                .eq(UserTeam::getTeamId, teamId)
                .count();

        Assertions.assertEquals(2L, totalMembershipCount);
    }

    @Test
    @Transactional
    void quitTeam_whenMemberQuits_shouldRemoveMembershipAndKeepTeam() {
        User creator = createTestUser();
        User member = createTestUser();
        long teamId = createTeam(creator, 2);
        TeamJoinRequest teamJoinRequest = new TeamJoinRequest();
        teamJoinRequest.setTeamId(teamId);
        boolean joined = teamService.joinTeam(teamJoinRequest, member);
        Assertions.assertTrue(joined);

        // Act: attempts to quit team
        TeamQuitRequest quitRequest = new TeamQuitRequest();
        quitRequest.setTeamId(teamId);
        boolean quited = teamService.quitTeam(quitRequest, member);

        // Assert: successfully quited
        Assertions.assertTrue(quited);
        // Assert: varifies that the data in the database is consistent
        Long memberRelationCount = userTeamService.lambdaQuery()
                .eq(UserTeam::getTeamId, teamId)
                .eq(UserTeam::getUserId, member.getId())
                .count();
        Assertions.assertEquals(0L, memberRelationCount);

        // 队长关系仍然存在
        long creatorRelationCount = userTeamService.lambdaQuery()
                .eq(UserTeam::getTeamId, teamId)
                .eq(UserTeam::getUserId, creator.getId())
                .count();

        Assertions.assertEquals(1L, creatorRelationCount);

        // 队伍中最终只剩队长
        long totalMembershipCount = userTeamService.lambdaQuery()
                .eq(UserTeam::getTeamId, teamId)
                .count();

        Assertions.assertEquals(1L, totalMembershipCount);

        // 普通成员退出不能导致队伍被删除
        Team remainingTeam = teamService.getById(teamId);

        Assertions.assertNotNull(remainingTeam);
        Assertions.assertEquals(creator.getId(), remainingTeam.getUserId());
    }

    @Test
    @Transactional
    void quitTeam_whenCaptainQuits_shouldTransferCaptainAndKeepTeam() {
        // Arrange
        User creator = createTestUser();
        User member = createTestUser();
        long teamId = createTeam(creator, 2);

        TeamJoinRequest joinRequest = new TeamJoinRequest();
        joinRequest.setTeamId(teamId);

        boolean joined = teamService.joinTeam(joinRequest, member);
        Assertions.assertTrue(joined);

        TeamQuitRequest quitRequest = new TeamQuitRequest();
        quitRequest.setTeamId(teamId);

        // Act：队长退出
        boolean quitResult = teamService.quitTeam(quitRequest, creator);

        // Assert：退出成功
        Assertions.assertTrue(quitResult);

        // 原队长的成员关系已经删除
        long creatorRelationCount = userTeamService.lambdaQuery()
                .eq(UserTeam::getTeamId, teamId)
                .eq(UserTeam::getUserId, creator.getId())
                .count();

        Assertions.assertEquals(0L, creatorRelationCount);

        // 原成员关系仍然存在
        long memberRelationCount = userTeamService.lambdaQuery()
                .eq(UserTeam::getTeamId, teamId)
                .eq(UserTeam::getUserId, member.getId())
                .count();

        Assertions.assertEquals(1L, memberRelationCount);

        // 队伍最终只剩一个成员
        long totalMembershipCount = userTeamService.lambdaQuery()
                .eq(UserTeam::getTeamId, teamId)
                .count();

        Assertions.assertEquals(1L, totalMembershipCount);

        // 队伍没有被删除
        Team remainingTeam = teamService.getById(teamId);

        Assertions.assertNotNull(remainingTeam);

        // 剩余成员被转为新队长
        Assertions.assertEquals(
                member.getId(),
                remainingTeam.getUserId()
        );
    }

    @Test
    @Transactional
    void quitTeam_whenLastOneQuits_shouldRemoveTeamAndMembership() {
        User creator = createTestUser();
        long teamId = createTeam(creator, 2);

        TeamQuitRequest teamQuitRequest = new TeamQuitRequest();
        teamQuitRequest.setTeamId(teamId);
        // 队长退出队伍
        boolean quiteResult = teamService.quitTeam(teamQuitRequest, creator);
        Assertions.assertTrue(quiteResult);

        // 原队伍关系已删除
        Long memberRelationCount = userTeamService.lambdaQuery()
                .eq(UserTeam::getTeamId, teamId)
                .count();
        Assertions.assertEquals(0L, memberRelationCount);

        // 原队伍已删除
        Long teamCount = teamService.lambdaQuery()
                .eq(Team::getId, teamId)
                .count();
        Assertions.assertEquals(0L, teamCount);

        // 退出队伍不能删除用户
        User remainingCreator = userService.getById(creator.getId());
        Assertions.assertNotNull(remainingCreator);
    }

    @Test
    void listTeams_structuredFields_shouldFilterByCityActivityTimeAndBudget() {
        User creator = null;
        try {
            creator = createTestUser();
            Date tomorrowMorning = new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000);
            Date twoDaysLater = new Date(System.currentTimeMillis() + 2 * 24 * 60 * 60 * 1000);
            long matchedTeamId = createStructuredTeam(creator, "羽毛球", "西安", "雁塔", tomorrowMorning,
                    new BigDecimal("45.00"), "中等", 6);
            createStructuredTeam(creator, "徒步", "西安", "长安", twoDaysLater,
                    new BigDecimal("30.00"), "入门", 6);

            TeamQuery query = new TeamQuery();
            query.setActivityCategory(1);
            query.setActivityType("羽毛球");
            query.setCity("西安");
            query.setStartTimeBegin(new Date(System.currentTimeMillis() + 12 * 60 * 60 * 1000));
            query.setStartTimeEnd(new Date(System.currentTimeMillis() + 36 * 60 * 60 * 1000));
            query.setMaxBudgetPerPerson(new BigDecimal("50.00"));
            query.setSkillLevel("中等");

            List<TeamUserVO> teams = teamService.listTeams(query, false);

            Assertions.assertEquals(1, teams.size());
            Assertions.assertEquals(matchedTeamId, teams.get(0).getId());
            Assertions.assertEquals("羽毛球", teams.get(0).getActivityType());
            Assertions.assertEquals(new BigDecimal("45.00"), teams.get(0).getBudgetPerPerson());
        } finally {
            cleanupUserAndTeams(creator);
        }
    }

    @Test
    void listTeams_onlyAvailable_shouldExcludeFullTeams() {
        User creator = null;
        User member = null;
        try {
            creator = createTestUser();
            member = createTestUser();
            long teamId = createStructuredTeam(creator, "羽毛球", "西安", null,
                    new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000),
                    new BigDecimal("20.00"), "中等", 2);

            TeamJoinRequest joinRequest = new TeamJoinRequest();
            joinRequest.setTeamId(teamId);
            Assertions.assertTrue(teamService.joinTeam(joinRequest, member));

            TeamQuery query = new TeamQuery();
            query.setCity("西安");
            query.setOnlyAvailable(true);

            List<TeamUserVO> teams = teamService.listTeams(query, false);

            Assertions.assertTrue(teams.stream().noneMatch(team -> team.getId().equals(teamId)));
        } finally {
            cleanupUserAndTeams(creator);
            cleanupUserAndTeams(member);
        }
    }

    @Test
    void listTeams_withoutStructuredFilters_shouldKeepLegacyTeams() {
        User creator = null;
        try {
            creator = createTestUser();
            long legacyTeamId = createTeam(creator, 3);

            TeamQuery query = new TeamQuery();
            query.setUserId(creator.getId());

            List<TeamUserVO> teams = teamService.listTeams(query, false);

            Assertions.assertTrue(teams.stream().anyMatch(team -> team.getId().equals(legacyTeamId)));
        } finally {
            cleanupUserAndTeams(creator);
        }
    }

    private User createTestUser() {
        User user = new User();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        user.setUsername("lock_test");
        user.setUserAccount("u_" + suffix);
        user.setUserPassword("12345678");
        user.setUserRole(0);
        boolean saved = userService.save(user);
        Assertions.assertTrue(saved, "test user should be created");
        return user;
    }

    private long createPublicTeam(long userId) {
        Team team = new Team();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 5);
        team.setName("lock_team" + suffix);
        team.setMaxNum(5);
        team.setUserId(userId);
        team.setStatus(0);

        boolean saved = teamService.save(team);
        Assertions.assertTrue(saved, "test team should be created");
        return team.getId();
    }

    private long createTeam(User creator, int maxNum) {
        Team team = new Team();
        team.setName("t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        team.setDescription("lock_team");
        team.setActivityCategory(9);
        team.setMaxNum(maxNum);
        team.setStatus(0);
        team.setExpireTime(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000));

        return teamService.addTeam(team, creator);
    }

    private long createStructuredTeam(User creator, String activityType, String city, String district, Date startTime,
                                      BigDecimal budgetPerPerson, String skillLevel, int maxNum) {
        Team team = new Team();
        team.setName("t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        team.setDescription("stage0.5 test");
        team.setActivityCategory(resolveActivityCategory(activityType));
        team.setMaxNum(maxNum);
        team.setStatus(0);
        team.setExpireTime(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000));
        team.setActivityType(activityType);
        team.setCity(city);
        team.setDistrict(district);
        team.setStartTime(startTime);
        team.setDurationMinutes(120);
        team.setBudgetPerPerson(budgetPerPerson);
        team.setSkillLevel(skillLevel);
        return teamService.addTeam(team, creator);
    }


    private int resolveActivityCategory(String activityType) {
        if ("徒步".equals(activityType)) {
            return 2;
        }
        return 1;
    }

    private void cleanupUserAndTeams(User user) {
        if (user == null || user.getId() <= 0) {
            return;
        }
        clearUserTeams(user.getId());
        userMapper.deleteByIdPhysically(user.getId());
    }

    private void clearUserTeams(long userId) {
        userTeamMapper.deleteByTeamCreatorUserIdPhysically(userId);
        userTeamMapper.deleteByUserIdPhysically(userId);
        teamMapper.deleteByUserIdPhysically(userId);
    }

    @Test
    @Disabled("Manual cleanup helper. Do not run with the regular test suite.")
    void clearTeams() {
        long userId = 6;
        List<Team> teams = teamService.list(new QueryWrapper<Team>().eq("userId", userId));
        System.out.println("teams: " + teams + teams.size());
        System.out.println("------");
        List<Long> teamIds = teamService.list(new QueryWrapper<Team>().eq("userId", userId))
                .stream()
                .map(Team::getId)
                .collect(Collectors.toList());
        System.out.println(teamIds);
        clearUserTeams(userId);
    }
}
