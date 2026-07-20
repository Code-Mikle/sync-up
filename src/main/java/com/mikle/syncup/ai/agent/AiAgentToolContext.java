package com.mikle.syncup.ai.agent;

import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.vo.AiTeamDeleteConfirmationVO;
import com.mikle.syncup.ai.model.vo.TeamDraftVO;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.model.domain.User;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * // Tool methods are invoked synchronously by LangChain4j in the current request thread.
 * // If we switch to async/streaming/parallel tool execution, replace this ThreadLocal bridge
 * // with an invocation-scoped tool context.
 */
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
        snapshot.setDeleteConfirmation(state.getDeleteConfirmation());
        snapshot.setIntent(state.getIntent());
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

        private TeamDraftVO draft;

        private AiTeamDeleteConfirmationVO deleteConfirmation;

        private TeamIntent intent;
    }
}
