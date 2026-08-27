package com.mikle.syncup.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;

public interface AiChatSessionMapper extends BaseMapper<AiChatSession> {

    @Select("SELECT * FROM ai_chat_session WHERE id = #{sessionId} AND isDelete = 0 FOR UPDATE")
    AiChatSession selectByIdForUpdate(@Param("sessionId") long sessionId);

    @Update("""
            UPDATE ai_chat_session
            SET summary = #{summary},
                lastSummaryMessageId = #{targetCursor},
                summaryVersion = summaryVersion + 1,
                summaryUpdatedAt = #{updatedAt},
                summaryModel = #{model},
                summaryPromptVersion = #{promptVersion}
            WHERE id = #{sessionId}
              AND lastSummaryMessageId = #{expectedCursor}
              AND isDelete = 0
            """)
    int updateSummaryCas(@Param("sessionId") long sessionId,
                         @Param("expectedCursor") long expectedCursor,
                         @Param("targetCursor") long targetCursor,
                         @Param("summary") String summary,
                         @Param("model") String model,
                         @Param("promptVersion") String promptVersion,
                         @Param("updatedAt") Date updatedAt);

    @Update("""
            UPDATE ai_chat_session
            SET lastClosedMessageId = #{messageId}
            WHERE id = #{sessionId}
              AND lastClosedMessageId < #{messageId}
              AND isDelete = 0
            """)
    int advanceLastClosedMessage(@Param("sessionId") long sessionId,
                                 @Param("messageId") long messageId);
}
