package com.example.deepresearch.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 工作流 Tracing 助手 — 为每个工作流节点创建 Span（traceId + 标签）+ Metrics（Timer + Counter）.
 * <p>
 * 当 Micrometer {@link Tracer} Bean 可用时（OpenTelemetry SDK 已配置），创建真正的分布式 Span，
 * traceId 写入日志 MDC 并通过 OTLP 导出到 Jaeger。
 * 当 Tracer 不可用时，生成局部 traceId 并仅记录 Metrics，不影响应用启动和运行。
 * </p>
 *
 * <h3>输出</h3>
 * <ul>
 *   <li><b>日志 MDC</b>: traceId 写入 {@code %X{traceId}}，日志可搜索</li>
 *   <li><b>Tracing Span</b>: 通过 OTLP 导出到 Jaeger（需 OpenTelemetry SDK 完整配置）</li>
 *   <li><b>Metrics</b>: Timer + Counter，始终可用（通过 Prometheus 端点暴露）</li>
 * </ul>
 */
@Component
public class WorkflowTracingHelper {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTracingHelper.class);

    public static final String WORKFLOW_PREFIX = "workflow.";
    public static final String SEARCH_OBSERVATION = "deepresearch.search";

    private final Tracer tracer;
    private final MeterRegistry meterRegistry;
    private final boolean tracingEnabled;

    /**
     * 使用 ObjectProvider 注入 Tracer，使其成为可选依赖.
     * 当 OpenTelemetry SDK 未完整配置时，Tracer Bean 可能不存在，
     * 此时优雅降级为 Metrics-only 模式。
     */
    public WorkflowTracingHelper(ObjectProvider<Tracer> tracerProvider,
                                  MeterRegistry meterRegistry) {
        this.tracer = tracerProvider.getIfAvailable();
        this.meterRegistry = meterRegistry;
        this.tracingEnabled = this.tracer != null;
        if (tracingEnabled) {
            log.info("[Tracing] 分布式追踪已启用 (Tracer={})", tracer.getClass().getSimpleName());
        } else {
            log.info("[Tracing] 分布式追踪未启用 (Tracer Bean 不可用)，"
                + "traceId 使用本地生成，Metrics 正常采集");
        }
    }

    /**
     * 在追踪上下文中执行操作 — Span + Metrics + MDC traceId.
     */
    public <T> T observe(String name, Map<String, String> tags, Supplier<T> action) {
        long start = System.currentTimeMillis();

        if (tracingEnabled) {
            return observeWithSpan(name, tags, null, action, start);
        } else {
            return observeLocal(name, tags, action, start);
        }
    }

    /**
     * 在追踪上下文中执行操作（带高基数标签）.
     */
    public <T> T observeWithHighCardinality(String name,
                                             Map<String, String> lowCardTags,
                                             Map<String, String> highCardTags,
                                             Supplier<T> action) {
        long start = System.currentTimeMillis();

        if (tracingEnabled) {
            return observeWithSpan(name, lowCardTags, highCardTags, action, start);
        } else {
            return observeLocal(name, lowCardTags, action, start);
        }
    }

    // ======================== Span 模式（Tracer 可用时） ========================

    private <T> T observeWithSpan(String name, Map<String, String> lowCardTags,
                                   Map<String, String> highCardTags,
                                   Supplier<T> action, long start) {
        Span span = tracer.nextSpan().name(name).start();
        span.tag("application", "deep-research-agent");
        if (lowCardTags != null) lowCardTags.forEach(span::tag);
        if (highCardTags != null) highCardTags.forEach(span::tag);

        try (var ws = tracer.withSpan(span)) {
            log.debug("[Tracing] {} started (traceId={})", name, span.context().traceId());
            T result = action.get();
            long durationMs = System.currentTimeMillis() - start;
            log.debug("[Tracing] {} completed ({}ms)", name, durationMs);
            recordTimer(name, lowCardTags, durationMs);
            return result;
        } catch (Exception e) {
            span.error(e);
            long durationMs = System.currentTimeMillis() - start;
            log.error("[Tracing] {} failed ({}ms): {}", name, durationMs, e.getMessage());
            recordTimer(name, lowCardTags, durationMs);
            throw e;
        } finally {
            span.end();
        }
    }

    // ======================== 本地模式（Tracer 不可用时） ========================

    private <T> T observeLocal(String name, Map<String, String> tags,
                                Supplier<T> action, long start) {
        // 生成局部 traceId 用于日志关联
        String localTraceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", localTraceId);
        try {
            log.debug("[Tracing] {} started (localTraceId={})", name, localTraceId);
            T result = action.get();
            long durationMs = System.currentTimeMillis() - start;
            log.debug("[Tracing] {} completed ({}ms)", name, durationMs);
            recordTimer(name, tags, durationMs);
            return result;
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            log.error("[Tracing] {} failed ({}ms): {}", name, durationMs, e.getMessage());
            recordTimer(name, tags, durationMs);
            throw e;
        } finally {
            MDC.remove("traceId");
        }
    }

    // ======================== Metrics ========================

    private void recordTimer(String name, Map<String, String> tags, long durationMs) {
        Timer.Builder builder = Timer.builder("deepresearch.workflow.node.duration")
            .tag("node", name);
        if (tags != null) {
            for (var entry : tags.entrySet()) {
                builder = builder.tag(entry.getKey(), entry.getValue());
            }
        }
        builder.register(meterRegistry).record(durationMs, TimeUnit.MILLISECONDS);
    }
}
