package com.mikle.syncup.ai.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sync-up.ai.embedding")
public class AiEmbeddingProperties {

    private boolean enabled = false;

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private String model = "text-embedding-v4";

    private String apiKey;

    private Integer dimensions = 1024;

    private long timeoutMs = 10000;

    private int maxRetries = 1;

    private boolean logRequests = false;

    private boolean logResponses = false;

    public boolean available() {
        return enabled
                && StringUtils.isNotBlank(baseUrl)
                && StringUtils.isNotBlank(model)
                && StringUtils.isNotBlank(apiKey);
    }
}
