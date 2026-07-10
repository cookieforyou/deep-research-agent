package com.example.deepresearch.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Token 用量追踪器 — 实时统计 LLM API 调用次数、Token 消耗和成本.
 * <p>
 * 通过 Spring AI 的 {@code ChatClient} Advisor 拦截每次 LLM 调用，
 * 提取 {@code ChatResponse} 中的 {@code usage} 信息，
 * 写入 Micrometer 指标供 Prometheus 抓取。
 * </p>
 *
 * <h3>追踪指标</h3>
 * <ul>
 *   <li>{@code deepresearch.llm.calls.total} — LLM 调用总次数（按 agent 标签分组）</li>
 *   <li>{@code deepresearch.llm.tokens.input} — 输入 Token 总数</li>
 *   <li>{@code deepresearch.llm.tokens.output} — 输出 Token 总数</li>
 *   <li>{@code deepresearch.llm.cost.estimated} — 估算 API 成本（USD）</li>
 *   <li>{@code deepresearch.llm.latency} — 调用延迟（毫秒）</li>
 * </ul>
 *
 * <h3>TODO: 集成</h3>
 * 需要在 ChatClient 配置中添加自定义 Advisor，
 * 在每次 {@code chatClient.call()} 后调用 {@code track()}.
 */
@Component
public class TokenUsageTracker {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageTracker.class);

    private final MeterRegistry meterRegistry;

    /** 单次研究会话的 Token 统计（sessionId → SessionStats） */
    private final ConcurrentHashMap<String, SessionStats> sessionStats = new ConcurrentHashMap<>();

    /**
     * DeepSeek V4 成本估算（每百万 Token 的 USD 价格）.
     * 价格随官方调整，此处为 2026 年 7 月参考值.
     */
    private static final double PRO_INPUT_PRICE  = 0.55;   // $0.55 / 1M input tokens
    private static final double PRO_OUTPUT_PRICE = 2.19;   // $2.19 / 1M output tokens
    private static final double FLASH_INPUT_PRICE  = 0.14; // $0.14 / 1M input tokens
    private static final double FLASH_OUTPUT_PRICE = 0.55; // $0.55 / 1M output tokens

    public TokenUsageTracker(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 记录一次 LLM 调用.
     *
     * @param agentName     Agent 名称（如 "planner", "writer"）
     * @param modelTier     模型层级（"pro" 或 "flash"）
     * @param inputTokens   输入 Token 数
     * @param outputTokens  输出 Token 数
     * @param latencyMs     调用耗时（毫秒）
     * @param sessionId     会话 ID
     */
    public void track(String agentName, String modelTier, int inputTokens,
                       int outputTokens, long latencyMs, String sessionId) {
        // 计数器
        counter("calls.total", "agent", agentName, "tier", modelTier).increment();
        counter("tokens.input", "agent", agentName, "tier", modelTier)
            .increment(inputTokens);
        counter("tokens.output", "agent", agentName, "tier", modelTier)
            .increment(outputTokens);

        // 成本估算
        double cost = estimateCost(modelTier, inputTokens, outputTokens);
        counter("cost.estimated", "agent", agentName, "tier", modelTier)
            .increment((long) (cost * 1_000_000));  // 微美元（避免浮点 Counter）

        // 延迟
        Timer.builder("deepresearch.llm.latency")
            .tag("agent", agentName)
            .tag("tier", modelTier)
            .register(meterRegistry)
            .record(latencyMs, TimeUnit.MILLISECONDS);

        // 会话级统计
        sessionStats.computeIfAbsent(sessionId, k -> new SessionStats())
            .accumulate(modelTier, inputTokens, outputTokens, cost);

        log.debug("[TokenTracker] {} ({}): in={}, out={}, cost=${}, latency={}ms",
            agentName, modelTier, inputTokens, outputTokens, String.format("%.4f", cost), latencyMs);
    }

    /**
     * 获取会话的总成本.
     */
    public double getSessionCost(String sessionId) {
        SessionStats stats = sessionStats.get(sessionId);
        return stats != null ? stats.totalCost : 0.0;
    }

    /**
     * 记录模型降级事件.
     *
     * @param agentName Agent 名称（如 "Planner", "Writer"）
     * @param fromModel 原模型层级（如 "pro"）
     * @param toModel   降级后模型层级（如 "flash"）
     */
    public void trackFallback(String agentName, String fromModel, String toModel) {
        counter("fallback.total", "agent", agentName, "from", fromModel, "to", toModel).increment();
        log.warn("[TokenTracker] 模型降级: {} {}→{}", agentName, fromModel, toModel);
    }

    /**
     * 清理会话统计.
     */
    public void clearSession(String sessionId) {
        SessionStats stats = sessionStats.remove(sessionId);
        if (stats != null) {
            log.info("[TokenTracker] 会话 {} 完成: 总Token={}, 总成本=${}",
                sessionId, stats.totalTokens(), String.format("%.4f", stats.totalCost));
        }
    }

    /** 估算 API 成本 */
    private double estimateCost(String tier, int inputTokens, int outputTokens) {
        return "pro".equals(tier)
            ? (inputTokens / 1_000_000.0) * PRO_INPUT_PRICE
              + (outputTokens / 1_000_000.0) * PRO_OUTPUT_PRICE
            : (inputTokens / 1_000_000.0) * FLASH_INPUT_PRICE
              + (outputTokens / 1_000_000.0) * FLASH_OUTPUT_PRICE;
    }

    /** 创建 Counter（带标签） */
    private Counter counter(String name, String... tags) {
        return Counter.builder("deepresearch.llm." + name)
            .tags(tags)
            .register(meterRegistry);
    }

    /** 会话级 Token 统计 */
    static class SessionStats {
        long totalInputTokens;
        long totalOutputTokens;
        double totalCost;

        void accumulate(String tier, int input, int output, double cost) {
            totalInputTokens += input;
            totalOutputTokens += output;
            totalCost += cost;
        }

        long totalTokens() { return totalInputTokens + totalOutputTokens; }
    }
}
