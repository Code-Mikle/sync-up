package com.mikle.syncup.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mikle.syncup.common.BaseResponse;
import com.mikle.syncup.common.DeleteRequest;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.common.ResultUtils;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.Team;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.domain.UserTeam;
import com.mikle.syncup.model.dto.TeamQuery;
import com.mikle.syncup.model.request.TeamAddRequest;
import com.mikle.syncup.model.request.TeamJoinRequest;
import com.mikle.syncup.model.request.TeamQuitRequest;
import com.mikle.syncup.model.request.TeamUpdateRequest;
import com.mikle.syncup.model.vo.TeamUserVO;
import com.mikle.syncup.service.TeamService;
import com.mikle.syncup.service.UserService;
import com.mikle.syncup.service.UserTeamService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/team")
@CrossOrigin(origins = {"http://localhost:3000"})
@Slf4j
public class TeamController {

    @Resource
    private UserService userService;

    @Resource
    private TeamService teamService;

    @Resource
    private UserTeamService userTeamService;

    @PostMapping("/add")
    public BaseResponse<Long> addTeam(@Valid @RequestBody TeamAddRequest teamAddRequest, HttpServletRequest request) {
        if (teamAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        Team team = new Team();
        BeanUtils.copyProperties(teamAddRequest, team);
        long teamId = teamService.addTeam(team, loginUser);
        return ResultUtils.success(teamId);
    }

    @PostMapping("/update")
    public BaseResponse<Boolean> updateTeam(@Valid @RequestBody TeamUpdateRequest teamUpdateRequest,
                                            HttpServletRequest request) {
        if (teamUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.updateTeam(teamUpdateRequest, loginUser);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "update team failed");
        }
        return ResultUtils.success(true);
    }

    @GetMapping("/get")
    public BaseResponse<Team> getTeamById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = teamService.getById(id);
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        if (!Objects.equals(team.getUserId(), loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        return ResultUtils.success(team);
    }

    @GetMapping("/list")
    public BaseResponse<List<TeamUserVO>> listTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean isAdmin = userService.isAdmin(request);
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, isAdmin);
        try {
            User loginUser = userService.getLoginUser(request);
            fillTeamHasJoin(teamList, loginUser);
        } catch (BusinessException e) {
            if (e.getCode() != ErrorCode.NOT_LOGIN.getCode()) {
                throw e;
            }
            // Anonymous users can still browse the public team list.
        }
        fillTeamHasJoinNum(teamList);
        return ResultUtils.success(teamList);
    }

    @GetMapping("/list/page")
    public BaseResponse<Page<TeamUserVO>> listTeamsByPage(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        int pageNum = teamQuery.getPageNum();
        int pageSize = teamQuery.getPageSize();
        if (pageNum <= 0 || pageSize <= 0 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分页参数错误");
        }
        boolean isAdmin = userService.isAdmin(request);
        List<TeamUserVO> teams = teamService.listTeams(teamQuery, isAdmin);
        try {
            fillTeamHasJoin(teams, userService.getLoginUser(request));
        } catch (BusinessException e) {
            if (e.getCode() != ErrorCode.NOT_LOGIN.getCode()) {
                throw e;
            }
            // Anonymous users can browse public teams.
        }
        fillTeamHasJoinNum(teams);
        long offset = (long) (pageNum - 1) * pageSize;
        int fromIndex = (int) Math.min(offset, teams.size());
        int toIndex = Math.min(fromIndex + pageSize, teams.size());
        Page<TeamUserVO> resultPage = new Page<>(pageNum, pageSize, teams.size());
        resultPage.setRecords(new ArrayList<>(teams.subList(fromIndex, toIndex)));
        return ResultUtils.success(resultPage);
    }

    @PostMapping("/join")
    public BaseResponse<Boolean> joinTeam(@Valid @RequestBody TeamJoinRequest teamJoinRequest, HttpServletRequest request) {
        if (teamJoinRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.joinTeam(teamJoinRequest, loginUser);
        return ResultUtils.success(result);
    }

    @PostMapping("/quit")
    public BaseResponse<Boolean> quitTeam(@Valid @RequestBody TeamQuitRequest teamQuitRequest, HttpServletRequest request) {
        if (teamQuitRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.quitTeam(teamQuitRequest, loginUser);
        return ResultUtils.success(result);
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteTeam(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.deleteTeam(id, loginUser);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "delete team failed");
        }
        return ResultUtils.success(true);
    }

    @GetMapping("/list/my/create")
    public BaseResponse<List<TeamUserVO>> listMyCreateTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        teamQuery.setUserId(loginUser.getId());
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, true);
        fillTeamHasJoin(teamList, loginUser);
        fillTeamHasJoinNum(teamList);
        return ResultUtils.success(teamList);
    }

    @GetMapping("/list/my/join")
    public BaseResponse<List<TeamUserVO>> listMyJoinTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", loginUser.getId());
        List<UserTeam> userTeamList = userTeamService.list(queryWrapper);
        Map<Long, List<UserTeam>> listMap = userTeamList.stream()
                .collect(Collectors.groupingBy(UserTeam::getTeamId));
        List<Long> idList = new ArrayList<>(listMap.keySet());
        if (idList.isEmpty()) {
            return ResultUtils.success(new ArrayList<>());
        }
        teamQuery.setIdList(idList);
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, true);
        fillTeamHasJoin(teamList, loginUser);
        fillTeamHasJoinNum(teamList);
        return ResultUtils.success(teamList);
    }

    private void fillTeamHasJoin(List<TeamUserVO> teamList, User loginUser) {
        if (teamList == null || teamList.isEmpty() || loginUser == null) {
            return;
        }
        List<Long> teamIdList = teamList.stream().map(TeamUserVO::getId).collect(Collectors.toList());
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("userId", loginUser.getId());
        userTeamQueryWrapper.in("teamId", teamIdList);
        List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
        Set<Long> hasJoinTeamIdSet = userTeamList.stream().map(UserTeam::getTeamId).collect(Collectors.toSet());
        teamList.forEach(team -> team.setHasJoin(hasJoinTeamIdSet.contains(team.getId())));
    }

    private void fillTeamHasJoinNum(List<TeamUserVO> teamList) {
        if (teamList == null || teamList.isEmpty()) {
            return;
        }
        List<Long> teamIdList = teamList.stream().map(TeamUserVO::getId).collect(Collectors.toList());
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.in("teamId", teamIdList);
        List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
        Map<Long, List<UserTeam>> teamIdUserTeamList = userTeamList.stream()
                .collect(Collectors.groupingBy(UserTeam::getTeamId));
        teamList.forEach(team ->
                team.setHasJoinNum(teamIdUserTeamList.getOrDefault(team.getId(), new ArrayList<>()).size()));
    }
}
