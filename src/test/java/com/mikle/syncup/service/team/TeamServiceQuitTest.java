package com.mikle.syncup.service.team;

import com.mikle.syncup.model.domain.Team;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.domain.UserTeam;
import com.mikle.syncup.model.enums.TeamStatusEnum;
import com.mikle.syncup.model.request.TeamJoinRequest;
import com.mikle.syncup.model.request.TeamQuitRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;


public class TeamServiceQuitTest extends TeamServiceTestSupport{

    @Test
    @Transactional
    void quitTeam_whenMemberQuits_shouldRemoveMembershipAndKeepTeam() {
        User creator = createTestUser();
        User member = createTestUser();
        Long teamId = createTeam(creator.getId(), null, 2, TeamStatusEnum.PUBLIC);
        createMembership(creator.getId(), teamId);

        TeamJoinRequest teamJoinRequest = new TeamJoinRequest();
        teamJoinRequest.setTeamId(teamId);
        boolean joined = teamService.joinTeam(teamJoinRequest, member);
        Assertions.assertTrue(joined);

        // Act: attempts to quit team
        TeamQuitRequest quitRequest = new TeamQuitRequest();
        quitRequest.setTeamId(teamId);
        boolean quitResult = teamService.quitTeam(quitRequest, member);

        // Assert: successfully quited
        Assertions.assertTrue(quitResult);
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
        Long teamId = createTeam(creator.getId(), null, 2, TeamStatusEnum.PUBLIC);
        createMembership(creator.getId(), teamId);

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
    void quitTeam_whenLastMemberQuits_shouldRemoveTeamAndMembership() {
        User creator = createTestUser();
        Long teamId = createTeam(creator.getId(), null, 2, TeamStatusEnum.PUBLIC);
        createMembership(creator.getId(), teamId);

        TeamQuitRequest teamQuitRequest = new TeamQuitRequest();
        teamQuitRequest.setTeamId(teamId);
        // 队长退出队伍
        boolean quitResult = teamService.quitTeam(teamQuitRequest, creator);
        Assertions.assertTrue(quitResult);

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
}
