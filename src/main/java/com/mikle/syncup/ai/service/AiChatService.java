package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.dto.AiChatRequest;
import com.mikle.syncup.ai.model.vo.AiChatHistoryVO;
import com.mikle.syncup.ai.model.vo.AiChatResponseVO;
import com.mikle.syncup.ai.model.dto.AiTeamDetailsRequest;
import com.mikle.syncup.ai.model.tool.AiToolResult;

import jakarta.servlet.http.HttpServletRequest;

public interface AiChatService {

    AiChatResponseVO chat(AiChatRequest aiChatRequest, HttpServletRequest request);

    AiChatHistoryVO getHistory(HttpServletRequest request);

    AiToolResult getTeamDetails(Long teamId, AiTeamDetailsRequest aiTeamDetailsRequest, HttpServletRequest request);

    AiToolResult deleteTeam(Long teamId, AiTeamDetailsRequest aiTeamDetailsRequest, HttpServletRequest request);
}
