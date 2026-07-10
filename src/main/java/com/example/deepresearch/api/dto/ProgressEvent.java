package com.example.deepresearch.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * 进度事件 — SSE 流式推送的单个进度消息.
 * <p>
 * 每个事件描述工作流中某个节点的执行状态，
 * 前端可据此渲染进度条、当前阶段标签和详细描述。
 * </p>
 *
 * @param sessionId 会话 ID
 * @param stage     当前研究阶段
 * @param nodeName  当前节点名称（如 "plan", "web_search"）
 * @param percent   总体完成百分比（0.0 ~ 100.0）
 * @param message   人类可读的进度描述
 * @param timestamp 事件时间戳
 */
public record ProgressEvent(
    String sessionId,
    ResearchStage stage,
    String nodeName,
    double percent,
    String message,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    Instant timestamp
) {
    /** 便捷构造 — 自动填充时间戳 */
    public ProgressEvent(String sessionId, ResearchStage stage, String nodeName,
                          double percent, String message) {
        this(sessionId, stage, nodeName, percent, message, Instant.now());
    }

    /**
     * 研究阶段枚举 — 对应工作流中的各个节点.
     */
    public enum ResearchStage {
        /** 意图路由中 */
        INTENT_ROUTING,
        /** 任务规划中 */
        PLANNING,
        /** 网络搜索中 */
        WEB_SEARCHING,
        /** 本地知识库检索中 */
        LOCAL_SEARCHING,
        /** 证据裁判中 */
        JUDGING,
        /** 分析归纳中 */
        ANALYZING,
        /** 反思补搜中 */
        REFLECTING,
        /** 撰写报告中 */
        WRITING,
        /** 研究完成 */
        COMPLETED,
        /** 缓存命中 */
        CACHE_HIT,
        /** 模型降级通知 */
        MODEL_FALLBACK,
        /** 搜索降级通知 */
        SEARCH_FALLBACK,
        /** 发生错误 */
        ERROR
    }

    // =========================== 工厂方法 ===========================

    /** 创建节点开始的进度事件 */
    public static ProgressEvent started(String sessionId, ResearchStage stage,
                                         String nodeName, String message) {
        return new ProgressEvent(sessionId, stage, nodeName, 0.0, message);
    }

    /** 创建节点完成的进度事件 */
    public static ProgressEvent completed(String sessionId, ResearchStage stage,
                                           String nodeName, String message) {
        return new ProgressEvent(sessionId, stage, nodeName, 100.0, message);
    }

    /** 创建带进度百分比的事件 */
    public static ProgressEvent progress(String sessionId, ResearchStage stage,
                                          String nodeName, double percent, String message) {
        return new ProgressEvent(sessionId, stage, nodeName, percent, message);
    }

    /** 创建搜索进度事件 */
    public static ProgressEvent searching(String sessionId, String source,
                                           int current, int total) {
        ResearchStage stage = "Web".equals(source)
            ? ResearchStage.WEB_SEARCHING : ResearchStage.LOCAL_SEARCHING;
        double pct = total > 0 ? (double) current / total * 100.0 : 0;
        return new ProgressEvent(sessionId, stage, source + "_search",
            pct, String.format("正在检索%s证据 (%d/%d)...", source, current, total));
    }
}
