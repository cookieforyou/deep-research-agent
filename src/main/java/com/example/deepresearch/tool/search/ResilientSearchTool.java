package com.example.deepresearch.tool.search;

import com.example.deepresearch.common.model.SearchResult;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 韧性搜索工具 — CircuitBreaker + Fallback 装饰器.
 * <p>
 * 包装 BochaSearchTool（主力）+ FallbackSearchTool（备用），
 * 通过 Resilience4j {@link CircuitBreaker} 实现运行时自动切换。
 * </p>
 *
 * <h3>工作流程</h3>
 * <pre>
 * search(query)
 *   ├─ [正常] CircuitBreaker → BochaSearchTool.search() → 返回结果
 *   ├─ [Bocha 失败] CircuitBreaker 记录失败
 *   ├─ [熔断打开] 跳过 Bocha，直接走 FallbackSearchTool
 *   └─ [Fallback 也失败] → 返回空列表 → WebScout 自然降级为纯 Local RAG
 * </pre>
 *
 * <h3>CircuitBreaker 配置</h3>
 * 从 {@code application.yml} 中 {@code search-circuit-breaker} 读取：
 * <ul>
 *   <li>滑动窗口: 10</li>
 *   <li>失败率阈值: 50%</li>
 *   <li>熔断持续时间: 30s</li>
 *   <li>半开允许请求数: 3</li>
 * </ul>
 */
public class ResilientSearchTool implements SearchTool {

    private static final Logger log = LoggerFactory.getLogger(ResilientSearchTool.class);

    private final SearchTool primary;
    private final SearchTool fallback;
    private final CircuitBreaker circuitBreaker;

    public ResilientSearchTool(
        SearchTool primary,
        SearchTool fallback,
        CircuitBreakerRegistry cbRegistry
    ) {
        this.primary = primary;
        this.fallback = fallback;
        this.circuitBreaker = cbRegistry.circuitBreaker("search-circuit-breaker");
        log.info("ResilientSearchTool 初始化完成: primary={}, fallback={}",
            primary.getEngineName(), fallback.getEngineName());
    }

    @Override
    public List<SearchResult> search(String query, int count) {
        Supplier<List<SearchResult>> primaryCall = () -> {
            log.debug("[ResilientSearch] 调用主力引擎 {}: query='{}'", primary.getEngineName(), query);
            return primary.search(query, count);
        };

        Supplier<List<SearchResult>> fallbackCall = () -> {
            // 仅在熔断器 OPEN 时打 WARN（跳过 Bocha 直接走 Tavily），
            // 其他异常降级的情况已由 catch 块统一记录日志
            if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                log.warn("[ResilientSearch] 熔断器已打开，跳过 {} 直接使用 {}: query='{}'",
                    primary.getEngineName(), fallback.getEngineName(), query);
            }
            return fallback.search(query, count);
        };

        Supplier<List<SearchResult>> decorated = circuitBreaker.decorateSupplier(primaryCall);
        try {
            List<SearchResult> results = decorated.get();

            // Bocha 成功但返回空结果（罕见情况）
            if (results.isEmpty() && fallback.isAvailable()) {
                log.debug("[ResilientSearch] {} 返回空结果: query='{}'",
                    primary.getEngineName(), query);
            }
            return results;

        } catch (Exception e) {
            // CircuitBreaker 触发或 Bocha 调用失败 → 降级到备用引擎
            log.warn("[ResilientSearch] {} 失败 ({}), 切换到 {}: query='{}'",
                primary.getEngineName(), e.getMessage(), fallback.getEngineName(), query);

            try {
                List<SearchResult> fallbackResults = fallbackCall.get();
                if (fallbackResults.isEmpty()) {
                    log.error("[ResilientSearch] 所有搜索引擎均不可用 (Bocha失败, {}返回空): query='{}'",
                        fallback.getEngineName(), query);
                }
                return fallbackResults;
            } catch (Exception fallbackError) {
                log.error("[ResilientSearch] {} 也抛出异常, 所有搜索引擎不可用: query='{}', error={}",
                    fallback.getEngineName(), query, fallbackError.getMessage());
                return Collections.emptyList();
            }
        }
    }

    @Override
    public List<SearchResult> batchSearch(List<String> queries, int count) {
        return queries.stream()
            .flatMap(q -> search(q, count).stream())
            .toList();
    }

    @Override
    public boolean isAvailable() {
        return primary.isAvailable() || fallback.isAvailable();
    }

    @Override
    public String getEngineName() {
        return "Resilient(" + primary.getEngineName() + "+" + fallback.getEngineName() + ")";
    }
}
