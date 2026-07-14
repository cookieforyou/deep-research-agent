package com.example.deepresearch.api.dto;

import com.example.deepresearch.memory.entity.ResearchHistory;

import java.time.LocalDateTime;

/**
 * 研究历史摘要 DTO（不含报告全文）。
 * <p>
 * 用于历史列表 API 的响应，避免传输大文本（report 可能 > 3000 字），
 * 同时避免直接修改 JPA managed entity（{@code setReport(null)}）。
 * </p>
 * <p>
 * 从 {@link ResearchHistory} 实体构造，排除 report 字段。
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
     * 从 ResearchHistory 实体创建摘要（排除 report 字段）。
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
