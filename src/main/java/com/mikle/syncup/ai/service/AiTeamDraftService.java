package com.mikle.syncup.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mikle.syncup.ai.model.AiTeamDraft;
import com.mikle.syncup.ai.model.AiTeamDraftConfirmResponse;
import com.mikle.syncup.ai.model.TeamDraft;
import com.mikle.syncup.model.domain.User;

public interface AiTeamDraftService extends IService<AiTeamDraft> {

    TeamDraft saveDraft(TeamDraft draft, User loginUser, String sessionId);

    AiTeamDraftConfirmResponse confirmDraft(String draftId, User loginUser);
}
