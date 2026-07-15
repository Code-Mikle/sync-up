package com.mikle.syncup.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mikle.syncup.model.domain.UserTeam;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * User-team relation mapper.
 */
public interface UserTeamMapper extends BaseMapper<UserTeam> {

    @Delete("DELETE FROM user_team WHERE userId = #{userId} AND teamId = #{teamId}")
    int deleteByUserIdAndTeamIdPhysically(@Param("userId") Long userId, @Param("teamId") Long teamId);

    @Delete("DELETE FROM user_team WHERE userId = #{userId}")
    int deleteByUserIdPhysically(@Param("userId") Long userId);

    @Delete("DELETE ut FROM user_team ut INNER JOIN team t ON ut.teamId = t.id WHERE t.userId = #{userId}")
    int deleteByTeamCreatorUserIdPhysically(@Param("userId") Long userId);

    @Delete("DELETE FROM user_team WHERE teamId = #{teamId}")
    int deleteByTeamIdPhysically(@Param("teamId") Long teamId);

}
