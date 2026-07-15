package com.example.deepresearch.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 研究请求 DTO.
 * <p>
 * 身份字段说明：userId / tenantId 以 JWT claims（sub / tenant_id）为准，
 * 由 Controller 层解析覆盖；请求体中的值仅在 JWT 缺少对应 claim 时
 * 作为兼容回退（不可信，客户端可伪造）。
 * </p>
 */
public record ResearchRequest(
    /** 研究查询文本（必填，1-5000 字符） */
    @NotBlank(message = "查询不能为空")
    @Size(min = 1, max = 5000, message = "查询长度需在 1-5000 字符之间")
    String query,

    /** 用户 ID（可选，仅 JWT 缺少 sub claim 时作回退） */
    String userId,

    /** 租户 ID（可选，仅 JWT 缺少 tenant_id claim 时作回退） */
    String tenantId,

    /** 是否启用深度研究模式 (false=直接回答, 默认 true) */
    boolean deepResearch
) {
    public ResearchRequest {
        if (userId == null || userId.isBlank()) userId = "anonymous";
        if (tenantId == null || tenantId.isBlank()) tenantId = "default";
    }

    /** 便捷构造（默认启用深度研究） */
    public ResearchRequest(String query, String userId, String tenantId) {
        this(query, userId, tenantId, true);
    }
}
