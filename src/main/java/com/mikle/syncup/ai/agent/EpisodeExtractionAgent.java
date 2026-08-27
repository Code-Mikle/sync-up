package com.mikle.syncup.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface EpisodeExtractionAgent {

    @SystemMessage(fromResource = "prompt/episode-extraction-prompt.txt")
    String extract(@UserMessage String sourceText);
}
