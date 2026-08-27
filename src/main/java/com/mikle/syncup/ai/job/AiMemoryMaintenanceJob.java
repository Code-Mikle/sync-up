package com.mikle.syncup.ai.job;

import com.mikle.syncup.ai.service.AiChatMessageService;
import com.mikle.syncup.ai.service.AiMemoryTaskProcessorService;
import com.mikle.syncup.ai.service.SessionSummaryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiMemoryMaintenanceJob {

    @Resource
    private SessionSummaryService sessionSummaryService;

    @Resource
    private AiChatMessageService chatMessageService;

    @Resource
    private AiMemoryTaskProcessorService memoryTaskProcessorService;

    @Scheduled(fixedDelayString = "${sync-up.ai.memory.summary-scan-fixed-delay-ms:60000}")
    public void processSessionSummaries() {
        try {
            int processed = sessionSummaryService.processPendingSummaries();
            if (processed > 0) {
                log.info("processed AI session summaries, count={}", processed);
            }
        } catch (RuntimeException e) {
            log.error("process AI session summaries failed", e);
        }
    }

    @Scheduled(fixedDelayString = "${sync-up.ai.memory.task-scan-fixed-delay-ms:30000}")
    public void processMemoryTasks() {
        try {
            int episodes = memoryTaskProcessorService.processEpisodeExtractionTasks();
            int profiles = memoryTaskProcessorService.processProfileUpdateTasks();
            if (episodes > 0 || profiles > 0) {
                log.info("processed AI memory tasks, episodeTasks={}, profileTasks={}", episodes, profiles);
            }
        } catch (RuntimeException e) {
            log.error("process AI memory tasks failed", e);
        }
    }

    @Scheduled(cron = "0 0 * * * ?")
    public void deleteExpiredChatHistory() {
        try {
            int deleted = 0;
            int batch;
            do {
                batch = chatMessageService.deleteExpiredPhysically();
                deleted += batch;
            } while (batch == 1000 && deleted < 10000);
            if (deleted > 0) {
                log.info("deleted expired AI chat history messages, count={}", deleted);
            }
        } catch (RuntimeException e) {
            log.error("delete expired AI chat history failed", e);
        }
    }
}
