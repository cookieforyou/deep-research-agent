package com.example.deepresearch.common.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.io.Serializable;
import java.util.List;

/**
 * 分析结果 — Analyst Agent 的结构化输出.
 * <p>
 * 包含研究结论、证据完备性评估和信息缺口识别。
 * </p>
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record AnalysisResult(
    /** 各子问题的研究结论 */
    List<Finding> findings,
    /** 是否需要补充检索 */
    boolean needsMoreResearch,
    /** 识别的信息缺口描述 */
    List<String> missingGaps,
    /** 证据完备性评分（0.0 ~ 1.0） */
    double completenessScore
) implements Serializable {}
