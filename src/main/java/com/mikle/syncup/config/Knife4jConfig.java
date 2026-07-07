package com.mikle.syncup.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Knife4j API document configuration.
 */
@Configuration
@Profile({"dev", "test"})
public class Knife4jConfig {

    @Bean
    public OpenAPI syncupOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sync Up API")
                        .description("Sync Up backend API document")
                        .version("1.0")
                        .contact(new Contact()
                                .name("mikle")
                                .url("https://github.com/Code-Mikle")
                                .email("xxx@qq.com")));
    }
}
