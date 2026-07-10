package com.example.deepresearch.common.model;

/**
 * 证据审计标记 — Evidence Judge 发现的证据问题.
 * <p>
 * 每条标记描述一个证据质量问题：冲突、低可信度、不可引用、或重复。
 * </p>
 *
 * @param evidenceIdA  涉及的证据 A 的 sourceId
 * @param evidenceIdB  涉及的证据 B 的 sourceId（无冲突时为 null）
 * @param flagType     标记类型
 * @param description  问题描述
 */
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.io.Serializable;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record AuditFlag(
    String evidenceIdA,
    String evidenceIdB,
    FlagType flagType,
    String description
) implements Serializable {
    /**
     * 审计标记类型.
     */
    public enum FlagType {
        /** 两条证据矛盾 */
        CONFLICT,
        /** 单一证据可信度偏低 */
        LOW_CONFIDENCE,
        /** 来源不可引用（如匿名论坛） */
        UNCITABLE,
        /** 内容高度重复 */
        DUPLICATE
    }
}
