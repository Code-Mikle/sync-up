package com.mikle.syncup.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.constant.UserConstant;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.ai.service.AiUserProfileService;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.vo.UserLoginVO;
import com.mikle.syncup.model.vo.UserSearchResultVO;
import com.mikle.syncup.model.vo.UserVO;
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
import org.springframework.util.DigestUtils;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.mikle.syncup.constant.UserConstant.TOKEN_NAME;
import static com.mikle.syncup.constant.UserConstant.TOKEN_PREFIX;

/**
 * 用户服务实现类
 */
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

    /**
     * 盐值，混淆密码
     */
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private static final String SALT = "mikle";

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
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            return null;
        }
        if (userAccount.length() < 4) {
            return null;
        }
        if (userPassword.length() < 8) {
            return null;
        }
        // 账户不能包含特殊字符
        String validPattern = "[`~!@#$%^&*()+=|{}':;',\\\\[\\\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find()) {
            return null;
        }
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        User user = userMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null || !passwordMatches(userPassword, user.getUserPassword())) {
            log.info("user login failed, userAccount cannot match userPassword");
            return null;
        }
        if (isLegacyMd5Password(user.getUserPassword())) {
            User updateUser = new User();
            updateUser.setId(user.getId());
            updateUser.setUserPassword(PASSWORD_ENCODER.encode(userPassword));
            this.updateById(updateUser);
        }
        // 3. 用户脱敏
        User safetyUser = getSafetyUser(user);
        // 4. 记录用户的登录态
        StpUtil.login(user.getId());
        Date lastActiveTime = new Date();
        updateLastActiveTime(user.getId(), lastActiveTime);
        safetyUser.setLastActiveTime(lastActiveTime);
        UserLoginVO userLoginVO = new UserLoginVO();
        userLoginVO.setUser(getUserVO(safetyUser));
        userLoginVO.setToken(StpUtil.getTokenValue());
        userLoginVO.setTokenName(TOKEN_NAME);
        userLoginVO.setTokenPrefix(TOKEN_PREFIX);
        return userLoginVO;
    }

    private boolean passwordMatches(String rawPassword, String storedPassword) {
        if (StringUtils.isBlank(rawPassword) || StringUtils.isBlank(storedPassword)) {
            return false;
        }
        if (isLegacyMd5Password(storedPassword)) {
            String legacyPassword = DigestUtils.md5DigestAsHex((SALT + rawPassword).getBytes());
            return legacyPassword.equals(storedPassword);
        }
        return PASSWORD_ENCODER.matches(rawPassword, storedPassword);
    }

    private boolean isLegacyMd5Password(String storedPassword) {
        return storedPassword != null && storedPassword.matches("^[a-fA-F0-9]{32}$");
    }

    /**
     * 用户脱敏
     */
    @Override
    public User getSafetyUser(User originUser) {
        if (originUser == null) {
            return null;
        }
        User safetyUser = new User();
        safetyUser.setId(originUser.getId());
        safetyUser.setUsername(originUser.getUsername());
        safetyUser.setUserAccount(originUser.getUserAccount());
        safetyUser.setAvatarUrl(originUser.getAvatarUrl());
        safetyUser.setGender(originUser.getGender());
        safetyUser.setPhone(originUser.getPhone());
        safetyUser.setEmail(originUser.getEmail());
        safetyUser.setCity(originUser.getCity());
        safetyUser.setUserRole(originUser.getUserRole());
        safetyUser.setUserStatus(originUser.getUserStatus());
        safetyUser.setCreateTime(originUser.getCreateTime());
        safetyUser.setLastActiveTime(originUser.getLastActiveTime());
        safetyUser.setTagIds(originUser.getTagIds());
        safetyUser.setProfile(originUser.getProfile());
        return safetyUser;
    }

    @Override
    public UserVO getUserVO(User originUser) {
        if (originUser == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        userVO.setId(originUser.getId());
        userVO.setUsername(originUser.getUsername());
        userVO.setUserAccount(originUser.getUserAccount());
        userVO.setAvatarUrl(originUser.getAvatarUrl());
        userVO.setGender(originUser.getGender());
        userVO.setPhone(originUser.getPhone());
        userVO.setEmail(originUser.getEmail());
        userVO.setCity(originUser.getCity());
        userVO.setUserRole(originUser.getUserRole());
        userVO.setUserStatus(originUser.getUserStatus());
        userVO.setCreateTime(originUser.getCreateTime());
        userVO.setUpdateTime(originUser.getUpdateTime());
        userVO.setLastActiveTime(originUser.getLastActiveTime());
        userVO.setProfile(originUser.getProfile());
        userVO.setTagIds(tagService.parseTagIds(originUser.getTagIds()));
        userVO.setTagNames(tagService.toDisplayTagNames(originUser.getTagIds()));
        return userVO;
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

    /**
     * 根据标准标签 id 搜索用户，默认命中任一标签即可。
     */
    @Override
    public List<User> searchUsersByTags(List<Long> tagIds) {
        if (CollectionUtils.isEmpty(tagIds)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        List<Long> normalizedTagIds = tagService.validateEnabledTagIds(tagIds).stream()
                .map(com.mikle.syncup.model.domain.Tag::getId)
                .toList();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(wrapper -> {
            java.util.Iterator<Long> iterator = normalizedTagIds.iterator();
            wrapper.apply("JSON_CONTAINS(tagIds, CAST({0} AS JSON))", iterator.next());
            while (iterator.hasNext()) {
                wrapper.or().apply("JSON_CONTAINS(tagIds, CAST({0} AS JSON))", iterator.next());
            }
        });
        return userMapper.selectList(queryWrapper).stream()
                .map(this::getSafetyUser)
                .collect(Collectors.toList());
    }

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
        queryWrapper.select("id", "username", "avatarUrl", "gender", "city", "tagIds", "profile", "createTime", "lastActiveTime");
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
                .map(this::getPublicUser)
                .collect(Collectors.toList()));
        return resultPage;
    }

    @Override
    public UserSearchResultVO getPublicUser(User originUser) {
        if (originUser == null) {
            return null;
        }
        UserSearchResultVO userSearchResultVO = new UserSearchResultVO();
        userSearchResultVO.setId(originUser.getId());
        userSearchResultVO.setUsername(originUser.getUsername());
        userSearchResultVO.setAvatarUrl(originUser.getAvatarUrl());
        userSearchResultVO.setGender(originUser.getGender());
        userSearchResultVO.setCity(originUser.getCity());
        userSearchResultVO.setTagNames(tagService.toDisplayTagNames(originUser.getTagIds()));
        userSearchResultVO.setProfile(originUser.getProfile());
        userSearchResultVO.setCreateTime(originUser.getCreateTime());
        userSearchResultVO.setLastActiveTime(originUser.getLastActiveTime());
        return userSearchResultVO;
    }

    private void updateLastActiveTime(long userId, Date lastActiveTime) {
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setLastActiveTime(lastActiveTime);
        this.updateById(updateUser);
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

    private void tryEnqueueProfileGeneration(long userId, String sourceText) {
        try {
            aiUserProfileService.onSelfIntroductionChanged(userId, sourceText);
        } catch (Exception e) {
            log.warn("enqueue AI profile generation failed, userId={}", userId, e);
        }
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
        return getSafetyUser(user);
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

    /**
     * 是否为管理员
     */
    @Override
    public boolean isAdmin(User loginUser) {
        return loginUser != null && loginUser.getUserRole() == UserConstant.ADMIN_ROLE;
    }

}
