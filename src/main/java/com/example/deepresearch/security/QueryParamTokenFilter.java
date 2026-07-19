package com.example.deepresearch.security;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 将 query 参数 {@code token} 的值复制到 Authorization header。
 * <p>
 * SSE 直连场景：浏览器跨域 fetch 携带 Authorization header 会触发
 * CORS preflight，而 SSE 流式响应对 preflight 不友好。
 * 改为 JWT 通过 query param 传递（GET 请求不会触发 preflight），
 * 此 Filter 在 Spring Security 认证链之前将其还原为标准 Bearer 头。
 * </p>
 */
@Component
@Order(-200) // 在 Spring Security 认证链（默认 -100）之前执行
public class QueryParamTokenFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = exchange.getRequest().getQueryParams().getFirst("token");
        if (token != null && !token.isEmpty()) {
            // 仅在缺少 Authorization header 时从 query param 注入
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null) {
                exchange = exchange.mutate()
                    .request(r -> r.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .build();
            }
        }
        return chain.filter(exchange);
    }
}
