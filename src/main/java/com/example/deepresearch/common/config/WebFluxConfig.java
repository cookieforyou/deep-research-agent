package com.example.deepresearch.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * WebFlux 配置 — CORS、SSE、静态资源.
 */
@Configuration
public class WebFluxConfig implements WebFluxConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);

        // SSE 流端点额外允许长时间连接
        registry.addMapping("/api/research/*/stream")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600);
    }
}
