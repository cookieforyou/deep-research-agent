package com.example.deepresearch.tool.search;

import com.example.deepresearch.common.config.DeepResearchProperties.FallbackConfig.TavilyConfig;
import com.example.deepresearch.common.model.SearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 降级搜索工具 — 当 Bocha 不可用时的备用搜索引擎.
 * <p>
 * 接入 Tavily Search API（AI Agent 专用搜索引擎），
 * 作为 Bocha 熔断/不可用时的自动降级方案。
 * Tavily 免费额度 1000 次/月，适合作为备用引擎。
 * </p>
 *
 * <h3>触发条件</h3>
 * <ul>
 *   <li>Bocha API 熔断器打开（连续失败触发熔断）</li>
 *   <li>Bocha API 超时</li>
 *   <li>Bocha API Key 未配置</li>
 * </ul>
 *
 * <h3>降级策略</h3>
 * <ol>
 *   <li>尝试调用 Tavily Search API</li>
 *   <li>若 Tavily 也不可用 → 返回空列表 → 研究流程仅依赖 Local RAG</li>
 * </ol>
 *
 * @see <a href="https://tavily.com">Tavily Search API</a>
 */
public class FallbackSearchTool implements SearchTool {

    private static final Logger log = LoggerFactory.getLogger(FallbackSearchTool.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final boolean configured;

    /** Tavily Search API 端点 */
    private static final String SEARCH_PATH = "/search";

    public FallbackSearchTool(TavilyConfig config, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        if (config.apiKey() != null && !config.apiKey().isBlank()) {
            this.apiKey = config.apiKey();
            this.webClient = WebClient.builder()
                .baseUrl(config.baseUrl())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
            this.configured = true;
            log.info("FallbackSearchTool 初始化完成 (Tavily), baseUrl={}", config.baseUrl());
        } else {
            this.apiKey = null;
            this.webClient = null;
            this.configured = false;
            log.warn("Tavily API Key 未配置，降级搜索不可用（纯 Local RAG 模式）");
        }
    }

    @Override
    public List<SearchResult> search(String query, int count) {
        if (!configured) {
            log.debug("[降级搜索] Tavily 未配置，返回空结果。query='{}'", query);
            return Collections.emptyList();
        }

        int actualCount = count > 0 ? Math.min(count, 15) : 10;
        log.debug("[降级搜索] Tavily 搜索: query='{}', count={}", query, actualCount);

        try {
            Map<String, Object> body = Map.of(
                "api_key", apiKey,
                "query", query,
                "max_results", actualCount,
                "search_depth", "basic"
            );
            String requestBody = objectMapper.writeValueAsString(body);

            String responseBody = webClient.post()
                .uri(SEARCH_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();

            List<SearchResult> results = parseResponse(responseBody, query);
            log.debug("[降级搜索] Tavily 搜索完成: query='{}', 返回 {} 条结果", query, results.size());
            return results;

        } catch (Exception e) {
            log.warn("[降级搜索] Tavily 不可用 (已由上层处理): query='{}', error={}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public boolean isAvailable() {
        return configured;
    }

    @Override
    public String getEngineName() {
        return configured ? "Tavily" : "Fallback(None)";
    }

    // =========================== 内部方法 ===========================

    /**
     * 解析 Tavily API 响应.
     * <p>
     * Tavily 响应格式:
     * <pre>{@code
     * {
     *   "results": [
     *     { "title": "...", "url": "https://...", "content": "...", "score": 0.95 }
     *   ]
     * }
     * }</pre>
     * </p>
     */
    List<SearchResult> parseResponse(String responseBody, String query) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            if (root.has("error")) {
                String errorMsg = root.path("error").asText("");
                log.error("[降级搜索] Tavily API 返回错误: {}", errorMsg);
                return Collections.emptyList();
            }

            JsonNode results = root.path("results");
            if (!results.isArray()) {
                log.warn("[降级搜索] Tavily 响应中没有 results 数组: query='{}'", query);
                return Collections.emptyList();
            }

            List<SearchResult> searchResults = new ArrayList<>();
            for (JsonNode item : results) {
                String title = item.path("title").asText("");
                String url = item.path("url").asText("");
                String content = item.path("content").asText("");

                if (title.isEmpty() || url.isEmpty()) {
                    continue;
                }

                String domain = extractDomain(url);
                searchResults.add(new SearchResult(
                    title, url, content, domain, LocalDateTime.now()));
            }
            return searchResults;

        } catch (Exception e) {
            log.error("[降级搜索] 解析 Tavily 响应失败: query='{}'", query, e);
            return Collections.emptyList();
        }
    }

    /** 从 URL 中提取域名 */
    private String extractDomain(String url) {
        try {
            String host = new java.net.URL(url).getHost();
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
