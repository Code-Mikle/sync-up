package com.mikle.syncup.ai.service;

/** 标准活动标签向量维护。 */
public interface TagEmbeddingService {

    /** 刷新缺失、过期或模型不一致的标签向量。 */
    int refreshPendingTags();
}
