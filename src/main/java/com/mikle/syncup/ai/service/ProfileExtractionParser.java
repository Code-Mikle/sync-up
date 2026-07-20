package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.schema.ProfileExtraction;

public interface ProfileExtractionParser {

    ProfileExtraction parse(String sourceText);
}

