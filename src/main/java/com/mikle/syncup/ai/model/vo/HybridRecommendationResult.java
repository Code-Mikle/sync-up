package com.mikle.syncup.ai.model.vo;

import java.util.List;

public record HybridRecommendationResult<T>(
        List<T> items,
        int candidateCount,
        boolean degraded, // 是否发生了降级
        String embeddingModel,
        long rankingDurationMs
) {
}
