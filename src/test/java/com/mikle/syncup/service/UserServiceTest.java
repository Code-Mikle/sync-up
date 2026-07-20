package com.mikle.syncup.service;

import com.mikle.syncup.mapper.TeamMapper;
import com.mikle.syncup.mapper.UserMapper;
import com.mikle.syncup.mapper.UserTeamMapper;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.vo.UserSearchResultVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.DigestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserServiceTest {

    private static final String LEGACY_SALT = "mikle";

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Resource
    private UserService userService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private UserTeamMapper userTeamMapper;

    @Autowired
    private MockMvc mockMvc;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void ensureUserRecommendationColumns() {
        addUserColumnIfMissing("city", "alter table user add column city varchar(64) null comment '常驻城市' after email");
        addUserColumnIfMissing("lastActiveTime", "alter table user add column lastActiveTime datetime null comment '最近活跃时间' after updateTime");
    }

    private void addUserColumnIfMissing(String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject("""
                        select count(1)
                        from information_schema.columns
                        where table_schema = database()
                          and table_name = 'user'
                          and column_name = ?
                        """,
                Integer.class,
                columnName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    @Test
    void saveAndGetUser_shouldUseGeneratedTestData() {
        User user = null;
        try {
            user = createTestUser();
            User savedUser = userService.getById(user.getId());
            Assertions.assertNotNull(savedUser);
            Assertions.assertEquals(user.getUserAccount(), savedUser.getUserAccount());
        } finally {
            deletePhysically(user);
        }
    }

    @Test
    void updateUser_shouldOnlyUpdateSelfForNormalUser() {
        User user = null;
        try {
            user = createTestUser();
            User updateUser = new User();
            updateUser.setId(user.getId());
            updateUser.setUsername("updated_" + randomSuffix());
            updateUser.setCity("  Xi'an  ");

            User loginUser = User.builder()
                    .id(user.getId())
                    .userRole(0)
                    .build();

            int updated = userService.updateUser(updateUser, loginUser);

            Assertions.assertEquals(1, updated);
            Assertions.assertEquals(updateUser.getUsername(), userService.getById(user.getId()).getUsername());
            Assertions.assertEquals("Xi'an", userService.getById(user.getId()).getCity());
        } finally {
            deletePhysically(user);
        }
    }

    @Test
    void removeById_shouldDeleteCreatedUser() {
        User user = null;
        try {
            user = createTestUser();
            boolean removed = userService.removeById(user.getId());

            Assertions.assertTrue(removed);
            Assertions.assertNull(userService.getById(user.getId()));
        } finally {
            deletePhysically(user);
        }
    }

    @Test
    void userRegister_shouldStoreBCryptPassword() {
        Long userId = null;
        String userAccount = "reg_" + randomSuffix();
        String rawPassword = "Password123";
        String planetCode = planetCode();
        try {
            userId = userService.userRegister(userAccount, rawPassword, rawPassword, planetCode);
            User savedUser = userService.getById(userId);

            Assertions.assertNotNull(savedUser);
            Assertions.assertFalse(savedUser.getUserPassword().matches("^[a-fA-F0-9]{32}$"));
            Assertions.assertTrue(PASSWORD_ENCODER.matches(rawPassword, savedUser.getUserPassword()));
        } finally {
            deletePhysically(userId);
        }
    }

    @Test
    void userLogin_shouldUpgradeLegacyMd5Password() throws Exception {
        User user = null;
        String rawPassword = "Password123";
        try {
            user = createTestUser();
            String legacyPassword = DigestUtils.md5DigestAsHex((LEGACY_SALT + rawPassword).getBytes());
            User updateUser = new User();
            updateUser.setId(user.getId());
            updateUser.setUserPassword(legacyPassword);
            userService.updateById(updateUser);

            mockMvc.perform(post("/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"userAccount\":\"%s\",\"userPassword\":\"%s\"}",
                                    user.getUserAccount(), rawPassword)))
                    .andExpect(status().isOk());
            User upgradedUser = userService.getById(user.getId());

            Assertions.assertFalse(upgradedUser.getUserPassword().matches("^[a-fA-F0-9]{32}$"));
            Assertions.assertTrue(PASSWORD_ENCODER.matches(rawPassword, upgradedUser.getUserPassword()));
            Assertions.assertNotNull(upgradedUser.getLastActiveTime());
        } finally {
            deletePhysically(user);
        }
    }

    @Test
    void updateUserApi_shouldIgnorePrivilegedAndPasswordFields() throws Exception {
        User user = null;
        try {
            user = createTestUser();
            String originalPassword = user.getUserPassword();
            String loginContent = mockMvc.perform(post("/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"userAccount\":\"%s\",\"userPassword\":\"Password123\"}",
                                    user.getUserAccount())))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode login = objectMapper.readTree(loginContent).path("data");
            String authorization = login.path("tokenPrefix").asText() + " " + login.path("token").asText();

            mockMvc.perform(post("/user/update")
                            .header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"id\":%d,\"username\":\"safe_name\",\"city\":\"Xi'an\",\"userRole\":1,\"userStatus\":1,\"userPassword\":\"hacked\"}",
                                    user.getId())))
                    .andExpect(status().isOk());

            User updated = userService.getById(user.getId());
            Assertions.assertEquals("safe_name", updated.getUsername());
            Assertions.assertEquals("Xi'an", updated.getCity());
            Assertions.assertEquals(0, updated.getUserRole());
            Assertions.assertEquals(0, updated.getUserStatus());
            Assertions.assertEquals(originalPassword, updated.getUserPassword());
        } finally {
            deletePhysically(user);
        }
    }

    @Test
    void searchUsersByTags_shouldReturnCreatedUser() {
        User user = null;
        String tagA = "tag_" + randomSuffix();
        String tagB = "tag_" + randomSuffix();
        try {
            user = createTestUser();
            User updateUser = new User();
            updateUser.setId(user.getId());
            updateUser.setTags(String.format("[\"%s\",\"%s\"]", tagA, tagB));
            userService.updateById(updateUser);

            List<User> userList = userService.searchUsersByTags(Arrays.asList(tagA, tagB));
            List<Long> idList = userList.stream().map(User::getId).collect(Collectors.toList());

            Assertions.assertTrue(idList.contains(user.getId()));
        } finally {
            deletePhysically(user);
        }
    }

    @Test
    void searchUsersByKeywords_shouldReturnPagedPublicUsers() {
        User loginUser = null;
        User matchedUser = null;
        User unmatchedUser = null;
        try {
            loginUser = createTestUser();
            matchedUser = createTestUser();
            unmatchedUser = createTestUser();

            User updateMatchedUser = new User();
            updateMatchedUser.setId(matchedUser.getId());
            updateMatchedUser.setUsername("badminton_" + randomSuffix());
            updateMatchedUser.setTags("[\"Java\",\"羽毛球\",\"健身\"]");
            userService.updateById(updateMatchedUser);

            User updateUnmatchedUser = new User();
            updateUnmatchedUser.setId(unmatchedUser.getId());
            updateUnmatchedUser.setUsername("reading_" + randomSuffix());
            updateUnmatchedUser.setTags("[\"Java\",\"阅读\"]");
            userService.updateById(updateUnmatchedUser);

            Page<UserSearchResultVO> userPage = userService.searchUsersByKeywords(
                    Arrays.asList("Java", "羽毛球"),
                    1,
                    5,
                    loginUser.getId()
            );
            List<Long> idList = userPage.getRecords()
                    .stream()
                    .map(UserSearchResultVO::getId)
                    .collect(Collectors.toList());

            Assertions.assertTrue(idList.contains(matchedUser.getId()));
            Assertions.assertFalse(idList.contains(unmatchedUser.getId()));
            Assertions.assertFalse(idList.contains(loginUser.getId()));
        } finally {
            deletePhysically(loginUser);
            deletePhysically(matchedUser);
            deletePhysically(unmatchedUser);
        }
    }

    @Test
    void searchUsersByKeywords_shouldRejectOversizedPageSize() {
        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> userService.searchUsersByKeywords(Arrays.asList("Java"), 1, 11, null)
        );

        Assertions.assertNotNull(exception);
    }

    @Test
    void matchUsers_shouldSupportPlainStringTags() {
        User currentUser = null;
        User matchedUser = null;
        try {
            currentUser = createTestUser();
            matchedUser = createTestUser();

            currentUser.setTags("Java");

            User updateMatchedUser = new User();
            updateMatchedUser.setId(matchedUser.getId());
            updateMatchedUser.setTags("[\"Java\",\"Python\"]");
            userService.updateById(updateMatchedUser);

            List<User> matchedUsers = userService.matchUsers(10, currentUser);
            List<Long> matchedUserIds = matchedUsers.stream().map(User::getId).collect(Collectors.toList());

            Assertions.assertTrue(matchedUserIds.contains(matchedUser.getId()));
        } finally {
            deletePhysically(currentUser);
            deletePhysically(matchedUser);
        }
    }

    private User createTestUser() {
        User user = new User();
        String suffix = randomSuffix();
        user.setUsername("test_" + suffix);
        user.setUserAccount("account_" + suffix);
        user.setUserPassword(PASSWORD_ENCODER.encode("Password123"));
        user.setPlanetCode(planetCode());
        user.setUserRole(0);
        user.setUserStatus(0);
        boolean saved = userService.save(user);
        Assertions.assertTrue(saved, "test user should be created");
        return user;
    }

    private String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String planetCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 5);
    }

    private void deletePhysically(User user) {
        if (user != null && user.getId() > 0) {
            deletePhysically(user.getId());
        }
    }

    private void deletePhysically(Long userId) {
        if (userId != null && userId > 0) {
            userTeamMapper.deleteByTeamCreatorUserIdPhysically(userId);
            userTeamMapper.deleteByUserIdPhysically(userId);
            teamMapper.deleteByUserIdPhysically(userId);
            userMapper.deleteByIdPhysically(userId);
        }
    }
}
