package com.mikle.syncup.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mikle.syncup.common.BaseResponse;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.common.PageResult;
import com.mikle.syncup.common.ResultUtils;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.request.UserLoginRequest;
import com.mikle.syncup.model.request.UserRegisterRequest;
import com.mikle.syncup.model.request.UserUpdateRequest;
import com.mikle.syncup.model.vo.UserLoginVO;
import com.mikle.syncup.model.vo.UserSearchResultVO;
import com.mikle.syncup.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 用户接口
 */
@RestController
@RequestMapping("/user")
@CrossOrigin(origins = {"http://localhost:3000"})
@Slf4j
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        String planetCode = userRegisterRequest.getPlanetCode();
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword, planetCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        long result = userService.userRegister(userAccount, userPassword, checkPassword, planetCode);
        return ResultUtils.success(result);
    }

    @PostMapping("/login")
    public BaseResponse<UserLoginVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        if (userLoginRequest == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }
        UserLoginVO userLoginVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(userLoginVO);
    }

    @PostMapping("/logout")
    public BaseResponse<Integer> userLogout(HttpServletRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        int result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    @GetMapping("/current")
    public BaseResponse<User> getCurrentUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(loginUser);
    }

    @GetMapping("/search")
    public BaseResponse<List<User>> searchUsers(String username, HttpServletRequest request) {
        if (!userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(username)) {
            queryWrapper.like("username", username);
        }
        List<User> userList = userService.list(queryWrapper);
        List<User> list = userList.stream()
                .map(user -> userService.getSafetyUser(user))
                .collect(Collectors.toList());
        return ResultUtils.success(list);
    }

    @GetMapping("/search/tags")
    public BaseResponse<List<UserSearchResultVO>> searchUsersByTags(@RequestParam(required = false) List<String> tagNameList) {
        if (CollectionUtils.isEmpty(tagNameList)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        List<User> userList = userService.searchUsersByTags(tagNameList);
        return ResultUtils.success(
                userList.stream()
                        .map(userService::getPublicUser)
                        .toList()
        );
    }

    @GetMapping("/search/keywords")
    public BaseResponse<Page<UserSearchResultVO>> searchUsersByKeywords(@RequestParam(required = false) List<String> keywords,
                                                                        @RequestParam(defaultValue = "1") long pageNum,
                                                                        @RequestParam(defaultValue = "5") long pageSize,
                                                                        HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Page<UserSearchResultVO> userPage = userService.searchUsersByKeywords(keywords, pageNum, pageSize, loginUser.getId());
        return ResultUtils.success(userPage);
    }

    @GetMapping("/recommend")
    public BaseResponse<Page<UserSearchResultVO>> recommendUsers(long pageSize, long pageNum, HttpServletRequest request) {
        if (pageNum <= 0 || pageSize <= 0 || pageSize > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分页参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        String redisKey = String.format("syncup:user:recommend:public:v3:%s:%s:%s", loginUser.getId(), pageNum, pageSize);
        ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();
        Object cachedValue = valueOperations.get(redisKey);
        if (cachedValue instanceof PageResult<?> cachedPage) {
            Page<UserSearchResultVO> cachedUserPage = toUserSearchResultPage(cachedPage);
            if (cachedUserPage != null) {
                return ResultUtils.success(cachedUserPage);
            }
        }
        // 无缓存，查数据库
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "username", "avatarUrl", "gender", "city", "tags", "createTime", "lastActiveTime", "planetCode")
                .ne("id", loginUser.getId())
                .and(qw -> qw.eq("userStatus", 0).or().isNull("userStatus"));
        Page<User> entityPage = userService.page(new Page<>(pageNum, pageSize), queryWrapper);
        Page<UserSearchResultVO> userPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        userPage.setRecords(entityPage.getRecords()
                .stream()
                .map(userService::getPublicUser)
                .toList()
        );
        // 写缓存
        try {
            valueOperations.set(redisKey, PageResult.of(userPage), 30000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("redis set key error", e);
        }
        return ResultUtils.success(userPage);
    }

    private Page<UserSearchResultVO> toUserSearchResultPage(PageResult<?> pageResult) {
        List<UserSearchResultVO> records = new ArrayList<>();
        if (pageResult.getRecords() != null) {
            for (Object record : pageResult.getRecords()) {
                if (!(record instanceof UserSearchResultVO userSearchResultVO)) {
                    return null;
                }
                records.add(userSearchResultVO);
            }
        }
        Page<UserSearchResultVO> page = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        page.setRecords(records);
        return page;
    }


    @PostMapping("/update")
    public BaseResponse<Integer> updateUser(@Valid @RequestBody UserUpdateRequest updateRequest,
                                            HttpServletRequest request) {
        // 校验参数是否为空
        if (updateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        User user = new User();
        BeanUtils.copyProperties(updateRequest, user);
        user.setId(updateRequest.getId());
        int result = userService.updateUser(user, loginUser);
        return ResultUtils.success(result);
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteUser(@RequestBody long id, HttpServletRequest request) {
        if (!userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = userService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 获取最匹配的用户
     */
    @GetMapping("/match")
    public BaseResponse<List<UserSearchResultVO>> matchUsers(long num, HttpServletRequest request) {
        if (num <= 0 || num > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        return ResultUtils.success(
                userService.matchUsers(num, user)
                .stream()
                .map(userService::getPublicUser)
                .toList()
        );
    }
}
