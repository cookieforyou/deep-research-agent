package com.example.deepresearch.common.util;

import com.example.deepresearch.common.exception.ResearchException;
import com.example.deepresearch.common.exception.ResearchException.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM JSON 输出安全解析工具.
 * <p>
 * 大模型输出的 JSON 经常带有 Markdown 代码块标记（```json ... ```）或格式错误。
 * 本工具类提供鲁棒的解析能力：
 * <ul>
 *   <li>自动剥离 Markdown 代码块标记</li>
 *   <li>正则提取最外层 JSON 对象/数组</li>
 *   <li>解析失败时返回默认 Fallback 对象，确保工作流不崩溃</li>
 * </ul>
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * PlanResult result = jsonUtils.safeParse(rawLlmOutput, PlanResult.class,
 *     new PlanResult(List.of(), "fallback outline"));
 * }</pre>
 */
@Component
public class JsonParseUtils {

    private static final Logger log = LoggerFactory.getLogger(JsonParseUtils.class);

    private final ObjectMapper objectMapper;

    /** 匹配 Markdown 代码块: ```json ... ``` 或 ``` ... ``` */
    private static final Pattern MARKDOWN_FENCE = Pattern.compile(
        "```(?:json|JSON)?\\s*\\n?([\\s\\S]*?)\\n?```", Pattern.MULTILINE);

    /** 提取最外层 JSON 对象 {...} */
    private static final Pattern JSON_OBJECT = Pattern.compile(
        "\\{[\\s\\S]*\\}", Pattern.MULTILINE);

    /** 提取最外层 JSON 数组 [...] */
    private static final Pattern JSON_ARRAY = Pattern.compile(
        "\\[[\\s\\S]*\\]", Pattern.MULTILINE);

    /**
     * 匹配未加引号的 JSON 字符串值.
     * <p>
     * LLM 偶尔会在 JSON 输出中遗漏 value 的双引号，例如:
     * {@code "title": 【氨】2026年...} 而非 {@code "title": "【氨】2026年..."}。
     * 此正则检测冒号后紧跟非 JSON 值起始符的内容并为其补上引号。
     * </p>
     * <p>
     * 捕获组: $1=字段名及冒号, $2=未引号值内容, $3=结尾分隔符(逗号/换行/大括号)
     * JSON 值合法起始符: {@code "} (字符串), {@code {} (对象), {@code [} (数组),
     * 数字, 负号, {@code true/false/null} 首字母 — 以上均被排除。
     * </p>
     */
    private static final Pattern UNQUOTED_STRING_VALUE = Pattern.compile(
        "(\"[^\"]+\"\\s*:\\s*)([^\"\\{\\[\\d\\-tfn\\s][^,\\n\\}\\]\\]]*?)(\\s*[,\\n\\}\\]\\]])");

    public JsonParseUtils(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 安全解析 LLM 输出的 JSON 字符串.
     *
     * @param rawOutput LLM 原始输出（可能包含 Markdown 标记）
     * @param targetType 目标 Java 类型
     * @param fallback 解析失败时返回的默认值
     * @param agentName Agent 名称（用于日志和异常标识）
     * @param <T> 目标类型
     * @return 解析结果或 fallback
     * @throws ResearchException 当 fallback 为 null 且解析失败时抛出
     */
    public <T> T safeParse(String rawOutput, Class<T> targetType, T fallback, String agentName) {
        if (rawOutput == null || rawOutput.isBlank()) {
            log.warn("[{}] LLM 输出为空，返回 fallback", agentName);
            if (fallback == null) {
                throw new ResearchException(ErrorCode.LLM_JSON_PARSE_ERROR, agentName,
                    "LLM 输出为空且无 fallback", null);
            }
            return fallback;
        }

        try {
            // 步骤 1: 剥离 Markdown 代码块标记
            String cleanJson = stripMarkdownFence(rawOutput);

            // 步骤 2: 提取 JSON 对象或数组
            String extracted = extractJson(cleanJson);
            if (extracted == null) {
                log.warn("[{}] 无法从输出中提取有效 JSON: {}", agentName,
                    rawOutput.substring(0, Math.min(200, rawOutput.length())));
                return fallbackOrThrow(fallback, agentName, rawOutput);
            }

            // 步骤 3: Jackson 反序列化
            try {
                return objectMapper.readValue(extracted, targetType);
            } catch (JsonProcessingException e) {
                // 定位 Jackson 报错的具体行
                String errorLine = extractErrorLine(extracted, e);
                log.warn("[{}] JSON 反序列化失败: {} — 错误位置: {}",
                    agentName, e.getMessage(), errorLine);

                // 步骤 4: 二次尝试 — 修复常见 LLM JSON 缺陷后重试
                String repaired = repairCommonJsonIssues(extracted);
                if (!repaired.equals(extracted)) {
                    try {
                        log.info("[{}] JSON 修复后重试解析成功", agentName);
                        return objectMapper.readValue(repaired, targetType);
                    } catch (JsonProcessingException e2) {
                        log.warn("[{}] JSON 修复后仍失败: {}", agentName, e2.getMessage());
                    }
                }

                log.warn("[{}] 原始内容前200字符: {}",
                    agentName, extracted.substring(0, Math.min(200, extracted.length())));
                return fallbackOrThrow(fallback, agentName, extracted);
            }

        } catch (ResearchException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] JSON 解析过程异常", agentName, e);
            return fallbackOrThrow(fallback, agentName, rawOutput);
        }
    }

    /**
     * 安全解析（无 agentName 的简化版本）.
     */
    public <T> T safeParse(String rawOutput, Class<T> targetType, T fallback) {
        return safeParse(rawOutput, targetType, fallback, "unknown");
    }

