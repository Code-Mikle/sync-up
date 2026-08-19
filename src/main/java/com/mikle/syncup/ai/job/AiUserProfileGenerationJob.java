package com.mikle.syncup.ai.job;

import com.mikle.syncup.ai.service.AiUserProfileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiUserProfileGenerationJob {

    @Resource
    private AiUserProfileService aiUserProfileService;

    @Scheduled(fixedDelayString = "${sync-up.ai.profile-generation.fixed-delay-ms:60000}")
    public void generatePendingProfiles() {
        try {
            int processed = aiUserProfileService.processPendingTasks();
            if (processed > 0) {
                log.info("processed AI user profile generation tasks, count={}", processed);
            }
        } catch (Exception e) {
            log.error("process AI user profile generation tasks failed", e);
        }
    }
}
