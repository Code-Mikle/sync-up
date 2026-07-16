package com.mikle.syncup.ai.tool;

import com.mikle.syncup.ai.model.AiToolResult;
import com.mikle.syncup.ai.model.TeamIntent;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AiToolRegistry {

    private final Map<String, AiTool> toolMap = new HashMap<>();

    public AiToolRegistry(List<AiTool> tools) {
        for (AiTool tool : tools) {
            toolMap.put(tool.name(), tool);
        }
    }

    public AiToolResult execute(String toolName, TeamIntent intent, User loginUser) {
        AiTool tool = toolMap.get(toolName);
        if (tool == null) {
            throw new BusinessException(ErrorCode.NO_AUTH, "AI tool is not allowed");
        }
        return tool.execute(intent, loginUser);
    }

    public boolean contains(String toolName) {
        return toolMap.containsKey(toolName);
    }

    public Set<String> listToolNames() {
        return toolMap.keySet();
    }
}
