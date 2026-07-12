package com.example.deepresearch.agent.bundle;

import com.example.deepresearch.common.observability.TokenTrackingAdvisor;
import com.example.deepresearch.security.AuditLogAdvisor;
import com.example.deepresearch.security.OutputGuardrailAdvisor;
import com.example.deepresearch.security.PiiMaskingAdvisor;
import com.example.deepresearch.security.TokenBudgetAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 企业级 ChatClient 统一配置 — 基于 Spring AI 2.0 Advisor 链的声明式装配.
 * <p>
 * 与 AgentBundle 并行存在，不修改现有 Agent 的 ChatClient Bean。
 * 组装完整的 Advisor 链：Token预算 → 输入脱敏 → 输出护栏 → 对话记忆 → RAG检索 → Token追踪 → 审计日志。
 * 新开发的 Agent 或重构后的 Agent 可从此配置获取 ChatClient。
 * </p>
 *
 * <h3>Advisor 执行序</h3>
 * <pre>
 * 请求流入
 *   ├─ TokenBudgetAdvisor        [200] Token预算检查 + 限流
 *   ├─ PiiMaskingAdvisor         [300] 输入PII脱敏（HIGHEST_PRECEDENCE）
 *   ├─ OutputGuardrailAdvisor    [300] 输出安全护栏
 *   ├─ MessageChatMemoryAdvisor  [400] 对话记忆注入
 *   ├─ QuestionAnswerAdvisor     [500] RAG检索增强
 *   ├─ TokenTrackingAdvisor      [900] Token追踪
 *   └─ AuditLogAdvisor           [100] 审计日志
 * 响应返回
 * </pre>
 */
@Configuration
public class EnterpriseChatClientConfig {

    @Bean("enterpriseChatClient")
    public ChatClient enterpriseChatClient(
            ChatModel chatModel,
            PiiMaskingAdvisor piiMask,
            TokenTrackingAdvisor tokenTrack,
            TokenBudgetAdvisor tokenBudget,
            OutputGuardrailAdvisor outputGuard,
            AuditLogAdvisor auditLog,
            ChatMemory chatMemory,
            VectorStore vectorStore) {

        return ChatClient.builder(chatModel)
            .defaultSystem("你是企业智能研究助手，请用中文回答。")
            .defaultAdvisors(
                tokenBudget,                                                      // [200] Token预算检查
                piiMask,                                                          // [300] 输入PII脱敏
                outputGuard,                                                      // [300] 输出安全护栏
                MessageChatMemoryAdvisor.builder(chatMemory).build(),             // [400] 对话记忆
                QuestionAnswerAdvisor.builder(vectorStore)
                    .searchRequest(SearchRequest.builder()
                        .topK(5)
                        .similarityThreshold(0.7)
                        .build())
                    .build(),                                                     // [500] RAG检索
                tokenTrack,                                                       // [900] Token追踪
                auditLog                                                          // [100] 审计日志
            )
            .build();
    }
}
