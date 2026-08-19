package com.mikle.syncup.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mikle.syncup.ai.model.entity.AiTeamEmbedding;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface AiTeamEmbeddingMapper extends BaseMapper<AiTeamEmbedding> {

    @Delete("delete from ai_team_embedding where teamId = #{teamId}")
    int deletePhysicallyByTeamId(@Param("teamId") long teamId);
}
