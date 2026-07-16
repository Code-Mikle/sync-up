package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.TeamIntent;

public interface TeamIntentParser {

    TeamIntent parse(String message);
}
