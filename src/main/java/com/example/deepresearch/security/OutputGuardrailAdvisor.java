package com.example.deepresearch.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 输出安全护栏 Advisor — LLM 响应后置安全校验.
 * <p>
 * 在 LLM 生成响应后检查输出内容，拦截包含敏感模式（如系统提示词泄露、
 * 内部指令暴露等）的回复，替换为安全兜底文案。
 * 触发拦截时通过 {@link SecurityLogService} 记录安全事件。
 * </p>
 */
@Component
public class OutputGuardrailAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(OutputGuardrailAdvisor.class);

    private final SecurityLogService securityLog;

    /** 敏感词列表（后续可迁移到 application.yml 配置） */
    private static final List<String> BLOCKED_PATTERNS = List.of(
        "系统提示词", "system prompt", "internal instructions"
    );

    public OutputGuardrailAdvisor(SecurityLogService securityLog) {
        this.securityLog = securityLog;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // 先执行 LLM 调用
        ChatClientResponse response = chain.nextCall(request);

        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null) {
            return response;
        }

        String output = chatResponse.getResult().getOutput().getText();
        if (output == null) {
            return response;
        }

        // 检查敏感模式
        for (String pattern : BLOCKED_PATTERNS) {
            if (output.toLowerCase().contains(pattern.toLowerCase())) {
                // 优先从 TenantContext 获取（跨虚拟线程传播），advisor context 作为 fallback
                String userId = TenantContext.getCurrentUser();
                if (userId == null) {
                    userId = (String) request.context().getOrDefault("user_id", "unknown");
                }
                securityLog.logOutputBlocked(userId, pattern);
                log.warn("[OutputGuardrail] 输出被护栏拦截: pattern='{}'", pattern);
                return replaceWithSafeResponse(response);
            }
        }

        return response;
    }

    /**
     * 替换响应内容为安全兜底文案.
     */
    private ChatClientResponse replaceWithSafeResponse(ChatClientResponse original) {
        ChatResponse chatResponse = original.chatResponse();
        AssistantMessage safeMessage = AssistantMessage.builder()
            .content("抱歉，无法生成该内容。请重新描述您的问题。")
            .build();

        ChatResponse safeChatResponse = ChatResponse.builder()
            .generations(List.of(new Generation(safeMessage, chatResponse.getResult().getMetadata())))
            .metadata(chatResponse.getMetadata())
            .build();

        return ChatClientResponse.builder()
            .chatResponse(safeChatResponse)
            .context(original.context())
            .build();
    }

    @Override
    public String getName() {
        return "OutputGuardrailAdvisor";
    }

    @Override
    public int getOrder() {
        return 300;  // 输出侧，在模型调用之后执行
    }
}
