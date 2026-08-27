package com.mikle.syncup.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sync-up.ai.memory")
public class AiMemoryProperties {

    private long chatHistoryRetentionDays = 365;

    private WorkingMemory workingMemory = new WorkingMemory();

    private Episode episode = new Episode();

    private ProfileUpdate profileUpdate = new ProfileUpdate();

    @Data
    public static class WorkingMemory {

        private int recentMessageCount = 20;

        private int summaryBatchSize = 10;

        private int summaryInputMaxTokens = 4000;

        private int maxContextTokens = 6000;

        private int summaryMaxChars = 1500;
    }

    @Data
    public static class Episode {

        private boolean extractionEnabled = true;

        private int maxRetries = 3;

        private int processingTimeoutMinutes = 10;

        private int batchSize = 20;

        private int messageBatchSize = 100;

        private int messageBatchMaxTokens = 6000;
    }

    @Data
    public static class ProfileUpdate {

        private int defaultEvidenceThreshold = 5;

        private int maxWaitDays = 7;

        private int maxRetries = 3;

        private int processingTimeoutMinutes = 10;

        private int batchSize = 20;
    }
}
