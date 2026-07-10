package com.example.deepresearch.common.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.io.Serializable;

/**
 * 搜索计划 — Planner 生成的单条搜索任务.
 * <p>
 * 每个 SearchPlan 描述一个需要检索的方向/子问题，
 * 包含查询词、理由和优先级。
 * </p>
 *
 * @param queryId   查询 ID（对应子问题编号）
 * @param query     搜索查询词
 * @param rationale 搜索理由（为什么需要查这个方向）
 * @param priority  优先级（数字越小越优先）
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record SearchPlan(
    String queryId,
    String query,
    String rationale,
    int priority
) implements Serializable {
    /**
     * 按优先级排序的比较器.
     */
    public static int compareByPriority(SearchPlan a, SearchPlan b) {
        return Integer.compare(a.priority, b.priority);
    }
}
