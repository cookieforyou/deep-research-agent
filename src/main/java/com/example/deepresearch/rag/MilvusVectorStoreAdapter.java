package com.example.deepresearch.rag;

import com.example.deepresearch.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Milvus VectorStore 适配器 — 将现有 VectorStoreService 包装为 Spring AI 标准接口.
 * <p>
 * 不修改 VectorStoreService 的任何代码，纯适配器模式。
 * QuestionAnswerAdvisor 依赖此接口实现自动 RAG 检索。
 * 多租户隔离通过 {@link TenantContext#getCurrentTenant()} 自动获取。
 * </p>
 */
@Component
public class MilvusVectorStoreAdapter implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(MilvusVectorStoreAdapter.class);

    private final VectorStoreService vectorStoreService;

    public MilvusVectorStoreAdapter(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    @Override
    public void add(List<Document> documents) {
        String tenantId = TenantContext.getCurrentTenant();
        vectorStoreService.insertDocuments(documents, tenantId != null ? tenantId : "default");
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        String tenantId = TenantContext.getCurrentTenant();
        String filterExpr = request.getFilterExpression() != null
            ? request.getFilterExpression().toString() : null;
        return vectorStoreService.similaritySearch(
            request.getQuery(),
            tenantId != null ? tenantId : "default",
            filterExpr,
            request.getTopK(),
            request.getSimilarityThreshold());
    }

    @Override
    public void delete(List<String> idList) {
        String tenantId = TenantContext.getCurrentTenant();
        vectorStoreService.deleteDocuments(idList, tenantId != null ? tenantId : "default");
    }

    @Override
    public void delete(org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression) {
        // 暂不支持按 FilterExpression 删除，后续可扩展
        log.debug("[MilvusVectorStore] delete by FilterExpression not implemented");
    }

    /**
     * 按文档 ID 获取单个文档（VectorStore 默认方法，Milvus 暂不支持）.
     */
    public Optional<Document> get(String id) {
        // Milvus VectorStoreService 不支持按 ID 单独获取，返回空
        return Optional.empty();
    }
}
