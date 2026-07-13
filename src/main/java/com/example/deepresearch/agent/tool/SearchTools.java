package com.example.deepresearch.agent.tool;

import com.example.deepresearch.common.model.SearchResult;
import com.example.deepresearch.rag.VectorStoreService;
import com.example.deepresearch.security.TenantContext;
import com.example.deepresearch.tool.search.SearchTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 搜索工具集 — 使用 @Tool 注解暴露给 ToolCallingAdvisor.
 * <p>
 * 封装 Web 搜索和本地知识库检索为 Spring AI 2.0 标准工具。
 * LLM 通过 ToolCallingAdvisor 自主决定何时调用、以什么参数调用，
 * 无需 Agent 手动编排搜索流程。
 * </p>
 */
@Component
public class SearchTools {

    private static final Logger log = LoggerFactory.getLogger(SearchTools.class);

    private final SearchTool searchTool;
    private final VectorStoreService vectorStoreService;

    /**
     * 租户 ID 暂存 — 由 Agent 在 LLM 调用前设置，解决 @Tool 执行时
     * ThreadLocal TenantContext 在虚拟线程边界丢失的问题.
     */
    private volatile String storedTenantId;

    public SearchTools(SearchTool searchTool, VectorStoreService vectorStoreService) {
        this.searchTool = searchTool;
        this.vectorStoreService = vectorStoreService;
    }

    /** Agent 在 LLM 调用前设置当前租户 ID */
    public void setTenantId(String tenantId) {
        this.storedTenantId = tenantId;
    }

    @Tool(description = """
        搜索互联网获取最新公开信息。
        返回结果包含：标题、URL、摘要、发布时间、来源域名。
        适用场景：查询最新新闻、市场数据、行业趋势等公开信息。""")
    public List<WebSearchResult> webSearch(
            @ToolParam(description = "搜索关键词，建议使用精确的关键词组合") String query,
            @ToolParam(description = "返回结果数量，默认10，最大15") int count) {
        log.debug("[SearchTools] webSearch: query='{}', count={}", query, count);
        return searchTool.search(query, Math.min(count, 15))
            .stream().map(WebSearchResult::from).toList();
    }

    @Tool(description = """
        从企业内部知识库检索相关文档。
        返回结果包含：文档内容片段、来源文件、相似度分数。
        适用场景：查询公司政策、产品文档、历史研究报告等内部资料。""")
    public List<DocSearchResult> localSearch(
            @ToolParam(description = "检索查询语句，建议使用专业术语") String query) {
        // 优先使用 Agent 设置的 storedTenantId，fallback 到 TenantContext ThreadLocal
        String tenantId = storedTenantId != null ? storedTenantId
            : TenantContext.getCurrentTenant();
        if (tenantId == null) tenantId = "default";
        log.debug("[SearchTools] localSearch: query='{}', tenantId={}", query, tenantId);
        return vectorStoreService.similaritySearch(query, tenantId, 4, 0.7)
            .stream().map(DocSearchResult::from).toList();
    }

    // =========================== 工具返回类型 ===========================

    /** Web 搜索结果（供 LLM 理解结果结构） */
    public record WebSearchResult(
        String title,
        String url,
        String snippet,
        String publishTime,
        String domain
    ) {
        static WebSearchResult from(SearchResult r) {
            return new WebSearchResult(
                r.title(),
                r.url(),
                r.snippet(),
                r.publishTime() != null
                    ? r.publishTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : "",
                r.domain() != null ? r.domain() : "unknown"
            );
        }
    }

    /** 本地知识库检索结果（供 LLM 理解结果结构） */
    public record DocSearchResult(
        String content,
        String source,
        double score
    ) {
        static DocSearchResult from(Document d) {
            return new DocSearchResult(
                d.getText(),
                d.getMetadata().getOrDefault("source_url",
                    d.getMetadata().getOrDefault("doc_title", "unknown")).toString(),
                ((Number) d.getMetadata().getOrDefault("score", 0.0)).doubleValue()
            );
        }
    }
}
