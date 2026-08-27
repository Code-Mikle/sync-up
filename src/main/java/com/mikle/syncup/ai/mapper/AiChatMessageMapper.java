package com.mikle.syncup.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mikle.syncup.ai.model.entity.AiChatMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {

    @Delete("""
            <script>
            DELETE FROM ai_chat_message WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deletePhysicallyByIds(@Param("ids") List<Long> ids);
}
