package com.example.deepresearch.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 高级 RAG 服务 — 查询改写 + QuestionAnswerAdvisor.
 * <p>
 * 在基础 RAG 之上增加 Pre-Retrieval 查询改写步骤：
 * 用轻量 Flash 模型将用户口语化、模糊的查询改写为精确的检索查询，
 * 提升向量检索的召回精度。
 * </p>
 *
 * <h3>对比</h3>
 * <table>
 *   <tr><th>模式</th><th>流程</th></tr>
 *   <tr><td>基础 RAG</td><td>用户查询 → 向量检索 → LLM 生成</td></tr>
 *   <tr><td>高级 RAG</td><td>用户查询 → Flash 改写 → 向量检索 → LLM 生成</td></tr>
 * </table>
 */
@Service
public class AdvancedRagService {

    private static final Logger log = LoggerFactory.getLogger(AdvancedRagService.class);

    /** 用于查询改写的轻量 ChatClient */
    private final ChatClient rewriteClient;
    /** 用于带 RAG 生成回答的 ChatClient */
    private final ChatClient ragClient;

    public AdvancedRagService(
            @Qualifier("intentRouterClient") ChatClient rewriteClient,
            ChatClient.Builder builder,
            VectorStore vectorStore) {
        this.rewriteClient = rewriteClient;
        this.ragClient = builder
            .defaultAdvisors(
                QuestionAnswerAdvisor.builder(vectorStore)
                    .searchRequest(SearchRequest.builder()
                        .topK(5)
                        .similarityThreshold(0.7)
                        .build())
                    .build())
            .build();
    }

    /**
     * 带查询改写的增强检索问答.
     *
     * @param userQuery 用户原始查询（可能口语化、模糊）
     * @return LLM 基于检索文档生成的回答
     */
    public String askWithRewriting(String userQuery) {
        log.debug("[AdvancedRAG] 原始查询: {}", userQuery);

        // Step 1: 查询改写 — 用 Flash 模型优化检索精度
        String rewrittenQuery = rewriteQuery(userQuery);
        log.debug("[AdvancedRAG] 改写后查询: {}", rewrittenQuery);

        // Step 2: RAG 检索 + 生成 — QuestionAnswerAdvisor 自动处理
        return ragClient.prompt()
            .user(rewrittenQuery)
            .call()
            .content();
    }

    /**
     * 使用轻量 Flash 模型改写用户模糊查询为精确检索语句.
     */
    private String rewriteQuery(String original) {
        try {
            String rewritten = rewriteClient.prompt()
                .user("请将用户的口语化问题改写为适合在知识库中检索的精确查询语句。" +
                    "只输出改写后的查询，不要解释。\n\n用户问题: " + original)
                .call()
                .content();

            if (rewritten != null && !rewritten.isBlank()) {
                return rewritten;
            }
        } catch (Exception e) {
            log.warn("[AdvancedRAG] 查询改写失败，使用原始查询: {}", e.getMessage());
        }
        return original;  // 改写失败时回退到原始查询
    }
}
