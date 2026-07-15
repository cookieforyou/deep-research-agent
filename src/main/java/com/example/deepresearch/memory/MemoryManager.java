package com.example.deepresearch.memory;

import com.example.deepresearch.memory.entity.ResearchHistory;
import com.example.deepresearch.memory.entity.UserProfile;
import com.example.deepresearch.security.PiiMaskingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

/**
 * 记忆管理器 — 统一的记忆系统入口.
 * <p>
 * 聚合短期记忆（Redis 会话上下文）、
 * 长期记忆（PostgreSQL 用户画像）、
 * 语义记忆（Milvus 向量相似历史研究），
 * 为 Planner Agent 提供完整的上下文注入。
 * </p>
 *
 * <h3>三层记忆架构</h3>
 * <table>
 *   <tr><th>层级</th><th>存储</th><th>范围</th><th>用途</th></tr>
 *   <tr><td>短期</td><td>Redis</td><td>单会话</td><td>对话上下文窗口</td></tr>
 *   <tr><td>语义</td><td>Milvus</td><td>跨会话/租户</td><td>历史研究报告相似检索</td></tr>
 *   <tr><td>长期</td><td>PostgreSQL</td><td>跨会话/用户</td><td>用户画像、偏好、历史主题</td></tr>
 * </table>
 */
@Service
public class MemoryManager {

    private static final Logger log = LoggerFactory.getLogger(MemoryManager.class);

    private final ShortTermMemoryService shortTermMemory;
    private final LongTermMemoryService longTermMemory;
    private final SemanticMemoryService semanticMemory;
    private final PiiMaskingService piiMaskingService;

    public MemoryManager(
        ShortTermMemoryService shortTermMemory,
        LongTermMemoryService longTermMemory,
        SemanticMemoryService semanticMemory,
        PiiMaskingService piiMaskingService
    ) {
        this.shortTermMemory = shortTermMemory;
        this.longTermMemory = longTermMemory;
        this.semanticMemory = semanticMemory;
        this.piiMaskingService = piiMaskingService;
    }

    /**
     * 构建完整的记忆上下文（供 Planner Agent Prompt 注入）.
     * <p>
     * 包含：
     * <ol>
     *   <li>用户画像（长期记忆）</li>
     *   <li>历史相似研究报告（语义记忆）</li>
     *   <li>当前会话对话历史（短期记忆）</li>
     * </ol>
     * </p>
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @param tenantId  租户 ID
     * @param query     当前研究查询（用于语义相似度检索）
     * @return 记忆上下文字符串
     */
    public Mono<String> buildMemoryContext(String sessionId, String userId,
                                            String tenantId, String query) {
        return Mono.zip(
            shortTermMemory.getContextForPrompt(sessionId),
            Mono.fromCallable(() -> longTermMemory.getUserProfileContext(userId, tenantId)),
            Mono.fromCallable(() -> semanticMemory.searchSimilarHistory(query, tenantId))
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(e -> {
                    log.warn("[Memory] 语义记忆检索超时/失败（优雅降级）: {}", e.getMessage());
                    return Mono.just("");
                })
        ).map(tuple -> {
            String shortContext = tuple.getT1();
            String longContext = tuple.getT2();
            String semanticContext = tuple.getT3();

            StringBuilder sb = new StringBuilder();
            if (!longContext.isEmpty()) {
                sb.append("## 用户画像\n").append(longContext).append("\n\n");
            }
            if (!semanticContext.isEmpty()) {
                sb.append(semanticContext).append("\n\n");
            }
            if (!shortContext.isEmpty()) {
                sb.append("## 近期对话\n").append(shortContext);
            }
            return sb.toString();
        });
    }

    /**
     * 研究完成后更新用户长期记忆.
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @param topic    研究主题
     */
    public void recordResearchCompletion(String userId, String tenantId, String topic) {
        log.info("[Memory] 记录研究完成: userId={}, topic='{}'",
            userId, piiMaskingService.tokenizeToString(topic));
        longTermMemory.updateUserInterests(userId, topic, tenantId);
    }

