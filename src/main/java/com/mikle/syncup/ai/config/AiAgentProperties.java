package com.mikle.syncup.ai.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sync-up.ai.agent")
public class AiAgentProperties {

    private boolean enabled = false;

    private String provider = "dashscope";

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private String model;

    private String apiKey;

    private Double temperature = 0.1;

    private long timeoutMs = 8000;

    private int maxToolCalls = 3;

    private int maxInputLength = 1000;

    private boolean logRequests = false;

    private boolean logResponses = false;

    private Memory memory = new Memory();

    public boolean available() {
        return enabled && StringUtils.isNotBlank(apiKey);
    }

    @Data
    public static class Memory {

        private boolean enabled = true;

        private int maxMessages = 20;

        private long redisTtlHours = 12;

        private long mysqlTtlHours = 24;
    }
}
