package com.example.deepresearch.agent.reflect;

import com.example.deepresearch.common.model.ReflectResult;
import com.example.deepresearch.common.util.JsonParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * 反思 Agent — 针对信息缺口生成补充搜索查询.
 * <p>
 * 使用 deepseek-v4-pro (T=0.3)，需要针对性地生成补充搜索词。
 * 这是 Reflect 循环的<strong>核心引擎</strong>：
 * 分析缺失信息 → 生成新搜索词 → 重新进入检索阶段。
 * </p>
 *
 * <h3>查询生成策略</h3>
 * <ul>
 *   <li><b>针对性</b>: 每个新查询直接针对一个特定信息缺口</li>
 *   <li><b>去重</b>: 与已搜索过的查询词对比，避免重复搜索</li>
 *   <li><b>多样性</b>: 从不同角度/关键词重述同一缺口，提高命中率</li>
 *   <li><b>收敛性</b>: 随着迭代次数增加，查询应更聚焦</li>
 * </ul>
 */
// @Service — 已废弃，单轮模式不再需要反思补搜循环
public class ReflectAgent {

    private static final Logger log = LoggerFactory.getLogger(ReflectAgent.class);

    private final ChatClient chatClient;
    private final JsonParseUtils jsonUtils;
    private final String promptTemplate;

    /** Fallback: 无法生成新查询，停止补搜 */
    private static final ReflectResult FALLBACK = new ReflectResult(
        Collections.emptyList(), "Fallback: 无法生成补搜查询", false);

    public ReflectAgent(
        @Qualifier("reflectClient") ChatClient chatClient,
        JsonParseUtils jsonUtils,
        ResourceLoader resourceLoader
    ) {
        this.chatClient = chatClient;
        this.jsonUtils = jsonUtils;
        this.promptTemplate = loadPrompt(resourceLoader);
    }

    /**
     * 生成补充搜索查询.
     *
     * @param query           原始研究查询
     * @param missingGaps     分析师识别的信息缺口
     * @param existingQueries 已执行过的搜索查询（用于去重）
     * @param iteration       当前迭代轮次
     * @return 反思结果（新搜索查询 + 继续探索建议）
     */
    public ReflectResult reflect(String query, List<String> missingGaps,
                                  List<String> existingQueries, int iteration) {
        if (missingGaps == null || missingGaps.isEmpty()) {
            log.info("[Reflect] 无信息缺口，跳过补搜");
            return new ReflectResult(List.of(), "无缺口，无需补搜", false);
        }

        log.info("[Reflect] 开始反思: {} 个缺口, {} 个已有查询, 第 {} 轮迭代",
            missingGaps.size(), existingQueries != null ? existingQueries.size() : 0, iteration);

        try {
            String prompt = promptTemplate
                .replace("{{query}}", query)
                .replace("{{missingGaps}}", String.join("\n", missingGaps))
                .replace("{{existingQueries}}",
                    existingQueries != null ? String.join("\n", existingQueries) : "（首次搜索）")
                .replace("{{iteration}}", String.valueOf(iteration));

            String rawOutput = chatClient.prompt().user(prompt).call().content();
            log.debug("[Reflect] LLM 输出: {}", rawOutput);

            ReflectResult result = jsonUtils.safeParse(
                rawOutput, ReflectResult.class, FALLBACK, "Reflect");

            // 过滤重复查询
            List<String> uniqueQueries = result.newSearchQueries().stream()
                .filter(q -> existingQueries == null || !existingQueries.contains(q))
                .distinct()
                .limit(5)  // 每轮最多 5 个新查询
                .toList();

            log.info("[Reflect] 反思完成: 生成 {} 个新查询（过滤后 {} 个）, 继续探索={}",
                result.newSearchQueries().size(), uniqueQueries.size(),
                result.hasMoreToExplore());

            return new ReflectResult(uniqueQueries, result.rationale(), result.hasMoreToExplore());

        } catch (Exception e) {
            log.error("[Reflect] 反思异常，返回 fallback", e);
            return FALLBACK;
        }
    }

    private String loadPrompt(ResourceLoader loader) {
        try {
            Resource resource = loader.getResource("classpath:prompts/reflect.st");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[Reflect] 无法加载 prompt 模板", e);
            return """
                你是信息检索专家。为填补以下信息缺口，生成补充搜索查询。

                研究问题: {{query}}
                信息缺口:
                {{missingGaps}}
                已搜索查询:
                {{existingQueries}}
                当前迭代: {{iteration}}

                返回 JSON: {"newSearchQueries": ["..."], "rationale": "...", "hasMoreToExplore": true}
                """;
        }
    }
}
