package com.mikle.syncup.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mikle.syncup.assembler.UserAssembler;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.constant.UserConstant;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.ai.service.AiUserProfileService;
import com.mikle.syncup.model.domain.Tag;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.vo.UserLoginVO;
import com.mikle.syncup.model.vo.UserSearchResultVO;
import com.mikle.syncup.service.UserService;
import com.mikle.syncup.service.TagService;
import com.mikle.syncup.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.mikle.syncup.constant.UserConstant.TOKEN_NAME;
import static com.mikle.syncup.constant.UserConstant.TOKEN_PREFIX;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private AiUserProfileService aiUserProfileService;

    @Resource
    private TagService tagService;

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Resource
    private UserAssembler userAssembler;

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        userAccount = userAccount.trim();
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 账户不能包含特殊字符
        String validPattern = "[`~!@#$%^&*()+=|{}':;',\\\\[\\\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号不能包含特殊字符");
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 账户不能重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        long count = userMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        // 2. 加密
        String encryptPassword = PASSWORD_ENCODER.encode(userPassword);
        // 3. 插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        boolean saveResult;
        try {
            saveResult = this.save(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号已存在");
        }
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败");
        }
        return user.getId();
    }

    @Override
    public UserLoginVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) { return null; }
        if (userAccount.length() < 4) { return null; }
        if (userPassword.length() < 8) { return null; }
        // 账户不能包含特殊字符
        String validPattern = "[`~!@#$%^&*()+=|{}':;',\\\\[\\\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find()) { return null; }
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        User user = userMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null || !passwordMatches(userPassword, user.getUserPassword())) {
            log.info("user login failed, userAccount cannot match userPassword");
            return null;
        }
        // 3. 记录用户的登录态
        StpUtil.login(user.getId());
        Date lastActiveTime = new Date();
        updateLastActiveTime(user.getId(), lastActiveTime);
        user.setLastActiveTime(lastActiveTime);
        UserLoginVO userLoginVO = new UserLoginVO();
        userLoginVO.setUser(userAssembler.toCurrentUserVO(user));
        userLoginVO.setToken(StpUtil.getTokenValue());
        userLoginVO.setTokenName(TOKEN_NAME);
        userLoginVO.setTokenPrefix(TOKEN_PREFIX);
        return userLoginVO;
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        if (!StpUtil.isLogin()) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        long userId = StpUtil.getLoginIdAsLong();
        User user = this.getById(userId);
        if (user == null) {
            StpUtil.logout();
            throw new BusinessException(ErrorCode.NOT_LOGIN, "登录用户不存在");
        }
        return userAssembler.toAuthenticatedUser(user);
    }

    /**
     * 用户注销
     */
    @Override
    public int userLogout(HttpServletRequest request) {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
        return 1;
    }

    @Override
    public int updateUser(User user, User loginUser) {
        long userId = user.getId();
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 如果是管理员，允许更新任意用户
        // 如果不是管理员，只允许更新当前（自己的）信息
        if (!isAdmin(loginUser) && userId != loginUser.getId()) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        if (user.getGender() != null && (user.getGender() < 0 || user.getGender() > 2)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "性别参数错误");
        }
        if (user.getCity() != null) {
            user.setCity(user.getCity().trim());
            if (user.getCity().length() > 64) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "城市名称过长");
            }
        }
        if (user.getProfile() != null) {
            user.setProfile(user.getProfile().trim());
            if (user.getProfile().length() > 1000) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "自我介绍过长");
            }
        }
        User oldUser = userMapper.selectById(userId);
        if (oldUser == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        int updated = userMapper.updateById(user);
        if (updated > 0 && user.getProfile() != null
                && !Objects.equals(user.getProfile(), StringUtils.defaultString(oldUser.getProfile()).trim())) {
            tryEnqueueProfileGeneration(user.getId(), user.getProfile());
        }
        return updated;
    }

    /**
     * 根据标准标签 id 搜索用户，默认命中任一标签即可。
     */
    @Override
    public Page<UserSearchResultVO> searchUsersByTags(List<Long> tagIds,
                                                      long pageNum,
                                                      long pageSize,
                                                      Long excludeUserId) {
        if (CollectionUtils.isEmpty(tagIds)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (pageNum <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "pageNum must be greater than 0");
        }
        if (pageSize <= 0 || pageSize > 10) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "pageSize must be between 1 and 10");
        }
        List<Long> normalizedTagIds = tagService.validateEnabledTagIds(tagIds).stream()
                .map(Tag::getId)
                .toList();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "username", "avatarUrl", "gender", "city", "tagIds", "profile",
                        "createTime", "lastActiveTime")
                .eq("userStatus", 0)
                .ne(excludeUserId != null && excludeUserId > 0, "id", excludeUserId);
        queryWrapper.and(wrapper -> {
            Iterator<Long> iterator = normalizedTagIds.iterator();
            wrapper.apply("JSON_CONTAINS(tagIds, CAST({0} AS JSON))", iterator.next());
            while (iterator.hasNext()) {
                wrapper.or().apply("JSON_CONTAINS(tagIds, CAST({0} AS JSON))", iterator.next());
            }
        });
        queryWrapper.orderByDesc("lastActiveTime").orderByDesc("id");
        Page<User> entityPage = userMapper.selectPage(new Page<>(pageNum, pageSize), queryWrapper);
        Page<UserSearchResultVO> resultPage =
                new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        resultPage.setRecords(entityPage.getRecords().stream()
                .map(userAssembler::toPublicUserVO)
                .toList());
        return resultPage;
    }


    /**
     * 根据输入的关键词检索，检索字段范围为 username、profile
     */
    @Override
    public Page<UserSearchResultVO> searchUsersByKeywords(List<String> keywords, long pageNum, long pageSize, Long excludeUserId) {
        if (pageNum <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "pageNum must be greater than 0");
        }
        if (pageSize <= 0 || pageSize > 10) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "pageSize must be between 1 and 10");
        }
        List<String> normalizedKeywords = Optional.ofNullable(keywords)
                .orElse(Collections.emptyList())
                .stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(normalizedKeywords)) {
            return new Page<>(pageNum, pageSize, 0);
        }

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "username", "avatarUrl", "gender", "city", "tagIds", "profile",
                "createTime", "lastActiveTime");
        if (excludeUserId != null && excludeUserId > 0) {
            queryWrapper.ne("id", excludeUserId);
        }
        queryWrapper.and(qw -> qw.eq("userStatus", 0).or().isNull("userStatus"));
        for (String keyword : normalizedKeywords) {
            queryWrapper.and(qw -> qw.like("username", keyword)
                    .or().like("profile", keyword));
        }
        queryWrapper.orderByDesc("updateTime");

        Page<User> userPage = this.page(new Page<>(pageNum, pageSize), queryWrapper);
        Page<UserSearchResultVO> resultPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        resultPage.setRecords(userPage.getRecords()
                .stream()
                .map(userAssembler::toPublicUserVO)
                .collect(Collectors.toList()));
        return resultPage;
    }

    /**
     * 是否为管理员
     */
    @Override
    public boolean isAdmin(HttpServletRequest request) {
        if (!StpUtil.isLogin()) {
            return false;
        }
        User user = getLoginUser(request);
        return isAdmin(user);
    }

    @Override
    public boolean isAdmin(User loginUser) {
        return loginUser != null && loginUser.getUserRole() == UserConstant.ADMIN_ROLE;
    }

    private boolean passwordMatches(String rawPassword, String storedPassword) {
        return StringUtils.isNotBlank(rawPassword)
                && StringUtils.isNotBlank(storedPassword)
                && PASSWORD_ENCODER.matches(rawPassword, storedPassword);
    }

    private void tryEnqueueProfileGeneration(long userId, String sourceText) {
        try {
            aiUserProfileService.onSelfIntroductionChanged(userId, sourceText);
        } catch (Exception e) {
            log.warn("enqueue AI profile generation failed, userId={}", userId, e);
        }
    }

    private void updateLastActiveTime(long userId, Date lastActiveTime) {
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setLastActiveTime(lastActiveTime);
        this.updateById(updateUser);
    }

}
