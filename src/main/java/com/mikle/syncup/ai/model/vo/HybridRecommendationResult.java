package com.mikle.syncup.ai.model.vo;

import java.util.List;

public record HybridRecommendationResult<T>(
        List<T> items,
        int candidateCount,
        boolean degraded,
        String embeddingModel,
        long rankingDurationMs
) {
}
