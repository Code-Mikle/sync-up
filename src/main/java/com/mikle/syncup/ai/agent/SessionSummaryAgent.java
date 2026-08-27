package com.mikle.syncup.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface SessionSummaryAgent {

    @SystemMessage(fromResource = "prompt/session-summary-prompt.txt")
    String summarize(@UserMessage String input);
}
