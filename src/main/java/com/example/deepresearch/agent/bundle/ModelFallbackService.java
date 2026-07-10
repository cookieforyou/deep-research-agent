package com.example.deepresearch.agent.bundle;

import com.example.deepresearch.common.config.DeepResearchProperties;
import com.example.deepresearch.common.observability.TokenUsageTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.util.function.Supplier;

/**
 * 模型降级服务 — 当 Pro 模型不可用时自动切换到 Flash 模型.
 * <p>
 * 使用 Resilience4j {@link CircuitBreaker} 保护 Pro 模型调用，
 * 失败率 ≥ 50%（滑动窗口 10）触发熔断，所有后续请求直接走 Flash，
 * 30 秒后半开尝试恢复 Pro。
 * </p>
 *
 * <h3>降级策略</h3>
 * <ol>
 *   <li>Pro 成功 → 正常返回结果</li>
 *   <li>Pro 失败 (429/503/timeout) → CircuitBreaker 记录失败</li>
 *   <li>熔断器打开 → 自动切换 Flash ChatClient</li>
 *   <li>Flash 也失败 → 抛出异常，由 Agent 层 fallback 兜底</li>
 * </ol>
 *
 * <h3>受保护的 Agent</h3>
 * <ul>
 *   <li>{@code PlannerAgent} — Pro (deepseek-v4-pro) → Flash (deepseek-v4-flash)</li>
 *   <li>{@code WriterAgent} — Pro (deepseek-v4-pro) → Flash (deepseek-v4-flash)</li>
 * </ul>
 */
@Service
public class ModelFallbackService {

    private static final Logger log = LoggerFactory.getLogger(ModelFallbackService.class);

    private final CircuitBreakerRegistry cbRegistry;
    private final TokenUsageTracker tokenTracker;
    private final boolean enabled;

    public ModelFallbackService(
        CircuitBreakerRegistry cbRegistry,
        TokenUsageTracker tokenTracker,
        DeepResearchProperties props
    ) {
        this.cbRegistry = cbRegistry;
        this.tokenTracker = tokenTracker;
        this.enabled = props.fallback() != null
            && props.fallback().model() != null
            && props.fallback().model().enabled();
    }

    /**
     * 带降级的 LLM 调用（旧版兼容 — 单消息）.
     */
    public String callWithFallback(ChatClient primary, ChatClient fallback,
                                    String prompt, String agentName) {
        return callWithFallback(primary, fallback, null, prompt, agentName);
    }

    /**
     * 带降级的 LLM 调用（推荐 — System/User 分离）.
     * <p>
     * 使用 DeepSeek V4 原生 system/user 角色分离实现架构级注入防护。
     * 当 {@code systemPrompt} 为 null 时退化为普通 user-only 调用。
     * </p>
     * <p>
     * 通过 {@code .advisors()} 传递 Agent 身份信息（name + tier），
     * 由 {@code TokenTrackingAdvisor} 在 Adapter Context 中读取并写入指标。
     * </p>
     *
     * @param primary      主力 ChatClient（Pro 模型）
     * @param fallback     降级 ChatClient（Flash 模型）
     * @param systemPrompt System 消息（角色定义+规则），可为 null
     * @param userPrompt   User 消息（查询数据）
     * @param agentName    Agent 名称（用于日志和追踪，如 "Planner"）
     * @return LLM 输出的文本内容
     */
    public String callWithFallback(ChatClient primary, ChatClient fallback,
                                    String systemPrompt, String userPrompt, String agentName) {
        if (!enabled) {
            return callWithRoles(primary, systemPrompt, userPrompt, agentName, "pro");
        }

        CircuitBreaker cb = cbRegistry.circuitBreaker("llm-circuit-breaker");

        Supplier<String> primaryCall = () -> {
            log.debug("[ModelFallback] {} 调用 Pro 模型", agentName);
            return callWithRoles(primary, systemPrompt, userPrompt, agentName, "pro");
        };

        Supplier<String> fallbackCall = () -> {
            log.warn("[ModelFallback] {} Pro→Flash 降级 (熔断状态={})",
                agentName, cb.getState());
            tokenTracker.trackFallback(agentName, "pro", "flash");
            return callWithRoles(fallback, systemPrompt, userPrompt, agentName, "flash");
        };

        Supplier<String> decorated = cb.decorateSupplier(primaryCall);
        try {
            return decorated.get();
        } catch (Exception e) {
            log.warn("[ModelFallback] {} Pro 调用失败 ({}), 切换到 Flash",
                agentName, e.getMessage());
            try {
                return fallbackCall.get();
            } catch (Exception fallbackError) {
                log.error("[ModelFallback] {} Flash 也调用失败", agentName, fallbackError);
                throw new RuntimeException(
                    agentName + " 模型调用失败（Pro+Flash 均不可用）: " + fallbackError.getMessage(),
                    fallbackError);
            }
        }
    }

    /**
     * 使用 system/user 角色分离调用 ChatClient，并传递 Agent 身份给 Advisor.
     *
     * @param client       ChatClient 实例
     * @param systemPrompt System 消息，可为 null
     * @param userPrompt   User 消息
     * @param agentName    Agent 名称
     * @param tier         模型层级 ("pro" 或 "flash")
     * @return LLM 输出的文本内容
     */
    private String callWithRoles(ChatClient client, String systemPrompt,
                                  String userPrompt, String agentName, String tier) {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            return client.prompt()
                .advisors(a -> a.param("agent", agentName).param("tier", tier))
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        }
        return client.prompt()
            .advisors(a -> a.param("agent", agentName).param("tier", tier))
            .user(userPrompt)
            .call()
            .content();
    }
}
