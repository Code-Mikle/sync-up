package com.mikle.syncup.ai.agent;

import com.mikle.syncup.ai.model.AiChatResponse;
import com.mikle.syncup.model.domain.User;

import java.util.Optional;

public interface AiAssistantAgentService {

    Optional<AiChatResponse> chat(String message, String sessionId, User loginUser);
}
