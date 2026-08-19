package com.mikle.syncup.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mikle.syncup.ai.model.entity.AiUserProfileEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface AiUserProfileMapper extends BaseMapper<AiUserProfileEntity> {

    @Delete("delete from ai_user_profile where userId = #{userId}")
    int deletePhysicallyByUserId(@Param("userId") long userId);
}
