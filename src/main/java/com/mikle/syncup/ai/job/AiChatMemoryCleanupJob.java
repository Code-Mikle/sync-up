package com.mikle.syncup.ai.job;

import com.mikle.syncup.ai.mapper.AiChatMemoryMapper;
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

    @Scheduled(cron = "0 0 * * * ?")
    public void deleteExpiredChatMemory() {
        int deleted = aiChatMemoryMapper.deleteExpiredPhysically(new Date());
        if (deleted > 0) {
            log.info("deleted expired AI chat memories, count={}", deleted);
        }
    }
}
