package com.mikle.syncup.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mikle.syncup.ai.model.entity.AiChatSession;

import java.util.List;

public interface AiChatSessionService extends IService<AiChatSession> {

    AiChatSession getOrCreate(long userId, String sessionKey);

    AiChatSession getByUserAndKey(long userId, String sessionKey);

    void markClosedMessage(long sessionId, long messageId);

    List<AiChatSession> listSessionsNeedingSummary(int limit);
}
