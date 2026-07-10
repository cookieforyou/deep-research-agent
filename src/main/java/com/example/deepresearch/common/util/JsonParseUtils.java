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
                log.warn("[{}] JSON 反序列化失败: {} — 原始内容前200字符: {}",
                    agentName, e.getMessage(),
                    extracted.substring(0, Math.min(200, extracted.length())));
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
