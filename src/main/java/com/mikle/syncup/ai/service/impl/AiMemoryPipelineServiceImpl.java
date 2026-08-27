package com.mikle.syncup.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mikle.syncup.ai.config.AiMemoryProperties;
import com.mikle.syncup.ai.mapper.AiEpisodeExtractionTaskMapper;
import com.mikle.syncup.ai.mapper.AiChatSessionMapper;
import com.mikle.syncup.ai.mapper.AiUserEpisodeMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileEmbeddingMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileMapper;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.ai.model.entity.AiChatMessage;
import com.mikle.syncup.ai.model.entity.AiEpisodeExtractionTask;
import com.mikle.syncup.ai.model.enums.EpisodeStatus;
import com.mikle.syncup.ai.model.enums.MemorySourceType;
import com.mikle.syncup.ai.model.enums.MemoryTaskStatus;
import com.mikle.syncup.ai.model.enums.ProfileStatus;
import com.mikle.syncup.ai.model.enums.ProfileUpdateTriggerType;
import com.mikle.syncup.ai.service.AiChatSessionService;
import com.mikle.syncup.ai.service.AiChatMessageService;
import com.mikle.syncup.ai.service.AiMemoryPipelineService;
import com.mikle.syncup.ai.service.AiProfileUpdateTaskService;
import com.mikle.syncup.ai.service.SessionSummaryService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class AiMemoryPipelineServiceImpl implements AiMemoryPipelineService {

    @Resource
    private AiChatSessionService chatSessionService;

    @Resource
    private AiChatSessionMapper chatSessionMapper;

    @Resource
    private AiChatMessageService chatMessageService;

    @Resource
    private AiEpisodeExtractionTaskMapper extractionTaskMapper;

    @Resource
    private AiUserEpisodeMapper episodeMapper;

    @Resource
    private AiUserProfileMapper profileMapper;

    @Resource
    private AiUserProfileEmbeddingMapper profileEmbeddingMapper;

    @Resource
    private AiProfileUpdateTaskService profileUpdateTaskService;

    @Resource
    private SessionSummaryService sessionSummaryService;

    @Resource
    private AiMemoryProperties memoryProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onChatTurnCompleted(long userId, AiChatSession session, long lastClosedMessageId) {
        if (session == null || session.getId() == null || lastClosedMessageId <= 0) {
            return;
        }
        chatSessionService.markClosedMessage(session.getId(), lastClosedMessageId);
        createNextChatExtractionTaskIfNecessary(userId, session.getId());
        sessionSummaryService.summarizeAsyncIfNecessary(userId, session.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createNextChatExtractionTaskIfNecessary(long userId, long chatSessionId) {
        if (!memoryProperties.getEpisode().isExtractionEnabled() || userId <= 0 || chatSessionId <= 0) {
            return;
        }
        AiChatSession current = chatSessionMapper.selectByIdForUpdate(chatSessionId);
        if (current == null || !Long.valueOf(userId).equals(current.getUserId())) {
            return;
        }
        long activeTasks = extractionTaskMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiEpisodeExtractionTask>()
                        .eq("chatSessionId", chatSessionId)
                        .in("status", MemoryTaskStatus.PENDING.name(), MemoryTaskStatus.PROCESSING.name()));
        if (activeTasks > 0) {
            return;
        }
        long from = current.getLastEpisodeExtractedMessageId() == null ? 0L : current.getLastEpisodeExtractedMessageId();
        long closed = current.getLastClosedMessageId() == null ? 0L : current.getLastClosedMessageId();
        List<AiChatMessage> messages = chatMessageService.listClosedMessages(
                chatSessionId, from, closed, memoryProperties.getEpisode().getMessageBatchSize());
        if (messages.isEmpty()) {
            return;
        }
        messages = fitExtractionBudget(messages, memoryProperties.getEpisode().getMessageBatchMaxTokens());
        AiEpisodeExtractionTask task = new AiEpisodeExtractionTask();
        task.setUserId(userId);
        task.setChatSessionId(chatSessionId);
        task.setSourceType(MemorySourceType.CHAT_MESSAGE.name());
        task.setFromMessageIdExclusive(from);
        task.setToMessageIdInclusive(messages.getLast().getId());
        task.setStatus(MemoryTaskStatus.PENDING.name());
        task.setRetryCount(0);
        task.setNextRetryAt(new Date());
        try {
            extractionTaskMapper.insert(task);
        } catch (DuplicateKeyException ignored) {
            extractionTaskMapper.update(null, new UpdateWrapper<AiEpisodeExtractionTask>()
                    .set("status", MemoryTaskStatus.PENDING.name())
                    .set("retryCount", 0).set("lastError", null).set("nextRetryAt", new Date())
                    .eq("chatSessionId", chatSessionId)
                    .eq("fromMessageIdExclusive", from)
                    .eq("toMessageIdInclusive", task.getToMessageIdInclusive())
                    .eq("status", MemoryTaskStatus.FAILED.name()));
        }
    }

    private List<AiChatMessage> fitExtractionBudget(List<AiChatMessage> messages, int maxTokens) {
        int budget = Math.max(1000, maxTokens);
        int used = 0;
        int count = 0;
        for (AiChatMessage message : messages) {
            String content = StringUtils.defaultString(message.getContent());
            int estimated = 12;
            int nonHan = 0;
            for (int offset = 0; offset < content.length();) {
                int codePoint = content.codePointAt(offset);
                if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) estimated++;
                else nonHan++;
                offset += Character.charCount(codePoint);
            }
            estimated += (nonHan + 3) / 4;
            if (count > 0 && used + estimated > budget) break;
            used += estimated;
            count++;
        }
        return messages.subList(0, Math.max(1, count));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onSelfIntroductionChanged(long userId, String sourceText) {
        profileUpdateTaskService.supersedeActiveTasks(userId);
        extractionTaskMapper.update(null, new UpdateWrapper<AiEpisodeExtractionTask>()
                .set("status", MemoryTaskStatus.SUPERSEDED.name())
                .eq("userId", userId)
                .eq("sourceType", MemorySourceType.SELF_INTRODUCTION.name())
                .in("status", MemoryTaskStatus.PENDING.name(), MemoryTaskStatus.PROCESSING.name()));
        episodeMapper.update(null, new UpdateWrapper<com.mikle.syncup.ai.model.entity.AiUserEpisode>()
                .set("status", EpisodeStatus.INVALID.name())
                .eq("userId", userId)
                .eq("sourceType", MemorySourceType.SELF_INTRODUCTION.name())
                .ne("status", EpisodeStatus.INVALID.name()));
        profileMapper.update(null, new UpdateWrapper<com.mikle.syncup.ai.model.entity.AiUserProfileEntity>()
                .set("status", ProfileStatus.REBUILD_REQUIRED.name()).eq("userId", userId));
        profileEmbeddingMapper.update(null,
                new UpdateWrapper<com.mikle.syncup.ai.model.entity.AiUserProfileEmbedding>()
                        .set("status", 0).eq("userId", userId).eq("status", 1));
        if (StringUtils.isBlank(sourceText)) {
            profileUpdateTaskService.enqueueRebuildAll(userId, ProfileUpdateTriggerType.SOURCE_DELETED);
            return;
        }
        AiEpisodeExtractionTask task = new AiEpisodeExtractionTask();
        task.setUserId(userId);
        task.setSourceType(MemorySourceType.SELF_INTRODUCTION.name());
        task.setSourceText(sourceText.trim());
        task.setSourceReferenceId("self-intro:" + System.currentTimeMillis());
        task.setStatus(MemoryTaskStatus.PENDING.name());
        task.setRetryCount(0);
        task.setNextRetryAt(new Date());
        extractionTaskMapper.insert(task);
    }
}
