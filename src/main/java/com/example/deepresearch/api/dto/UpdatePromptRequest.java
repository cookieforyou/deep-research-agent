package com.example.deepresearch.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Prompt 模板更新请求 DTO。
 * 所有字段可选，只更新传入的字段。
 */
public record UpdatePromptRequest(
    @Size(min = 10, max = 20000, message = "模板内容长度需在 10-20000 字符之间")
    String content,

    @Pattern(regexp = "active|inactive|deprecated", message = "状态必须为 active/inactive/deprecated")
    String status,

    @Pattern(regexp = "A|B", message = "AB分组必须为 A 或 B")
    @JsonProperty("abGroup")
    String abGroup
) {}
