package com.example.deepresearch.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 研究请求 DTO（客户端传入）。
 * <p>
 * 身份字段（userId / tenantId）由 Controller 从 JWT claims 提取，
 * 不再接受客户端传值，防止篡改。
 * </p>
 */
public record ResearchRequest(
    /* 研究查询文本（必填，1-5000 字符） */
    @NotBlank(message = "查询不能为空")
    @Size(min = 1, max = 5000, message = "查询长度需在 1-5000 字符之间")
    String query,

    /* 是否启用深度研究模式 (false=直接回答, 默认 true) */
    boolean deepResearch
) {
    /** 便捷构造（默认启用深度研究） */
    public ResearchRequest(String query) {
        this(query, true);
    }
}