    /**
     * 剥离 Markdown 代码块标记.
     * <p>
     * 处理 ```json ... ``` 和 ``` ... ``` 两种形式。
     * 如果没有代码块标记，返回原始字符串。
     * </p>
     */
    String stripMarkdownFence(String raw) {
        Matcher matcher = MARKDOWN_FENCE.matcher(raw.trim());
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // 尝试移除单独的开头/结尾 ``` 标记
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
            // 移除可能的 "json" 语言标识
            if (cleaned.startsWith("json") || cleaned.startsWith("JSON")) {
                cleaned = cleaned.substring(4);
            }
            cleaned = cleaned.trim();
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }
        return cleaned;
    }

    /**
     * 从文本中提取 JSON 对象或数组.
     * <p>
     * 先尝试匹配 JSON 对象 {...}，如果失败再尝试数组 [...]。
     * 使用贪婪匹配获取最外层结构。
     * </p>
     *
     * @return 提取的 JSON 字符串，或 null
     */
    String extractJson(String text) {
        // 先尝试匹配 JSON 对象
        Matcher objectMatcher = JSON_OBJECT.matcher(text);
        if (objectMatcher.find()) {
            return objectMatcher.group();
        }

        // 再尝试匹配 JSON 数组
        Matcher arrayMatcher = JSON_ARRAY.matcher(text);
        if (arrayMatcher.find()) {
            return arrayMatcher.group();
        }

        return null;
    }

    /**
     * 从 Jackson 异常消息中提取错误所在行的内容（用于调试 LLM JSON 缺陷）.
     */
    private String extractErrorLine(String json, JsonProcessingException e) {
        try {
            String msg = e.getMessage();
            // Jackson 错误格式: " at [Source: ...; line: N, column: M]"
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "line:\\s*(\\d+)");
            java.util.regex.Matcher m = p.matcher(msg);
            if (m.find()) {
                int lineNum = Integer.parseInt(m.group(1));
                String[] lines = json.split("\n");
                if (lineNum > 0 && lineNum <= lines.length) {
                    String errorLine = lines[lineNum - 1];
                    if (errorLine.length() > 120) {
                        return "line " + lineNum + ": " + errorLine.substring(0, 120) + "...";
                    }
                    return "line " + lineNum + ": " + errorLine;
                }
            }
        } catch (Exception ignored) {}
        return "(无法定位错误行)";
    }

    /**
     * 修复 LLM JSON 输出中的常见缺陷.
     * <ol>
     *   <li>JSON 对象/数组末尾的多余逗号 (如 [1,2,] → [1,2])</li>
     *   <li>BOM 和零宽字符移除</li>
     *   <li>JSON 对象 value 遗漏引号 (如 "title": 中国... → "title": "中国...")</li>
     * </ol>
     *
     * @return 修复后的 JSON 字符串（若无修复则返回原始字符串）
     */
    private String repairCommonJsonIssues(String json) {
        String repaired = json;

        // 修复 1: JSON 数组/对象末尾的多余逗号 (如 [1,2,] → [1,2])
        repaired = repaired.replaceAll(",\\s*(\\]|\\})", "$1");

        // 修复 2: 移除 JSON 外的 BOM 和零宽字符
        repaired = repaired.replaceAll("[\\uFEFF\\u200B]", "");

        // 修复 3: 未引号字符串值 — LLM 偶尔遗漏 value 的双引号
        // 例如 "title": 【氨】2026年... → "title": "【氨】2026年..."
        repaired = UNQUOTED_STRING_VALUE.matcher(repaired).replaceAll("$1\"$2\"$3");

        // 修复 4: 截断的 JSON — LLM 输出超出 token 限制导致数组/对象未闭合
        // 统计括号深度，自动补全缺失的 ] }
        repaired = closeUnclosedBrackets(repaired);

        return repaired;
    }

    /**
     * 自动闭合未完成的 JSON 字符串和括号.
     * <p>
     * LLM 输出可能因 token 限制被截断，导致字符串未闭合或数组/对象未完成。
     * 此方法按顺序修复：闭合未完成字符串 → 剥离截断逗号 → 按栈逆序补齐缺失括号。
     * </p>
     */
    private String closeUnclosedBrackets(String json) {
        // 用栈追踪括号开启顺序(存储期望的闭合字符)
        java.util.ArrayList<Character> bracketStack = new java.util.ArrayList<>();
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && inString) { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;

            if (c == '{') bracketStack.add('}');
            else if (c == '[') bracketStack.add(']');
            else if (c == '}' || c == ']') {
                // 移除已匹配的括号（假设输入中已闭合部分是正确的）
                if (!bracketStack.isEmpty()) {
                    bracketStack.remove(bracketStack.size() - 1);
                }
            }
        }

        // 无需修复
        if (bracketStack.isEmpty() && !inString) return json;

        String trimmed = json.stripTrailing();

        // 步骤 1: 闭合未完成的字符串（截断在字符串中间）
        if (inString) trimmed += "\"";

        // 步骤 2: 去除截断点末尾的逗号
        if (trimmed.endsWith(",")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        // 步骤 3: 按栈逆序补齐缺失的括号
        StringBuilder sb = new StringBuilder(trimmed);
        for (int i = bracketStack.size() - 1; i >= 0; i--) {
            sb.append(bracketStack.get(i));
        }

        return sb.toString();
    }

    /**
     * 返回 fallback 或抛出异常.
     */
    private <T> T fallbackOrThrow(T fallback, String agentName, String rawOutput) {
        if (fallback != null) {
            return fallback;
        }
        throw new ResearchException(ErrorCode.LLM_JSON_PARSE_ERROR, agentName,
            "JSON 解析失败: " + rawOutput.substring(0, Math.min(200, rawOutput.length())), null);
    }
}
