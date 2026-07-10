package com.example.deepresearch.service;

import com.example.deepresearch.api.dto.ProgressEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 进度事件发布器.
 * <p>
 * 为每个研究会话维护一个 Reactor {@link Sinks.Many}，
 * 工作流各节点通过此发布器向对应会话推送细粒度进度事件。
 * </p>
 *
 * <h3>进度事件类型</h3>
 * <ul>
 *   <li>INTENT_ROUTING — 正在判断查询意图</li>
 *   <li>PLANNING — 正在规划研究路径</li>
 *   <li>WEB_SEARCHING / LOCAL_SEARCHING — 正在检索证据</li>
 *   <li>JUDGING — 正在裁判证据</li>
 *   <li>ANALYZING — 正在分析归纳</li>
 *   <li>REFLECTING — 正在反思补搜</li>
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

    /**
     * 会话 ID → Sinks.Many 的映射.
     * <p>
     * 使用 ConcurrentHashMap 支持高并发下的会话创建/销毁。
     * </p>
     */
    private final ConcurrentHashMap<String, Sinks.Many<ProgressEvent>> sessionSinks =
        new ConcurrentHashMap<>();

    /**
     * 获取或创建会话的 Sink.
     *
     * @param sessionId 会话 ID
     * @return 该会话对应的 Sink（可安全多播）
     */
    public Sinks.Many<ProgressEvent> getOrCreateSink(String sessionId) {
        return sessionSinks.computeIfAbsent(sessionId, id -> {
            log.debug("[Progress] 创建 SSE Sink: sessionId={}", id);
            return Sinks.many()
                .multicast()                    // 多播（允许多个订阅者）
                .onBackpressureBuffer(100);     // 背压缓冲 100 条事件
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
     *
     * @param sessionId 会话 ID
     * @return 进度事件的冷 Flux（客户端连接时才开始推送）
     */
    public Flux<ProgressEvent> getStream(String sessionId) {
        return getOrCreateSink(sessionId).asFlux();
    }

    /**
     * 标记会话完成并清理 Sink.
     * <p>
     * 发送 COMPLETED 信号后，后续的 publish() 调用将被忽略。
     * </p>
     *
     * @param sessionId 会话 ID
     */
    public void complete(String sessionId) {
        Sinks.Many<ProgressEvent> sink = sessionSinks.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
            log.debug("[Progress] 会话完成: sessionId={}", sessionId);
        }
    }

    /**
     * 标记会话错误.
     *
     * @param sessionId 会话 ID
     * @param error     错误信息
     */
    public void error(String sessionId, Throwable error) {
        Sinks.Many<ProgressEvent> sink = sessionSinks.get(sessionId);
        if (sink != null) {
            publish(sessionId, new ProgressEvent(
                sessionId, ProgressEvent.ResearchStage.ERROR, "error", 100.0,
                "研究失败: " + error.getMessage()));
            sink.tryEmitError(error);
            sessionSinks.remove(sessionId);
        }
    }
}
