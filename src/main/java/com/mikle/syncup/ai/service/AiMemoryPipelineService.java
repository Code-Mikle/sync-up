package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.entity.AiChatSession;

public interface AiMemoryPipelineService {

    void onChatTurnCompleted(long userId, AiChatSession session, long lastClosedMessageId);

    void createNextChatExtractionTaskIfNecessary(long userId, long chatSessionId);

    void onSelfIntroductionChanged(long userId, String sourceText);
}
