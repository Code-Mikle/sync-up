package com.mikle.syncup.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mikle.syncup.ai.model.entity.AiTeamDraft;
import com.mikle.syncup.ai.model.vo.AiTeamDraftConfirmResponse;
import com.mikle.syncup.ai.model.vo.TeamDraftVO;
import com.mikle.syncup.model.domain.User;

public interface AiTeamDraftService extends IService<AiTeamDraft> {

    TeamDraftVO saveDraft(TeamDraftVO draft, User loginUser, String sessionId);

    AiTeamDraftConfirmResponse confirmDraft(String draftId, User loginUser);
}
