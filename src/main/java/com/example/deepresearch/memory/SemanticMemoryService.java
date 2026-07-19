package com.example.deepresearch.memory;

import com.example.deepresearch.common.config.DeepResearchProperties;
import com.example.deepresearch.rag.VectorStoreService;
import com.example.deepresearch.security.PiiMaskingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 语义记忆服务 — 基于 Milvus 的历史研究向量检索与索引.
 * <p>
 * 实现三层记忆架构中的<strong>L2 自生长层</strong>：
 * 研究完成后自动将报告向量化写入 Milvus，
 * 研究前检索相似历史研究以增强 Planner 上下文。
 * </p>
 *
 * <h3>与其他记忆层的关系</h3>
 * <ul>
 *   <li><b>L1 短期记忆 (Redis)</b>: 会话级对话窗口</li>
 *   <li><b>L2 语义记忆 (Milvus)</b>: 跨会话研究知识自生长 — 本服务</li>
 *   <li><b>L3 长期记忆 (PostgreSQL)</b>: 用户画像与结构化历史</li>
 * </ul>
 *
 * <h3>数据隔离</h3>
 * 通过 Milvus 的 {@code doc_type} 标量字段区分：
 * <ul>
 *   <li>{@code research_history} — 自动索引的研究报告（本服务写入）</li>
 *   <li>{@code unknown} / 其他 — 用户手动上传的文档（DocumentIngestionService）</li>
 * </ul>
 * 检索时通过 {@code doc_type == "research_history"} 过滤，
 * 确保语义记忆只召回历史研究报告，不与用户上传文档混淆。
 *
 * <h3>优雅降级</h3>
 * 所有 Milvus 操作内置 try-catch：连接失败、向量化异常、超时等
 * 均不阻塞主流程，log.warn 后返回空结果或跳过写入。
 */
@Service
public class SemanticMemoryService {

    private static final Logger log = LoggerFactory.getLogger(SemanticMemoryService.class);

    /** Milvus doc_type 值：区分语义记忆和用户上传文档 */
    private static final String DOC_TYPE_RESEARCH_HISTORY = "research_history";

    /** Milvus 额外过滤表达式：仅检索研究报告 */
    private static final String HISTORY_FILTER = "doc_type == \"" + DOC_TYPE_RESEARCH_HISTORY + "\"";

    /** 语义记忆检索默认 Top-K */
    private static final int DEFAULT_TOP_K = 5;

    /** 语义记忆检索相似度阈值（低于知识库阈值，历史研究即使不太相似也可参考） */
    private static final double DEFAULT_THRESHOLD = 0.55;

    private final VectorStoreService vectorStoreService;
    private final int chunkSize;
    private final int chunkOverlap;
    private final double similarityThreshold;
    private final PiiMaskingService piiMaskingService;

    public SemanticMemoryService(
        VectorStoreService vectorStoreService,
        DeepResearchProperties properties,
        PiiMaskingService piiMaskingService
    ) {
        this.vectorStoreService = vectorStoreService;
        this.chunkSize = properties.rag().chunkSize();
        this.chunkOverlap = properties.rag().chunkOverlap();
        // 使用配置的阈值，但不低于 0.50（语义记忆天然相似度较低）
        this.similarityThreshold = Math.max(properties.rag().similarityThreshold() - 0.15, 0.50);
        this.piiMaskingService = piiMaskingService;
    }

    // =========================== 检索 ===========================

    /**
     * 检索与当前查询相似的历史研究报告.
     * <p>
     * 通过 Milvus 向量相似度检索，过滤 {@code doc_type == "research_history"}，
     * 仅返回该租户下已完成的历史研究报告片段。
     * </p>
     *
     * @param query    当前研究查询
     * @param tenantId 租户 ID（多租户隔离）
     * @return 相似历史研究的格式化文本（供 Planner memoryContext 注入）
     */
    public String searchSimilarHistory(String query, String tenantId) {
        return searchSimilarHistory(query, tenantId, DEFAULT_TOP_K);
    }

