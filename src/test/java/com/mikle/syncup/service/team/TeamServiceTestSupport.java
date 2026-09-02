package com.mikle.syncup.service.team;

import com.mikle.syncup.mapper.TeamMapper;
import com.mikle.syncup.mapper.UserMapper;
import com.mikle.syncup.mapper.UserTeamMapper;
import com.mikle.syncup.model.domain.Team;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.domain.UserTeam;
import com.mikle.syncup.model.enums.TeamStatusEnum;
import com.mikle.syncup.service.TeamService;
import com.mikle.syncup.service.UserService;
import com.mikle.syncup.service.UserTeamService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TeamServiceTestSupport {

    @Resource
    protected TeamService teamService;

    @Resource
    protected UserService userService;

    @Resource
    protected UserTeamService userTeamService;

    @Resource
    protected TeamMapper teamMapper;

    @Resource
    protected UserMapper userMapper;

    @Resource
    protected UserTeamMapper userTeamMapper;

    @Resource
    protected DataSource dataSource;

    @BeforeEach
    protected void ensureUsingTestDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String databaseName = connection.getCatalog();

            Assertions.assertEquals(
                    "sync_up_test",
                    databaseName,
                    "当前连接的不是 sync_up_test，已停止测试，避免污染开发数据库"
            );
        }
    }

    protected User createTestUser() {
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

    protected void createMembership(long userId, long teamId) {
        UserTeam userTeam = new UserTeam();
        userTeam.setUserId(userId);
        userTeam.setTeamId(teamId);
        userTeam.setJoinTime(new Date());
        boolean saved = userTeamService.save(userTeam);
        Assertions.assertTrue(saved);
    }

    protected Long createTeam(Long creatorId, Date expireTime, Integer maxNum, TeamStatusEnum teamStatusEnum) {
        Team team = new Team();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 5);
        team.setName("lock_team" + suffix);
        team.setExpireTime(expireTime);
        team.setMaxNum(maxNum);
        team.setStatus(teamStatusEnum.getValue());
        team.setUserId(creatorId);

        boolean saved = teamService.save(team);
        Assertions.assertTrue(saved, "test team should be created");
        return team.getId();
    }

    protected long createStructuredTeam(
            String name, String description, Integer activityCategoryCode, int maxNum,
            Date expireTime, String city, Date startTime, Integer duration,
            BigDecimal budgetPerPerson, String skillLevel, User creator) {
        Team team = new Team();
        team.setName(name);
        team.setDescription(description);
        team.setActivityCategory(activityCategoryCode);
        team.setMaxNum(maxNum);
        team.setExpireTime(expireTime);
        team.setCity(city);
        team.setStartTime(startTime);
        team.setDurationMinutes(duration);
        team.setBudgetPerPerson(budgetPerPerson);
        team.setSkillLevel(skillLevel);
        return teamService.addTeam(team, creator);
    }

    protected void cleanupUserAndTeams(User user) {
        if (user == null || user.getId() <= 0) {
            return;
        }
        clearUserTeams(user.getId());
        userMapper.deleteByIdPhysically(user.getId());
    }

    protected void clearUserTeams(long userId) {
        userTeamMapper.deleteByTeamCreatorUserIdPhysically(userId);
        userTeamMapper.deleteByUserIdPhysically(userId);
        teamMapper.deleteByUserIdPhysically(userId);
    }
}
