package com.mikle.syncup.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import com.mikle.syncup.model.request.TeamUpdateRequest;
import com.mikle.syncup.model.vo.TeamUserVO;
import com.mikle.syncup.model.vo.UserVO;
import com.mikle.syncup.service.TeamService;
import com.mikle.syncup.service.UserService;
import com.mikle.syncup.service.UserTeamService;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team> implements TeamService {

    private static final Set<String> ALLOWED_SKILL_LEVELS = Set.of("入门", "中等", "熟练");

    @Resource
    private UserTeamService userTeamService;

    @Resource
    private UserService userService;

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserTeamMapper userTeamMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long addTeam(Team team, User loginUser) {
        if (team == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        long userId = loginUser.getId();

        int maxNum = Optional.ofNullable(team.getMaxNum()).orElse(0);
        if (maxNum < 1 || maxNum > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "team size is invalid");
        }
        String name = team.getName();
        if (StringUtils.isBlank(name) || name.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "team name is invalid");
        }
        String description = team.getDescription();
        if (StringUtils.isNotBlank(description) && description.length() > 512) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "team description is too long");
        }
        int status = Optional.ofNullable(team.getStatus()).orElse(0);
        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);
        if (statusEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "team status is invalid");
        }
        String password = team.getPassword();
        if (TeamStatusEnum.SECRET.equals(statusEnum)
                && (StringUtils.isBlank(password) || password.length() > 32)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "team password is invalid");
        }
        Date expireTime = team.getExpireTime();
        if (expireTime != null && new Date().after(expireTime)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "expire time cannot be earlier than now");
        }
        validateStructuredTeamFields(team);

        Long lockUserById = userMapper.lockUserById(userId);
        if (lockUserById == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        long hasTeamNum = this.count(queryWrapper);
        if (hasTeamNum >= 5) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "user can create at most 5 teams");
        }

        team.setId(null);
        team.setUserId(userId);
        boolean result = this.save(team);
        Long teamId = team.getId();
        if (!result || teamId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "create team failed");
        }

        UserTeam userTeam = new UserTeam();
        userTeam.setUserId(userId);
        userTeam.setTeamId(teamId);
        userTeam.setJoinTime(new Date());
        result = userTeamService.save(userTeam);
        if (!result) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "create team failed");
        }
        return teamId;
    }

    @Override
    public List<TeamUserVO> listTeams(TeamQuery teamQuery, boolean isAdmin) {
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
        if (teamQuery != null) {
            Long id = teamQuery.getId();
            if (id != null && id > 0) {
                queryWrapper.eq("id", id);
            }
            List<Long> idList = teamQuery.getIdList();
            if (CollectionUtils.isNotEmpty(idList)) {
                queryWrapper.in("id", idList);
            }
            String searchText = teamQuery.getSearchText();
            if (StringUtils.isNotBlank(searchText)) {
                queryWrapper.and(qw -> qw.like("name", searchText)
                        .or().like("description", searchText));
            }
            String name = teamQuery.getName();
            if (StringUtils.isNotBlank(name)) {
                queryWrapper.like("name", name);
            }
            String description = teamQuery.getDescription();
            if (StringUtils.isNotBlank(description)) {
                queryWrapper.like("description", description);
            }
            Integer maxNum = teamQuery.getMaxNum();
            if (maxNum != null && maxNum > 0) {
                queryWrapper.eq("maxNum", maxNum);
            }
            String activityType = teamQuery.getActivityType();
            if (StringUtils.isNotBlank(activityType)) {
                queryWrapper.eq("activityType", activityType.trim());
            }
            String city = teamQuery.getCity();
            if (StringUtils.isNotBlank(city)) {
                queryWrapper.eq("city", city.trim());
            }
            String district = teamQuery.getDistrict();
            if (StringUtils.isNotBlank(district)) {
                queryWrapper.eq("district", district.trim());
            }
            Date startTimeBegin = teamQuery.getStartTimeBegin();
            Date startTimeEnd = teamQuery.getStartTimeEnd();
            if (startTimeBegin != null && startTimeEnd != null && startTimeBegin.after(startTimeEnd)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "start time range is invalid");
            }
            if (startTimeBegin != null) {
                queryWrapper.ge("startTime", startTimeBegin);
            }
            if (startTimeEnd != null) {
                queryWrapper.le("startTime", startTimeEnd);
            }
            BigDecimal maxBudgetPerPerson = teamQuery.getMaxBudgetPerPerson();
            if (maxBudgetPerPerson != null) {
                if (maxBudgetPerPerson.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "budget range is invalid");
                }
                queryWrapper.le("budgetPerPerson", maxBudgetPerPerson);
            }
            String skillLevel = teamQuery.getSkillLevel();
            if (StringUtils.isNotBlank(skillLevel)) {
                queryWrapper.eq("skillLevel", skillLevel.trim());
            }
            Long userId = teamQuery.getUserId();
            if (userId != null && userId > 0) {
                queryWrapper.eq("userId", userId);
            }
            Integer status = teamQuery.getStatus();
            TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);
            if (status != null && statusEnum == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "team status is invalid");
            }
            if (status == null && !isAdmin) {
                statusEnum = TeamStatusEnum.PUBLIC;
            }
            if (statusEnum != null && !isAdmin && statusEnum.equals(TeamStatusEnum.PRIVATE)) {
                throw new BusinessException(ErrorCode.NO_AUTH);
            }
            if (statusEnum != null) {
                queryWrapper.eq("status", statusEnum.getValue());
            }
        }
        queryWrapper.and(qw -> qw.gt("expireTime", new Date()).or().isNull("expireTime"));
        List<Team> teamList = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(teamList)) {
            return new ArrayList<>();
        }
        if (teamQuery != null && Boolean.TRUE.equals(teamQuery.getOnlyAvailable())) {
            Iterator<Team> iterator = teamList.iterator();
            while (iterator.hasNext()) {
                Team team = iterator.next();
                Integer maxNum = team.getMaxNum();
                if (maxNum == null || countTeamUserByTeamId(team.getId()) >= maxNum) {
                    iterator.remove();
                }
            }
        }
        List<TeamUserVO> teamUserVOList = new ArrayList<>();
        for (Team team : teamList) {
            Long userId = team.getUserId();
            if (userId == null) {
                continue;
            }
            User user = userService.getById(userId);
            TeamUserVO teamUserVO = new TeamUserVO();
            BeanUtils.copyProperties(team, teamUserVO);
            if (user != null) {
                UserVO userVO = new UserVO();
                BeanUtils.copyProperties(user, userVO);
                teamUserVO.setCreateUser(userVO);
            }
            teamUserVOList.add(teamUserVO);
        }
        return teamUserVOList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTeam(TeamUpdateRequest teamUpdateRequest, User loginUser) {
        if (teamUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        Long id = teamUpdateRequest.getId();
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team oldTeam = teamMapper.lockTeamById(id);
        if (oldTeam == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "team does not exist");
        }
        if (!Objects.equals(oldTeam.getUserId(), loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(teamUpdateRequest.getStatus());
        if (teamUpdateRequest.getStatus() != null && statusEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "team status is invalid");
        }
        if (TeamStatusEnum.SECRET.equals(statusEnum) && StringUtils.isBlank(teamUpdateRequest.getPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "secret team password is required");
        }
        Date expireTime = teamUpdateRequest.getExpireTime();
        if (expireTime != null && expireTime.before(new Date())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "expire time cannot be earlier than now");
        }
        Team updateTeam = new Team();
        BeanUtils.copyProperties(teamUpdateRequest, updateTeam);
        validateStructuredTeamFields(updateTeam);
        return this.updateById(updateTeam);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean joinTeam(TeamJoinRequest teamJoinRequest, User loginUser) {
        if (teamJoinRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        Long teamId = teamJoinRequest.getTeamId();
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long userId = loginUser.getId();

        Long lockUserById = userMapper.lockUserById(userId);
        if (lockUserById == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        Team team = teamMapper.lockTeamById(teamId);
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "team does not exist");
        }

        Date expireTime = team.getExpireTime();
        if (expireTime != null && expireTime.before(new Date())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "team has expired");
        }
        Integer status = team.getStatus();
        TeamStatusEnum teamStatusEnum = TeamStatusEnum.getEnumByValue(status);
        if (TeamStatusEnum.PRIVATE.equals(teamStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "cannot join a private team");
        }
        String password = teamJoinRequest.getPassword();
        if (TeamStatusEnum.SECRET.equals(teamStatusEnum)
                && (StringUtils.isBlank(password) || !password.equals(team.getPassword()))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "wrong password");
        }

        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("userId", userId);
        long hasJoinNum = userTeamService.count(userTeamQueryWrapper);
        if (hasJoinNum >= 5) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "can create and join at most 5 teams");
        }

        userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("userId", userId);
        userTeamQueryWrapper.eq("teamId", teamId);
        long hasUserJoinTeam = userTeamService.count(userTeamQueryWrapper);
        if (hasUserJoinTeam > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "user already joined this team");
        }

        long teamHasJoinNum = this.countTeamUserByTeamId(teamId);
        if (teamHasJoinNum >= team.getMaxNum()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "team is full");
        }

        UserTeam userTeam = new UserTeam();
        userTeam.setUserId(userId);
        userTeam.setTeamId(teamId);
        userTeam.setJoinTime(new Date());
        try {
            return userTeamService.save(userTeam);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "user already joined this team");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean quitTeam(TeamQuitRequest teamQuitRequest, User loginUser) {
        if (teamQuitRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        Long teamId = teamQuitRequest.getTeamId();
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = teamMapper.lockTeamById(teamId);
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "team does not exist");
        }

        long userId = loginUser.getId();
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("teamId", teamId);
        queryWrapper.eq("userId", userId);
        long count = userTeamService.count(queryWrapper);
        if (count == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "user has not joined this team");
        }

        long teamHasJoinNum = this.countTeamUserByTeamId(teamId);
        if (teamHasJoinNum == 1) {
            this.removeById(teamId);
        } else if (team.getUserId() == userId) {
            QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
            userTeamQueryWrapper.eq("teamId", teamId);
            userTeamQueryWrapper.last("order by id asc limit 2");
            List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
            if (CollectionUtils.isEmpty(userTeamList) || userTeamList.size() <= 1) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
            UserTeam nextUserTeam = userTeamList.get(1);
            Team updateTeam = new Team();
            updateTeam.setId(teamId);
            updateTeam.setUserId(nextUserTeam.getUserId());
            boolean result = this.updateById(updateTeam);
            if (!result) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "update team leader failed");
            }
        }
        return userTeamMapper.deleteByUserIdAndTeamIdPhysically(userId, teamId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTeam(long id, User loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        Team team = teamMapper.lockTeamById(id);
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "team does not exist");
        }
        long teamId = team.getId();
        if (team.getUserId() != loginUser.getId()) {
            throw new BusinessException(ErrorCode.NO_AUTH, "no permission");
        }
        userTeamMapper.deleteByTeamIdPhysically(teamId);
        return teamMapper.deleteByTeamIdPhysically(teamId) > 0;
    }

    private long countTeamUserByTeamId(long teamId) {
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("teamId", teamId);
        return userTeamService.count(userTeamQueryWrapper);
    }

    private void validateStructuredTeamFields(Team team) {
        if (team == null) {
            return;
        }
        team.setActivityType(trimToNull(team.getActivityType()));
        team.setCity(trimToNull(team.getCity()));
        team.setDistrict(trimToNull(team.getDistrict()));
        team.setSkillLevel(trimToNull(team.getSkillLevel()));

        validateTextLength(team.getActivityType(), 64, "activity type is too long");
        validateTextLength(team.getCity(), 64, "city is too long");
        validateTextLength(team.getDistrict(), 64, "district is too long");
        validateTextLength(team.getSkillLevel(), 32, "skill level is too long");
        if (team.getSkillLevel() != null && !ALLOWED_SKILL_LEVELS.contains(team.getSkillLevel())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "skill level is invalid");
        }

        Integer durationMinutes = team.getDurationMinutes();
        if (durationMinutes != null && durationMinutes <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "duration must be positive");
        }
        BigDecimal budgetPerPerson = team.getBudgetPerPerson();
        if (budgetPerPerson != null && budgetPerPerson.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "budget must be non-negative");
        }
        Date startTime = team.getStartTime();
        if (startTime != null && startTime.before(new Date())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "start time cannot be earlier than now");
        }
    }

    private void validateTextLength(String value, int maxLength, String message) {
        if (value != null && value.length() > maxLength) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, message);
        }
    }

    private String trimToNull(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.trim();
    }
}
