package com.example.deepresearch.agent.bundle;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.stereotype.Component;

/**
 * 智能模型路由器 — 根据 Prompt 复杂度自动选择 Pro/Flash 模型.
 * <p>
 * 实现 Spring AI 2.0 的 {@link ChatModel} 接口，在单个 ChatModel Bean 基础上
 * 通过动态 {@link DeepSeekChatOptions} 切换模型。
 * 简单推理（短文本）→ Flash（低成本），复杂推理（长文本）→ Pro（高质量）。
 * 内置 CircuitBreaker 保护 Pro 调用，熔断时自动降级到 Flash。
 * </p>
 *
 * <h3>路由策略</h3>
 * <table>
 *   <tr><th>条件</th><th>模型选择</th></tr>
 *   <tr><td>Prompt 文本 &lt; 500 字符</td><td>Flash</td></tr>
 *   <tr><td>Prompt 文本 ≥ 500 字符</td><td>Pro</td></tr>
 *   <tr><td>Pro CircuitBreaker 熔断</td><td>自动降级 Flash</td></tr>
 * </table>
 */
@Component
public class SmartRoutingChatModel {

    private static final Logger log = LoggerFactory.getLogger(SmartRoutingChatModel.class);

    private static final int COMPLEXITY_THRESHOLD = 500;

    private final ChatModel chatModel;
    private final CircuitBreaker circuitBreaker;

    public SmartRoutingChatModel(ChatModel chatModel,
                                  CircuitBreakerRegistry cbRegistry) {
        this.chatModel = chatModel;
        this.circuitBreaker = cbRegistry.circuitBreaker("llm-circuit-breaker");
    }

    /**
     * 智能路由调用 — 根据 Prompt 复杂度选择 Pro/Flash 模型，Pro 带 CircuitBreaker 降级保护.
     */
    public ChatResponse call(Prompt prompt) {
        DeepSeekApi.ChatModel targetModel = selectModel(prompt);
        log.debug("[SmartRouting] 选择模型: {} (prompt 约 {} 字符)",
            targetModel.getValue(), estimateLength(prompt));

        // 为 Pro 调用包裹 CircuitBreaker
        if (DeepSeekApi.ChatModel.DEEPSEEK_V4_PRO.equals(targetModel)) {
            try {
                return circuitBreaker.executeSupplier(() ->
                    chatModel.call(withModelOptions(prompt, targetModel)));
            } catch (Exception e) {
                log.warn("[SmartRouting] Pro 调用失败 ({}), 降级到 Flash", e.getMessage());
                return chatModel.call(withModelOptions(prompt,
                    DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH));
            }
        }

        return chatModel.call(withModelOptions(prompt, targetModel));
    }

    /**
     * 为 Prompt 设置模型选择.
     */
    private Prompt withModelOptions(Prompt prompt, DeepSeekApi.ChatModel model) {
        return new Prompt(
            prompt.getInstructions(),
            DeepSeekChatOptions.builder()
                .model(model)
                .build());
    }

    /**
     * 基于 Prompt 复杂度选择模型：短文本 → Flash，长文本 → Pro.
     */
    private DeepSeekApi.ChatModel selectModel(Prompt prompt) {
        int length = prompt.getInstructions().stream()
            .mapToInt(msg -> msg.getText() != null ? msg.getText().length() : 0)
            .sum();

        if (length >= COMPLEXITY_THRESHOLD) {
            return DeepSeekApi.ChatModel.DEEPSEEK_V4_PRO;
        }
        return DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH;
    }

    /**
     * 估算 Prompt 文本总长度（用于日志）.
     */
    private int estimateLength(Prompt prompt) {
        return prompt.getInstructions().stream()
            .mapToInt(msg -> msg.getText() != null ? msg.getText().length() : 0)
            .sum();
    }
}
