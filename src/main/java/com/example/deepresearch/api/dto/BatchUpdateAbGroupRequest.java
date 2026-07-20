package com.example.deepresearch.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * 批量更新 Prompt A/B 分组请求。
 */
public record BatchUpdateAbGroupRequest(
    @NotEmpty(message = "items 不能为空")
    @Valid
    List<AbGroupItem> items
) {
    public record AbGroupItem(
        @NotBlank(message = "id 不能为空")
        String id,

        @Pattern(regexp = "A|B", message = "AB分组必须为 A 或 B")
        @JsonProperty("abGroup")
        String abGroup // null 表示清除分组
    ) {}
}
