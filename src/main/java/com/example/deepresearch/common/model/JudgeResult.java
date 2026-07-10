package com.example.deepresearch.common.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.io.Serializable;
import java.util.List;

/**
 * 裁判结果 — Evidence Judge Agent 的结构化输出.
 * <p>
 * 对双源证据池进行评分、去重、冲突检测后的结构化结果。
 * </p>
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record JudgeResult(
    /** 评分和去重后的证据池 */
    List<Evidence> scoredEvidence,
    /** 检测到的审计标记 */
    List<AuditFlag> flags,
    /** 统一来源索引（sourceId → Evidence 的映射键） */
    List<String> sourceIndex
) implements Serializable {}
