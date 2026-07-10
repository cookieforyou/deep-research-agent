package com.example.deepresearch.common.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Token 追踪 Advisor — 透明拦截所有 ChatClient 调用，记录 Token 用量和延迟.
 * <p>
 * 通过 Spring AI 的 {@link BaseAdvisor} 机制，在每次 ChatClient 调用前后
 * 自动提取 {@link Usage} 元数据，写入 Micrometer 指标供 Prometheus 抓取。
 * </p>
 *
 * <h3>工作原理</h3>
 * <ol>
 *   <li>{@code before()}: 在 Advisor Context 中记录调用开始时间戳</li>
 *   <li>{@code after()}: 从 {@link ChatResponse} 提取 Usage（promptTokens/completionTokens），
 *       计算耗时，调用 {@link TokenUsageTracker#track()} 写入指标</li>
 * </ol>
 *
 * <h3>Agent 名称和模型层级识别</h3>
 * <p>
 * 调用方通过 {@code .advisors(a -> a.param("agent", "planner").param("tier", "pro"))}
 * 传入 Agent 身份信息。这些参数由 Spring AI 框架自动注入
 * {@link ChatClientRequest#context()}，Advisor 从中读取。
 * 如果未指定，默认使用 "unknown" / "unknown"。
 * </p>
 *
 * <h3>优先级</h3>
 * <p>{@link Ordered#LOWEST_PRECEDENCE + 100} — 在 {@code PiiMaskingAdvisor}
 * ({@link Ordered#HIGHEST_PRECEDENCE}) 之后执行，确保 PII 脱敏先完成，
 * Token 追踪统计的是脱敏后的实际 API 调用。
 * </p>
 *
 * <h3>线程安全</h3>
 * <p>无状态设计。开始时间通过 Advisor Context 传递，天然线程隔离。</p>
 */
@Component
public class TokenTrackingAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TokenTrackingAdvisor.class);

    /** Advisor Context 中存储调用开始时间的 key */
    static final String START_TIME_KEY = "token_tracker.start_time";

    private final TokenUsageTracker tokenTracker;

    public TokenTrackingAdvisor(TokenUsageTracker tokenTracker) {
        this.tokenTracker = tokenTracker;
    }

    @Override
    public String getName() {
        return "TokenTrackingAdvisor";
    }

    @Override
    public int getOrder() {
        // PiiMaskingAdvisor = HIGHEST_PRECEDENCE，Token 追踪在其后执行
        return Ordered.LOWEST_PRECEDENCE + 100;
    }

    /**
     * 请求前处理: 在 Advisor Context 中记录调用开始时间.
     */
    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        return request.mutate()
            .context(START_TIME_KEY, System.currentTimeMillis())
            .build();
    }

    /**
     * 响应后处理: 提取 Usage 元数据并写入 Micrometer 指标.
     * <p>
     * 此方法<b>绝不抛出异常</b> — Token 追踪失败不应影响业务流程。
     * </p>
     */
    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null) {
            return response;
        }

        try {
            Usage usage = chatResponse.getMetadata().getUsage();
            if (usage != null) {
                // 计算延迟
                Long startTime = (Long) response.context().get(START_TIME_KEY);
                long latencyMs = (startTime != null)
                    ? System.currentTimeMillis() - startTime
                    : 0;

                // 提取 Agent 身份信息
                String agentName = getContextString(response, "agent", "unknown");
                String modelTier = getContextString(response, "tier", "unknown");
                String sessionId = getContextString(response, "sessionId", "");

                // 提取 Token 用量（null-safe）
                int inputTokens = usage.getPromptTokens() != null
                    ? usage.getPromptTokens() : 0;
                int outputTokens = usage.getCompletionTokens() != null
                    ? usage.getCompletionTokens() : 0;

                // 写入指标
                tokenTracker.track(agentName, modelTier,
                    inputTokens, outputTokens, latencyMs, sessionId);

                log.debug("[TokenTracker] {} ({}) prompt={} completion={} latency={}ms",
                    agentName, modelTier, inputTokens, outputTokens, latencyMs);
            } else {
                log.debug("[TokenTracker] Usage 元数据为空，跳过追踪");
            }
        } catch (Exception e) {
            // Token 追踪失败不应影响业务流程
            log.warn("[TokenTracker] 追踪失败（非致命）: {}", e.getMessage());
        }

        return response;
    }

    /**
     * 从 Advisor Context 中提取字符串参数.
     */
    private String getContextString(ChatClientResponse response, String key, String defaultValue) {
        Object value = response.context().get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
