package com.mikle.syncup.ai.config;

import com.mikle.syncup.ai.validation.AiToolArgumentsValidator;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MyConfiguration {

    @Bean
    ChatModelListener aiToolArgumentsValidationListener(AiToolArgumentsValidator validator) {
        return new ChatModelListener() {
            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                validator.validate(responseContext.chatResponse().aiMessage());
            }
        };
    }
}
