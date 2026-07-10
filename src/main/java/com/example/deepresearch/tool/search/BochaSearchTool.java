package com.example.deepresearch.tool.search;

import com.example.deepresearch.common.config.DeepResearchProperties.SearchConfig.BochaConfig;
import com.example.deepresearch.common.exception.ResearchException;
import com.example.deepresearch.common.exception.ResearchException.ErrorCode;
import com.example.deepresearch.common.model.SearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bocha（博查）AI 搜索工具.
 * <p>
 * 封装 Bocha Search API（{@code api.bocha.cn/v1/web-search}），
 * 提供结构化的搜索结果。Bocha 是国内领先的 AI Agent 专用搜索引擎，
 * 日调用量超 3000 万次，支持中英文搜索和时效性过滤。
 * </p>
 *
 * <h3>API 认证</h3>
 * 使用 Bearer Token 认证，API Key 通过 {@code BOCHA_API_KEY} 环境变量注入。
 *
 * <h3>熔断与重试</h3>
 * <ul>
 *   <li>重试: 最多 2 次，指数退避 2s → 4s（由 Resilience4j {@code @Retry} 处理）</li>
 *   <li>熔断: 滑动窗口 10 次请求，失败率 ≥ 50% 触发熔断 30s</li>
 * </ul>
 *
 * @see <a href="https://open.bochaai.com">Bocha Open Platform</a>
 */
public class BochaSearchTool implements SearchTool {

    private static final Logger log = LoggerFactory.getLogger(BochaSearchTool.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final BochaConfig config;

    /**
     * Bocha Web Search API 端点.
     */
    private static final String WEB_SEARCH_PATH = "/v1/web-search";

    public BochaSearchTool(BochaConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
            .baseUrl(config.baseUrl())
            .defaultHeader("Authorization", "Bearer " + config.apiKey())
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build();
        log.info("BochaSearchTool 初始化完成, baseUrl={}, defaultCount={}",
            config.baseUrl(), config.defaultCount());
    }

    /**
     * 执行单次 Bocha 搜索.
     * <p>
     * 带 Resilience4j 重试：LLM API 级别的重试通过 {@code @Retry} 注解触发。
     * </p>
     *
     * @param query 搜索查询词
     * @param count 返回结果数量（1-50，取 config 默认值或指定值）
     * @return 结构化搜索结果列表
     * @throws ResearchException 搜索失败时（触发降级到 FallbackSearchTool）
     */
    @Override
    @Retry(name = "search-retry")
    public List<SearchResult> search(String query, int count) {
        int actualCount = count > 0 ? Math.min(count, 50) : config.defaultCount();

        log.debug("Bocha 搜索: query='{}', count={}", query, actualCount);

        try {
            // 构建请求体
            String requestBody = buildRequestBody(query, actualCount);

            // 发起 HTTP POST 请求（WebFlux 非阻塞）
            String responseBody = webClient.post()
                .uri(WEB_SEARCH_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.is5xxServerError() || status.value() == 429,
                    response -> Mono.error(new ResearchException(
                        ErrorCode.SEARCH_API_ERROR,
                        "Bocha API 返回错误: " + response.statusCode())))
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();

            // 解析响应
            List<SearchResult> results = parseResponse(responseBody, query);
            log.debug("Bocha 搜索完成: query='{}', 返回 {} 条结果", query, results.size());
            return results;

        } catch (ResearchException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Bocha 搜索失败 (已由 ResilientSearchTool 降级处理): query='{}', error={}", query, e.getMessage());
            throw new ResearchException(ErrorCode.SEARCH_API_ERROR,
                "Bocha 搜索失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getEngineName() {
        return "Bocha";
    }

    // =========================== 内部方法 ===========================

    /**
     * 构建 Bocha API 请求体 JSON.
     *
     * @param query 查询词
     * @param count 结果数量
     * @return JSON 字符串
     */
    private String buildRequestBody(String query, int count) {
        try {
            return objectMapper.writeValueAsString(
                java.util.Map.of(
                    "query", query,
                    "count", count,
                    "freshness", "noLimit"  // 不限时效
                ));
        } catch (Exception e) {
            // 手动构建（兜底）
            return String.format("{\"query\":\"%s\",\"count\":%d,\"freshness\":\"noLimit\"}",
                query.replace("\"", "\\\""), count);
        }
    }

    /**
     * 解析 Bocha API 响应.
     * <p>
     * Bocha 响应格式:
     * <pre>{@code
     * {
     *   "code": 200,
     *   "data": {
     *     "webPages": [
     *       {
     *         "name": "标题",
     *         "url": "https://...",
     *         "snippet": "摘要",
     *         "dateLastCrawled": "2026-07-01T10:00:00Z"
     *       }
     *     ]
     *   }
     * }
     * }</pre>
     * </p>
     */
    List<SearchResult> parseResponse(String responseBody, String query) {
        try {
            // log.debug("Bocha API 原始响应: {}", responseBody);
            JsonNode root = objectMapper.readTree(responseBody);

            // 检查状态码
            int code = root.path("code").asInt(200);
            if (code != 200) {
                String msg = root.path("message").asText("无错误信息");
                log.error("Bocha API 返回非 200: code={}, message={}, 原始响应: {}", code, msg,
                    responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);
                return Collections.emptyList();
            }

            // 提取搜索结果
            // Bocha API 实际结构: data.webPages.value (webPages 是对象，value 是结果数组)
            JsonNode webPages = root.path("data").path("webPages").path("value");
            if (!webPages.isArray()) {
                log.warn("Bocha 响应中没有 webPages 数组: query='{}', 响应结构: {}",
                    query,
                    responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);
                return Collections.emptyList();
            }

            List<SearchResult> results = new ArrayList<>();
            for (JsonNode page : webPages) {
                String title = page.path("name").asText("");
                String url = page.path("url").asText("");
                String snippet = page.path("snippet").asText("");
                String domain = extractDomain(url);

                // 跳过无效结果
                if (title.isEmpty() || url.isEmpty()) {
                    continue;
                }

                LocalDateTime publishTime = null;
                String dateStr = page.path("dateLastCrawled").asText(null);
                if (dateStr != null && !dateStr.isEmpty()) {
                    try {
                        publishTime = LocalDateTime.parse(dateStr.substring(0, 19));
                    } catch (Exception ignored) {
                        // 日期解析失败不阻塞
                    }
                }

                results.add(new SearchResult(title, url, snippet, domain, publishTime));
            }

            return results;

        } catch (Exception e) {
            log.error("解析 Bocha 响应失败: query='{}'", query, e);
            return Collections.emptyList();
        }
    }

    /**
     * 从 URL 中提取域名.
     */
    private String extractDomain(String url) {
        try {
            String host = new java.net.URL(url).getHost();
            // 去掉 www. 前缀
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
