package com.example.deepresearch.common.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 可观测性配置 — 启用 {@code @Timed} 和 {@code @Observed} AOP 切面.
 * <p>
 * Spring Boot Actuator 自动配置已处理基础设施（MeterRegistry、
 * ObservationRegistry、Tracing），此配置类显式注册 AOP 切面 Bean，
 * 使开发者可在任意 Spring Bean 方法上使用注解自动记录耗时和创建 Span。
 * </p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 自动记录方法耗时 + 调用次数
 * @Timed(value = "deepresearch.workflow.duration", extraTags = {"node", "plan"})
 * public PlanResult plan(String query) { ... }
 *
 * // 自动创建 Observation（含 Span + Metrics）
 * @Observed(name = "deepresearch.search", contextualName = "web-search")
 * public List<SearchResult> search(String q) { ... }
 * }</pre>
 *
 * <h3>自动采集的指标</h3>
 * <ul>
 *   <li>{@code method.timed} — 被 {@code @Timed} 标注的方法耗时直方图</li>
 *   <li>{@code method.observed} — 被 {@code @Observed} 标注的方法调用计数 + 耗时</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class ObservabilityConfig {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityConfig.class);

    /**
     * 启用 {@code @Timed} 注解的 AOP 切面.
     * <p>
     * 注册后，在任意 Spring Bean 的 public 方法上使用 {@code @Timed} 注解，
     * 即可自动记录：
     * <ul>
     *   <li>方法调用次数 (Counter)</li>
     *   <li>方法执行耗时直方图 (Timer with percentiles)</li>
     * </ul>
     * </p>
     *
     * @param meterRegistry Micrometer MeterRegistry（自动注入）
     * @return TimedAspect 切面 Bean
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry meterRegistry) {
        log.info("[Observability] @Timed AOP 切面已注册");
        return new TimedAspect(meterRegistry);
    }

    /**
     * 启用 {@code @Observed} 注解的 AOP 切面.
     * <p>
     * 注册后，在任意 Spring Bean 方法上使用 {@code @Observed} 注解，
     * 即可自动创建 Micrometer Observation，同时生成：
     * <ul>
     *   <li>Metrics — 调用次数 + 耗时（由 ObservationHandler 自动生成）</li>
     *   <li>Tracing Span — 通过 OpenTelemetry Bridge 导出到 Collector</li>
     * </ul>
     * </p>
     *
     * @param observationRegistry Micrometer ObservationRegistry（自动注入）
     * @return ObservedAspect 切面 Bean
     */
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        log.info("[Observability] @Observed AOP 切面已注册");
        return new ObservedAspect(observationRegistry);
    }
}
