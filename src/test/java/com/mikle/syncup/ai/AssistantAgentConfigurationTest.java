package com.mikle.syncup.ai;

import com.mikle.syncup.ai.agent.AssistantAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

class AssistantAgentConfigurationTest {

    @Test
    void assistantAgent_shouldBuildWithoutChatMemoryProvider() {
        ChatModel chatModel = mock(ChatModel.class);

        assertDoesNotThrow(() -> AiServices.builder(AssistantAgent.class)
                .chatModel(chatModel)
                .build());
    }
}
