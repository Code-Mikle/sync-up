package com.mikle.syncup.mapper;


import com.mikle.syncup.model.domain.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper
 */
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT id FROM `user` WHERE id = #{userId} FOR UPDATE")
    Long lockUserById(@Param("userId") Long userId);
}




