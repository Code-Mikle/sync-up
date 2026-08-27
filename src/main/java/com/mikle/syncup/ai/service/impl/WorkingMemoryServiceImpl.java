package com.mikle.syncup.ai.service.impl;

import com.mikle.syncup.ai.config.AiMemoryProperties;
import com.mikle.syncup.ai.model.entity.AiChatMessage;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.ai.service.AiChatMessageService;
import com.mikle.syncup.ai.service.AiUserProfileService;
import com.mikle.syncup.ai.service.WorkingMemoryService;
import com.mikle.syncup.model.domain.User;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;

@Service
public class WorkingMemoryServiceImpl implements WorkingMemoryService {

    private static final String NL = System.lineSeparator();
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private AiChatMessageService chatMessageService;

    @Resource
    private AiUserProfileService userProfileService;

    @Resource
    private AiMemoryProperties memoryProperties;

    @Override
    public String buildModelContext(AiChatSession session, User loginUser, String currentMessage) {
        if (session == null || session.getId() == null) {
            return "当前服务端时间：" + LocalDateTime.now().format(DATE_TIME_FORMATTER)
                    + NL + "当前用户原始需求：" + currentMessage;
        }
        AiMemoryProperties.WorkingMemory properties = memoryProperties.getWorkingMemory();
        List<AiChatMessage> recentMessages = new ArrayList<>(chatMessageService.listLatestClosedMessages(
                session.getId(), safeLong(session.getLastClosedMessageId()),
                properties.getRecentMessageCount()));
        String interactionProfile = userProfileService.getInteractionProfileText(loginUser.getId());
        trimToBudget(recentMessages, session.getSummary(), currentMessage,
                interactionProfile, properties.getMaxContextTokens());
        StringBuilder builder = new StringBuilder("当前服务端时间：")
                .append(LocalDateTime.now().format(DATE_TIME_FORMATTER));
        if (StringUtils.isNotBlank(interactionProfile)) {
            builder.append(NL).append("内部交流偏好（仅用于调整表达方式，禁止向用户展示或复述）：")
                    .append(NL).append(interactionProfile);
        }
        if (StringUtils.isNotBlank(session.getSummary())) {
            builder.append(NL).append("当前会话摘要：").append(NL).append(session.getSummary());
        }
        if (!recentMessages.isEmpty()) {
            builder.append(NL).append("当前会话近期原始消息：");
            for (AiChatMessage message : recentMessages) {
                builder.append(NL).append("[").append(roleLabel(message.getRole())).append("] ")
                        .append(StringUtils.defaultString(message.getContent()));
            }
        }
        return builder.append(NL).append("当前用户原始需求：").append(currentMessage).toString();
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private void trimToBudget(List<AiChatMessage> messages, String summary, String currentMessage,
                              String interactionProfile, int maxTokens) {
        int fixedTokens = estimateTokens(summary) + estimateTokens(currentMessage) + estimateTokens(interactionProfile) + 100;
        while (messages.size() > 1 && fixedTokens + messages.stream()
                .mapToInt(message -> estimateTokens(message.getContent())).sum() > Math.max(500, maxTokens)) {
            messages.removeFirst();
        }
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

    private String roleLabel(String role) {
        return switch (StringUtils.defaultString(role)) {
            case "user" -> "用户";
            case "assistant" -> "助手";
            case "event" -> "业务事件";
            default -> "消息";
        };
    }
}
