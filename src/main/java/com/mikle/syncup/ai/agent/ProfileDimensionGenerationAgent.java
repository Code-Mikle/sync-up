package com.mikle.syncup.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface ProfileDimensionGenerationAgent {

    @SystemMessage(fromResource = "prompt/profile-dimension-generation-prompt.txt")
    String generate(@UserMessage String input);
}
