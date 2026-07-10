package com.example.deepresearch.common.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.io.Serializable;
import java.util.List;

/**
 * 计划结果 — Planner Agent 的结构化输出.
 * <p>
 * 包含任务规划的全部产出：子问题列表、报告大纲、搜索计划。
 * 由 {@code BeanOutputConverter} 将 LLM JSON 输出直接映射为此 Record。
 * </p>
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record PlanResult(
    /** 拆解后的子问题列表（核心研究维度） */
    List<String> subQuestions,
    /** 报告大纲（Markdown 层级结构） */
    String reportOutline,
    /** 每个子问题对应的搜索计划 */
    List<SearchPlan> searchPlans
) implements Serializable {}
