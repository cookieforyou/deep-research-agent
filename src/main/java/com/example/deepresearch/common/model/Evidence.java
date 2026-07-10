package com.example.deepresearch.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 证据 — 研究中收集的单条信息片段.
 * <p>
 * 每条 Evidence 有唯一的 {@code sourceId}（如 WEB01_1-1），
 * 用于在最终报告中精确引用和溯源。
 * </p>
 *
 * @param sourceId   来源 ID（格式: {WEB|LOC}{序号}_{子序号}-{段落}）
 * @param sourceType 来源类型（WEB 网络 / LOCAL 本地知识库）
 * @param url        原始 URL（LOCAL 类型为知识库文档标识）
 * @param title      证据标题
 * @param content    证据正文（截断后的关键段落）
 * @param score      可信度评分（0.0 ~ 1.0）
 * @param domain     来源域名（用于评分规则匹配）
 * @param retrievedAt 检索时间
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record Evidence(
    String sourceId,
    SourceType sourceType,
    String url,
    String title,
    String content,
    double score,
    int relevanceRank,
    String domain,
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime retrievedAt
) implements Serializable {
    /**
     * 证据来源类型.
     */
    public enum SourceType {
        /** 网络检索 */
        WEB,
        /** 本地知识库（RAG）检索 */
        LOCAL
    }

    /**
     * 创建一个带有评分调整的副本.
     */
    public Evidence withScore(double newScore) {
        return new Evidence(sourceId, sourceType, url, title, content,
            newScore, relevanceRank, domain, retrievedAt);
    }
}
