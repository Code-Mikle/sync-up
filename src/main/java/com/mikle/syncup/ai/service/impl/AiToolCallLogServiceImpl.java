package com.mikle.syncup.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mikle.syncup.ai.mapper.AiToolCallLogMapper;
import com.mikle.syncup.ai.model.AiToolCallLog;
import com.mikle.syncup.ai.service.AiToolCallLogService;
import com.mikle.syncup.model.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiToolCallLogServiceImpl extends ServiceImpl<AiToolCallLogMapper, AiToolCallLog>
        implements AiToolCallLogService {

    private static final Logger log = LoggerFactory.getLogger(AiToolCallLogServiceImpl.class);

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordToolCall(String sessionId,
                               User loginUser,
                               String toolName,
                               String status,
                               String argumentsSummary,
                               String resultSummary,
                               String errorMessage,
                               long durationMs) {
        AiToolCallLog record = new AiToolCallLog();
        record.setSessionId(sessionId);
        record.setUserId(loginUser == null ? null : loginUser.getId());
        record.setActionType("tool");
        record.setToolName(toolName);
        record.setStatus(status);
        record.setArgumentsSummary(limit(argumentsSummary));
        record.setResultSummary(limit(resultSummary));
        record.setErrorMessage(limit(errorMessage));
        record.setDurationMs(durationMs);
        saveQuietly(record);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDraftConfirm(String sessionId,
                                   User loginUser,
                                   String draftId,
                                   Long teamId,
                                   String status,
                                   String resultSummary,
                                   String errorMessage,
                                   long durationMs) {
        AiToolCallLog record = new AiToolCallLog();
        record.setSessionId(sessionId);
        record.setUserId(loginUser == null ? null : loginUser.getId());
        record.setActionType("confirmDraft");
        record.setToolName("confirmTeamDraft");
        record.setStatus(status);
        record.setArgumentsSummary(limit("draftId=" + draftId));
        record.setResultSummary(limit(resultSummary));
        record.setErrorMessage(limit(errorMessage));
        record.setDurationMs(durationMs);
        record.setRelatedDraftId(draftId);
        record.setRelatedTeamId(teamId);
        saveQuietly(record);
    }

    private void saveQuietly(AiToolCallLog record) {
        try {
            this.save(record);
        } catch (Exception e) {
            log.warn("AI tool call audit log save failed, actionType={}, toolName={}",
                    record.getActionType(),
                    record.getToolName(),
                    e);
        }
    }

    private String limit(String value) {
        if (value == null || value.length() <= 1024) {
            return value;
        }
        return value.substring(0, 1024);
    }
}
