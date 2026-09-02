package com.mikle.syncup.service.team;

import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.Team;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.domain.UserTeam;
import com.mikle.syncup.model.enums.TeamStatusEnum;
import com.mikle.syncup.model.request.TeamJoinRequest;
import com.mikle.syncup.model.request.TeamQuitRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class TeamServiceJoinTest extends TeamServiceTestSupport{

    @Test
    @Transactional
    void joinTeam_publicTeam_shouldCreateMembership() {
        // Arrange
        User creator = createTestUser();
        User member = createTestUser();
        Long teamId = createTeam(creator.getId(), null, null, TeamStatusEnum.PUBLIC);
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
        Long teamId = createTeam(creator.getId(), null, 5, TeamStatusEnum.PUBLIC);
        createMembership(creator.getId(), teamId);

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
        Long membershipCount = userTeamService.lambdaQuery()
                .eq(UserTeam::getUserId, member.getId())
                .eq(UserTeam::getTeamId, teamId)
                .count();
        Assertions.assertEquals(1, membershipCount);
    }

    @Test
    @Transactional
    void joinTeam_whenTeamIsFull_shouldBeRejected() {
        // Arrange
        User creator = createTestUser();
        User member = createTestUser();
        Long teamId = createTeam(creator.getId(), null, 1, TeamStatusEnum.PUBLIC);
        createMembership(creator.getId(), teamId);

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
        Long teamId = createTeam(creator.getId(), null, 5, TeamStatusEnum.PUBLIC);
        createMembership(creator.getId(), teamId);

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
            Long teamId = createTeam(creator.getId(), null, 2, TeamStatusEnum.PUBLIC);
            createMembership(creator.getId(), teamId);

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
        Long teamId = createTeam(creator.getId(), null, 5, TeamStatusEnum.PUBLIC);
        createMembership(creator.getId(), teamId);

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
    void joinTeam_whenSecretTeamWithCorrectPassword_shouldSucceed() {
        // Arrange
        User creator = createTestUser();
        User member = createTestUser();
        Long teamId = createTeam(creator.getId(), null, 5, TeamStatusEnum.PUBLIC);
        createMembership(creator.getId(), teamId);

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
        Long teamId = createTeam(creator.getId(), null, 5, TeamStatusEnum.PUBLIC);
        createMembership(creator.getId(), teamId);

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
        Long teamId = createTeam(creator.getId(), null, 2, TeamStatusEnum.PUBLIC);
        createMembership(creator.getId(), teamId);

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
}
