package com.mikle.syncup.ai.tool;

import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.agent.AiIntent;
import com.mikle.syncup.model.domain.User;

public interface AiTool<T extends AiIntent> {

    String name();

    String type();

    Class<T> intentType();

    AiToolResult execute(T intent, User loginUser);
}
