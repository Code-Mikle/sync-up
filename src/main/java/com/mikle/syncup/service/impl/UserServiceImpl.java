package com.mikle.syncup.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.constant.UserConstant;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.vo.UserLoginVO;
import com.mikle.syncup.model.vo.UserSearchResultVO;
import com.mikle.syncup.service.UserService;
import com.mikle.syncup.mapper.UserMapper;
import com.mikle.syncup.utils.AlgorithmUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    /**
     * 盐值，混淆密码
     */
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private static final String SALT = "mikle";

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword, String planetCode) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword, planetCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        userAccount = userAccount.trim();
        planetCode = planetCode.trim();
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (planetCode.length() > 5) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "星球编号过长");
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
        // 星球编号不能重复
        queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("planetCode", planetCode);
        count = userMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "编号重复");
        }
        // 2. 加密
        String encryptPassword = PASSWORD_ENCODER.encode(userPassword);
        // 3. 插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setPlanetCode(planetCode);
        boolean saveResult = this.save(user);
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
        UserLoginVO userLoginVO = new UserLoginVO();
        userLoginVO.setUser(safetyUser);
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
        safetyUser.setPlanetCode(originUser.getPlanetCode());
        safetyUser.setUserRole(originUser.getUserRole());
        safetyUser.setUserStatus(originUser.getUserStatus());
        safetyUser.setCreateTime(originUser.getCreateTime());
        safetyUser.setTags(originUser.getTags());
        return safetyUser;
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
     * 根据标签搜索用户（内存过滤）
     * @param tagNameList 用户要拥有的标签
     */
    @Override
    public List<User> searchUsersByTags(List<String> tagNameList) {
        if (CollectionUtils.isEmpty(tagNameList)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        List<String> normalizedTagNameList = tagNameList.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(normalizedTagNameList)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 1. 先查询所有用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        List<User> userList = userMapper.selectList(queryWrapper);
        // 2. 在内存中判断是否包含要求的标签
        return userList.stream().filter(user -> {
            Set<String> tempTagNameSet = parseTagNameSet(user.getTags());
            for (String tagName : normalizedTagNameList) {
                if (!tempTagNameSet.contains(tagName)) {
                    return false;
                }
            }
            return true;
        }).map(this::getSafetyUser).collect(Collectors.toList());
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
        queryWrapper.select("id", "username", "avatarUrl", "gender", "tags", "createTime", "planetCode");
        if (excludeUserId != null && excludeUserId > 0) {
            queryWrapper.ne("id", excludeUserId);
        }
        queryWrapper.and(qw -> qw.eq("userStatus", 0).or().isNull("userStatus"));
        for (String keyword : normalizedKeywords) {
            queryWrapper.and(qw -> qw.like("username", keyword)
                    .or().like("planetCode", keyword)
                    .or().like("tags", keyword));
        }
        queryWrapper.orderByDesc("updateTime");

        Page<User> userPage = this.page(new Page<>(pageNum, pageSize), queryWrapper);
        Page<UserSearchResultVO> resultPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        resultPage.setRecords(userPage.getRecords()
                .stream()
                .map(this::getUserSearchResultVO)
                .collect(Collectors.toList()));
        return resultPage;
    }

    private UserSearchResultVO getUserSearchResultVO(User originUser) {
        if (originUser == null) {
            return null;
        }
        UserSearchResultVO userSearchResultVO = new UserSearchResultVO();
        userSearchResultVO.setId(originUser.getId());
        userSearchResultVO.setUsername(originUser.getUsername());
        userSearchResultVO.setAvatarUrl(originUser.getAvatarUrl());
        userSearchResultVO.setGender(originUser.getGender());
        userSearchResultVO.setTags(originUser.getTags());
        userSearchResultVO.setCreateTime(originUser.getCreateTime());
        userSearchResultVO.setPlanetCode(originUser.getPlanetCode());
        return userSearchResultVO;
    }

    private Set<String> parseTagNameSet(String tagsStr) {
        if (StringUtils.isBlank(tagsStr)) {
            return Collections.emptySet();
        }
        String trimmedTags = tagsStr.trim();
        Gson gson = new Gson();
        try {
            if (trimmedTags.startsWith("[")) {
                Set<String> tagNameSet = gson.fromJson(trimmedTags, new TypeToken<Set<String>>() {
                }.getType());
                return Optional.ofNullable(tagNameSet).orElse(Collections.emptySet());
            }
            if (trimmedTags.startsWith("\"")) {
                String tagName = gson.fromJson(trimmedTags, String.class);
                return StringUtils.isBlank(tagName)
                        ? Collections.emptySet()
                        : Collections.singleton(tagName.trim());
            }
        } catch (JsonSyntaxException e) {
            log.warn("parse user tags failed, tags={}", tagsStr, e);
            return Collections.emptySet();
        }
        return Arrays.stream(trimmedTags.split("[,，]"))
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    @Override
    public int updateUser(User user, User loginUser) {
        long userId = user.getId();
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // todo 补充校验，如果用户没有传任何要更新的值，就直接报错，不用执行 update 语句
        // 如果是管理员，允许更新任意用户
        // 如果不是管理员，只允许更新当前（自己的）信息
        if (!isAdmin(loginUser) && userId != loginUser.getId()) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        if (user.getGender() != null && (user.getGender() < 0 || user.getGender() > 2)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "性别参数错误");
        }
        User oldUser = userMapper.selectById(userId);
        if (oldUser == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        return userMapper.updateById(user);
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

    @Override
    public List<User> matchUsers(long num, User loginUser) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "tags");
        queryWrapper.isNotNull("tags");
        List<User> userList = this.list(queryWrapper);
        List<String> tagList = parseTagNameList(loginUser.getTags());
        if (CollectionUtils.isEmpty(tagList)) {
            return new ArrayList<>();
        }
        // 用户列表的下标 => 相似度
        List<Pair<User, Long>> list = new ArrayList<>();
        // 依次计算所有用户和当前用户的相似度
        for (int i = 0; i < userList.size(); i++) {
            User user = userList.get(i);
            String userTags = user.getTags();
            // 无标签或者为当前用户自己
            if (StringUtils.isBlank(userTags) || user.getId() == loginUser.getId()) {
                continue;
            }
            List<String> userTagList = parseTagNameList(userTags);
            if (CollectionUtils.isEmpty(userTagList)) {
                continue;
            }
            // 计算分数
            long distance = AlgorithmUtils.minDistance(tagList, userTagList);
            list.add(new Pair<>(user, distance));
        }
        // 按编辑距离由小到大排序
        List<Pair<User, Long>> topUserPairList = list.stream()
                .sorted((a, b) -> (int) (a.getValue() - b.getValue()))
                .limit(num)
                .collect(Collectors.toList());
        // 原本顺序的 userId 列表
        List<Long> userIdList = topUserPairList.stream().map(pair -> pair.getKey().getId()).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(userIdList)) {
            return new ArrayList<>();
        }
        QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
        userQueryWrapper.in("id", userIdList);
        // 1, 3, 2
        // User1、User2、User3
        // 1 => User1, 2 => User2, 3 => User3
        Map<Long, List<User>> userIdUserListMap = this.list(userQueryWrapper)
                .stream()
                .map(user -> getSafetyUser(user))
                .collect(Collectors.groupingBy(User::getId));
        List<User> finalUserList = new ArrayList<>();
        for (Long userId : userIdList) {
            finalUserList.add(userIdUserListMap.get(userId).get(0));
        }
        return finalUserList;
    }

    private List<String> parseTagNameList(String tagsStr) {
        return new ArrayList<>(parseTagNameSet(tagsStr));
    }

    /**
     * 根据标签搜索用户（SQL 查询版）
     * @param tagNameList 用户要拥有的标签
     * @return
     */
    @Deprecated
    private List<User> searchUsersByTagsBySQL(List<String> tagNameList) {
        if (CollectionUtils.isEmpty(tagNameList)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        // 拼接 and 查询
        // like '%Java%' and like '%Python%'
        for (String tagName : tagNameList) {
            queryWrapper = queryWrapper.like("tags", tagName);
        }
        List<User> userList = userMapper.selectList(queryWrapper);
        return userList.stream().map(this::getSafetyUser).collect(Collectors.toList());
    }

}
