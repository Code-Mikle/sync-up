package com.mikle.syncup.ai.service.impl;

import com.mikle.syncup.ai.model.vo.AiBusinessEventVO;
import com.mikle.syncup.ai.service.AiChatMessageService;
import com.mikle.syncup.ai.service.AiConversationContextService;
import com.mikle.syncup.model.domain.User;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.List;

@Service
public class AiConversationContextServiceImpl implements AiConversationContextService {

    private static final int DEFAULT_EVENT_LIMIT = 6;

    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Resource
    private AiChatMessageService aiChatMessageService;

    @Override
    public String buildRecentBusinessContext(User loginUser, String sessionId) {
        if (loginUser == null || loginUser.getId() <= 0 || StringUtils.isBlank(sessionId)) {
            return "";
        }
        List<AiBusinessEventVO> events = aiChatMessageService.listRecentBusinessEvents(
                loginUser,
                sessionId,
                DEFAULT_EVENT_LIMIT
        );
        if (events.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("当前会话近期业务事件（用于理解“刚刚”“刚才”“那个”等指代）：");
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_PATTERN);
        for (AiBusinessEventVO event : events) {
            builder.append("\n- ");
            if (event.getOccurredAt() != null) {
                builder.append(dateFormat.format(event.getOccurredAt())).append(" ");
            }
            builder.append(StringUtils.defaultString(event.getEventType(), "BUSINESS_EVENT"));
            if (StringUtils.isNotBlank(event.getSubjectType()) && event.getSubjectId() != null) {
                builder.append(" ")
                        .append(event.getSubjectType())
                        .append("#")
                        .append(event.getSubjectId());
            }
            if (StringUtils.isNotBlank(event.getSubjectName())) {
                builder.append("「").append(event.getSubjectName()).append("」");
            }
            if (StringUtils.isNotBlank(event.getSummary())) {
                builder.append("：").append(event.getSummary());
            }
        }
        builder.append("\n使用规则：当用户提到刚刚创建、刚才删除、刚修改的对象时，优先匹配上面的最近业务事件；涉及删除、撤销、修改等写操作时仍然必须先生成确认。");
        return builder.toString();
    }
}
