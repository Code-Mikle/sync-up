package com.mikle.syncup.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mikle.syncup.ai.model.AiChatMemory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

public interface AiChatMemoryMapper extends BaseMapper<AiChatMemory> {

    @Delete("DELETE FROM ai_chat_memory WHERE expireAt < #{now}")
    int deleteExpiredPhysically(@Param("now") Date now);

    @Delete("DELETE FROM ai_chat_memory WHERE memoryId = #{memoryId}")
    int deleteByMemoryIdPhysically(@Param("memoryId") String memoryId);
}
