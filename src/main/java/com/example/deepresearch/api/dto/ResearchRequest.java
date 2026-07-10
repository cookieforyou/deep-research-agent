package com.example.deepresearch.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 研究请求 DTO.
 */
public record ResearchRequest(
    /** 研究查询文本（必填，1-5000 字符） */
    @NotBlank(message = "查询不能为空")
    @Size(min = 1, max = 5000, message = "查询长度需在 1-5000 字符之间")
    String query,

    /** 用户 ID（必填） */
    @NotBlank(message = "用户 ID 不能为空")
    String userId,

    /** 租户 ID（必填，多租户隔离） */
    @NotBlank(message = "租户 ID 不能为空")
    String tenantId,

    /** 是否启用深度研究模式 (false=直接回答, 默认 true) */
    boolean deepResearch
) {
    public ResearchRequest {
        if (userId == null) userId = "anonymous";
        if (tenantId == null) tenantId = "default";
    }

    /** 便捷构造（默认启用深度研究） */
    public ResearchRequest(String query, String userId, String tenantId) {
        this(query, userId, tenantId, true);
    }
}
