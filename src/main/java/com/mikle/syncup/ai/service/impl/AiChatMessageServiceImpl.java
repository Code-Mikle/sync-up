package com.mikle.syncup.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.config.AiMemoryProperties;
import com.mikle.syncup.ai.mapper.AiChatMessageMapper;
import com.mikle.syncup.ai.mapper.AiChatSessionMapper;
import com.mikle.syncup.ai.mapper.AiEpisodeExtractionTaskMapper;
import com.mikle.syncup.ai.mapper.AiUserEpisodeMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileEmbeddingMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileMapper;
import com.mikle.syncup.ai.model.entity.AiChatMessage;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.ai.model.entity.AiEpisodeExtractionTask;
import com.mikle.syncup.ai.model.entity.AiUserEpisode;
import com.mikle.syncup.ai.model.entity.AiUserProfileEmbedding;
import com.mikle.syncup.ai.model.entity.AiUserProfileEntity;
import com.mikle.syncup.ai.model.enums.EpisodeStatus;
import com.mikle.syncup.ai.model.enums.MemoryTaskStatus;
import com.mikle.syncup.ai.model.enums.ProfileStatus;
import com.mikle.syncup.ai.model.enums.ProfileUpdateTriggerType;
import com.mikle.syncup.ai.model.vo.AiChatHistoryVO;
import com.mikle.syncup.ai.model.vo.AiChatMessageVO;
import com.mikle.syncup.ai.model.vo.AiChatResponseVO;
import com.mikle.syncup.ai.service.AiChatMessageService;
import com.mikle.syncup.ai.service.AiProfileUpdateTaskService;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class AiChatMessageServiceImpl extends ServiceImpl<AiChatMessageMapper, AiChatMessage>
        implements AiChatMessageService {

    private static final int VISIBLE = 1;
    private static final int HIDDEN = 0;
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ROLE_EVENT = "event";
    private static final String EVENT_TEAM_CREATED = "TEAM_CREATED";
    private static final String EVENT_TEAM_DELETED = "TEAM_DELETED";
    private static final String SUBJECT_TEAM = "TEAM";
    private static final int MAX_CONTENT_LENGTH = 2048;

    @Resource
    private AiChatMessageMapper aiChatMessageMapper;

    @Resource
    private AiChatSessionMapper aiChatSessionMapper;

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
    private AiMemoryProperties memoryProperties;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public AiChatMessage saveUserMessage(User loginUser, AiChatSession session, String content) {
        return saveMessage(loginUser, session, ROLE_USER, content, null, VISIBLE);
    }

    @Override
    public AiChatMessage saveAssistantMessage(User loginUser,
                                               AiChatSession session,
                                               String content,
                                               AiChatResponseVO response) {
        return saveMessage(loginUser, session, ROLE_ASSISTANT, content, writeJson(response), VISIBLE);
    }

    @Override
    public AiChatMessage saveTeamDraftConfirmedEvent(User loginUser, AiChatSession session, String draftId, Long teamId) {
        if (session == null) {
            return null;
        }
        EventPayload payload = new EventPayload(
                EVENT_TEAM_CREATED, SUBJECT_TEAM, teamId, null,
                "CREATED", "SUCCESS", "创建队伍成功：#" + teamId, draftId, teamId);
        return saveMessage(loginUser, session, ROLE_EVENT,
                "用户已确认创建队伍，draftId=" + draftId + "，teamId=" + teamId,
                writeJson(payload), HIDDEN);
    }

    @Override
    public AiChatMessage saveTeamDeletedEvent(User loginUser, AiChatSession session, Long teamId) {
        if (session == null) {
            return null;
        }
        EventPayload payload = new EventPayload(
                EVENT_TEAM_DELETED, SUBJECT_TEAM, teamId, null,
                "DELETED", "SUCCESS", "删除队伍成功：#" + teamId, null, teamId);
        return saveMessage(loginUser, session, ROLE_EVENT,
                "用户已确认删除队伍，teamId=" + teamId,
                writeJson(payload), HIDDEN);
    }

    @Override
    public List<AiChatMessage> listClosedMessages(long chatSessionId,
                                                   long afterMessageId,
                                                   long lastClosedMessageId,
                                                   int limit) {
        if (chatSessionId <= 0 || lastClosedMessageId <= afterMessageId || limit <= 0) {
            return List.of();
        }
        return list(new QueryWrapper<AiChatMessage>()
                .eq("chatSessionId", chatSessionId)
                .gt("id", afterMessageId)
                .le("id", lastClosedMessageId)
                .orderByAsc("id")
                .last("limit " + Math.max(1, Math.min(limit, 200))));
    }

    @Override
    public List<AiChatMessage> listLatestClosedMessages(long chatSessionId,
                                                         long lastClosedMessageId,
                                                         int limit) {
        if (chatSessionId <= 0 || lastClosedMessageId <= 0 || limit <= 0) {
            return List.of();
        }
        List<AiChatMessage> messages = list(new QueryWrapper<AiChatMessage>()
                .eq("chatSessionId", chatSessionId)
                .le("id", lastClosedMessageId)
                .orderByDesc("id")
                .last("limit " + Math.max(1, Math.min(limit, 200))));
        java.util.Collections.reverse(messages);
        return messages;
    }

    @Override
    public AiChatHistoryVO getLatestHistory(User loginUser) {
        validateLoginUser(loginUser);
        AiChatHistoryVO history = new AiChatHistoryVO();
        AiChatSession latest = aiChatSessionMapper.selectOne(new QueryWrapper<AiChatSession>()
                .eq("userId", loginUser.getId())
                .orderByDesc("updateTime")
                .last("limit 1"));
        if (latest == null) {
            return history;
        }
        history.setSessionId(latest.getSessionKey());
        List<AiChatMessage> messages = list(new QueryWrapper<AiChatMessage>()
                .eq("chatSessionId", latest.getId())
                .orderByAsc("id"));
        history.setMessages(messages.stream().map(message -> toVO(message, latest.getSessionKey())).toList());
        return history;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteExpiredPhysically() {
        List<AiChatMessage> expired = list(new QueryWrapper<AiChatMessage>()
                .isNotNull("retentionExpireAt").lt("retentionExpireAt", new Date())
                .orderByAsc("id").last("limit 1000"));
        if (expired.isEmpty()) return 0;
        List<Long> messageIds = expired.stream().map(AiChatMessage::getId).toList();
        Set<Long> sessionIds = expired.stream().map(AiChatMessage::getChatSessionId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String messageIdsJson = writeJson(messageIds);
        List<AiUserEpisode> affectedEpisodes = episodeMapper.selectList(new QueryWrapper<AiUserEpisode>()
                .apply("JSON_OVERLAPS(sourceMessageIds, CAST({0} AS JSON))", messageIdsJson));
        Set<Long> affectedUsers = affectedEpisodes.stream().map(AiUserEpisode::getUserId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!affectedEpisodes.isEmpty()) {
            episodeMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AiUserEpisode>()
                    .set("status", EpisodeStatus.INVALID.name())
                    .in("id", affectedEpisodes.stream().map(AiUserEpisode::getId).toList()));
        }
        if (!sessionIds.isEmpty()) {
            extractionTaskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AiEpisodeExtractionTask>()
                    .set("status", MemoryTaskStatus.SUPERSEDED.name())
                    .in("chatSessionId", sessionIds)
                    .in("status", MemoryTaskStatus.PENDING.name(), MemoryTaskStatus.PROCESSING.name()));
            aiChatSessionMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AiChatSession>()
                    .set("summary", null).set("lastSummaryMessageId", 0)
                    .set("summaryModel", null).set("summaryPromptVersion", null).set("summaryUpdatedAt", null)
                    .in("id", sessionIds));
        }
        for (Long userId : affectedUsers) {
            profileUpdateTaskService.supersedeActiveTasks(userId);
            profileMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AiUserProfileEntity>()
                    .set("status", ProfileStatus.REBUILD_REQUIRED.name()).eq("userId", userId));
            profileEmbeddingMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AiUserProfileEmbedding>()
                            .set("status", 0).eq("userId", userId).eq("status", 1));
        }
        int deleted = aiChatMessageMapper.deletePhysicallyByIds(messageIds);
        for (Long userId : affectedUsers) {
            profileUpdateTaskService.enqueueRebuildAll(userId, ProfileUpdateTriggerType.SOURCE_DELETED);
        }
        return deleted;
    }

    private AiChatMessage saveMessage(User loginUser,
                                      AiChatSession session,
                                      String role,
                                      String content,
                                      String responseJson,
                                      Integer visible) {
        validateLoginUser(loginUser);
        if (session == null || session.getId() == null || session.getUserId() == null
                || !session.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "chat session is invalid");
        }
        AiChatMessage message = new AiChatMessage();
        message.setUserId(loginUser.getId());
        message.setChatSessionId(session.getId());
        message.setRole(role);
        message.setContent(sanitizeContent(content));
        message.setResponseJson(responseJson);
        message.setVisible(visible);
        message.setRetentionExpireAt(resolveRetentionExpireAt());
        if (!save(message)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "save AI chat message failed");
        }
        return message;
    }

    private Date resolveRetentionExpireAt() {
        long days = memoryProperties.getChatHistoryRetentionDays();
        return days <= 0 ? null : Date.from(new Date().toInstant().plus(days, ChronoUnit.DAYS));
    }

    private String sanitizeContent(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }
        String sanitized = content.trim()
                .replaceAll("(?i)(token|api[_-]?key|password|密码)\\s*[:：=]\\s*[^\\s,，。；;\"\\\\]+", "$1=***")
                .replaceAll("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b", "***@***")
                .replaceAll("1[3-9]\\d{9}", "1**********");
        return sanitized.substring(0, Math.min(MAX_CONTENT_LENGTH, sanitized.length()));
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "serialize AI chat message failed");
        }
    }

    private AiChatMessageVO toVO(AiChatMessage message, String sessionKey) {
        AiChatMessageVO vo = new AiChatMessageVO();
        vo.setId(message.getId());
        vo.setSessionId(sessionKey);
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setVisible(message.getVisible());
        vo.setCreateTime(message.getCreateTime());
        if (ROLE_ASSISTANT.equals(message.getRole()) && StringUtils.isNotBlank(message.getResponseJson())) {
            vo.setResponse(readResponse(message.getResponseJson()));
        }
        if (ROLE_EVENT.equals(message.getRole()) && StringUtils.isNotBlank(message.getResponseJson())) {
            fillEventFields(vo, message.getResponseJson());
        }
        return vo;
    }

    private AiChatResponseVO readResponse(String responseJson) {
        try {
            return objectMapper.readValue(responseJson, AiChatResponseVO.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void fillEventFields(AiChatMessageVO vo, String responseJson) {
        try {
            JsonNode jsonNode = objectMapper.readTree(responseJson);
            vo.setEventType(jsonNode.path("eventType").asText(null));
            if (jsonNode.hasNonNull("relatedTeamId")) {
                vo.setRelatedTeamId(jsonNode.path("relatedTeamId").asLong());
            }
            vo.setRelatedDraftId(jsonNode.path("relatedDraftId").asText(null));
        } catch (Exception ignored) {
            vo.setEventType(null);
        }
    }

    private void validateLoginUser(User loginUser) {
        if (loginUser == null || loginUser.getId() <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
    }

    private record EventPayload(String eventType,
                                String subjectType,
                                Long subjectId,
                                String subjectName,
                                String action,
                                String status,
                                String summary,
                                String relatedDraftId,
                                Long relatedTeamId) {
    }
}
