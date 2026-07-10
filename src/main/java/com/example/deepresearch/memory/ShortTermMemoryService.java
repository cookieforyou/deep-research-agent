package com.example.deepresearch.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 短期记忆服务 — 基于 Redis 的会话级别上下文存储.
 * <p>
 * 使用 Redis 的 List 结构存储会话消息历史，
 * 支持固定窗口大小（windowSize）和 TTL 自动过期。
 * </p>
 *
 * <h3>与 Spring AI ChatMemory 的关系</h3>
 * 本服务封装与 Redis 的交互，Spring AI 的 {@code MessageWindowChatMemory}
 * 可直接配置为基于 Redis 的实现。这里提供了更细粒度的控制。
 *
 * <h3>存储结构</h3>
 * <pre>
 * Redis Key: memory:session:{sessionId}
 * Redis Type: List (LPUSH 追加消息, LTRIM 限制窗口)
 * TTL: 1 小时（研究会话结束后自动清除）
 * </pre>
 */
@Service
public class ShortTermMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ShortTermMemoryService.class);

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    /** 会话窗口大小（消息数） */
    private static final int WINDOW_SIZE = 20;
    /** 会话 TTL */
    private static final Duration TTL = Duration.ofHours(1);

    public ShortTermMemoryService(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 添加消息到会话记忆.
     *
     * @param sessionId 会话 ID
     * @param role      消息角色（user / assistant / system）
     * @param content   消息内容
     */
    public Mono<Void> addMessage(String sessionId, String role, String content) {
        String key = sessionKey(sessionId);
        String message = String.format("[%s] %s", role, content);

        return redisTemplate.opsForList()
            .leftPush(key, message)
            .then(redisTemplate.opsForList().trim(key, 0, WINDOW_SIZE - 1))
            .then(redisTemplate.expire(key, TTL))
            .doOnSuccess(v -> log.trace("[ShortMem] 消息已追加: sessionId={}", sessionId))
            .then();
    }

    /**
     * 获取会话的最近 N 条消息.
     *
     * @param sessionId 会话 ID
     * @param limit     返回消息数（默认窗口大小）
     * @return 消息列表（最新在前）
     */
    public Mono<List<String>> getMessages(String sessionId, int limit) {
        String key = sessionKey(sessionId);
        int count = limit > 0 ? Math.min(limit, WINDOW_SIZE) : WINDOW_SIZE;

        return redisTemplate.opsForList()
            .range(key, 0, count - 1)
            .collectList()
            .defaultIfEmpty(new ArrayList<>());
    }

    /**
     * 获取会话的格式化上下文（供 Agent Prompt 注入）.
     *
     * @param sessionId 会话 ID
     * @return 格式化的对话历史文本
     */
    public Mono<String> getContextForPrompt(String sessionId) {
        return getMessages(sessionId, WINDOW_SIZE)
            .map(messages -> {
                if (messages.isEmpty()) return "（无历史对话）";
                StringBuilder sb = new StringBuilder();
                // 反转以按时间顺序显示（先添加的在前）
                for (int i = messages.size() - 1; i >= 0; i--) {
                    sb.append(messages.get(i)).append("\n");
                }
                return sb.toString();
            });
    }

    /**
     * 清除会话记忆.
     */
    public Mono<Boolean> clear(String sessionId) {
        String key = sessionKey(sessionId);
        return redisTemplate.delete(key)
            .map(count -> count > 0);
    }

    private String sessionKey(String sessionId) {
        return "memory:session:" + sessionId;
    }
}
