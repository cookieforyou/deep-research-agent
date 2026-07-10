package com.example.deepresearch.security;

import com.example.deepresearch.common.config.DeepResearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * PII 脱敏 Advisor — 可逆标记化 + LLM 响应还原.
 * <p>
 * 在 ChatClient 调用链的两个关键点执行操作：
 * <ul>
 *   <li><b>before()</b>: 将 Prompt 中的 PII 替换为类型化令牌（如 {@code <PHONE_0>}），
 *       确保 DeepSeek API 永远看不到原始 PII</li>
 *   <li><b>after()</b>: 扫描 LLM 响应中的令牌，从 Vault 中还原原始 PII 值</li>
 * </ul>
 * </p>
 *
 * <h3>优先级</h3>
 * <p>{@link Ordered#HIGHEST_PRECEDENCE} — 最高优先级，在任何其他 Advisor 之前运行。</p>
 *
 * <h3>线程安全</h3>
 * <p>无状态设计，所有状态由 {@link PiiMaskingService} 的线程安全 Vault 管理。</p>
 */
@Component
public class PiiMaskingAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(PiiMaskingAdvisor.class);

    private final PiiMaskingService maskingService;
    private final SecurityLogService securityLog;
    private final DeepResearchProperties properties;

    public PiiMaskingAdvisor(PiiMaskingService maskingService,
                              SecurityLogService securityLog,
                              DeepResearchProperties properties) {
        this.maskingService = maskingService;
        this.securityLog = securityLog;
        this.properties = properties;
    }

    @Override
    public String getName() {
        return "PiiMaskingAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * 请求前处理: 将 Prompt 中的 PII 替换为令牌.
     */
    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        if (properties.pii() == null || !properties.pii().enabled()) {
            return request;
        }

        Prompt originalPrompt = request.prompt();
        List<Message> originalMessages = originalPrompt.getInstructions();
        List<Message> tokenizedMessages = new ArrayList<>();
        boolean anyTokenized = false;

        for (Message message : originalMessages) {
            if (message instanceof UserMessage userMessage) {
                String text = userMessage.getText();
                if (text != null && !text.isBlank()) {
                    String tokenizedText = maskingService.tokenizeToString(text);
                    if (!tokenizedText.equals(text)) {
                        anyTokenized = true;
                        UserMessage tokenizedMsg = userMessage.mutate()
                            .text(tokenizedText)
                            .build();
                        tokenizedMessages.add(tokenizedMsg);
                        continue;
                    }
                }
            }
            tokenizedMessages.add(message);
        }

        if (anyTokenized) {
            Prompt tokenizedPrompt = originalPrompt.mutate()
                .messages(tokenizedMessages)
                .build();

            return request.mutate().prompt(tokenizedPrompt).build();
        }

        return request;
    }

    /**
     * 响应后处理: 将 LLM 响应中的令牌还原为原始 PII 值.
     */
    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        if (properties.pii() == null || !properties.pii().enabled()) {
            return response;
        }

        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null) {
            return response;
        }

        List<Generation> generations = chatResponse.getResults();
        if (generations == null || generations.isEmpty()) {
            return response;
        }

        List<Generation> restoredGenerations = new ArrayList<>();
        boolean anyRestored = false;

        for (Generation gen : generations) {
            AssistantMessage output = gen.getOutput();
            if (output != null) {
                String text = output.getText();
                if (text != null && !text.isBlank()) {
                    String restoredText = maskingService.restore(text);
                    if (!restoredText.equals(text)) {
                        anyRestored = true;
                        AssistantMessage restoredMsg = AssistantMessage.builder()
                            .content(restoredText)
                            .properties(output.getMetadata())
                            .toolCalls(output.getToolCalls())
                            .build();
                        restoredGenerations.add(new Generation(restoredMsg, gen.getMetadata()));
                        continue;
                    }
                }
            }
            restoredGenerations.add(gen);
        }

        if (anyRestored) {
            ChatResponse restoredChatResponse = ChatResponse.builder()
                .generations(restoredGenerations)
                .metadata(chatResponse.getMetadata())
                .build();

            return ChatClientResponse.builder()
                .chatResponse(restoredChatResponse)
                .context(response.context())
                .build();
        }

        return response;
    }
}
