package com.mikle.syncup.ai;

import com.mikle.syncup.ai.agent.AiAssistantTools;
import com.mikle.syncup.ai.tool.CreateTeamDraftTool;
import com.mikle.syncup.ai.tool.DeleteTeamConfirmationTool;
import com.mikle.syncup.ai.tool.ResolveTagsTool;
import com.mikle.syncup.ai.tool.SearchTeamsTool;
import com.mikle.syncup.ai.tool.SearchUsersTool;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

class AiAssistantToolExposureTest {

    @Test
    void assistantTools_shouldExposeDraftsAndQueriesButNoDirectWriteTools() {
        Set<String> exposedToolNames = Arrays.stream(AiAssistantTools.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Tool.class))
                .filter(Objects::nonNull)
                .map(this::toolName)
                .collect(Collectors.toSet());

        Assertions.assertAll(
                () -> Assertions.assertTrue(exposedToolNames.contains(SearchTeamsTool.TOOL_NAME)),
                () -> Assertions.assertTrue(exposedToolNames.contains(SearchUsersTool.TOOL_NAME)),
                () -> Assertions.assertTrue(exposedToolNames.contains(ResolveTagsTool.TOOL_NAME)),
                () -> Assertions.assertTrue(exposedToolNames.contains(CreateTeamDraftTool.TOOL_NAME)),
                () -> Assertions.assertTrue(exposedToolNames.contains(DeleteTeamConfirmationTool.TOOL_NAME)),
                () -> Assertions.assertFalse(exposedToolNames.contains("delete_team")),
                () -> Assertions.assertFalse(exposedToolNames.contains("join_team")),
                () -> Assertions.assertFalse(exposedToolNames.contains("quit_team"))
        );
    }

    private String toolName(Tool tool) {
        if (!tool.name().isBlank()) {
            return tool.name();
        }
        return Arrays.stream(tool.value()).findFirst().orElse("");
    }
}
