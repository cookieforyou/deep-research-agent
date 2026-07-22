package com.example.deepresearch.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置 — JWT + OAuth2 无状态认证 + 方法级权限.
 * <p>
 * 采用 WebFlux 响应式安全配置（非 Servlet），
 * 使用 JWT Bearer Token 进行无状态认证，
 * 支持 {@code @PreAuthorize("hasRole('ADMIN')")} 方法级权限控制。
 * </p>
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${deep-research.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    /**
     * 安全过滤器链（WebFlux 响应式）.
     */
    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
            // CORS（允许前端 localhost:3000 跨域请求）
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 禁用 CSRF（REST API 不使用浏览器 Cookie）
            .csrf(ServerHttpSecurity.CsrfSpec::disable)

            // 禁用 Session（无状态 JWT 认证）
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())

            // 禁用默认登录页
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

            // 端点授权
            .authorizeExchange(exchanges -> exchanges
                // Actuator 健康检查无需认证
                .pathMatchers(HttpMethod.GET, "/actuator/**")
                    .permitAll()
                // Swagger / OpenAPI 文档（如果启用）
                .pathMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**")
                    .permitAll()
                // 研究 API 需要认证
                .pathMatchers("/api/**")
                    .authenticated()
                // 其他所有请求需要认证
                .anyExchange()
                    .authenticated()
            )

            // OAuth2 Resource Server JWT
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtDecoder(jwtDecoder())
                    .jwtAuthenticationConverter(new TenantJwtAuthenticationConverter())
                )
            )

            .build();
    }

    /**
     * JWT 解码器.
     * <p>
     * 从 OAuth2 / OIDC Provider（如 Casdoor）的 issuer-uri 获取 JWK Set。
     * Casdoor 遵循标准 OIDC 协议，通过 /.well-known/openid-configuration 自动发现 JWKS 端点。
     * </p>
     * <p>
     * 配置方式：环境变量 JWT_ISSUER_URI 或 application.yml 中的
     * spring.security.oauth2.resourceserver.jwt.issuer-uri。
     * 例如 Casdoor: {@code http://<casdoor-host>:<port>}
     * </p>
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        return ReactiveJwtDecoders.fromIssuerLocation(issuerUri);
    }

    /**
     * CORS 配置：允许前端 localhost:3000 跨域访问（SSE 直连场景）。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
