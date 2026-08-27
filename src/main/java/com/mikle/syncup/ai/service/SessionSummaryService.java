package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.entity.AiChatSession;

public interface SessionSummaryService {

    void summarizeAsyncIfNecessary(long userId, long chatSessionId);

    boolean summarizeIfNecessary(AiChatSession session);

    int processPendingSummaries();
}
