package com.example.deepresearch.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import java.time.LocalDateTime;

/**
 * 搜索原始结果 — Bocha Search API 返回的单条搜索结果.
 * <p>
 * 这是搜索 API 的原始响应结构，在传给 WebScout Agent 前不做加工。
 * WebScout Agent 负责相关性过滤和 Evidence 结构化转换。
 * </p>
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record SearchResult(
    /** 搜索结果标题 */
    String title,
    /** 目标 URL */
    String url,
    /** 搜索摘要/片段 */
    String snippet,
    /** 来源域名 */
    String domain,
    /** 发布时间（可能为空） */
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime publishTime
) {}
