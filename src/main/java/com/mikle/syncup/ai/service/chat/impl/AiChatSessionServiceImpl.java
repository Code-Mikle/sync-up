package com.mikle.syncup.ai.service.chat.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mikle.syncup.ai.mapper.AiChatSessionMapper;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.ai.service.chat.AiChatSessionService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class AiChatSessionServiceImpl extends ServiceImpl<AiChatSessionMapper, AiChatSession>
        implements AiChatSessionService {

    @Resource
    private AiChatSessionMapper sessionMapper;

    @Override
    public AiChatSession getOrCreate(long userId, String conversationId) {
        AiChatSession existing = getByUserAndConversationId(userId, conversationId);
        if (existing != null) {
            return existing;
        }
        AiChatSession session = new AiChatSession();
        session.setUserId(userId);
        session.setConversationId(conversationId);
        session.setLastSummaryMessageId(0L);
        session.setLastClosedMessageId(0L);
        session.setLastEpisodeExtractedMessageId(0L);
        session.setLastMessageAt(new Date());
        try {
            save(session);
            return session;
        } catch (DuplicateKeyException ignored) {
            AiChatSession concurrent = getByUserAndConversationId(userId, conversationId);
            if (concurrent != null) {
                return concurrent;
            }
            throw ignored;
        }
    }

    @Override
    public AiChatSession getByUserAndConversationId(long userId, String conversationId) {
        return getOne(new QueryWrapper<AiChatSession>()
                .eq("userId", userId)
                .eq("conversationId", conversationId)
                .last("limit 1"));
    }

    @Override
    public void markClosedMessage(long chatSessionId, long messageId) {
        if (chatSessionId <= 0 || messageId <= 0) {
            return;
        }
        if (sessionMapper.advanceLastClosedMessage(chatSessionId, messageId) == 0) {
            update(null, new UpdateWrapper<AiChatSession>()
                    .set("lastClosedMessageId", messageId)
                    .set("updateTime", new Date())
                    .eq("id", chatSessionId)
                    .eq("lastClosedMessageId", 0));
        }
    }

    @Override
    public List<AiChatSession> listSessionsNeedingSummary(int limit) {
        return list(new QueryWrapper<AiChatSession>()
                .apply("lastClosedMessageId > lastSummaryMessageId")
                .orderByAsc("updateTime")
                .last("limit " + Math.max(1, Math.min(limit, 100))));
    }
}
