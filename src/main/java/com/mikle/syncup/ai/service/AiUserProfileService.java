package com.mikle.syncup.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mikle.syncup.ai.model.entity.AiUserProfileEntity;
import com.mikle.syncup.ai.model.entity.AiUserProfileEmbedding;

public interface AiUserProfileService extends IService<AiUserProfileEntity> {

    void onSelfIntroductionChanged(long userId, String sourceText);

    int processPendingTasks();

    AiUserProfileEntity getInternalProfile(long userId);

    AiUserProfileEmbedding getActiveEmbedding(long userId);

    String getInteractionProfileText(long userId);
}
