package com.example.deepresearch.common.config;

import com.example.deepresearch.common.observability.BusinessMetrics;
import com.example.deepresearch.common.observability.WorkflowTracingHelper;
import com.example.deepresearch.tool.EvidenceScorer;
import com.example.deepresearch.tool.search.BochaSearchTool;
import com.example.deepresearch.tool.search.FallbackSearchTool;
import com.example.deepresearch.tool.search.ResilientSearchTool;
import com.example.deepresearch.tool.search.SearchTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 应用通用配置 — SearchTool 策略选择 + 韧性包装.
 * <p>
 * 通过 {@link Value @Value} 注入已解析的 API Key（而非依赖条件注解）来判断
 * 使用主力搜索还是降级搜索。原因：{@code @ConditionalOnProperty} 和
 * {@code @ConditionalOnExpression} 在条件求值时，{@code Environment.getProperty()}
 * 返回的是 YAML 中未经 {@code PropertySourcesPlaceholderConfigurer} 递归解析的
 * 原始占位符 {@code ${BOCHA_API_KEY:}}，无法正确判断环境变量是否已设置。
 * </p>
 *
 * <h3>搜索韧性链</h3>
 * <pre>
 * ResilientSearchTool
 *   ├── BochaSearchTool (主力，带 CircuitBreaker 保护)
 *   └── FallbackSearchTool (Tavily 备用，或空壳)
 * </pre>
 */
@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    /**
     * 韧性搜索引擎 Bean.
     * <p>
     * 始终创建 ResilientSearchTool，内部包装 Bocha（主力）+ Tavily（备用）。
     * Bocha 不可用时 CircuitBreaker 自动切换到 Tavily，
     * Tavily 也不可用时返回空结果，触发纯 Local RAG 模式。
     * </p>
     */
    @Bean
    @Primary
    public SearchTool searchTool(
            @Value("${deep-research.search.bocha.api-key}") String bochaApiKey,
            DeepResearchProperties props,
            ObjectMapper objectMapper,
            CircuitBreakerRegistry cbRegistry,
            WorkflowTracingHelper tracingHelper,
            BusinessMetrics businessMetrics) {

        // 创建主力搜索引擎（Bocha）
        SearchTool primary;
        if (bochaApiKey != null && !bochaApiKey.isBlank()) {
            primary = new BochaSearchTool(props.search().bocha(), objectMapper);
        } else {
            log.warn("Bocha API Key 未配置，主力搜索不可用");
            primary = new FallbackSearchTool(props.fallback().tavily(), objectMapper);
        }

        // 创建备用搜索引擎（Tavily）
        FallbackSearchTool fallback = new FallbackSearchTool(
            props.fallback().tavily(), objectMapper);

        // 包装为韧性搜索工具（带 Tracing 支持）
        ResilientSearchTool resilient = new ResilientSearchTool(
            primary, fallback, cbRegistry, tracingHelper, businessMetrics);
        log.info("搜索引擎注册完成: {}", resilient.getEngineName());
        return resilient;
    }

    /**
     * 证据评分器 Bean.
     */
    @Bean
    public EvidenceScorer evidenceScorer(DeepResearchProperties props) {
        return new EvidenceScorer(props.evidenceScoring());
    }
}
