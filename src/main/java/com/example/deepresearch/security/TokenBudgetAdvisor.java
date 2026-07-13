package com.example.deepresearch.security;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Token 预算管控 Advisor — 用户配额限流 + Token 消耗追踪.
 * <p>
 * 基于 Redis 的简单计数器实现分布式限流，
 * 在 ChatClient 调用前检查用户配额，调用后根据实际 Token 消耗记录指标。
 * 使用 Redis INCR + EXPIRE 原子操作，无需额外依赖。
 * </p>
 */
@Component
public class TokenBudgetAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TokenBudgetAdvisor.class);

    /** 默认每用户每小时调用次数上限 */
    private static final int DEFAULT_HOURLY_LIMIT = 100;

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;

    public TokenBudgetAdvisor(ReactiveRedisTemplate<String, String> redisTemplate,
                               MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // 优先从 TenantContext 获取（跨虚拟线程传播），advisor context 作为 fallback
        String userId = TenantContext.getCurrentUser();
        if (userId == null) {
            userId = (String) request.context().getOrDefault("user_id", "anonymous");
        }

        // 前置：基于 Redis INCR + EXPIRE 的分布式限流
        String hourBucket = String.valueOf(System.currentTimeMillis() / 3600000);
        String rateLimitKey = "ai:budget:" + userId + ":" + hourBucket;

        Long currentCount = redisTemplate.opsForValue().increment(rateLimitKey)
            .block(Duration.ofSeconds(3));
        if (currentCount != null && currentCount == 1) {
            // 首次访问，设置 TTL
            redisTemplate.expire(rateLimitKey, Duration.ofHours(2)).subscribe();
        }

        if (currentCount != null && currentCount > DEFAULT_HOURLY_LIMIT) {
            meterRegistry.counter("deepresearch.security.quota.exceeded",
                "user_id", userId).increment();
            log.warn("[TokenBudget] 配额超限: userId={}, count={}, limit={}",
                userId, currentCount, DEFAULT_HOURLY_LIMIT);
            throw new AiQuotaExceededException("AI 调用配额已用完，请稍后再试");
        }

        // 执行 LLM 调用
        ChatClientResponse response = chain.nextCall(request);

        // 后置：记录实际 Token 消耗到 Micrometer
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse != null) {
            Usage usage = chatResponse.getMetadata().getUsage();
            if (usage != null) {
                long promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
                long completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
                long total = promptTokens + completionTokens;
                meterRegistry.counter("deepresearch.token.consumed",
                    "user_id", userId,
                    "type", "prompt").increment(promptTokens);
                meterRegistry.counter("deepresearch.token.consumed",
                    "user_id", userId,
                    "type", "completion").increment(completionTokens);
                log.debug("[TokenBudget] userId={}, prompt={}, completion={}, total={}",
                    userId, promptTokens, completionTokens, total);
            }
        }

        return response;
    }

    @Override
    public String getName() {
        return "TokenBudgetAdvisor";
    }

    @Override
    public int getOrder() {
        return 200;  // 在模型调用前尽早执行
    }

    /**
     * AI 调用配额超限异常.
     */
    public static class AiQuotaExceededException extends RuntimeException {
        public AiQuotaExceededException(String message) {
            super(message);
        }
    }
}
