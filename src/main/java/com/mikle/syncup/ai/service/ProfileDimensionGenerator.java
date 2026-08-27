package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.entity.AiUserEpisode;
import com.mikle.syncup.ai.model.enums.ProfileType;

import java.util.List;

public interface ProfileDimensionGenerator {

    String generate(ProfileType profileType, String currentText, List<AiUserEpisode> evidence);

    boolean isAvailable();

    String modelName();

    String promptVersion();
}
