package com.example.deepresearch.common.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.io.Serializable;
import java.util.List;

/**
 * 撰写结果 — Writer Agent 的结构化输出.
 * <p>
 * 包含最终研究报告的完整内容、使用的引用列表和字数统计。
 * </p>
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record WriteResult(
    /** Markdown 格式的完整研报正文 */
    String reportContent,
    /** 报告中实际使用的合法引用 sourceId 列表 */
    List<String> usedCitations,
    /** 报告总字数 */
    int wordCount,
    /** 报告章节数 */
    int sectionCount
) implements Serializable {}
