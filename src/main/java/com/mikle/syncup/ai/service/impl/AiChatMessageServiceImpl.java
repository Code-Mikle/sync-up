package com.mikle.syncup.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.config.AiAgentProperties;
import com.mikle.syncup.ai.mapper.AiChatMessageMapper;
import com.mikle.syncup.ai.model.entity.AiChatMessage;
import com.mikle.syncup.ai.model.vo.AiBusinessEventVO;
import com.mikle.syncup.ai.model.vo.AiChatHistoryVO;
import com.mikle.syncup.ai.model.vo.AiChatMessageVO;
import com.mikle.syncup.ai.model.vo.AiChatResponseVO;
import com.mikle.syncup.ai.service.AiChatMessageService;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class AiChatMessageServiceImpl extends ServiceImpl<AiChatMessageMapper, AiChatMessage>
        implements AiChatMessageService {

    private static final int VISIBLE = 1;

    private static final int HIDDEN = 0;

    private static final String ROLE_USER = "user";

    private static final String ROLE_ASSISTANT = "assistant";

    private static final String ROLE_EVENT = "event";

    private static final String EVENT_TEAM_DRAFT_CONFIRMED = "TEAM_DRAFT_CONFIRMED";

    private static final String EVENT_TEAM_CREATED = "TEAM_CREATED";

    private static final String EVENT_TEAM_DELETED = "TEAM_DELETED";

    private static final String SUBJECT_TEAM = "TEAM";

    private static final int MAX_CONTENT_LENGTH = 2048;

    @Resource
    private AiChatMessageMapper aiChatMessageMapper;

    @Resource
    private AiAgentProperties aiAgentProperties;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public void saveUserMessage(User loginUser, String sessionId, String content) {
        saveMessage(loginUser, sessionId, ROLE_USER, content, null, VISIBLE);
    }

    @Override
    public void saveAssistantMessage(User loginUser, String sessionId, String content, AiChatResponseVO response) {
        saveMessage(loginUser, sessionId, ROLE_ASSISTANT, content, writeJson(response), VISIBLE);
    }

    @Override
    public void saveTeamDraftConfirmedEvent(User loginUser, String sessionId, String draftId, Long teamId) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        String content = "用户已确认创建队伍，draftId=" + draftId + "，teamId=" + teamId;
        EventPayload payload = new EventPayload(
                EVENT_TEAM_CREATED,
                SUBJECT_TEAM,
                teamId,
                null,
                "CREATED",
                "SUCCESS",
                "创建队伍成功：#" + teamId,
                draftId,
                teamId
        );
        saveMessage(loginUser, sessionId, ROLE_EVENT, content, writeJson(payload), HIDDEN);
    }

    @Override
    public void saveTeamDeletedEvent(User loginUser, String sessionId, Long teamId) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        String content = "用户已确认删除队伍，teamId=" + teamId;
        EventPayload payload = new EventPayload(
                EVENT_TEAM_DELETED,
                SUBJECT_TEAM,
                teamId,
                null,
                "DELETED",
                "SUCCESS",
                "删除队伍成功：#" + teamId,
                null,
                teamId
        );
        saveMessage(loginUser, sessionId, ROLE_EVENT, content, writeJson(payload), HIDDEN);
    }

    @Override
    public List<AiBusinessEventVO> listRecentBusinessEvents(User loginUser, String sessionId, int limit) {
        validateLoginUser(loginUser);
        if (StringUtils.isBlank(sessionId)) {
            return List.of();
        }
        Date now = new Date();
        List<AiChatMessage> events = list(new QueryWrapper<AiChatMessage>()
                .eq("userId", loginUser.getId())
                .eq("sessionId", sessionId.trim())
                .eq("role", ROLE_EVENT)
                .gt("expireAt", now)
                .orderByDesc("createTime")
                .orderByDesc("id")
                .last("limit " + Math.max(1, Math.min(limit, 20))));
        List<AiBusinessEventVO> result = new ArrayList<>();
        for (AiChatMessage event : events) {
            AiBusinessEventVO businessEvent = readBusinessEvent(event);
            if (businessEvent != null) {
                result.add(businessEvent);
            }
        }
        return result;
    }

    @Override
    public AiChatHistoryVO getLatestHistory(User loginUser) {
        validateLoginUser(loginUser);
        Date now = new Date();
        AiChatMessage latest = getOne(new QueryWrapper<AiChatMessage>()
                .eq("userId", loginUser.getId())
                .gt("expireAt", now)
                .orderByDesc("createTime")
                .last("limit 1"));
        AiChatHistoryVO history = new AiChatHistoryVO();
        if (latest == null || StringUtils.isBlank(latest.getSessionId())) {
            return history;
        }
        history.setSessionId(latest.getSessionId());
        List<AiChatMessage> messages = list(new QueryWrapper<AiChatMessage>()
                .eq("userId", loginUser.getId())
                .eq("sessionId", latest.getSessionId())
                .gt("expireAt", now)
                .orderByAsc("createTime")
                .orderByAsc("id"));
        history.setMessages(messages.stream().map(this::toVO).toList());
        return history;
    }

    @Override
    public int deleteExpiredPhysically() {
        return aiChatMessageMapper.deleteExpiredPhysically(new Date());
    }

    private void saveMessage(User loginUser,
                             String sessionId,
                             String role,
                             String content,
                             String responseJson,
                             Integer visible) {
        validateLoginUser(loginUser);
        if (StringUtils.isBlank(sessionId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "sessionId is required");
        }
        AiChatMessage message = new AiChatMessage();
        message.setUserId(loginUser.getId());
        message.setSessionId(sessionId.trim());
        message.setRole(role);
        message.setContent(sanitizeContent(content));
        message.setResponseJson(responseJson);
        message.setVisible(visible);
        message.setExpireAt(resolveExpireAt());
        if (!save(message)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "save AI chat message failed");
        }
    }

    private Date resolveExpireAt() {
        long ttlHours = Math.max(1, aiAgentProperties.getMemory().getMysqlTtlHours());
        return new Date(System.currentTimeMillis() + ttlHours * 60 * 60 * 1000L);
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

    private AiChatMessageVO toVO(AiChatMessage message) {
        AiChatMessageVO vo = new AiChatMessageVO();
        vo.setId(message.getId());
        vo.setSessionId(message.getSessionId());
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
        } catch (Exception e) {
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
        } catch (Exception e) {
            vo.setEventType(null);
        }
    }

    private AiBusinessEventVO readBusinessEvent(AiChatMessage message) {
        try {
            if (message == null || StringUtils.isBlank(message.getResponseJson())) {
                return null;
            }
            JsonNode jsonNode = objectMapper.readTree(message.getResponseJson());
            String rawEventType = jsonNode.path("eventType").asText(null);
            if (StringUtils.isBlank(rawEventType)) {
                return null;
            }
            Long relatedTeamId = jsonNode.hasNonNull("relatedTeamId") ? jsonNode.path("relatedTeamId").asLong() : null;
            String normalizedEventType = EVENT_TEAM_DRAFT_CONFIRMED.equals(rawEventType)
                    ? EVENT_TEAM_CREATED
                    : rawEventType;

            AiBusinessEventVO event = new AiBusinessEventVO();
            event.setEventType(normalizedEventType);
            event.setSubjectType(jsonNode.path("subjectType").asText(resolveSubjectType(normalizedEventType)));
            event.setSubjectId(jsonNode.hasNonNull("subjectId") ? jsonNode.path("subjectId").asLong() : relatedTeamId);
            event.setSubjectName(jsonNode.path("subjectName").asText(null));
            event.setAction(jsonNode.path("action").asText(resolveAction(normalizedEventType)));
            event.setStatus(jsonNode.path("status").asText("SUCCESS"));
            event.setSummary(jsonNode.path("summary").asText(resolveSummary(normalizedEventType, relatedTeamId)));
            event.setRelatedDraftId(jsonNode.path("relatedDraftId").asText(null));
            event.setRelatedTeamId(relatedTeamId);
            event.setOccurredAt(message.getCreateTime());
            return event;
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveSubjectType(String eventType) {
        if (EVENT_TEAM_CREATED.equals(eventType) || EVENT_TEAM_DELETED.equals(eventType)) {
            return SUBJECT_TEAM;
        }
        return null;
    }

    private String resolveAction(String eventType) {
        if (EVENT_TEAM_CREATED.equals(eventType)) {
            return "CREATED";
        }
        if (EVENT_TEAM_DELETED.equals(eventType)) {
            return "DELETED";
        }
        return null;
    }

    private String resolveSummary(String eventType, Long teamId) {
        if (EVENT_TEAM_CREATED.equals(eventType)) {
            return "创建队伍成功：#" + teamId;
        }
        if (EVENT_TEAM_DELETED.equals(eventType)) {
            return "删除队伍成功：#" + teamId;
        }
        return eventType;
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
