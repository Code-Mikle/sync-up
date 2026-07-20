package com.mikle.syncup.ai.agent;

import com.mikle.syncup.ai.model.vo.AiChatResponseVO;
import com.mikle.syncup.model.domain.User;

import java.util.Optional;

public interface AiAssistantAgentService {

    Optional<AiChatResponseVO> chat(String message, String sessionId, User loginUser);
}
