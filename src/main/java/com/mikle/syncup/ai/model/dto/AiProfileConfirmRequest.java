package com.mikle.syncup.ai.model.dto;

import com.mikle.syncup.ai.model.schema.ProfileExtraction;
import lombok.Data;

@Data
public class AiProfileConfirmRequest {

    private ProfileExtraction profile;
}

