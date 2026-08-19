package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.entity.AiTeamEmbedding;

import java.util.Collection;
import java.util.Map;

public interface AiTeamEmbeddingService {

    int refreshPendingTeams();

    void invalidate(long teamId);

    void deletePhysically(long teamId);

    Map<Long, AiTeamEmbedding> getActiveEmbeddings(Collection<Long> teamIds);
}
