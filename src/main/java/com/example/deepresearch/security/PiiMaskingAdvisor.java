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
 *   <li><b>before()</b>: 创建<strong>请求级 Vault</strong>，将 Prompt 中的 PII 替换为
 *       类型化令牌（如 {@code <PHONE_0>}），Vault 经 advisor context 传递，
 *       确保 DeepSeek API 永远看不到原始 PII</li>
 *   <li><b>after()</b>: 从 context 取回本请求的 Vault，扫描 LLM 响应中的令牌还原原始值</li>
 * </ul>
 * </p>
 *
 * <h3>优先级</h3>
 * <p>{@link Ordered#HIGHEST_PRECEDENCE} — 最高优先级，在任何其他 Advisor 之前运行。</p>
 *
 * <h3>租户隔离</h3>
 * <p>
 * Vault 为请求级作用域（advisor context 携带），令牌只在本次调用内可还原——
 * 其他会话/租户的响应即使出现相同令牌字面量也无法还原出本请求的 PII。
 * </p>
 */
@Component
public class PiiMaskingAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(PiiMaskingAdvisor.class);

    /** advisor context 中携带请求级 Vault 的 key */
    static final String PII_VAULT_CONTEXT_KEY = "piiVault";

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

        // 跳过外部数据（搜索结果等公开数据无需脱敏，避免误匹配和性能浪费）
        if (Boolean.TRUE.equals(request.context().get("skipPiiMask"))) {
            return request;
        }

        Prompt originalPrompt = request.prompt();
        List<Message> originalMessages = originalPrompt.getInstructions();
        List<Message> tokenizedMessages = new ArrayList<>();
        boolean anyTokenized = false;

        // 请求级 Vault：仅本次调用可还原，经 advisor context 传递到 after()
        PiiMaskingService.PiiVault vault = new PiiMaskingService.PiiVault();

        for (Message message : originalMessages) {
            if (message instanceof UserMessage userMessage) {
                String text = userMessage.getText();
                if (text != null && !text.isBlank()) {
                    String tokenizedText = maskingService.tokenize(text, vault).tokenizedText();
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

            // Vault 挂入 context，供 after() 还原本请求的令牌
            request.context().put(PII_VAULT_CONTEXT_KEY, vault);

            return request.mutate().prompt(tokenizedPrompt).build();
        }

        return request;
    }

    /**
     * 响应后处理: 从 context 取回本请求的 Vault，将响应中的令牌还原为原始 PII 值.
     */
    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        if (properties.pii() == null || !properties.pii().enabled()) {
            return response;
        }

        // 本请求未做标记化（无 PII 或 skipPiiMask）→ 无需还原
        Object vaultObj = response.context().get(PII_VAULT_CONTEXT_KEY);
        if (!(vaultObj instanceof PiiMaskingService.PiiVault vault) || vault.isEmpty()) {
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
                    String restoredText = maskingService.restore(text, vault);
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
