package com.mikle.syncup.ai.job;

import com.mikle.syncup.ai.service.TagEmbeddingService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TagEmbeddingJob {

    @Resource
    private TagEmbeddingService tagEmbeddingService;

    @Scheduled(
            fixedDelayString = "${sync-up.ai.tag-embedding.fixed-delay-ms:60000}",
            initialDelayString = "${sync-up.ai.tag-embedding.initial-delay-ms:20000}")
    public void refreshTagEmbeddings() {
        try {
            int refreshed = tagEmbeddingService.refreshPendingTags();
            if (refreshed > 0) {
                log.info("refreshed activity tag embeddings, count={}", refreshed);
            }
        } catch (RuntimeException e) {
            log.warn("refresh activity tag embeddings job failed", e);
        }
    }
}
