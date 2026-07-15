package com.mikle.syncup.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mikle.syncup.model.domain.Team;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Team mapper.
 */
public interface TeamMapper extends BaseMapper<Team> {

    @Select("SELECT id, name, description, maxNum, expireTime, activityType, city, district, startTime, " +
            "durationMinutes, budgetPerPerson, skillLevel, userId, status, password, createTime, updateTime, isDelete " +
            "FROM team WHERE id = #{teamId} AND isDelete = 0 FOR UPDATE")
    Team lockTeamById(@Param("teamId") Long teamId);

    @Delete("DELETE FROM team WHERE userId = #{userId}")
    int deleteByUserIdPhysically(@Param("userId") Long userId);

    @Delete("DELETE FROM team WHERE id = #{teamId}")
    int deleteByTeamIdPhysically(@Param("teamId") Long teamId);
}
