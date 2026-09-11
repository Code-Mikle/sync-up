package com.mikle.syncup.ai.service.memory;

public interface SessionSummaryGenerator {

    String summarize(String input);

    boolean isAvailable();

    String modelName();

    String promptVersion();
}
