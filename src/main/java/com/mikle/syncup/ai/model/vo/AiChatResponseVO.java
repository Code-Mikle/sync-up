package com.mikle.syncup.ai.model.vo;

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

    /**
     * Stable presentation contract consumed by the frontend.
     * Tool names remain execution/audit details and must not drive UI rendering.
     */
    private List<AiUiBlockVO> uiBlocks = new ArrayList<>();

    private boolean needClarification;

    private List<String> clarificationQuestions = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
