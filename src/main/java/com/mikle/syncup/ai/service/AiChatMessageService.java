package com.mikle.syncup.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mikle.syncup.ai.model.entity.AiChatMessage;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.ai.model.vo.AiChatHistoryVO;
import com.mikle.syncup.ai.model.vo.AiChatResponseVO;
import com.mikle.syncup.model.domain.User;

import java.util.List;

public interface AiChatMessageService extends IService<AiChatMessage> {

    AiChatMessage saveUserMessage(User loginUser, AiChatSession session, String content);

    AiChatMessage saveAssistantMessage(User loginUser,
                                       AiChatSession session,
                                       String content,
                                       AiChatResponseVO response);

    AiChatMessage saveTeamDraftConfirmedEvent(User loginUser, AiChatSession session, String draftId, Long teamId);

    AiChatMessage saveTeamDeletedEvent(User loginUser, AiChatSession session, Long teamId);

    List<AiChatMessage> listClosedMessages(long chatSessionId,
                                            long afterMessageId,
                                            long lastClosedMessageId,
                                            int limit);

    List<AiChatMessage> listLatestClosedMessages(long chatSessionId,
                                                  long lastClosedMessageId,
                                                  int limit);

    AiChatHistoryVO getLatestHistory(User loginUser);

    int deleteExpiredPhysically();
}
