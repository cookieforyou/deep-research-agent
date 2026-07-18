package com.example.deepresearch.api.dto;

import com.example.deepresearch.memory.entity.ResearchHistory;

import java.time.LocalDateTime;

/**
 * 研究历史摘要 DTO（不含报告全文、证据池、研究结论）。
 * <p>
 * 用于历史列表 API 的响应，排除 report（可能 > 3000 字）、
 * sourceIndex（证据池 JSON 数组，可能 > 10 KB）、
 * findings（研究结论 JSON 数组，可能 > 5 KB），
 * 大幅减少列表页传输体积。
 * </p>
 * <p>
 * 详情页（GET /api/history/{sessionId}）返回完整 ResearchHistory 实体，
 * 包含 sourceIndex 和 findings 供引用溯源和关键发现渲染。
 * </p>
 */
public record ResearchHistorySummary(
    Long id,
    String sessionId,
    String userId,
    String tenantId,
    String query,
    int wordCount,
    int citationCount,
    int iterationCount,
    String status,
    String evalScores,
    LocalDateTime createdAt
) {
    /**
     * 从 ResearchHistory 实体创建摘要（排除 report / sourceIndex / findings）。
     */
    public static ResearchHistorySummary from(ResearchHistory h) {
        return new ResearchHistorySummary(
            h.getId(),
            h.getSessionId(),
            h.getUserId(),
            h.getTenantId(),
            h.getQuery(),
            h.getWordCount(),
            h.getCitationCount(),
            h.getIterationCount(),
            h.getStatus(),
            h.getEvalScores(),
            h.getCreatedAt()
        );
    }
}
