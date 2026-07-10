package com.example.deepresearch.common.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.io.Serializable;

/**
 * 评估结果 — EvalAgent 的结构化输出.
 * <p>
 * 对研究报告进行 5 个维度的质量评估，每个维度 1-5 分制。
 * 评估在 Writer 完成后异步执行（fire-and-forget），
 * 结果持久化到 {@code ResearchHistory.evalScores} JSON 字段。
 * </p>
 *
 * <h3>评估维度</h3>
 * <ul>
 *   <li><b>相关性 (Relevance)</b>: 报告内容是否准确回应了原始 query</li>
 *   <li><b>连贯性 (Coherence)</b>: 章节结构是否逻辑清晰</li>
 *   <li><b>引用准确性 (Citation Accuracy)</b>: 引用是否全部合法且有证据支撑</li>
 *   <li><b>完备性 (Completeness)</b>: 是否覆盖了所有子问题</li>
 *   <li><b>简洁性 (Conciseness)</b>: 是否避免了冗余和重复</li>
 * </ul>
 *
 * @param relevance        相关性评分 (1.0 ~ 5.0)
 * @param coherence        连贯性评分 (1.0 ~ 5.0)
 * @param citationAccuracy 引用准确性评分 (1.0 ~ 5.0)
 * @param completeness     完备性评分 (1.0 ~ 5.0)
 * @param conciseness      简洁性评分 (1.0 ~ 5.0)
 * @param overallScore     综合评分（5 维度均值）
 * @param summary          评估摘要（1-2 句话，概述主要优缺点）
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record EvalResult(
    double relevance,
    double coherence,
    double citationAccuracy,
    double completeness,
    double conciseness,
    double overallScore,
    String summary
) implements Serializable {

    /** 评估失败时的默认 Fallback */
    public static final EvalResult FALLBACK = new EvalResult(
        0, 0, 0, 0, 0, 0, "评估失败");
}
