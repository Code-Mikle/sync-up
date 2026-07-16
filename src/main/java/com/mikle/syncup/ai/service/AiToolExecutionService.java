package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.AiToolResult;
import com.mikle.syncup.ai.model.TeamIntent;
import com.mikle.syncup.model.domain.User;

public interface AiToolExecutionService {

    AiToolResult execute(String toolName, TeamIntent intent, User loginUser, String sessionId);
}
