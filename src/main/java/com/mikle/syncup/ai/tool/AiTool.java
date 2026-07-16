package com.mikle.syncup.ai.tool;

import com.mikle.syncup.ai.model.AiToolResult;
import com.mikle.syncup.ai.model.TeamIntent;
import com.mikle.syncup.model.domain.User;

public interface AiTool {

    String name();

    String type();

    AiToolResult execute(TeamIntent intent, User loginUser);
}
