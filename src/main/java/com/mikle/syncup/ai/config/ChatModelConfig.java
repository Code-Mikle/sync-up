package com.mikle.syncup.ai.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.time.Duration;
import java.util.List;

@Data
@Configuration
public class ChatModelConfig {

    @Resource
    private AiAgentProperties aiAgentProperties;

    @Resource(name = "aiToolArgumentsValidationListener")
    private ChatModelListener chatModelListener;

    @Bean
    @Scope("prototype")
    public ChatModel chatModelPrototype() {
        return OpenAiChatModel.builder()
                .baseUrl(aiAgentProperties.getBaseUrl())
                .apiKey(aiAgentProperties.getApiKey())
                .modelName(aiAgentProperties.getModel())
                .temperature(aiAgentProperties.getTemperature())
                .timeout(Duration.ofMillis(aiAgentProperties.getTimeoutMs()))
                .maxRetries(1)
                .logRequests(aiAgentProperties.isLogRequests())
                .logResponses(aiAgentProperties.isLogResponses())
                .listeners(List.of(chatModelListener))
                .build();
    }

}
