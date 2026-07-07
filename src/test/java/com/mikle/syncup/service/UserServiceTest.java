package com.mikle.syncup.service;

import com.mikle.syncup.model.domain.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 用户服务测试
 */
@SpringBootTest
public class UserServiceTest {

    @Resource
    private UserService userService;

    @Test
    public void testAddUser() {
        User user = new User();
        user.setUsername("mikle");
        user.setUserAccount("123");
        user.setAvatarUrl("");
        user.setGender(0);
        user.setUserPassword("xxx");
        user.setPhone("123");
        user.setEmail("456");
        boolean result = userService.save(user);
        System.out.println(user.getId());
        Assertions.assertTrue(result);
    }

    @Test
    public void testUpdateUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("tom");
        user.setUserAccount("123");
        user.setAvatarUrl("");
        user.setGender(0);
        user.setUserPassword("xxx");
        user.setPhone("123");
        user.setEmail("456");
        boolean result = userService.updateById(user);
        Assertions.assertTrue(result);
    }

    @Test
    public void testDeleteUser() {
        boolean result = userService.removeById(1L);
        Assertions.assertTrue(result);
    }

    @Test
    public void testGetUser() {
        User user = userService.getById(1L);
        Assertions.assertNotNull(user);
    }

    @Test
    void updateUser() {
        User user = User.builder()
                .id(2)
                .username("约翰")
                .userAccount("John")
                .avatarUrl("https://images.pexels.com/photos/37669288/pexels-photo-37669288.jpeg")
                .gender(1)
                .userPassword("12345678")
                .phone("15239181001")
                .email("chengdu@126.com")
                .tags("Java")
                .build();

        User loginUser = User.builder()
                .id(2)
                .userRole(0)
                .build();

        int updated = userService.updateUser(user, loginUser);

        Assertions.assertEquals(1, updated);
    }

    @Test
    void userRegister() {
        String userAccount = "John";
        String userPassword = "12345678";
        String checkPassword = "12345678";
        String planetCode = "10001";
        long result = userService.userRegister(userAccount, userPassword, checkPassword, planetCode);
        Assertions.assertEquals(1, result);
    }

    @Test
    public void testSearchUsersByTags() {
        List<String> tagNameList = Arrays.asList("java", "python");
        List<User> userList = userService.searchUsersByTags(tagNameList);
        Assertions.assertNotNull(userList);
    }
}
