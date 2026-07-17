package com.mikle.syncup.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mikle.syncup.ai.model.AiProfileExtractionTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AiProfileExtractionTaskMapper extends BaseMapper<AiProfileExtractionTask> {

    @Select("SELECT id, taskId, userId, sourceText, extractionJson, status, retryCount, nextRetryAt, " +
            "lastError, modelVersion, createTime, updateTime, isDelete FROM ai_profile_extraction_task " +
            "WHERE taskId = #{taskId} AND isDelete = 0 FOR UPDATE")
    AiProfileExtractionTask lockByTaskId(@Param("taskId") String taskId);
}

