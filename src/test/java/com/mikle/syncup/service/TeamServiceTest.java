package com.mikle.syncup.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mikle.syncup.model.domain.Team;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.domain.UserTeam;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.UUID;
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

    @Test
    void addTeam_pessimisticLock_shouldNotExceed5() throws Exception {
        User loginUser = createTestUser();
        // 线程池的容量 同时最多运行的任务数
        ExecutorService pool = Executors.newFixedThreadPool(80);

        try {
            clearUserTeams(loginUser.getId());

            int requestCount = 80;
            // 准备锁 统计“线程就绪”数量
            // 每个线程启动后先执行 ready.countDown()。
            CountDownLatch ready = new CountDownLatch(requestCount);
            // 统一发令枪，确保同时开始
            // 每个线程启动后执行 start.await()，这会阻塞所有线程。
            CountDownLatch start = new CountDownLatch(1);
            // 统计“线程完成”数量
            CountDownLatch done = new CountDownLatch(requestCount);
            // 线程安全统计成功次数
            AtomicInteger success = new AtomicInteger();
            // 线程安全统计失败次数
            AtomicInteger failed = new AtomicInteger();
            // 提交 80 个任务到线程池
            for (int i = 0; i < requestCount; i++) {
                final int idx = i;
                // pool.submit 提交一个并发任务，超过线程池的容量的任务就会先排队
                pool.submit(() -> {
                    // 当前任务已就绪，ready减1
                    // 子任务报到，我已经启动了
                    ready.countDown();
                    try {
                        // 阻塞线程，等待统一开始信号
                        // 是子任务等发令，先别干活，等主线程统一放行
                        start.await();

                        Team team = new Team();
                        team.setName("t" + idx + UUID.randomUUID().toString().substring(0, 6)); // <= 20
                        team.setDescription("lock test");
                        team.setMaxNum(5);
                        team.setStatus(0);
                        team.setExpireTime(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000));

                        long teamId = teamService.addTeam(team, loginUser);
                        if (teamId > 0) {
                            // 成功则成功计数+1
                            success.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // 异常视为失败，失败计数+1
                        failed.incrementAndGet();
                    } finally {
                        // 无论成功失败都标记任务完成
                        done.countDown();
                    }
                });
            }

            // 最多等 10 秒，确保 80 个任务都就绪
            // ready.await 是等所有已提交的任务都报到完
            Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS), "线程未就绪");
            // 发令，所有任务同时开跑
            // 所有卡在 start.await 的任务同时往下执行 addTeam
            start.countDown();
            // 最多等 60 秒，确保任务全部结束
            Assertions.assertTrue(done.await(60, TimeUnit.SECONDS), "并发请求未完成");
            // 查库统计该用户最终建队数量
            long finalCount = teamService.count(new QueryWrapper<Team>().eq("userId", loginUser.getId()));
            System.out.printf("success=%d, failed=%d, finalCount=%d%n", success.get(), failed.get(), finalCount);
            Assertions.assertTrue(finalCount <= 5, "同一用户创建队伍数不应超过5");
            Assertions.assertEquals(finalCount, success.get(), "成功次数应与最终落库数量一致");
        } finally {
            clearUserTeams(loginUser.getId());
            userService.removeById(loginUser.getId());
            pool.shutdownNow();
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
        Assertions.assertTrue(saved, "测试用户创建失败");
        return user;
    }

    private void clearUserTeams(long userId) {
        List<Long> teamIds = teamService.list(new QueryWrapper<Team>().eq("userId", userId))
                .stream()
                .map(Team::getId) // 取出 team 的 id 属性
                .collect(Collectors.toList());
        if (!teamIds.isEmpty()) {
            userTeamService.remove(new QueryWrapper<UserTeam>().in("teamId", teamIds));
            teamService.remove(new QueryWrapper<Team>().in("id", teamIds));
        }
    }

    @Test
    public void clearTeams() {
        long userId = 6;
        List<Team> teams = teamService.list(new QueryWrapper<Team>().eq("userId", userId));
        System.out.println("teams: "+ teams.toString() + teams.size());
        System.out.println("------");
        List<Long> teamIds = teamService.list(new QueryWrapper<Team>().eq("userId", userId))
                .stream()
                .map(Team::getId) // 取出 team 的 id 属性
                .collect(Collectors.toList());
        System.out.println(teamIds.toString());
        if (!teamIds.isEmpty()) {
            userTeamService.remove(new QueryWrapper<UserTeam>().in("teamId", teamIds));
            teamService.remove(new QueryWrapper<Team>().in("id", teamIds));
        }
    }
}
