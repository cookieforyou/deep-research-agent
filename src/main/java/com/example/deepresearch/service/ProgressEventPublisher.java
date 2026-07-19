package com.example.deepresearch.service;

import com.example.deepresearch.api.dto.ProgressEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE 进度事件发布器.
 * <p>
 * 为每个研究会话维护一个 Reactor {@link Sinks.Many}，
 * 工作流各节点通过此发布器向对应会话推送细粒度进度事件。
 * </p>
 *
 * <h3>断线重连支持（Replay Sink）</h3>
 * <p>
 * 使用 {@code replay().limit(100)} 替代 {@code multicast()}，
 * 新订阅者（包括页面刷新后重连）会收到最近 100 条历史事件回放，
 * 配合前端事件去重策略，确保断线重连后时间线完整恢复。
 * </p>
 *
 * <h3>延迟清理</h3>
 * <p>
 * 研究完成后 Sink 不立即移除，延迟 5 分钟清理，
 * 给页面刷新重连留出窗口期，避免创建空 Sink 导致客户端永久等待。
 * </p>
 *
 * <h3>进度事件类型</h3>
 * <ul>
 *   <li>INTENT_ROUTING — 正在判断查询意图</li>
 *   <li>PLANNING — 正在规划研究路径</li>
 *   <li>WEB_SEARCHING / LOCAL_SEARCHING — 正在检索证据</li>
 *   <li>JUDGING — 正在裁判证据</li>
 *   <li>ANALYZING — 正在分析归纳</li>
 *   <li>WRITING — 正在撰写报告</li>
 *   <li>COMPLETED — 研究完成</li>
 *   <li>ERROR — 发生错误</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 工作流节点中
 * progressPublisher.publish(sessionId,
 *     new ProgressEvent(ResearchStage.PLANNING, "plan", 0.0, "正在规划研究路径..."));
 *
 * // SSE Controller 中
 * Flux<ProgressEvent> stream = progressPublisher.getStream(sessionId);
 * }</pre>
 */
