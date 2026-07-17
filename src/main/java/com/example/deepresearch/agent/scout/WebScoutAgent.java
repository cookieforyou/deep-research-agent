package com.example.deepresearch.agent.scout;

import com.example.deepresearch.agent.tool.SearchTools;
import com.example.deepresearch.agent.tool.SearchTools.CollectedSource;
import com.example.deepresearch.common.model.Evidence;
import com.example.deepresearch.common.model.Evidence.SourceType;
import com.example.deepresearch.common.util.JsonParseUtils;
import com.example.deepresearch.common.util.PromptSplitUtils;
import com.example.deepresearch.common.util.PromptSplitUtils.PromptParts;
import com.example.deepresearch.service.DynamicPromptService;
import com.example.deepresearch.tool.EvidenceScorer;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 网络侦察 Agent — LLM 自主调用 webSearch 工具获取外部证据（Spring AI 2.0 @Tool 模式）.
 * <p>
 * 使用 deepseek-v4-flash (T=0.4)，通过 ToolCallingAdvisor 让 LLM 自主决定
 * 搜索关键词、时机和轮次。Planner 的搜索计划作为 prompt 中的上下文指引，
 * 而非硬性执行的批量搜索任务。
 * </p>
 *
 * <h3>工具层证据收集（根治 LLM 复述 JSON 脆弱性）</h3>
 * <ul>
 *   <li>原始搜索结果由 {@link SearchTools} 在 @Tool 执行时直接收集并分配 sourceId</li>
 *   <li>LLM 只输出选中的 {@code selections}（sourceId + score + relevanceRank），
 *       不复述 title/url/content — 消除未转义引号、输出截断、token 浪费</li>
 *   <li>Evidence 由 Java 代码从收集结果组装，content 忠于搜索原文</li>
 *   <li>LLM 输出不可用时降级：直接采用全部收集结果 + 规则评分（不再丢证据）</li>
 * </ul>
 */
@Service
public class WebScoutAgent {

    private static final Logger log = LoggerFactory.getLogger(WebScoutAgent.class);

    /** 降级路径最多采用的收集结果数（下游 dedup 截断上限为 40） */
    private static final int MAX_FALLBACK_EVIDENCE = 30;

    private final ChatClient chatClient;
    private final SearchTools searchTools;
    private final JsonParseUtils jsonUtils;
    private final EvidenceScorer evidenceScorer;
    private final DynamicPromptService dynamicPromptService;

    /** Fallback: LLM JSON 解析失败时的空选择（触发收集结果降级） */
    private static final SelectionListWrapper FALLBACK =
        new SelectionListWrapper(Collections.emptyList());

    public WebScoutAgent(
        @Qualifier("webScoutClient") ChatClient chatClient,
        SearchTools searchTools,
        JsonParseUtils jsonUtils,
        EvidenceScorer evidenceScorer,
        DynamicPromptService dynamicPromptService
    ) {
        this.chatClient = chatClient;
        this.searchTools = searchTools;
        this.jsonUtils = jsonUtils;
        this.evidenceScorer = evidenceScorer;
        this.dynamicPromptService = dynamicPromptService;
    }

