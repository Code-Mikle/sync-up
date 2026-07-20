package com.mikle.syncup.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.vo.AiTeamDeleteConfirmationVO;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.Team;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.domain.UserTeam;
import com.mikle.syncup.service.TeamService;
import com.mikle.syncup.service.UserTeamService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class PrepareDeleteTeamTool implements AiTool {

    public static final String TOOL_NAME = "prepareDeleteTeam";

    @Resource
    private TeamService teamService;

    @Resource
    private UserTeamService userTeamService;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String type() {
        return "draft";
    }

    @Override
    public AiToolResult execute(TeamIntent intent, User loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        if (intent == null || intent.getTeamId() == null || intent.getTeamId() <= 0) {
            return AiToolResult.failure(name(), type(), "删除队伍前需要先明确要删除的队伍。");
        }
        Team team = teamService.getById(intent.getTeamId());
        if (team == null) {
            return AiToolResult.failure(name(), type(), "没有找到要删除的队伍。");
        }
        if (!Objects.equals(team.getUserId(), loginUser.getId())) {
            return AiToolResult.failure(name(), type(), "只能删除自己创建的队伍。");
        }
        AiTeamDeleteConfirmationVO confirmation = new AiTeamDeleteConfirmationVO();
        confirmation.setTeamId(team.getId());
        confirmation.setName(team.getName());
        confirmation.setDescription(team.getDescription());
        confirmation.setActivityCategory(team.getActivityCategory());
        confirmation.setActivityType(team.getActivityType());
        confirmation.setCity(team.getCity());
        confirmation.setDistrict(team.getDistrict());
        confirmation.setStartTime(team.getStartTime());
        confirmation.setMaxNum(team.getMaxNum());
        confirmation.setHasJoinNum(countJoinedUsers(team.getId()));
        confirmation.setWarning("确认后会删除该队伍，并移除已有成员关系。");
        return AiToolResult.success(name(), type(), "prepared team delete confirmation", confirmation);
    }

    private int countJoinedUsers(Long teamId) {
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("teamId", teamId);
        return Math.toIntExact(userTeamService.count(queryWrapper));
    }
}
