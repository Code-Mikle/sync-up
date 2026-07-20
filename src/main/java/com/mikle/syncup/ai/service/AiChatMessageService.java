package com.mikle.syncup.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mikle.syncup.ai.model.entity.AiChatMessage;
import com.mikle.syncup.ai.model.vo.AiBusinessEventVO;
import com.mikle.syncup.ai.model.vo.AiChatHistoryVO;
import com.mikle.syncup.ai.model.vo.AiChatResponseVO;
import com.mikle.syncup.model.domain.User;

import java.util.List;

public interface AiChatMessageService extends IService<AiChatMessage> {

    void saveUserMessage(User loginUser, String sessionId, String content);

    void saveAssistantMessage(User loginUser, String sessionId, String content, AiChatResponseVO response);

    void saveTeamDraftConfirmedEvent(User loginUser, String sessionId, String draftId, Long teamId);

    void saveTeamDeletedEvent(User loginUser, String sessionId, Long teamId);

    List<AiBusinessEventVO> listRecentBusinessEvents(User loginUser, String sessionId, int limit);

    AiChatHistoryVO getLatestHistory(User loginUser);

    int deleteExpiredPhysically();
}