    /**
     * 执行网络搜索取证.
     * <p>
     * Planner 的搜索计划作为 prompt 上下文指引 LLM，LLM 通过 ToolCallingAdvisor
     * 自主决定如何调用 {@code webSearch} 工具。原始结果在工具层收集，
     * LLM 仅输出筛选结论（sourceId 列表）。
     * </p>
     *
     * @param query             原始研究查询（提供上下文）
     * @param searchPlanQueries 规划师生成的搜索查询词列表（作为指引）
     * @return 结构化的网络证据列表
     */
    public List<Evidence> search(String query, List<String> searchPlanQueries) {
        if (searchPlanQueries == null || searchPlanQueries.isEmpty()) {
            log.warn("[WebScout] 无搜索查询词，跳过网络检索");
            return List.of();
        }

        log.info("[WebScout] 开始网络检索: query='{}', {} 个搜索指引",
            query, searchPlanQueries.size());

        // 构建查询上下文（Planner 的搜索计划作为指引，而非硬性执行）
        String queriesContext = String.join("\n", searchPlanQueries.stream()
            .map(q -> "- " + q).toList());

        // 每次调用时加载模板（DynamicPromptService 内置 1min TTL 缓存）→ 支持 DB 热更新免重启
        PromptParts parts = PromptSplitUtils.split(
            dynamicPromptService.getTemplateContent("web-scout"));

        // 开启工具层证据收集（@Tool 执行与本方法同线程）
        searchTools.beginCollection("WEB");
        String raw = null;
        Map<String, CollectedSource> collected;
        try {
            raw = chatClient.prompt()
                .advisors(a -> a.param("agent", "WebScout").param("tier", "flash")
                    .param("skipPiiMask", true))
                .system(parts.system())
                .user(parts.user()
                    .replace("{{query}}", query)
                    .replace("{{searchPlanQueries}}", queriesContext))
                .call()
                .content();
        } catch (Exception ex) {
            log.warn("[WebScout] LLM 调用失败，尝试用已收集结果降级: {}", ex.getMessage());
        } finally {
            collected = searchTools.endCollection();
        }

        SelectionListWrapper result = raw != null
            ? jsonUtils.safeParse(raw, SelectionListWrapper.class, FALLBACK, "WebScout")
            : FALLBACK;

        // 诊断：LLM 输出了 selections 但 sourceId 为 null 时，打印原始输出定位根因
        if (result.selections() != null && !result.selections().isEmpty()
            && result.selections().stream().anyMatch(s -> s.sourceId() == null)) {
            log.warn("[WebScout] LLM 输出的 selections 包含 null sourceId，原始输出前500字符: {}",
                raw != null ? raw.substring(0, Math.min(500, raw.length())) : "null");
        }

        List<Evidence> evidences = toEvidences(result.selections(), collected);
        if (evidences.isEmpty() && !collected.isEmpty()) {
            log.warn("[WebScout] LLM 未返回有效选择，降级采用全部收集结果: {} 条",
                collected.size());
            evidences = fallbackEvidences(collected);
        }
        log.info("[WebScout] 检索完成: 收集 {} 条原始结果, 产出 {} 条证据",
            collected.size(), evidences.size());
        return evidences;
    }

    /**
     * 按 LLM 选择组装 Evidence — title/url/content 均来自工具层收集的原文.
     */
    private List<Evidence> toEvidences(List<Selection> selections,
                                        Map<String, CollectedSource> collected) {
        if (selections == null || selections.isEmpty()) {
            return List.of();
        }
        List<Evidence> evidences = new ArrayList<>();
        for (Selection sel : selections) {
            CollectedSource src = collected.get(sel.sourceId());
            if (src == null) {
                log.warn("[WebScout] LLM 引用了不存在的 sourceId（已跳过）: {}", sel.sourceId());
                continue;
            }
            double score = Math.max(0.0, Math.min(1.0, sel.score()));
            evidences.add(new Evidence(
                src.sourceId(), SourceType.WEB, src.url(), src.title(), src.content(),
                score, sel.relevanceRank(), src.domain(), LocalDateTime.now()));
        }
        return evidences;
    }

    /**
     * 降级路径：LLM 输出不可用时直接采用收集结果，用规则评分器打分.
     */
    private List<Evidence> fallbackEvidences(Map<String, CollectedSource> collected) {
        List<Evidence> evidences = new ArrayList<>();
        int rank = 0;
        for (CollectedSource src : collected.values()) {
            if (++rank > MAX_FALLBACK_EVIDENCE) {
                log.info("[WebScout] 降级证据超过上限 {}，截断剩余 {} 条",
                    MAX_FALLBACK_EVIDENCE, collected.size() - MAX_FALLBACK_EVIDENCE);
                break;
            }
            Evidence e = new Evidence(
                src.sourceId(), SourceType.WEB, src.url(), src.title(), src.content(),
                0.0, rank, src.domain(), LocalDateTime.now());
            evidences.add(evidenceScorer.score(e)); // 域名权威度规则评分
        }
        return evidences;
    }

    /** LLM 输出的筛选结论包装 — 只含 sourceId 引用，不复述内容.
     *  注意：全局 ObjectMapper 为 SNAKE_CASE，须显式声明驼峰命名，否则 sourceId 反序列化为 null
     */
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record SelectionListWrapper(List<Selection> selections) {}

    /** 单条筛选结论（sourceId 指向工具层收集的原始结果） */
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    record Selection(
        String sourceId,
        double score,
        int relevanceRank
    ) {}

}
