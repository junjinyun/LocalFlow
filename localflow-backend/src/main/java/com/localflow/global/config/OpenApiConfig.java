package com.localflow.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI localFlowOpenApi() {
        return new OpenAPI().info(new Info()
                .title("LocalFlow AI API")
                .description("프로젝트 기반 보조 코딩 에이전트 백엔드 API")
                .version("v0.1.0"));
    }
}
