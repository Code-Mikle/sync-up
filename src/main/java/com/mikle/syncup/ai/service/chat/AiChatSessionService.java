package com.mikle.syncup.ai.service.chat;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mikle.syncup.ai.model.entity.AiChatSession;

import java.util.List;

public interface AiChatSessionService extends IService<AiChatSession> {

    AiChatSession getOrCreate(long userId, String conversationId);

    AiChatSession getByUserAndConversationId(long userId, String conversationId);

    void markClosedMessage(long chatSessionId, long messageId);

    List<AiChatSession> listSessionsNeedingSummary(int limit);
}
