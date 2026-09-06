package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.entity.AiChatSession;

public interface SessionSummaryService {

    void summarizeAsyncIfNecessary(long userId, long chatSessionId);

    boolean summarizeIfNecessary(AiChatSession session);

    boolean summarizeForContextBudget(AiChatSession session, long targetMessageId);

    int processPendingSummaries();
}
