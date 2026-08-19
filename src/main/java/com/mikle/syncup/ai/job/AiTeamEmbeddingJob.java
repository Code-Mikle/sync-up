package com.mikle.syncup.ai.job;

import com.mikle.syncup.ai.service.AiTeamEmbeddingService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiTeamEmbeddingJob {

    @Resource
    private AiTeamEmbeddingService aiTeamEmbeddingService;

    @Scheduled(
            fixedDelayString = "${sync-up.ai.team-embedding.fixed-delay-ms:60000}",
            initialDelayString = "${sync-up.ai.team-embedding.initial-delay-ms:15000}")
    public void refreshTeamEmbeddings() {
        try {
            int refreshed = aiTeamEmbeddingService.refreshPendingTeams();
            if (refreshed > 0) {
                log.info("refreshed AI team embeddings, count={}", refreshed);
            }
        } catch (Exception e) {
            log.warn("refresh AI team embeddings job failed", e);
        }
    }
}
