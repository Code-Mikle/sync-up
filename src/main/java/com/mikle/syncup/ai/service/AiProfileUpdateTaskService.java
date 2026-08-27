package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.enums.ProfileType;
import com.mikle.syncup.ai.model.enums.ProfileUpdateTriggerType;

public interface AiProfileUpdateTaskService {

    void enqueueIfNecessary(long userId, ProfileType profileType,
                            ProfileUpdateTriggerType triggerType, boolean force);

    void enqueueRebuildAll(long userId, ProfileUpdateTriggerType triggerType);

    void enqueueOverdueTasks();

    void supersedeActiveTasks(long userId);
}
