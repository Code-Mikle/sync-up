package com.mikle.syncup.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mikle.syncup.ai.model.entity.AiChatMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {

    @Delete("DELETE FROM ai_chat_message WHERE expireAt < #{now}")
    int deleteExpiredPhysically(@Param("now") Date now);
}
