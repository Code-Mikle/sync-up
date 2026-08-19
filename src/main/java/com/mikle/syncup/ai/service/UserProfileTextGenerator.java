package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.schema.GeneratedUserProfile;

public interface UserProfileTextGenerator {

    GeneratedUserProfile generate(String sourceText);

    boolean isAvailable();

    String modelName();

    String promptVersion();
}