    /**
     * 检索与当前查询相似的历史研究报告（指定返回数量）.
     *
     * @param query    当前研究查询
     * @param tenantId 租户 ID
     * @param topK     返回结果数
     * @return 格式化的相似历史研究文本
     */
    public String searchSimilarHistory(String query, String tenantId, int topK) {
        if (query == null || query.isBlank()) {
            return "";
        }

        try {
            List<Document> docs = vectorStoreService.similaritySearch(
                query, tenantId, HISTORY_FILTER, topK, similarityThreshold);

            if (docs.isEmpty()) {
                log.debug("[SemanticMem] 无相似历史研究: query='{}', tenantId={}",
                    piiMaskingService.tokenizeToString(
                        query.substring(0, Math.min(50, query.length()))), tenantId);
                return "";
            }

            // 格式化为 Planner 可理解的上下文
            StringBuilder sb = new StringBuilder();
            sb.append("## 历史相似研究\n");
            sb.append("以下是与当前查询相似的历史研究摘要，可供参考：\n\n");

            for (int i = 0; i < docs.size(); i++) {
                Document doc = docs.get(i);
                String title = doc.getMetadata().getOrDefault("doc_title", "未知标题").toString();
                String originalQuery = doc.getMetadata().getOrDefault("query", "").toString();
                String sessionId = doc.getMetadata().getOrDefault("session_id", "").toString();
                // Milvus 返回的 score 可能是 Float 或 Double，用 Number 接口安全转换
                Object scoreObj = doc.getMetadata().get("score");
                double score = scoreObj instanceof Number n ? n.doubleValue() : 0.0;

                sb.append(String.format("### 相似研究 %d\n", i + 1));
                if (!title.isEmpty() && !"未知标题".equals(title)) {
                    sb.append("- 标题: ").append(title).append("\n");
                }
                if (!originalQuery.isEmpty()) {
                    sb.append("- 原始问题: ").append(originalQuery).append("\n");
                }
                sb.append("- 相似度: ").append(String.format("%.0f%%", score * 100)).append("\n");

                // 截取文档摘要（不超过 300 字符）
                String text = doc.getText();
                if (text != null && !text.isBlank()) {
                    String summary = text.length() > 300 ? text.substring(0, 300) + "..." : text;
                    sb.append("- 摘要: ").append(summary).append("\n");
                }
                sb.append("\n");
            }

            log.info("[SemanticMem] 检索到 {} 条相似历史研究: query='{}'",
                docs.size(),
                piiMaskingService.tokenizeToString(
                    query.substring(0, Math.min(50, query.length()))));
            return sb.toString();

        } catch (Exception e) {
            log.warn("[SemanticMem] 语义检索失败（优雅降级，不阻塞主流程）: {}", e.getMessage());
            return "";
        }
    }

    // =========================== 索引写入 ===========================

    /**
     * 将完成的研报向量化后写入 Milvus 语义记忆库.
     * <p>
     * 对长报告进行分块处理，每块独立向量化并写入，
     * 携带统一的 session_id 和 query 元数据，便于检索时关联。
     * </p>
     *
     * @param sessionId 会话 ID
     * @param tenantId  租户 ID
     * @param query     原始研究查询（作为检索时的关联键）
     * @param report    完整研报 Markdown
     * @return 写入的分块数量（0 表示跳过或失败）
     */
    public int indexResearchReport(String sessionId, String tenantId,
                                    String query, String report) {
        if (report == null || report.isBlank()) {
            log.debug("[SemanticMem] 报告为空，跳过语义索引: sessionId={}", sessionId);
            return 0;
        }
        try {
            // 步骤 0: 索引前清洗 — 剥离对语义检索无价值的内容
            // (1) 「参考资料」章节：纯链接列表，向量化后是检索噪音，还会稀释语义缓存匹配
            // (2) 正文引用链接 [[WEBx]](长URL) → [WEBx]：URL 字符稀释向量语义
            String indexableReport = stripForIndexing(report);

            // 步骤 1: 文本分块
            List<String> chunks = chunkText(indexableReport, chunkSize, chunkOverlap);
            if (chunks.isEmpty()) {
                log.debug("[SemanticMem] 分块为空，跳过语义索引: sessionId={}", sessionId);
                return 0;
            }
            log.info("[SemanticMem] 报告分块完成: sessionId={}, chunks={}, reportLength={} (清洗后={})",
                sessionId, chunks.size(), report.length(), indexableReport.length());

            // 提取报告标题（第一个 # 标题行）
            String reportTitle = extractTitle(report, query);

            // 步骤 2: 包装为 Spring AI Document
            List<Document> documents = new ArrayList<>();
            String now = LocalDateTime.now().toString();

            for (int i = 0; i < chunks.size(); i++) {
                Document doc = new Document(chunks.get(i));
                doc.getMetadata().put("doc_title", reportTitle);
                doc.getMetadata().put("doc_type", DOC_TYPE_RESEARCH_HISTORY);
                doc.getMetadata().put("session_id", sessionId);
                doc.getMetadata().put("query", query);
                doc.getMetadata().put("tenant_id", tenantId);
                doc.getMetadata().put("chunk_index", i);
                doc.getMetadata().put("total_chunks", chunks.size());
                doc.getMetadata().put("created_at", now);
                doc.getMetadata().put("source_url", "");  // 研报无外部 URL
                documents.add(doc);
            }

            // 步骤 3: 批量写入 Milvus（内部逐个向量化）
            log.info("[SemanticMem] 开始向量化并写入 Milvus: sessionId={}, chunks={}", sessionId, documents.size());
            vectorStoreService.insertDocuments(documents, tenantId);

            log.info("[SemanticMem] 研究报告已索引: sessionId={}, chunks={}, query='{}'",
                sessionId, chunks.size(),
                piiMaskingService.tokenizeToString(
                    query.substring(0, Math.min(50, query.length()))));
            return chunks.size();

        } catch (Exception e) {
            log.warn("[SemanticMem] 语义索引写入失败（优雅降级，不阻塞主流程）: sessionId={}, error={}",
                sessionId, e.getMessage());
            return 0;
        }
    }

