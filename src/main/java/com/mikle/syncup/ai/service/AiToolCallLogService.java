package com.mikle.syncup.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mikle.syncup.ai.model.entity.AiToolCallLog;
import com.mikle.syncup.model.domain.User;

public interface AiToolCallLogService extends IService<AiToolCallLog> {

    void recordToolCall(String sessionId,
                        User loginUser,
                        String toolName,
                        String status,
                        String argumentsSummary,
                        String resultSummary,
                        String errorMessage,
                        long durationMs);

    void recordDraftConfirm(String sessionId,
                            User loginUser,
                            String draftId,
                            Long teamId,
                            String status,
                            String resultSummary,
                            String errorMessage,
                            long durationMs);
}
