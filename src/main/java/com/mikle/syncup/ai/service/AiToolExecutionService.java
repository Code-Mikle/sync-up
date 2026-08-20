package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.agent.AiIntent;
import com.mikle.syncup.model.domain.User;

public interface AiToolExecutionService {

    AiToolResult execute(String toolName, AiIntent intent, User loginUser, String sessionId);
}
