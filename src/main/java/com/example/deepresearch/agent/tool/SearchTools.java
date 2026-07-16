package com.example.deepresearch.agent.tool;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索工具集 — 使用 @Tool 注解暴露给 ToolCallingAdvisor.
 * <p>
 * 封装 Web 搜索和本地知识库检索为 Spring AI 2.0 标准工具。
 * LLM 通过 ToolCallingAdvisor 自主决定何时调用、以什么参数调用，
 * 无需 Agent 手动编排搜索流程。
 * </p>
 *
 * <h3>工具层证据收集（根治 LLM 复述 JSON 脆弱性）</h3>
 * <p>
 * 搜索结果本身是结构化数据，无需 LLM 在输出中逐字复述 title/url/content
 * （复述会引入未转义引号、输出截断、token 浪费三类问题）。本类在 @Tool
 * 执行时通过线程级 {@link EvidenceCollector} 直接收集原始结果并分配
 * {@code sourceId}，返回给 LLM 的结果附带 sourceId；LLM 只需输出选中的
 * sourceId + 评分，Evidence 由 Scout 的 Java 代码组装。
 * </p>
 * <p>
 * 收集器使用 ThreadLocal：Spring AI 工具回调在发起 {@code .call()} 的
 * 同一（虚拟）线程上同步执行，Scout 在调用前 {@link #beginCollection}、
 * 调用后 {@link #endCollection} 取回，天然按 Scout 隔离。
 * </p>
 */
@Component
public class SearchTools {

    private static final Logger log = LoggerFactory.getLogger(SearchTools.class);

    private final SearchTool searchTool;
    private final VectorStoreService vectorStoreService;

    /** 线程级证据收集器（每个 Scout 的工具循环独占一个，同时承载本轮租户 ID） */
    private final ThreadLocal<EvidenceCollector> collector = new ThreadLocal<>();

    public SearchTools(SearchTool searchTool, VectorStoreService vectorStoreService) {
        this.searchTool = searchTool;
        this.vectorStoreService = vectorStoreService;
    }

    // =========================== 证据收集器生命周期 ===========================

    /**
     * 开始收集本轮工具调用的原始结果（Scout 在 LLM 调用前调用）.
     *
     * @param sourceIdPrefix sourceId 前缀（WEB / LOCAL）
     */
    public void beginCollection(String sourceIdPrefix) {
        beginCollection(sourceIdPrefix, null);
    }

    /**
     * 开始收集并绑定本轮租户 ID（LocalScout 使用）.
     * <p>
     * 租户 ID 随收集器存于 ThreadLocal，@Tool 回调在发起 {@code .call()}
     * 的同一线程上同步执行，天然按 Scout/会话隔离。
     * <strong>禁止改回单例字段暂存</strong>：曾用 {@code volatile String storedTenantId}
     * 实现，并发会话会互相覆盖导致跨租户检索泄漏（2026-07-16 修复）。
     * </p>
     *
     * @param sourceIdPrefix sourceId 前缀（WEB / LOCAL）
     * @param tenantId       本轮检索的租户 ID（webSearch 场景可为 null）
     */
    public void beginCollection(String sourceIdPrefix, String tenantId) {
        collector.set(new EvidenceCollector(sourceIdPrefix, tenantId));
    }

    /**
     * 结束收集并取回全部原始结果（Scout 在 LLM 调用后调用，含异常路径）.
     *
     * @return sourceId → 原始结果（按收集顺序），未开始收集时为空 Map
     */
    public Map<String, CollectedSource> endCollection() {
        EvidenceCollector c = collector.get();
        collector.remove();
        return c != null ? c.snapshot() : Map.of();
    }

    // =========================== @Tool 定义 ===========================

    @Tool(description = """
        搜索互联网获取最新公开信息。
        每条结果带有唯一 sourceId，可在最终输出中通过 sourceId 引用该结果。
        返回结果包含：sourceId、标题、URL、摘要、发布时间、来源域名。
        适用场景：查询最新新闻、市场数据、行业趋势等公开信息。""")
    public List<WebSearchResult> webSearch(
            @ToolParam(description = "搜索关键词，建议使用精确的关键词组合") String query,
            @ToolParam(description = "返回结果数量，默认10，最大15") int count) {
        log.debug("[SearchTools] webSearch: query='{}', count={}", query, count);
        EvidenceCollector c = collector.get();
        return searchTool.search(query, Math.min(count, 15))
            .stream().map(r -> {
                String snippet = r.snippet() != null ? r.snippet() : "";
                String domain = r.domain() != null ? r.domain() : "unknown";
                // 按 URL 去重注册：同一来源被多次搜索命中时复用同一 sourceId
                String sourceId = c != null
                    ? c.register(r.url(), r.title(), r.url(), snippet, domain, 0.0)
                    : "";
                return new WebSearchResult(
                    sourceId, r.title(), r.url(), snippet,
                    r.publishTime() != null
                        ? r.publishTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : "",
                    domain);
            }).toList();
    }

    @Tool(description = """
        从企业内部知识库检索相关文档。
        每条结果带有唯一 sourceId，可在最终输出中通过 sourceId 引用该结果。
        返回结果包含：sourceId、文档内容片段、来源文件、相似度分数。
        适用场景：查询公司政策、产品文档、历史研究报告等内部资料。""")
    public List<DocSearchResult> localSearch(
            @ToolParam(description = "检索查询语句，建议使用专业术语") String query) {
        EvidenceCollector c = collector.get();
        // 租户 ID 解析：收集器绑定值（LocalScout 在 beginCollection 时传入，线程隔离）
        // → fallback TenantContext ThreadLocal → default
        String tenantId = c != null && c.tenantId() != null
            ? c.tenantId()
            : TenantContext.getCurrentTenant();
        if (tenantId == null) tenantId = "default";
        log.debug("[SearchTools] localSearch: query='{}', tenantId={}", query, tenantId);
        return vectorStoreService.similaritySearch(query, tenantId, 4, 0.7)
            .stream().map(d -> {
                String content = d.getText();
                String source = d.getMetadata().getOrDefault("source_url",
                    d.getMetadata().getOrDefault("doc_title", "unknown")).toString();
                double score = ((Number) d.getMetadata()
                    .getOrDefault("score", 0.0)).doubleValue();
                // 按 来源+内容前缀 去重注册（同一文档块可能被多个查询命中）
                String dedupKey = source + "|" + content.substring(0, Math.min(64, content.length()));
                String sourceId = c != null
                    ? c.register(dedupKey, source, source, content, "internal", score)
                    : "";
                return new DocSearchResult(sourceId, content, source, score);
            }).toList();
    }

    // =========================== 工具返回类型 ===========================

    /** Web 搜索结果（供 LLM 理解结果结构，sourceId 用于最终输出引用） */
    public record WebSearchResult(
        String sourceId,
        String title,
        String url,
        String snippet,
        String publishTime,
        String domain
    ) {}

    /** 本地知识库检索结果（供 LLM 理解结果结构，sourceId 用于最终输出引用） */
    public record DocSearchResult(
        String sourceId,
        String content,
        String source,
        double score
    ) {}

    // =========================== 收集器 ===========================

    /**
     * 收集到的原始来源（@Tool 执行时登记，Scout 组装 Evidence 时消费）.
     *
     * @param sourceId  分配的来源 ID（WEB1 / LOCAL2 ...）
     * @param title     标题（LOCAL 类型为文档标识）
     * @param url       URL（LOCAL 类型为文档标识）
     * @param content   原始摘要/文档片段（忠于原文，不经 LLM 复述）
     * @param domain    来源域名（LOCAL 固定 internal）
     * @param baseScore 检索器给出的基础分（Web 无 → 0.0；Local 为相似度分）
     */
    public record CollectedSource(
        String sourceId,
        String title,
        String url,
        String content,
        String domain,
        double baseScore
    ) {}

    /**
     * 单线程证据收集器 — 分配自增 sourceId 并按 dedupKey 去重，同时承载本轮租户 ID.
     * <p>
     * 仅在发起工具循环的线程上访问（Spring AI 工具回调同步执行于调用线程），
     * 无需加锁。
     * </p>
     */
    static class EvidenceCollector {
        private final String prefix;
        private final String tenantId;
        private final Map<String, String> keyToId = new LinkedHashMap<>();
        private final Map<String, CollectedSource> sources = new LinkedHashMap<>();
        private int seq = 0;

        EvidenceCollector(String prefix) {
            this(prefix, null);
        }

        EvidenceCollector(String prefix, String tenantId) {
            this.prefix = prefix;
            this.tenantId = tenantId;
        }

        String tenantId() {
            return tenantId;
        }

        String register(String dedupKey, String title, String url,
                        String content, String domain, double baseScore) {
            String existing = keyToId.get(dedupKey);
            if (existing != null) {
                return existing;
            }
            String id = prefix + (++seq);
            keyToId.put(dedupKey, id);
            sources.put(id, new CollectedSource(id, title, url, content, domain, baseScore));
            return id;
        }

        Map<String, CollectedSource> snapshot() {
            return new LinkedHashMap<>(sources);
        }
    }
}