    /**
     * 持久化完整研究历史记录（L2 自生长层: PG + Milvus）.
     *
     * @param sessionId      会话 ID
     * @param userId         用户 ID
     * @param tenantId       租户 ID
     * @param query          原始研究查询
     * @param report         最终报告 Markdown
     * @param wordCount      报告字数
     * @param citationCount  引用来源数
     * @param iterationCount 迭代轮数
     * @param status         完成状态 (COMPLETED / ERROR)
     */
    public void recordResearchHistory(String sessionId, String userId, String tenantId,
                                       String query, String report, int wordCount,
                                       int citationCount, int iterationCount, String status,
                                       String sourceIndex, String findings) {
        log.info("[Memory] 持久化研究历史: sessionId={}, words={}, citations={}, status={}",
            sessionId, wordCount, citationCount, status);
        longTermMemory.recordResearch(sessionId, userId, tenantId,
            query, report, wordCount, citationCount, iterationCount, status,
            sourceIndex, findings);
    }

    /**
     * 将研究报告向量化写入 Milvus 语义记忆库（L2 自生长层）.
     * <p>
     * 此方法为 fire-and-forget 模式：内部 catch 所有异常，
     * 失败时仅 log.warn，不抛出异常影响主流程。
     * </p>
     *
     * @param sessionId 会话 ID
     * @param tenantId  租户 ID
     * @param query     原始研究查询
     * @param report    最终报告 Markdown
     */
    public void indexResearchToSemanticMemory(String sessionId, String tenantId,
                                               String query, String report) {
        log.info("[Memory] 开始语义记忆索引: sessionId={}, reportLength={}",
            sessionId, report != null ? report.length() : 0);
        try {
            int chunks = semanticMemory.indexResearchReport(sessionId, tenantId, query, report);
            if (chunks > 0) {
                log.info("[Memory] 语义记忆索引完成: sessionId={}, chunks={}", sessionId, chunks);
            } else {
                log.debug("[Memory] 语义记忆索引跳过: sessionId={} (报告为空或分块失败)", sessionId);
            }
        } catch (Exception e) {
            log.warn("[Memory] 语义记忆索引失败（不影响主流程）: sessionId={}, error={}",
                sessionId, e.getMessage());
        }
    }

    /**
     * 更新研究历史的评估分数（代理到 LongTermMemoryService）.
     *
     * @param sessionId      会话 ID
     * @param evalScoresJson 评估结果 JSON 字符串
     */
    public void updateEvalScores(String sessionId, String evalScoresJson) {
        longTermMemory.updateEvalScores(sessionId, evalScoresJson);
    }

    public Optional<ResearchHistory> getResearchBySessionId(String sessionId) {
        return longTermMemory.getResearchBySessionId(sessionId);
    }

    public void deleteResearchHistory(String sessionId) {
        longTermMemory.deleteResearchHistory(sessionId);
    }

    public Optional<UserProfile> getUserProfile(String userId, String tenantId) {
        return longTermMemory.getUserProfile(userId, tenantId);
    }

    /**
     * 将用户消息写入短期记忆（fire-and-forget）.
     */
    public void addUserMessage(String sessionId, String content) {
        shortTermMemory.addMessage(sessionId, "user", content)
            .doOnError(e -> log.warn("[Memory] 短期记忆写入失败: {}", e.getMessage()))
            .subscribe();
    }

    /**
     * 将助手回复摘要写入短期记忆（fire-and-forget）.
     */
    public void addAssistantSummary(String sessionId, String summary) {
        shortTermMemory.addMessage(sessionId, "assistant", summary)
            .doOnError(e -> log.warn("[Memory] 短期记忆写入失败: {}", e.getMessage()))
            .subscribe();
    }
}