    // =========================== 私有辅助方法 ===========================

    /** 匹配正文引用链接 [[WEB12]](url) 或 [[LOCAL3]](url)，还原为裸标记 [WEB12] */
    private static final Pattern LINKED_CITATION_PATTERN = Pattern.compile("\\[\\[(WEB\\d+|LOCAL\\d+)]]\\([^)]*\\)");

    /**
     * 索引前清洗报告文本（仅影响 Milvus 向量化文本，PG 中保留完整报告）.
     * <ol>
     *   <li>剥离「## 参考资料」章节 — 纯链接列表对语义检索零价值</li>
     *   <li>正文引用链接 {@code [[WEBx]](url)} 还原为 {@code [WEBx]} — 去除 URL 噪音</li>
     * </ol>
     */
    static String stripForIndexing(String report) {
        String cleaned = report;

        // 剥离参考资料章节（CitationValidator 统一以 "## 参考资料" 追加在文末）
        int refIdx = cleaned.lastIndexOf("\n## 参考资料");
        if (refIdx > 0) {
            cleaned = cleaned.substring(0, refIdx);
            // 顺带去掉章节前的 "---" 分隔线
            cleaned = cleaned.stripTrailing();
            if (cleaned.endsWith("---")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3).stripTrailing();
            }
        }

        // 引用链接还原为纯标记
        cleaned = LINKED_CITATION_PATTERN.matcher(cleaned).replaceAll("[$1]");

        return cleaned;
    }

    /**
     * 提取报告标题.
     * <p>
     * 从 Markdown 报告中提取第一个一级标题；如果没有，
     * 使用查询文本截断后的前 100 字符作为标题。
     * </p>
     */
    private String extractTitle(String report, String query) {
        // 尝试提取第一个 # 标题
        for (String line : report.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
                return trimmed.substring(2).trim();
            }
        }
        // Fallback: 截取 query
        return query.length() > 100 ? query.substring(0, 100) : query;
    }

    /**
     * 简单文本分块（与 DocumentIngestionService 保持一致）.
     * <p>
     * 按 chunkSize 切分，相邻块重叠 overlap 字符。
     * 尽量在段落边界（双换行）或句号处断开。
     * </p>
     *
     * @param text      原始文本
     * @param chunkSize 块大小（字符数）
     * @param overlap   重叠字符数
     * @return 文本块列表
     */
    private List<String> chunkText(String text, int chunkSize, int overlap) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());

            // 尽量在段落边界断开
            if (end < text.length()) {
                int breakPoint = text.lastIndexOf("\n\n", end);
                if (breakPoint > start + chunkSize / 2) {
                    end = breakPoint;
                } else {
                    breakPoint = text.lastIndexOf("。", end);
                    if (breakPoint > start + chunkSize / 2) {
                        end = breakPoint + 1;
                    }
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            // 已到达文本末尾，终止循环（避免 start 回退导致死循环）
            if (end >= text.length()) {
                break;
            }
            start = end - overlap;
        }

        return chunks;
    }
}
