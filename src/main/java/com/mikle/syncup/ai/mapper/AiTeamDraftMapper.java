package com.mikle.syncup.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mikle.syncup.ai.model.AiTeamDraft;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AiTeamDraftMapper extends BaseMapper<AiTeamDraft> {

    @Select("SELECT id, draftId, sessionId, userId, name, description, maxNum, activityType, city, district, " +
            "startTime, durationMinutes, budgetPerPerson, skillLevel, status, confirmedTeamId, confirmedAt, " +
            "expiresAt, createTime, updateTime, isDelete FROM ai_team_draft " +
            "WHERE draftId = #{draftId} AND isDelete = 0 FOR UPDATE")
    AiTeamDraft lockByDraftId(@Param("draftId") String draftId);
}
