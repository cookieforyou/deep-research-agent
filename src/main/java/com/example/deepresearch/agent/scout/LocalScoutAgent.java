package com.example.deepresearch.agent.scout;

import com.example.deepresearch.common.model.Evidence;
import com.example.deepresearch.common.model.Evidence.SourceType;
import com.example.deepresearch.common.util.JsonParseUtils;
import com.example.deepresearch.common.util.PromptSplitUtils;
import com.example.deepresearch.common.util.PromptSplitUtils.PromptParts;
import com.example.deepresearch.rag.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地知识库侦察 Agent — 从 Milvus 向量库检索企业内部证据（并行版）.
 * <p>
 * 使用 deepseek-v4-flash (T=0.4)，与 WebScout 对称但面向内部知识库。
 * 检索时强制携带 tenant_id 过滤，实现多租户数据隔离。
 * 所有查询通过虚拟线程并行执行，墙钟时间 ≈ 最慢的单次查询。
 * </p>
 *
 * <h3>与 WebScout 的区别</h3>
 * <ul>
 *   <li><b>数据源</b>: Milvus 向量库（内部文档）而非公开搜索引擎</li>
 *   <li><b>认证</b>: 租户级隔离（tenantId FilterExpression）</li>
 *   <li><b>Source ID</b>: 前缀为 LOCAL 而非 WEB（如 LOCAL01_1-1）</li>
 *   <li><b>评分</b>: 基础分 0.92（内部知识库信任度最高）</li>
 * </ul>
 */
@Service
public class LocalScoutAgent {

    private static final Logger log = LoggerFactory.getLogger(LocalScoutAgent.class);

    private final ChatClient chatClient;
    private final JsonParseUtils jsonUtils;
    private final VectorStoreService vectorStoreService;
    private final ExecutorService virtualThreadExecutor;
    private final String systemPrompt;
    private final String userPromptTemplate;

    public LocalScoutAgent(
        @Qualifier("localScoutClient") ChatClient chatClient,
        JsonParseUtils jsonUtils,
        VectorStoreService vectorStoreService,
        ExecutorService virtualThreadExecutor,
        ResourceLoader resourceLoader
    ) {
        this.chatClient = chatClient;
        this.jsonUtils = jsonUtils;
        this.vectorStoreService = vectorStoreService;
        this.virtualThreadExecutor = virtualThreadExecutor;
        String fullTemplate = loadPrompt(resourceLoader);
        PromptParts parts = PromptSplitUtils.split(fullTemplate);
        this.systemPrompt = parts.system();
        this.userPromptTemplate = parts.user();
    }

    /**
     * 执行本地知识库检索取证（并行版）.
     * <p>
     * 所有搜索查询通过虚拟线程并行执行，
     * Milvus 向量检索（~300ms）和 LLM 提取（~12s）均在各自虚拟线程中运行。
     * </p>
     *
     * @param query             原始研究查询
     * @param searchPlanQueries 搜索查询词列表
     * @param tenantId          租户 ID（多租户硬隔离）
     * @return 结构化的本地知识库证据列表
     */
    public List<Evidence> search(String query, List<String> searchPlanQueries, String tenantId) {
        if (searchPlanQueries == null || searchPlanQueries.isEmpty()) {
            log.warn("[LocalScout] 无搜索查询词，跳过本地检索");
            return List.of();
        }

        log.info("[LocalScout] 开始并行本地检索: {} 个查询词, tenantId={}",
            searchPlanQueries.size(), tenantId);

        // 并行提交所有检索任务
        AtomicInteger localIndex = new AtomicInteger(0);
        List<CompletableFuture<List<Evidence>>> futures = new ArrayList<>();

        for (String searchQuery : searchPlanQueries) {
            int index = localIndex.incrementAndGet();
            CompletableFuture<List<Evidence>> future = CompletableFuture.supplyAsync(() -> {
                log.debug("[LocalScout] 检索 LOCAL{}: '{}'", index, searchQuery);

                try {
                    // 步骤 1: Milvus 向量相似度检索（带 tenant_id 过滤）
                    List<Document> docs = vectorStoreService.similaritySearch(
                        searchQuery, tenantId, 4, 0.7);

                    if (docs.isEmpty()) {
                        log.debug("[LocalScout] LOCAL{} 无匹配文档", index);
                        return List.of();
                    }

                    // 步骤 2: LLM 结构化提取证据
                    List<Evidence> processed = processDocuments(
                        query, searchQuery, docs, index);

                    log.debug("[LocalScout] LOCAL{} 提取 {} 条证据", index,
                        processed.size());
                    return processed;

                } catch (Exception e) {
                    log.error("[LocalScout] LOCAL{} 检索失败: '{}'", index, searchQuery, e);
                    return List.of();
                }
            }, virtualThreadExecutor);
            futures.add(future);
        }

        // 等待所有并行任务完成
        List<Evidence> allEvidence = futures.stream()
            .map(CompletableFuture::join)
            .flatMap(List::stream)
            .toList();

        log.info("[LocalScout] 并行本地检索完成: 共 {} 条证据", allEvidence.size());
        return allEvidence;
    }

    /**
     * 将 Milvus 检索结果通过 LLM 处理为结构化 Evidence.
     */
    private List<Evidence> processDocuments(
        String originalQuery, String searchQuery,
        List<Document> documents, int localIndex
    ) {
        try {
            // 构建文档文本（供 LLM 分析）
            StringBuilder docsText = new StringBuilder();
            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                String docId = doc.getId() != null ? doc.getId() : "doc_" + (i + 1);
                docsText.append(String.format("[%s] %s\n\n", docId, doc.getText()));
            }

            // 构建 user prompt（仅包含查询数据）
            String userPrompt = userPromptTemplate
                .replace("{{query}}", originalQuery)
                .replace("{{searchQuery}}", searchQuery)
                .replace("{{documents}}", docsText.toString())
                .replace("{{localIndex}}", String.format("LOCAL%02d", localIndex));

            // 调用 LLM 过滤和结构化（system/user 分离），
            // .entity() 自动 JSON 解析 + 类型映射 + 自校正
            EvidenceListWrapper wrapper = chatClient.prompt()
                .advisors(a -> a.param("agent", "LocalScout").param("tier", "flash").param("skipPiiMask", true))
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(EvidenceListWrapper.class);
            log.debug("[LocalScout] LLM 解析完成: {} 条证据", wrapper.evidences().size());

            // 补充 sourceType 和检索时间
            return wrapper.evidences().stream()
                .map(e -> new Evidence(
                    e.sourceId(), SourceType.LOCAL, e.url(), e.title(), e.content(),
                    0.92,  // 本地知识库基础评分最高
                    e.relevanceRank(), e.domain(), LocalDateTime.now()))
                .toList();

        } catch (Exception e) {
            log.error("[LocalScout] 处理文档异常", e);
            return List.of();
        }
    }

    public record EvidenceListWrapper(List<Evidence> evidences) {}

    private String loadPrompt(ResourceLoader loader) {
        try {
            Resource resource = loader.getResource("classpath:prompts/local-scout.st");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[LocalScout] 无法加载 prompt 模板", e);
            return """
                你是内部知识库取证专家。从以下文档中提取与研究问题相关的证据。

                研究问题: {{query}}
                搜索词: {{searchQuery}}
                相关文档:
                {{documents}}

                返回 JSON: {"evidences": [{"sourceId":"{{localIndex}}_1","url":"...","title":"...","content":"...","score":0.92,"relevanceRank":1,"domain":"internal"}]}
                """;
        }
    }
}
