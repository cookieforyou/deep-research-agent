package com.example.deepresearch.common.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.io.Serializable;
import java.util.List;

/**
 * 反思结果 — Reflect Agent 的结构化输出.
 * <p>
 * 针对信息缺口生成的补充搜索查询词列表，
 * 避免与已搜索过的查询重复。
 * </p>
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record ReflectResult(
    /** 新的搜索查询词 */
    List<String> newSearchQueries,
    /** 反思理由（为什么这些方向可以填补缺口） */
    String rationale,
    /** 是否还有值得探索的方向 */
    boolean hasMoreToExplore
) implements Serializable {}
