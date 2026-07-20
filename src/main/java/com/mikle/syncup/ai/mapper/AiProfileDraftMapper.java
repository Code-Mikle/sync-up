package com.mikle.syncup.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mikle.syncup.ai.model.entity.AiProfileDraft;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;

public interface AiProfileDraftMapper extends BaseMapper<AiProfileDraft> {

    @Select("SELECT id, draftId, userId, sourceText, profileJson, status, expiresAt, confirmedAt, " +
            "modelVersion, createTime, updateTime, isDelete FROM ai_profile_draft " +
            "WHERE draftId = #{draftId} AND isDelete = 0 FOR UPDATE")
    AiProfileDraft lockByDraftId(@Param("draftId") String draftId);

    @Delete("DELETE FROM ai_profile_draft WHERE expiresAt <= #{now}")
    int deleteExpiredPhysically(@Param("now") Date now);
}
