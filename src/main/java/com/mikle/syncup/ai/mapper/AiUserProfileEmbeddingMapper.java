package com.mikle.syncup.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mikle.syncup.ai.model.entity.AiUserProfileEmbedding;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface AiUserProfileEmbeddingMapper extends BaseMapper<AiUserProfileEmbedding> {

    @Delete("delete from ai_user_profile_embedding where userId = #{userId}")
    int deletePhysicallyByUserId(@Param("userId") long userId);
}
