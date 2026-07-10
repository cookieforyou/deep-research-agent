package com.example.deepresearch.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prompt 模板分离工具 — 将单体 prompt 模板拆分为 system 和 user 两部分.
 * <p>
 * 利用 DeepSeek V4 原生支持的 {@code system} 角色实现架构级注入防护。
 * System 消息包含角色定义、规则约束、输出格式示例；
 * User 消息仅包含用户查询和当前上下文数据。
 * </p>
 *
 * <h3>分离策略</h3>
 * <p>
 * 所有 Agent prompt 模板使用统一的 {@code ---} 分隔符标记 system/user 边界。
 * 最后一个 {@code ---} 之前的内容为 system prompt，
 * 之后的内容（包含 {@code {{query}}} 占位符）为 user message 模板。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * String promptTemplate = loadPrompt("intent-router.st");
 * PromptParts parts = PromptSplitUtils.split(promptTemplate);
 * // parts.system() = 角色定义 + 判定规则 + 示例
 * // parts.user()   = "## 当前输入\n**query**: {{query}}\n..."
 * }</pre>
 */
public final class PromptSplitUtils {

    private static final Logger log = LoggerFactory.getLogger(PromptSplitUtils.class);

    /** 统一的 system/user 分隔标记 */
    private static final String SPLIT_MARKER = "\n---\n";

    private PromptSplitUtils() {}

    /**
     * Prompt 分离结果.
     */
    public record PromptParts(String system, String user) {}

    /**
     * 将单体 prompt 模板分离为 system 和 user 两部分.
     * <p>
     * 使用最后一个 {@code ---} 作为分隔点。如果模板中没有此标记，
     * 则整个模板作 system，user 留空（兼容旧模板）。
     * </p>
     *
     * @param promptTemplate 完整的 prompt 模板内容
     * @return 分离后的 system + user 部分
     */
    public static PromptParts split(String promptTemplate) {
        if (promptTemplate == null || promptTemplate.isBlank()) {
            return new PromptParts("", "");
        }

        int lastSplit = promptTemplate.lastIndexOf(SPLIT_MARKER);
        if (lastSplit == -1) {
            log.debug("[PromptSplit] 模板中未找到 '\\n---\\n' 分隔符，整体作为 system message");
            return new PromptParts(promptTemplate.strip(), "");
        }

        String systemPart = promptTemplate.substring(0, lastSplit).strip();
        String userPart = promptTemplate.substring(lastSplit + SPLIT_MARKER.length()).strip();

        return new PromptParts(systemPart, userPart);
    }
}
