package com.mikle.syncup.ai.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiChatResponse implements Serializable {

    private String sessionId;

    private String reply;

    private TeamIntent intent;

    private List<AiToolResult> toolResults = new ArrayList<>();

    private TeamDraft draft;

    private boolean needClarification;

    private List<String> clarificationQuestions = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
