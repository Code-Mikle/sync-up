package com.mikle.syncup.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mikle.syncup.ai.model.dto.AiProfileConfirmRequest;
import com.mikle.syncup.ai.model.entity.AiProfileDraft;
import com.mikle.syncup.ai.model.vo.AiProfileResponse;
import com.mikle.syncup.ai.model.entity.AiUserProfileEntity;
import com.mikle.syncup.model.domain.User;

public interface AiUserProfileService extends IService<AiUserProfileEntity> {

    AiProfileResponse getCurrentProfile(User loginUser);

    AiProfileResponse createProfileDraft(String sourceText, User loginUser);

    AiProfileResponse confirmDraft(String draftId, AiProfileConfirmRequest request, User loginUser);

    AiProfileResponse rejectDraft(String draftId, User loginUser);

    void createDraftFromUserUpdate(User updateUser, User loginUser);

    AiProfileDraft findLatestDraft(long userId);

    int deleteExpiredDraftsPhysically();
}
