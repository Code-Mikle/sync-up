package com.mikle.syncup.ai.service.impl;

import com.mikle.syncup.ai.config.AiMemoryProperties;
import com.mikle.syncup.ai.mapper.AiChatSessionMapper;
import com.mikle.syncup.ai.model.entity.AiChatMessage;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.ai.service.AiChatMessageService;
import com.mikle.syncup.ai.service.AiChatSessionService;
import com.mikle.syncup.ai.service.SessionSummaryGenerator;
import com.mikle.syncup.ai.service.SessionSummaryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class SessionSummaryServiceImpl implements SessionSummaryService {

    private static final String NL = System.lineSeparator();

    @Resource
    private AiChatSessionService chatSessionService;

    @Resource
    private AiChatSessionMapper chatSessionMapper;

    @Resource
    private AiChatMessageService chatMessageService;

    @Resource
    private SessionSummaryGenerator summaryGenerator;

    @Resource
    private AiMemoryProperties memoryProperties;

    @Override
    @Async
    public void summarizeAsyncIfNecessary(long userId, long chatSessionId) {
        AiChatSession session = chatSessionService.getById(chatSessionId);
        if (session == null || !Long.valueOf(userId).equals(session.getUserId())) {
            return;
        }
        try {
            summarizeIfNecessary(session);
        } catch (RuntimeException e) {
            log.warn("summarize chat session failed, sessionId={}, errorType={}",
                    chatSessionId, e.getClass().getSimpleName());
        }
    }

    @Override
    public boolean summarizeIfNecessary(AiChatSession session) {
        if (session == null || session.getId() == null || !summaryGenerator.isAvailable()) {
            return false;
        }
        AiChatSession current = chatSessionService.getById(session.getId());
        if (current == null) {
            return false;
        }
        long summaryCursor = safeLong(current.getLastSummaryMessageId());
        long closedCursor = safeLong(current.getLastClosedMessageId());
        if (closedCursor <= summaryCursor) {
            return false;
        }
        AiMemoryProperties.WorkingMemory properties = memoryProperties.getWorkingMemory();
        int queryLimit = Math.min(200, properties.getRecentMessageCount()
                + properties.getSummaryBatchSize() + 100);
        List<AiChatMessage> unsummarized = chatMessageService.listClosedMessages(
                current.getId(), summaryCursor, closedCursor, queryLimit);
        if (unsummarized.isEmpty()) {
            return false;
        }
        int estimatedTokens = estimateTokens(unsummarized) + estimateTokens(current.getSummary());
        int minimumCount = properties.getRecentMessageCount() + properties.getSummaryBatchSize();
        if (unsummarized.size() < minimumCount && estimatedTokens <= properties.getMaxContextTokens()) {
            return false;
        }
        int keepCount = Math.min(properties.getRecentMessageCount(), Math.max(0, unsummarized.size() - 1));
        int summarizable = unsummarized.size() - keepCount;
        if (summarizable <= 0) {
            return false;
        }
        int batchCount = chooseBatchCount(unsummarized, summarizable, current.getSummary(),
                properties.getSummaryInputMaxTokens());
        List<AiChatMessage> batch = unsummarized.subList(0, batchCount);
        String generated = summaryGenerator.summarize(buildSummaryInput(current.getSummary(), batch));
        String summary = sanitizeSummary(generated, properties.getSummaryMaxChars());
        if (StringUtils.isBlank(summary)) {
            throw new IllegalArgumentException("generated session summary is blank");
        }
        long targetCursor = batch.getLast().getId();
        return chatSessionMapper.updateSummaryCas(
                current.getId(), summaryCursor, targetCursor, summary,
                summaryGenerator.modelName(), summaryGenerator.promptVersion(), new Date()) > 0;
    }

    @Override
    public int processPendingSummaries() {
        if (!summaryGenerator.isAvailable()) {
            return 0;
        }
        int updated = 0;
        for (AiChatSession session : chatSessionService.listSessionsNeedingSummary(100)) {
            try {
                if (summarizeIfNecessary(session)) {
                    updated++;
                }
            } catch (RuntimeException e) {
                log.warn("scheduled session summary failed, sessionId={}, errorType={}",
                        session.getId(), e.getClass().getSimpleName());
            }
        }
        return updated;
    }

    private int chooseBatchCount(List<AiChatMessage> messages,
                                 int summarizable,
                                 String oldSummary,
                                 int maxInputTokens) {
        int result = 0;
        int usedTokens = estimateTokens(oldSummary);
        for (int i = 0; i < summarizable; i++) {
            int nextTokens = estimateTokens(messages.get(i).getContent());
            if (result > 0 && usedTokens + nextTokens > maxInputTokens) {
                break;
            }
            usedTokens += nextTokens;
            result++;
        }
        if (result == 0) {
            return 1;
        }
        return Math.min(summarizable, result);
    }

    private String buildSummaryInput(String oldSummary, List<AiChatMessage> messages) {
        StringBuilder builder = new StringBuilder("旧摘要：").append(NL)
                .append(StringUtils.defaultIfBlank(oldSummary, "暂无"))
                .append(NL).append(NL).append("新增原始消息：");
        for (AiChatMessage message : messages) {
            builder.append(NL).append("[").append(roleLabel(message.getRole())).append("] ")
                    .append(StringUtils.defaultString(message.getContent()));
        }
        return builder.toString();
    }

    private String sanitizeSummary(String summary, int maxChars) {
        String sanitized = StringUtils.defaultString(summary).trim()
                .replaceAll("(?i)(token|api[_-]?key|password|密码)\\s*[:：=]\\s*\\S+", "$1=***")
                .replaceAll("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b", "***@***")
                .replaceAll("1[3-9]\\d{9}", "1**********");
        return sanitized.substring(0, Math.min(Math.max(1, maxChars), sanitized.length()));
    }

    private int estimateTokens(List<AiChatMessage> messages) {
        return messages.stream().mapToInt(message -> estimateTokens(message.getContent())).sum();
    }

    private int estimateTokens(String text) {
        String value = StringUtils.defaultString(text);
        int tokens = 0;
        int latinChars = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) tokens++;
            else latinChars++;
            offset += Character.charCount(codePoint);
        }
        return tokens + (latinChars + 3) / 4;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private String roleLabel(String role) {
        return switch (StringUtils.defaultString(role)) {
            case "user" -> "用户";
            case "assistant" -> "助手";
            case "event" -> "业务事件";
            default -> "消息";
        };
    }
}
