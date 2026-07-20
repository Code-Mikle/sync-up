package com.mikle.syncup.ai.service;

import com.mikle.syncup.model.domain.User;

public interface AiConversationContextService {

    String buildRecentBusinessContext(User loginUser, String sessionId);
}
