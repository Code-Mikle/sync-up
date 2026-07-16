package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.AiChatRequest;
import com.mikle.syncup.ai.model.AiChatResponse;
import com.mikle.syncup.ai.model.AiTeamDetailsRequest;
import com.mikle.syncup.ai.model.AiToolResult;

import jakarta.servlet.http.HttpServletRequest;

public interface AiChatService {

    AiChatResponse chat(AiChatRequest aiChatRequest, HttpServletRequest request);

    AiToolResult getTeamDetails(Long teamId, AiTeamDetailsRequest aiTeamDetailsRequest, HttpServletRequest request);
}