@Service
public class ProgressEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProgressEventPublisher.class);

    /** 研究完成后 Sink 延迟清理时间（分钟），给页面刷新重连留出窗口 */
    private static final int SINK_CLEANUP_DELAY_MINUTES = 5;

    /** 重放缓冲区上限（事件数），覆盖典型研究流程 10-30 条事件 */
    private static final int REPLAY_LIMIT = 100;

    /**
     * 会话 ID → Sinks.Many 的映射.
     * <p>
     * 使用 ConcurrentHashMap 支持高并发下的会话创建/销毁。
     * </p>
     */
    private final ConcurrentHashMap<String, Sinks.Many<ProgressEvent>> sessionSinks =
        new ConcurrentHashMap<>();

    /** 已终止（completed/error）的会话 ID 集合，用于 hasActiveSink 快速判断 */
    private final ConcurrentHashMap<String, Boolean> terminatedSessions = new ConcurrentHashMap<>();

    /** 延迟清理调度器（单线程，仅处理定时移除） */
    private final ScheduledExecutorService cleanupScheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-sink-cleanup");
            t.setDaemon(true);
            return t;
        });

    /**
     * 判断指定会话是否已有活跃 Sink.
     *
     * @param sessionId 会话 ID
     * @return true 表示 Sink 已存在且未终止
     */
    public boolean hasActiveSink(String sessionId) {
        return sessionSinks.containsKey(sessionId) && !terminatedSessions.containsKey(sessionId);
    }

    /**
     * 获取或创建会话的 Sink.
     * <p>
     * 使用 {@code replay().limit(100)} 实现历史事件重放，
     * 确保页面刷新后重连的客户端能收到完整事件流。
     * </p>
     *
     * @param sessionId 会话 ID
     * @return 该会话对应的 Sink（支持重放）
     */
    public Sinks.Many<ProgressEvent> getOrCreateSink(String sessionId) {
        return sessionSinks.computeIfAbsent(sessionId, id -> {
            log.debug("[Progress] 创建 SSE Sink (replay): sessionId={}", id);
            return Sinks.many()
                .replay()
                .limit(REPLAY_LIMIT);
        });
    }

    /**
     * 发布进度事件到指定会话.
     * <p>
     * 使用 {@code tryEmitNext} 避免阻塞——如果 sink 已满，
     * 事件被静默丢弃（进度事件可丢失，不影响研究结果）。
     * </p>
     *
     * @param sessionId 会话 ID
     * @param event     进度事件
     */
    public void publish(String sessionId, ProgressEvent event) {
        Sinks.Many<ProgressEvent> sink = sessionSinks.get(sessionId);
        if (sink == null) {
            // Sink 尚未创建（客户端尚未连接 SSE），静默跳过
            log.trace("[Progress] Sink 未创建，跳过事件: sessionId={}, stage={}",
                sessionId, event.stage());
            return;
        }

        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result == Sinks.EmitResult.OK) {
            log.debug("[Progress] 事件已推送: sessionId={}, stage={}, progress={}%",
                sessionId, event.stage(), String.format("%.1f", event.percent()));
        } else {
            log.warn("[Progress] 推送失败: sessionId={}, result={}", sessionId, result);
        }
    }

    /**
     * 获取会话的 SSE 流.
     * <p>
     * 返回的 Flux 支持历史事件重放：新订阅者先收到缓冲区中的历史事件，
     * 再接收实时事件。研究已完成的会话通过延迟清理保留重放窗口。
     * </p>
     *
     * @param sessionId 会话 ID
     * @return 进度事件 Flux（支持重放 + 实时推送）
     */
    public Flux<ProgressEvent> getStream(String sessionId) {
        return getOrCreateSink(sessionId).asFlux();
    }

    /**
     * 标记会话完成.
     * <p>
     * 发送 COMPLETED 信号后延迟 {5} 分钟清理 Sink，
     * 期间页面刷新重连的客户端可通过重放获取完整事件流（含 COMPLETED）。
     * </p>
     *
     * @param sessionId 会话 ID
     */
    public void complete(String sessionId) {
        Sinks.Many<ProgressEvent> sink = sessionSinks.get(sessionId);
        if (sink == null) {
            log.debug("[Progress] complete() 调用时 Sink 已不存在: sessionId={}", sessionId);
            return;
        }

        terminatedSessions.put(sessionId, true);
        sink.tryEmitComplete();
        log.debug("[Progress] 会话完成（延迟 {} 分钟清理）: sessionId={}",
            SINK_CLEANUP_DELAY_MINUTES, sessionId);

        // 延迟清理：给页面刷新重连留窗口期，避免创建空 Sink 导致永久等待
        cleanupScheduler.schedule(() -> {
            Sinks.Many<ProgressEvent> removed = sessionSinks.remove(sessionId);
            terminatedSessions.remove(sessionId);
            if (removed != null) {
                log.debug("[Progress] 延迟清理 Sink: sessionId={}", sessionId);
            }
        }, SINK_CLEANUP_DELAY_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 标记会话错误.
     * <p>
     * 发送 ERROR 事件后延迟清理 Sink，逻辑同 {@link #complete(String)}。
     * </p>
     *
     * @param sessionId 会话 ID
     * @param error     错误信息
     */
    public void error(String sessionId, Throwable error) {
        Sinks.Many<ProgressEvent> sink = sessionSinks.get(sessionId);
        if (sink == null) {
            log.debug("[Progress] error() 调用时 Sink 已不存在: sessionId={}", sessionId);
            return;
        }

        terminatedSessions.put(sessionId, true);
        publish(sessionId, new ProgressEvent(
            sessionId, ProgressEvent.ResearchStage.ERROR, "error", 100.0,
            "研究失败: " + error.getMessage()));
        sink.tryEmitError(error);
        log.debug("[Progress] 会话错误（延迟 {} 分钟清理）: sessionId={}",
            SINK_CLEANUP_DELAY_MINUTES, sessionId);

        // 延迟清理
        cleanupScheduler.schedule(() -> {
            Sinks.Many<ProgressEvent> removed = sessionSinks.remove(sessionId);
            terminatedSessions.remove(sessionId);
            if (removed != null) {
                log.debug("[Progress] 延迟清理 Sink (error): sessionId={}", sessionId);
            }
        }, SINK_CLEANUP_DELAY_MINUTES, TimeUnit.MINUTES);
    }
}
