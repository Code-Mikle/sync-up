package com.mikle.syncup.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mikle.syncup.ai.model.AiProfileConfirmRequest;
import com.mikle.syncup.ai.model.AiProfileExtractionTask;
import com.mikle.syncup.ai.model.AiProfileResponse;
import com.mikle.syncup.ai.model.AiUserProfileEntity;
import com.mikle.syncup.model.domain.User;

public interface AiUserProfileService extends IService<AiUserProfileEntity> {

    AiProfileResponse getCurrentProfile(User loginUser);

    AiProfileResponse extractProfile(String sourceText, User loginUser);

    AiProfileResponse confirmExtraction(String taskId, AiProfileConfirmRequest request, User loginUser);

    AiProfileResponse rejectExtraction(String taskId, User loginUser);

    void createExtractionTaskFromUserUpdate(User updateUser, User loginUser);

    AiProfileExtractionTask findLatestTask(long userId);
}

