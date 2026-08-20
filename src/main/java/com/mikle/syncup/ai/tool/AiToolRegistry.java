package com.mikle.syncup.ai.tool;

import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.agent.AiIntent;
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

    private final Map<String, AiTool<?>> toolMap = new HashMap<>();

    public AiToolRegistry(List<AiTool<?>> tools) {
        for (AiTool<?> tool : tools) {
            toolMap.put(tool.name(), tool);
        }
    }

    public AiToolResult execute(String toolName, AiIntent intent, User loginUser) {
        AiTool<?> tool = toolMap.get(toolName);
        if (tool == null) {
            throw new BusinessException(ErrorCode.NO_AUTH, "AI tool is not allowed");
        }
        if (intent == null || !tool.intentType().isInstance(intent)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "AI tool intent type is invalid");
        }
        return executeTyped(tool, intent, loginUser);
    }

    private <T extends AiIntent> AiToolResult executeTyped(AiTool<T> tool, AiIntent intent, User loginUser) {
        return tool.execute(tool.intentType().cast(intent), loginUser);
    }

    public boolean contains(String toolName) {
        return toolMap.containsKey(toolName);
    }

    public Set<String> listToolNames() {
        return toolMap.keySet();
    }
}
