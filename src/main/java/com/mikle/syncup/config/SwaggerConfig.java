package com.mikle.syncup.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * OpenAPI interface document configuration.
 */
@Configuration
@Profile({"dev", "test"})
public class SwaggerConfig {

    @Bean
    public OpenAPI syncupOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("搭子星球接口文档")
                        .description("搭子星球后端接口")
                        .version("1.0")
                        .contact(new Contact()
                                .name("mikle")
                                .url("https://github.com/Code-Mikle")
                                .email("xxx@qq.com")));
    }
}
