package com.mikle.syncup.ai.model.vo;

import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiChatResponseVO implements Serializable {

    private String sessionId;

    private String reply;

    private TeamIntent intent;

    private List<AiToolResult> toolResults = new ArrayList<>();

    private TeamDraftVO draft;

    private AiTeamDeleteConfirmationVO deleteConfirmation;

    private boolean needClarification;

    private List<String> clarificationQuestions = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
