package com.example.deepresearch.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 研究响应 DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResearchResponse(
    /** 会话 ID（用于 SSE 流订阅） */
    String sessionId,
    /** 研究状态 */
    Status status,
    /** 最终报告（仅在 status=COMPLETED 时返回） */
    String report,
    /** 错误信息（仅在 status=ERROR 时返回） */
    String error,
    /** 元数据（字数、引用数等） */
    Metadata metadata
) {
    public enum Status {
        /** 研究进行中 */
        IN_PROGRESS,
        /** 研究已完成 */
        COMPLETED,
        /** 研究失败 */
        ERROR
    }

    public record Metadata(
        int wordCount,
        int citationCount,
        int iterationCount
    ) {}

    /** 创建进行中响应 */
    public static ResearchResponse inProgress(String sessionId) {
        return new ResearchResponse(sessionId, Status.IN_PROGRESS, null, null, null);
    }

    /** 创建完成响应 */
    public static ResearchResponse completed(String sessionId, String report,
                                              int wordCount, int citations, int iterations) {
        return new ResearchResponse(sessionId, Status.COMPLETED, report, null,
            new Metadata(wordCount, citations, iterations));
    }

    /** 创建错误响应 */
    public static ResearchResponse error(String sessionId, String errorMessage) {
        return new ResearchResponse(sessionId, Status.ERROR, null, errorMessage, null);
    }
}
