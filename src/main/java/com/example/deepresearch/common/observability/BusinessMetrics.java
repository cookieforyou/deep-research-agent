package com.example.deepresearch.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 业务指标注册中心 — 集中管理搜索、缓存、RAG、安全等业务级 Micrometer 指标.
 * <p>
 * 所有业务指标通过此类统一注册，确保命名规范一致、标签受控（低基数）。
 * 遵循 Prometheus 命名最佳实践：{@code deepresearch_{domain}_{metric}_{unit}}。
 * </p>
 *
 * <h3>指标清单</h3>
 * <table>
 *   <tr><th>指标名</th><th>类型</th><th>标签</th><th>说明</th></tr>
 *   <tr><td>deepresearch.search.calls.total</td><td>Counter</td><td>engine, status</td><td>搜索调用次数</td></tr>
 *   <tr><td>deepresearch.search.latency</td><td>Timer</td><td>engine</td><td>搜索延迟</td></tr>
 *   <tr><td>deepresearch.search.results.total</td><td>Counter</td><td>engine</td><td>搜索结果总数</td></tr>
 *   <tr><td>deepresearch.search.fallback.total</td><td>Counter</td><td>from, to</td><td>搜索降级次数</td></tr>
 *   <tr><td>deepresearch.cache.access.total</td><td>Counter</td><td>result</td><td>缓存访问（hit/miss）</td></tr>
 *   <tr><td>deepresearch.security.pii.masked.total</td><td>Counter</td><td>type</td><td>PII 脱敏次数</td></tr>
 *   <tr><td>deepresearch.security.injection.detected.total</td><td>Counter</td><td>pattern</td><td>注入检测次数</td></tr>
 *   <tr><td>deepresearch.workflow.completed.total</td><td>Counter</td><td>intent, status</td><td>工作流完成次数</td></tr>
 * </table>
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    /** 按租户的评估分 Gauge 后备值（tenant → 最近一次 overallScore） */
    private final java.util.concurrent.ConcurrentHashMap<String,
        java.util.concurrent.atomic.AtomicReference<Double>> evalScoreGauges =
        new java.util.concurrent.ConcurrentHashMap<>();

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // ======================== Eval 指标 ========================

    /**
     * 记录评估分（按租户隔离的 Gauge）.
     * <p>
     * 指标名保持 {@code deepresearch.eval.score}（兼容 EvalScoreLow 告警规则），
     * 增加 {@code tenant} tag——全局单值 Gauge 时代多租户并发评估互相覆盖，
     * 低分告警会被其他租户的高分掩盖（2026-07-17 修复）。
     * </p>
     *
     * @param tenantId 租户 ID
     * @param score    本次评估综合分
     */
    public void recordEvalScore(String tenantId, double score) {
        String tenant = tenantId != null && !tenantId.isBlank() ? tenantId : "default";
        evalScoreGauges.computeIfAbsent(tenant, t -> {
            var ref = new java.util.concurrent.atomic.AtomicReference<>(0.0);
            io.micrometer.core.instrument.Gauge
                .builder("deepresearch.eval.score", ref,
                    r -> r.get() != null ? r.get() : 0.0)
                .tag("tenant", t)
                .description("最近一次报告质量评估综合分（按租户）")
                .register(registry);
            return ref;
        }).set(score);
    }

    // ======================== 搜索指标 ========================

    /**
     * 记录一次搜索调用.
     *
     * @param engine    搜索引擎名称 ("bocha", "tavily", "milvus")
     * @param status    调用状态 ("success", "fallback", "error")
     * @param latencyMs 调用耗时（毫秒）
     */
    public void recordSearchCall(String engine, String status, long latencyMs) {
        Counter.builder("deepresearch.search.calls.total")
            .tag("engine", engine)
            .tag("status", status)
            .register(registry)
            .increment();

        Timer.builder("deepresearch.search.latency")
            .tag("engine", engine)
            .register(registry)
            .record(latencyMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录搜索结果数量.
     */
    public void recordSearchResults(String engine, int count) {
        Counter.builder("deepresearch.search.results.total")
            .tag("engine", engine)
            .register(registry)
            .increment(count);
    }

    /**
     * 记录搜索降级事件.
     */
    public void recordSearchFallback(String fromEngine, String toEngine) {
        Counter.builder("deepresearch.search.fallback.total")
            .tag("from", fromEngine)
            .tag("to", toEngine)
            .register(registry)
            .increment();
    }

    // ======================== 缓存指标 ========================

    /**
     * 记录语义缓存访问.
     *
     * @param hit true=命中, false=未命中
     */
    public void recordCacheAccess(boolean hit) {
        Counter.builder("deepresearch.cache.access.total")
            .tag("result", hit ? "hit" : "miss")
            .register(registry)
            .increment();
    }

    // ======================== 安全指标 ========================

    /**
     * 记录 PII 脱敏事件.
     *
     * @param type  脱敏类型 ("phone", "email", "id_card", "bank_card")
     * @param count 本次脱敏数量
     */
    public void recordPiiMasked(String type, int count) {
        Counter.builder("deepresearch.security.pii.masked.total")
            .tag("type", type)
            .register(registry)
            .increment(count);
    }

    /**
     * 记录 Prompt 注入检测事件.
     *
     * @param patternName 匹配的注入模式名称
     */
    public void recordInjectionDetected(String patternName) {
        Counter.builder("deepresearch.security.injection.detected.total")
            .tag("pattern", patternName)
            .register(registry)
            .increment();
    }

    // ======================== 工作流指标 ========================

    /**
     * 记录工作流完成.
     *
     * @param intent    路由意图 ("direct" 或 "research")
     * @param status    完成状态 ("success", "error")
     * @param durationMs 总耗时（毫秒）
     */
    public void recordWorkflowCompleted(String intent, String status, long durationMs) {
        Counter.builder("deepresearch.workflow.completed.total")
            .tag("intent", intent)
            .tag("status", status)
            .register(registry)
            .increment();

        Timer.builder("deepresearch.workflow.duration")
            .tag("intent", intent)
            .register(registry)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }
}
