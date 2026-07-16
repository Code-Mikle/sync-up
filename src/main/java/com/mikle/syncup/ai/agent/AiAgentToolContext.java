package com.mikle.syncup.ai.agent;

import com.mikle.syncup.ai.model.AiToolResult;
import com.mikle.syncup.ai.model.TeamDraft;
import com.mikle.syncup.model.domain.User;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AiAgentToolContext {

    private final ThreadLocal<State> current = new ThreadLocal<>();

    public void start(String sessionId, User loginUser) {
        State state = new State();
        state.setSessionId(sessionId);
        state.setLoginUser(loginUser);
        current.set(state);
    }

    public State getRequired() {
        State state = current.get();
        if (state == null) {
            throw new IllegalStateException("AI agent tool context is not initialized");
        }
        return state;
    }

    public State snapshot() {
        State state = getRequired();
        State snapshot = new State();
        snapshot.setSessionId(state.getSessionId());
        snapshot.setLoginUser(state.getLoginUser());
        snapshot.getToolResults().addAll(state.getToolResults());
        snapshot.setDraft(state.getDraft());
        return snapshot;
    }

    public void clear() {
        current.remove();
    }

    @Data
    public static class State {

        private String sessionId;

        private User loginUser;

        private List<AiToolResult> toolResults = new ArrayList<>();

        private TeamDraft draft;
    }
}
