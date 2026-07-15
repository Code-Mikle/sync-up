package com.mikle.syncup.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.mapper.TeamMapper;
import com.mikle.syncup.mapper.UserMapper;
import com.mikle.syncup.mapper.UserTeamMapper;
import com.mikle.syncup.model.domain.Team;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.domain.UserTeam;
import com.mikle.syncup.model.dto.TeamQuery;
import com.mikle.syncup.model.request.TeamJoinRequest;
import com.mikle.syncup.model.request.TeamQuitRequest;
import com.mikle.syncup.model.vo.TeamUserVO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@SpringBootTest
class TeamServiceTest {

    @Resource
    private TeamService teamService;

    @Resource
    private UserService userService;

    @Resource
    private UserTeamService userTeamService;

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserTeamMapper userTeamMapper;

    @Test
    void addTeam_pessimisticLock_shouldNotExceed5() throws Exception {
        User loginUser = createTestUser();
        int maxTeamCount = 5;
        int requestCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(requestCount);

        try {
            clearUserTeams(loginUser.getId());

            CountDownLatch ready = new CountDownLatch(requestCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(requestCount);
            AtomicInteger success = new AtomicInteger();
            AtomicInteger failedByQuota = new AtomicInteger();
            Queue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();

            for (int i = 0; i < requestCount; i++) {
                final int idx = i;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();

                        Team team = new Team();
                        team.setName("t" + idx + UUID.randomUUID().toString().substring(0, 6));
                        team.setDescription("lock test");
                        team.setMaxNum(5);
                        team.setStatus(0);
                        team.setExpireTime(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000));

                        long teamId = teamService.addTeam(team, loginUser);
                        if (teamId > 0) {
                            success.incrementAndGet();
                        }
                    } catch (BusinessException e) {
                        failedByQuota.incrementAndGet();
                    } catch (Exception e) {
                        unexpectedErrors.add(e);
                    } finally {
                        done.countDown();
                    }
                });
            }

            Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS), "worker threads are not ready");
            start.countDown();
            Assertions.assertTrue(done.await(60, TimeUnit.SECONDS), "concurrent requests did not finish");

            long finalTeamCount = countTeamsCreatedBy(loginUser.getId());
            long finalUserTeamCount = countUserTeamRelationsByUser(loginUser.getId());

            Assertions.assertTrue(unexpectedErrors.isEmpty(),
                    () -> "unexpected errors: " + unexpectedErrors.stream()
                            .map(error -> error.getClass().getName() + ": " + error.getMessage())
                            .collect(Collectors.joining("; ")));
            Assertions.assertEquals(maxTeamCount, finalTeamCount, "created team count should reach the limit exactly");
            Assertions.assertEquals(maxTeamCount, finalUserTeamCount, "user_team relation count should match created teams");
            Assertions.assertEquals(maxTeamCount, success.get(), "only the first 5 requests should succeed");
            Assertions.assertEquals(requestCount - maxTeamCount, failedByQuota.get(),
                    "remaining requests should fail because of the quota");
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
            cleanupUserAndTeams(loginUser);
        }
    }

    @Test
    void joinTeam_duplicateJoin_shouldBeRejected() {
        User creator = null;
        User member = null;
        try {
            creator = createTestUser();
            member = createTestUser();
            long teamId = createTeam(creator, 2);

            TeamJoinRequest joinRequest = new TeamJoinRequest();
            joinRequest.setTeamId(teamId);

            Assertions.assertTrue(teamService.joinTeam(joinRequest, member));
            User joinedMember = member;
            Assertions.assertThrows(BusinessException.class, () -> teamService.joinTeam(joinRequest, joinedMember));
            Assertions.assertEquals(1, countUserTeamRelationsByUser(member.getId()));
        } finally {
            cleanupUserAndTeams(creator);
            cleanupUserAndTeams(member);
        }
    }

    @Test
    void quitTeam_physicalDelete_shouldAllowRejoin() {
        User creator = null;
        User member = null;
        try {
            creator = createTestUser();
            member = createTestUser();
            long teamId = createTeam(creator, 2);

            TeamJoinRequest joinRequest = new TeamJoinRequest();
            joinRequest.setTeamId(teamId);
            Assertions.assertTrue(teamService.joinTeam(joinRequest, member));

            TeamQuitRequest quitRequest = new TeamQuitRequest();
            quitRequest.setTeamId(teamId);
            Assertions.assertTrue(teamService.quitTeam(quitRequest, member));

            Assertions.assertTrue(teamService.joinTeam(joinRequest, member));
            Assertions.assertEquals(1, countUserTeamRelationsByUser(member.getId()));
        } finally {
            cleanupUserAndTeams(creator);
            cleanupUserAndTeams(member);
        }
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

    private long countTeamsCreatedBy(long userId) {
        return teamService.count(new QueryWrapper<Team>().eq("userId", userId));
    }

    private long countUserTeamRelationsByUser(long userId) {
        return userTeamService.count(new QueryWrapper<UserTeam>().eq("userId", userId));
    }

    private long createTeam(User creator, int maxNum) {
        Team team = new Team();
        team.setName("t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        team.setDescription("stage0 test");
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
