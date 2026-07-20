package com.mikle.syncup.ai.job;

import com.mikle.syncup.ai.mapper.AiChatMemoryMapper;
import com.mikle.syncup.ai.service.AiChatMessageService;
import com.mikle.syncup.ai.service.AiUserProfileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
public class AiChatMemoryCleanupJob {

    @Resource
    private AiChatMemoryMapper aiChatMemoryMapper;

    @Resource
    private AiChatMessageService aiChatMessageService;

    @Resource
    private AiUserProfileService aiUserProfileService;

    @Scheduled(cron = "0 0 * * * ?")
    public void deleteExpiredChatMemory() {
        int deletedMemories = aiChatMemoryMapper.deleteExpiredPhysically(new Date());
        if (deletedMemories > 0) {
            log.info("deleted expired AI chat memories, count={}", deletedMemories);
        }
        int deletedMessages = aiChatMessageService.deleteExpiredPhysically();
        if (deletedMessages > 0) {
            log.info("deleted expired AI chat messages, count={}", deletedMessages);
        }
        int deletedProfileDrafts = aiUserProfileService.deleteExpiredDraftsPhysically();
        if (deletedProfileDrafts > 0) {
            log.info("deleted expired AI profile drafts, count={}", deletedProfileDrafts);
        }
    }
}
