package com.mikle.syncup.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;

public interface AiChatSessionMapper extends BaseMapper<AiChatSession> {

    @Select("SELECT * FROM ai_chat_session WHERE id = #{chatSessionId} AND isDelete = 0 FOR UPDATE")
    AiChatSession selectByIdForUpdate(@Param("chatSessionId") long chatSessionId);

    @Update("""
            UPDATE ai_chat_session
            SET summary = #{summary},
                lastSummaryMessageId = #{targetCursor},
                summaryUpdatedAt = #{updatedAt},
                summaryModel = #{model},
                summaryPromptVersion = #{promptVersion}
            WHERE id = #{chatSessionId}
              AND lastSummaryMessageId = #{expectedCursor}
              AND isDelete = 0
            """)
    int updateSummaryCas(@Param("chatSessionId") long chatSessionId,
                         @Param("expectedCursor") long expectedCursor,
                         @Param("targetCursor") long targetCursor,
                         @Param("summary") String summary,
                         @Param("model") String model,
                         @Param("promptVersion") String promptVersion,
                         @Param("updatedAt") Date updatedAt);

    @Update("""
            UPDATE ai_chat_session
            SET lastClosedMessageId = #{messageId}
            WHERE id = #{chatSessionId}
              AND lastClosedMessageId < #{messageId}
              AND isDelete = 0
            """)
    int advanceLastClosedMessage(@Param("chatSessionId") long chatSessionId,
                                 @Param("messageId") long messageId);
}
