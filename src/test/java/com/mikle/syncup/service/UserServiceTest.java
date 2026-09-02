package com.mikle.syncup.service;

import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.Tag;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.vo.UserSearchResultVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles(profiles = "test")
class UserServiceTest {

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Resource
    private UserService userService;

    @Resource
    protected DataSource dataSource;

    @Resource
    private TagService tagService;

    @BeforeEach
    protected void ensureUsingTestDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String databaseName = connection.getCatalog();

            Assertions.assertEquals(
                    "sync_up_test",
                    databaseName,
                    "当前连接的不是 sync_up_test，已停止测试，避免污染开发数据库"
            );
        }
    }

    @Test
    @Transactional
    void userRegister_whenInputValid_shouldCreateUserWithEncryptedPassword() {
        String account = "reg_" + randomSuffix();
        String password = "12345678";
        String checkPassword = "12345678";

        // Act
        long resultRegisterId = userService.userRegister(account, password, checkPassword);

        // Assert
        Assertions.assertTrue(resultRegisterId > 0L);
        // 用户表中能够查到该用户
        User registeredUser = userService.getById(resultRegisterId);
        Assertions.assertNotNull(registeredUser);
        Assertions.assertEquals(account, registeredUser.getUserAccount());
        Assertions.assertNotEquals(password, registeredUser.getUserPassword());
        Assertions.assertTrue(
                PASSWORD_ENCODER.matches(password, registeredUser.getUserPassword())
        );
    }

    @Test
    @Transactional
    void userRegister_whenAccountAlreadyExists_shouldBeRejected() {
        String account = "reg_" + randomSuffix();
        String password = "12345678";
        String checkPassword = "12345678";

        User registeredUser = User.builder()
                .userAccount(account)
                .userPassword(PASSWORD_ENCODER.encode(password))
                .build();

        boolean result = userService.save(registeredUser);
        Assertions.assertTrue(result);

        // Act
        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> userService.userRegister(account, password, checkPassword)
        );

        // Assert
        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        Assertions.assertEquals("账号重复", exception.getDescription());
        // 数据库中仍然只有一条该账号记录
        Long resultUserCount = userService.lambdaQuery()
                .eq(User::getUserAccount, account)
                .count();
        Assertions.assertEquals(1L, resultUserCount);
    }

    @Test
    @Transactional
    void userRegister_whenPasswordsDoNotMatch_shouldBeRejected() {
        String account = "reg_" + randomSuffix();
        String password = "12345678";
        String checkPassword = "12341233";

        // Act
        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> userService.userRegister(account, password, checkPassword)
        );

        // Assert
        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        Assertions.assertEquals("两次输入的密码不一致", exception.getDescription());
        // 数据库中不存在该账号记录
        Long resultUserCount = userService.lambdaQuery()
                .eq(User::getUserAccount, account)
                .count();
        Assertions.assertEquals(0L, resultUserCount);
    }

    @Test
    @Transactional
    void userRegister_whenAccountContainsSpecialCharacters_shouldBeRejected() {
        String account = "reg_!" + randomSuffix();
        String password = "1234~5678";
        String checkPassword = "1234~5678";

        // Act
        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> userService.userRegister(account, password, checkPassword)
        );

        // Assert
        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        Assertions.assertEquals("账号不能包含特殊字符", exception.getDescription());
        // 数据库中不存在该账号记录
        Long resultUserCount = userService.lambdaQuery()
                .eq(User::getUserAccount, account)
                .count();
        Assertions.assertEquals(0L, resultUserCount);
    }

    @Test
    @Transactional
    void userRegister_whenAccountHasSurroundingWhitespace_shouldTrimAccountAndSucceed() {
        String account = "    reg" + randomSuffix() + "   ";
        String password = "1234~5678";
        String checkPassword = "1234~5678";

        // Act
        long registeredId = userService.userRegister(account, password, checkPassword);

        // Assert
        Assertions.assertTrue(registeredId > 0);
        // 数据库中保存的是去除账号前后空格的格式
        User savedUser = userService.lambdaQuery()
                .eq(User::getId, registeredId).one();
        Assertions.assertNotNull(savedUser);
        Assertions.assertEquals(account.trim(), savedUser.getUserAccount());
    }

    @Test
    @Transactional
    void updateUser_normalUserUpdateSelf_shouldSucceed() {
        String account = "reg_" + randomSuffix();
        User normalUser = User.builder()
                .username("西北小子")
                .userAccount(account)
                .city("西安")
                .userPassword("12345678")
                .userRole(0)
                .build();

        boolean saved = userService.save(normalUser);
        Assertions.assertTrue(saved);

        // Act
        normalUser.setUsername("京城世子");
        normalUser.setCity("北京");
        int updated = userService.updateUser(normalUser, normalUser);

        // Assert
        Assertions.assertTrue(updated > 0);
        // 数据库中该对象资料应更新
        User updatedUser = userService.lambdaQuery()
                .eq(User::getId, normalUser.getId()).one();
        Assertions.assertEquals("北京", updatedUser.getCity());
        Assertions.assertEquals("京城世子", updatedUser.getUsername());
    }

    @Test
    @Transactional
    void searchUsersByTags_whenUsersMatchAnyRequestedTag_shouldReturnAllMatchingUsers() {
        Tag tagOne = createEnabledTag();
        Tag tagTwo = createEnabledTag();
        long now = System.currentTimeMillis();

        User currentUser = User.builder()
                .username("user-" + randomSuffix())
                .userAccount("account-" + randomSuffix())
                .userPassword("12345678")
                .userStatus(0)
                .tagIds("[" + tagOne.getId() + "]")
                .build();
        User matchTagOne = User.builder()
                .username("user-" + randomSuffix())
                .userAccount("account-" + randomSuffix())
                .userPassword("12345678")
                .userStatus(0)
                .tagIds("[" + tagOne.getId() + "]")
                .lastActiveTime(new Date(now - 60_000))
                .build();
        User matchTagTwo = User.builder()
                .username("user-" + randomSuffix())
                .userAccount("account-" + randomSuffix())
                .userPassword("12345678")
                .userStatus(0)
                .tagIds("[" + tagTwo.getId() + "]")
                .lastActiveTime(new Date(now))
                .build();
        User disabledUser = User.builder()
                .username("user-" + randomSuffix())
                .userAccount("account-" + randomSuffix())
                .userPassword("12345678")
                .userStatus(1)
                .tagIds("[" + tagOne.getId() + "]")
                .build();

        boolean savedBatch = userService.saveBatch(List.of(currentUser, matchTagOne, matchTagTwo, disabledUser));
        Assertions.assertTrue(savedBatch);

        // Act
        Page<UserSearchResultVO> firstPage = userService.searchUsersByTags(
                List.of(tagOne.getId(), tagTwo.getId()), 1, 1, currentUser.getId());
        Page<UserSearchResultVO> secondPage = userService.searchUsersByTags(
                List.of(tagOne.getId(), tagTwo.getId()), 2, 1, currentUser.getId());

        // Assert
        Assertions.assertEquals(2, firstPage.getTotal());
        Assertions.assertEquals(1, firstPage.getRecords().size());
        Assertions.assertEquals(matchTagTwo.getId(), firstPage.getRecords().getFirst().getId(),
                "最近活跃用户应该排在前面");
        Assertions.assertEquals(1, secondPage.getRecords().size());
        Assertions.assertEquals(matchTagOne.getId(), secondPage.getRecords().getFirst().getId());
        Set<Long> actualUserIds = Set.of(
                firstPage.getRecords().getFirst().getId(),
                secondPage.getRecords().getFirst().getId());
        Assertions.assertEquals(Set.of(matchTagOne.getId(), matchTagTwo.getId()), actualUserIds,
                "应命中任一标签，同时排除当前用户和禁用用户");
    }

    private Tag createEnabledTag() {
        String suffix = randomSuffix();
        Tag tag = Tag.builder()
                .categoryId(1L)
                .code("test-tag-" + suffix)
                .name("测试标签-" + suffix)
                .description("标签搜索测试数据")
                .status(1)
                .sortOrder(0)
                .build();
        Assertions.assertTrue(tagService.save(tag));
        return tag;
    }

    private String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
