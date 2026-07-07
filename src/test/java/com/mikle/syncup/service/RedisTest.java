package com.mikle.syncup.service;

import com.mikle.syncup.model.domain.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import jakarta.annotation.Resource;

/**
 * Redis 测试
 */
@SpringBootTest
public class RedisTest {

    @Resource
    private RedisTemplate redisTemplate;

    @Test
    void test() {
        ValueOperations valueOperations = redisTemplate.opsForValue();
        // 增
        valueOperations.set("TomString", "dog");
        valueOperations.set("TomInt", 1);
        valueOperations.set("TomDouble", 2.0);
        User user = new User();
        user.setId(1L);
        user.setUsername("Tom");
        valueOperations.set("TomUser", user);
        // 查
        Object Tom = valueOperations.get("TomString");
        Assertions.assertTrue("dog".equals((String) Tom));
        Tom = valueOperations.get("TomInt");
        Assertions.assertTrue(1 == (Integer) Tom);
        Tom = valueOperations.get("TomDouble");
        Assertions.assertTrue(2.0 == (Double) Tom);
        System.out.println(valueOperations.get("TomUser"));
        valueOperations.set("TomString", "dog");
        redisTemplate.delete("TomString");
    }
}
