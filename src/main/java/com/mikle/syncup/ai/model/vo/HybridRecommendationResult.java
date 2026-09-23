package com.mikle.syncup.ai.model.vo;

import java.util.List;

public record HybridRecommendationResult<T>(
        List<T> items,
        int candidateCount,
        // 完全降级或部分候选降级时均为 true，保留原字段语义。
        boolean degraded,
        // 最终排序是否实际使用了至少一个语义分。
        boolean semanticUsed,
        // 排序使用了语义分，但同时存在缺少有效语义分的候选。
        boolean partialDegraded,
        // 因向量缺失、过期、模型或维度不一致而无法计算语义分的候选数。
        int degradedCandidateCount,
        String embeddingModel,
        long rankingDurationMs
) {
}
