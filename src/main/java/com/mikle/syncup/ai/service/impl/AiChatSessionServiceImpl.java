package com.mikle.syncup.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mikle.syncup.ai.mapper.AiChatSessionMapper;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.ai.service.AiChatSessionService;
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
    public AiChatSession getOrCreate(long userId, String sessionKey) {
        AiChatSession existing = getByUserAndKey(userId, sessionKey);
        if (existing != null) {
            return existing;
        }
        AiChatSession session = new AiChatSession();
        session.setUserId(userId);
        session.setSessionKey(sessionKey);
        session.setLastSummaryMessageId(0L);
        session.setSummaryVersion(0);
        session.setLastClosedMessageId(0L);
        session.setLastEpisodeExtractedMessageId(0L);
        try {
            save(session);
            return session;
        } catch (DuplicateKeyException ignored) {
            AiChatSession concurrent = getByUserAndKey(userId, sessionKey);
            if (concurrent != null) {
                return concurrent;
            }
            throw ignored;
        }
    }

    @Override
    public AiChatSession getByUserAndKey(long userId, String sessionKey) {
        return getOne(new QueryWrapper<AiChatSession>()
                .eq("userId", userId)
                .eq("sessionKey", sessionKey)
                .last("limit 1"));
    }

    @Override
    public void markClosedMessage(long sessionId, long messageId) {
        if (sessionId <= 0 || messageId <= 0) {
            return;
        }
        if (sessionMapper.advanceLastClosedMessage(sessionId, messageId) == 0) {
            update(null, new UpdateWrapper<AiChatSession>()
                    .set("lastClosedMessageId", messageId)
                    .set("updateTime", new Date())
                    .eq("id", sessionId)
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
