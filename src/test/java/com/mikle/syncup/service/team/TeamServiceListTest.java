package com.mikle.syncup.service.team;

import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.dto.TeamQuery;
import com.mikle.syncup.model.enums.TeamActivityCategoryEnum;
import com.mikle.syncup.model.enums.TeamStatusEnum;
import com.mikle.syncup.model.vo.TeamUserVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public class TeamServiceListTest extends TeamServiceTestSupport{

    @Test
    @Transactional
    void listTeams_whenNonAdminUsesEmptyQuery_shouldReturnOnlyPublicTeams() {
        // Arrange
        User creator = createTestUser();

        Long publicTeam01 = createTeam(creator.getId(), null, null, TeamStatusEnum.PUBLIC);
        Long publicTeam02 = createTeam(creator.getId(), new Date(System.currentTimeMillis() - 10_000), null, TeamStatusEnum.PUBLIC);
        Long secretTeam = createTeam(creator.getId(), null, null, TeamStatusEnum.SECRET);
        Long privateTeam = createTeam(creator.getId(), null, null, TeamStatusEnum.PRIVATE);

        TeamQuery teamQuery = new TeamQuery();

        // Act
        List<TeamUserVO> teams = teamService.listTeams(teamQuery, false);

        // Assert
        List<Long> resultTeamIds = teams.stream()
                .map(TeamUserVO::getId)
                .toList();
        // 公开队伍可以查询到
        Assertions.assertTrue(resultTeamIds.contains(publicTeam01));
        // 加密、过期公开和私有队伍不能被默认查询到
        Assertions.assertFalse(resultTeamIds.contains(publicTeam02));
        Assertions.assertFalse(resultTeamIds.contains(secretTeam));
        Assertions.assertFalse(resultTeamIds.contains(privateTeam));
        // 普通用户查询结果中不应该出现非公开状态
        Assertions.assertTrue(
                teams.stream().allMatch(
                        team -> TeamStatusEnum.PUBLIC.getValue() == team.getStatus()
                )
        );
    }

    @Test
    @Transactional
    void listTeams_whenSearchTextMatchesNameOrDescription_shouldReturnMatchedTeams() {
        // Arrange
        User creator = createTestUser();
        Date startTime = new Date(System.currentTimeMillis() + 60 * 60 * 1000);
        Date expireTime = new Date(System.currentTimeMillis() + 2 * 60 * 60 * 1000);
        long descriptionMatchedTeamId = createStructuredTeam(
                "热爱运动", "羽毛球、跑步、跳绳",
                TeamActivityCategoryEnum.SPORT_FITNESS.getCode(),
                5, expireTime, "西安", startTime, 100,
                new BigDecimal("45.00"), null, creator);
        long nameMatchedTeamId = createStructuredTeam(
                "西安羽毛球小队", "欢迎大家加入，一起运动",
                TeamActivityCategoryEnum.SPORT_FITNESS.getCode(),
                5, expireTime, "西安", startTime, 100,
                new BigDecimal("45.00"), null, creator);
        long unmatchedTeamId = createStructuredTeam(
                "热爱运动", "攀岩、骑行、跳绳",
                TeamActivityCategoryEnum.SPORT_FITNESS.getCode(),
                5, expireTime, "西安", startTime, 100,
                new BigDecimal("45.00"), null, creator);

        TeamQuery teamQuery = new TeamQuery();
        teamQuery.setSearchText("羽毛球");
        // Act
        List<TeamUserVO> teams = teamService.listTeams(teamQuery, false);

        // Assert
        List<Long> resultTeamIds = teams.stream().map(TeamUserVO::getId).toList();
        // 名称或描述中包含“羽毛球”的队伍都应被查询到
        Assertions.assertTrue(resultTeamIds.contains(descriptionMatchedTeamId));
        Assertions.assertTrue(resultTeamIds.contains(nameMatchedTeamId));
        // 名称或描述中都不包含“羽毛球”的队伍不能被查询到
        Assertions.assertFalse(resultTeamIds.contains(unmatchedTeamId));
    }

}
