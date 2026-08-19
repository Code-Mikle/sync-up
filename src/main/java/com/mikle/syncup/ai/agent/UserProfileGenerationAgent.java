package com.mikle.syncup.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface UserProfileGenerationAgent {

    @SystemMessage(fromResource = "prompt/userprofile-generation-prompt.txt")
    String generate(@UserMessage String sourceText);
}
