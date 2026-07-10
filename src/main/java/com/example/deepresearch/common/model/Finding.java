package com.example.deepresearch.common.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.io.Serializable;
import java.util.List;

/**
 * 研究结论 — 针对某个子问题的分析结论.
 * <p>
 * 每个 Finding 关联一个子问题，包含结论文本、推理链条、
 * 以及支撑该结论的证据 ID 列表（引用溯源）。
 * </p>
 *
 * @param findingId              结论 ID
 * @param subQuestionId          关联的子问题 ID
 * @param conclusion             结论正文
 * @param reasoning              推理链条（如何从证据得出此结论）
 * @param supportingEvidenceIds  支撑证据的 sourceId 列表
 * @param confidence             置信度（0.0 ~ 1.0）
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record Finding(
    String findingId,
    String subQuestionId,
    String conclusion,
    String reasoning,
    List<String> supportingEvidenceIds,
    double confidence
) implements Serializable {}
