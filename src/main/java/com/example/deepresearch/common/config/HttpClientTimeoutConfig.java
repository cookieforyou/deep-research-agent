package com.example.deepresearch.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 为 Spring AI 的同步 RestClient 配置 HTTP 超时.
 * <p>
 * DeepSeek Pro 模型处理复杂规划任务可能超过 Jetty 默认的 10 秒超时，
 * 此处提供自定义 RestClient.Builder，使用 JDK HttpClient 并设置
 * read timeout 120s、connect timeout 30s。
 * </p>
 * <p>
 * Spring AI 的 DeepSeekChatModel 通过 ObjectProvider&lt;RestClient.Builder&gt;
 * 注入，会自动使用此 Bean 替代默认值。
 * </p>
 */
@Configuration
public class HttpClientTimeoutConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        var httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(120));
        return RestClient.builder().requestFactory(factory);
    }
}
