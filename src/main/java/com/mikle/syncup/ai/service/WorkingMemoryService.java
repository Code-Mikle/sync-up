package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.model.domain.User;

public interface WorkingMemoryService {

    String buildModelContext(AiChatSession session, User loginUser, String currentMessage);
}
