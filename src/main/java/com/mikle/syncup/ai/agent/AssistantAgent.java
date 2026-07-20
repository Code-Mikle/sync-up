package com.mikle.syncup.ai.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;


public interface AssistantAgent {

    @SystemMessage(fromResource = "prompt/system-prompt.txt")
    String chat(@MemoryId String memoryId, @UserMessage String message);
}
