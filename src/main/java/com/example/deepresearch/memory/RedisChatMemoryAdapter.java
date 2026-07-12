package com.example.deepresearch.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis ChatMemory 适配器 — 将现有 ShortTermMemoryService 包装为 Spring AI 标准接口.
 * <p>
 * 使 Spring AI 2.0 的 {@link org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor}
 * 能够透明使用现有的 Redis 短期记忆存储，无需修改 ShortTermMemoryService 的底层实现。
 * 由于 ChatMemory 接口为同步语义而 ShortTermMemoryService 为响应式，
 * 适配器内部使用 {@code .block()} 桥接。
 * </p>
 */
@Component
public class RedisChatMemoryAdapter implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemoryAdapter.class);

    private final ShortTermMemoryService shortTermMemory;

    public RedisChatMemoryAdapter(ShortTermMemoryService shortTermMemory) {
        this.shortTermMemory = shortTermMemory;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        for (Message msg : messages) {
            String role = msg instanceof UserMessage ? "user" : "assistant";
            shortTermMemory.addMessage(conversationId, role, msg.getText())
                .doOnError(e -> log.warn("[ChatMemory] 写入失败: conversationId={}, error={}",
                    conversationId, e.getMessage()))
                .subscribe();
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        try {
            // 获取全部消息（ShortTermMemoryService 窗口上限为 20 条）
            List<String> rawMessages = shortTermMemory.getMessages(conversationId, 20)
                .block(Duration.ofSeconds(5));
            if (rawMessages == null || rawMessages.isEmpty()) {
                return List.of();
            }

            // 反转以按时间顺序返回（Redis List 最新在前，ChatMemory 要求最早在前）
            List<Message> result = new ArrayList<>();
            for (int i = rawMessages.size() - 1; i >= 0; i--) {
                Message parsed = parseMessage(rawMessages.get(i));
                if (parsed != null) {
                    result.add(parsed);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[ChatMemory] 读取失败: conversationId={}, error={}",
                conversationId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void clear(String conversationId) {
        try {
            shortTermMemory.clear(conversationId)
                .block(Duration.ofSeconds(3));
        } catch (Exception e) {
            log.warn("[ChatMemory] 清除失败: conversationId={}, error={}",
                conversationId, e.getMessage());
        }
    }

    /**
     * 解析 ShortTermMemoryService 存储的消息格式 {@code [role] content}.
     */
    private Message parseMessage(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.startsWith("[user] ")) {
            return new UserMessage(raw.substring(7));
        }
        if (raw.startsWith("[assistant] ")) {
            return new AssistantMessage(raw.substring(12));
        }
        log.trace("[ChatMemory] 无法解析消息格式: {}", raw);
        return null;
    }
}
