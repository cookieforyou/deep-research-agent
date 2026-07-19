package com.example.deepresearch.cache;

import com.example.deepresearch.common.config.DeepResearchProperties;
import com.example.deepresearch.common.observability.BusinessMetrics;
import com.example.deepresearch.memory.LongTermMemoryService;
import com.example.deepresearch.security.PiiMaskingService;
import com.example.deepresearch.memory.entity.ResearchHistory;
import com.example.deepresearch.rag.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 语义缓存服务 — 基于 Milvus 向量相似度的查询结果缓存.
 * <p>
 * 对高度相似的 Query（相似度 &gt; 可配置阈值），直接返回历史研究报告，
 * 跳过昂贵的多 Agent 全流程（~135 秒 → ~2 秒）。
 * </p>
 *
 * <h3>缓存检索流程</h3>
 * <ol>
 *   <li>将 query 向量化后搜索 Milvus（{@code doc_type == "research_history"}）</li>
 *   <li>若 top-1 结果相似度 &ge; 缓存阈值，视为命中</li>
 *   <li>通过匹配 chunk 的 session_id 从 PostgreSQL 获取完整报告</li>
 *   <li>若未命中或任何环节异常，返回空结果（优雅降级，不阻塞主流程）</li>
 * </ol>
 *
 * <h3>与其他服务的关系</h3>
 * <ul>
 *   <li>{@link VectorStoreService} — 提供 Milvus 向量相似度检索</li>
 *   <li>{@link LongTermMemoryService} — 提供按 sessionId 获取完整报告</li>
 *   <li>{@link DeepResearchProperties.CacheConfig} — 提供阈值和开关配置</li>
 * </ul>
 *
 * @see DeepResearchProperties.CacheConfig
 */
@Service
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);

    /** Milvus doc_type 过滤：仅检索研究报告（与 SemanticMemoryService 保持一致） */
    private static final String HISTORY_FILTER = "doc_type == \"research_history\"";

    /** 缓存检索的 Top-K：只取最匹配的一条 */
    private static final int CACHE_TOP_K = 1;

    private final VectorStoreService vectorStoreService;
    private final LongTermMemoryService longTermMemory;
    private final DeepResearchProperties properties;
    private final PiiMaskingService piiMaskingService;
    private final BusinessMetrics businessMetrics;

    public SemanticCacheService(
        VectorStoreService vectorStoreService,
        LongTermMemoryService longTermMemory,
        DeepResearchProperties properties,
        PiiMaskingService piiMaskingService,
        BusinessMetrics businessMetrics
    ) {
        this.vectorStoreService = vectorStoreService;
        this.longTermMemory = longTermMemory;
        this.properties = properties;
        this.piiMaskingService = piiMaskingService;
        this.businessMetrics = businessMetrics;
    }

    /**
     * 检查语义缓存是否命中.
     * <p>
     * 所有异常均在内部捕获，失败时返回空结果（优雅降级）。
     * </p>
     *
     * @param query    用户研究查询
     * @param tenantId 租户 ID（多租户隔离）
     * @return 缓存结果（命中时包含完整报告）
     */
    public CacheResult checkCache(String query, String tenantId) {
        // 快速路径：缓存未启用
        if (!properties.cache().enabled()) {
            log.debug("[SemanticCache] 缓存已禁用，跳过检查");
            return CacheResult.empty();
        }

        if (query == null || query.isBlank()) {
            return CacheResult.empty();
        }

        double threshold = properties.cache().similarityThreshold();
        log.debug("[SemanticCache] 开始缓存检查: query='{}', tenantId={}, threshold={}",
            piiMaskingService.tokenizeToString(
                query.substring(0, Math.min(50, query.length()))), tenantId, threshold);

        try {
            // 步骤 1: Milvus 向量相似度检索（只取 top-1）
            List<Document> docs = vectorStoreService.similaritySearch(
                query, tenantId, HISTORY_FILTER, CACHE_TOP_K, threshold);

            if (docs.isEmpty()) {
                log.debug("[SemanticCache] 缓存未命中: 无相似度 >= {} 的历史报告", threshold);
                businessMetrics.recordCacheAccess(false);
                return CacheResult.empty();
            }

            // 步骤 2: 提取匹配 chunk 的元数据
            Document topDoc = docs.get(0);
            Object scoreObj = topDoc.getMetadata().get("score");
            double score = scoreObj instanceof Number n ? n.doubleValue() : 0.0;

            String sessionId = topDoc.getMetadata().getOrDefault("session_id", "").toString();
            String matchedQuery = topDoc.getMetadata().getOrDefault("query", "").toString();

            if (sessionId.isEmpty()) {
                log.warn("[SemanticCache] 匹配 chunk 缺少 session_id，跳过缓存");
                return CacheResult.empty();
            }

            // 步骤 3: 从 PostgreSQL 获取完整报告
            Optional<ResearchHistory> historyOpt =
                longTermMemory.getResearchBySessionId(sessionId);

            if (historyOpt.isEmpty()) {
                log.warn("[SemanticCache] 未找到 sessionId={} 的研究历史记录", sessionId);
                return CacheResult.empty();
            }

            ResearchHistory history = historyOpt.get();
            String report = history.getReport();
            int wordCount = history.getWordCount();
            matchedQuery = history.getQuery(); // PG 中记录的原始查询（更准确）
            String sourceIndex = history.getSourceIndex();
            String findings = history.getFindings();
            int citationCount = history.getCitationCount();

            if (report == null || report.isBlank()) {
                log.warn("[SemanticCache] 历史报告为空: sessionId={}", sessionId);
                return CacheResult.empty();
            }

            log.info("[SemanticCache] ✅ 缓存命中! query='{}', matchedQuery='{}', score={}, sessionId={}",
                piiMaskingService.tokenizeToString(
                    query.substring(0, Math.min(50, query.length()))),
                piiMaskingService.tokenizeToString(
                    matchedQuery.substring(0, Math.min(50, matchedQuery.length()))),
                String.format("%.2f", score), sessionId);

            businessMetrics.recordCacheAccess(true);

            return new CacheResult(true, report, score, matchedQuery,
                sessionId, wordCount, sourceIndex, findings, citationCount);

        } catch (Exception e) {
            log.warn("[SemanticCache] 缓存检查异常（优雅降级，走正常研究流程）: {}",
                e.getMessage());
            return CacheResult.empty();
        }
    }

    // =========================== 缓存结果 ===========================

    /**
     * 缓存检查结果.
     *
     * @param hit              是否命中缓存
     * @param report           完整研究报告 Markdown（命中时非空）
     * @param score            相似度得分（0.0 ~ 1.0，COSINE）
     * @param matchedQuery     匹配到的历史查询
     * @param matchedSessionId 匹配到的历史会话 ID
     * @param wordCount        报告字数
     * @param sourceIndex      证据索引 JSON（供前端引用溯源）
     * @param findings         研究结论 JSON（供前端"关键发现"渲染）
     * @param citationCount    引用来源数
     */
    public record CacheResult(
        boolean hit,
        String report,
        double score,
        String matchedQuery,
        String matchedSessionId,
        int wordCount,
        String sourceIndex,
        String findings,
        int citationCount
    ) {
        /** 空结果（缓存未命中或异常） */
        public static CacheResult empty() {
            return new CacheResult(false, null, 0.0, null, null, 0, null, null, 0);
        }
    }
}
