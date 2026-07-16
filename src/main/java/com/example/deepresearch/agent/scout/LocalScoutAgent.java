package com.example.deepresearch.agent.scout;

import com.example.deepresearch.agent.tool.SearchTools;
import com.example.deepresearch.agent.tool.SearchTools.CollectedSource;
import com.example.deepresearch.common.model.Evidence;
import com.example.deepresearch.common.model.Evidence.SourceType;
import com.example.deepresearch.common.util.JsonParseUtils;
import com.example.deepresearch.common.util.PromptSplitUtils;
import com.example.deepresearch.common.util.PromptSplitUtils.PromptParts;
import com.example.deepresearch.service.DynamicPromptService;
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
 * 本地知识库侦察 Agent — LLM 自主调用 localSearch 工具检索内部文档（Spring AI 2.0 @Tool 模式）.
 * <p>
 * 使用 deepseek-v4-flash (T=0.4)，通过 ToolCallingAdvisor 让 LLM 自主决定
 * 检索关键词和时机。Planner 的搜索计划作为 prompt 中的上下文指引，
 * 而非硬性执行的批量检索任务。多租户隔离由 {@link SearchTools#localSearch} 内部处理。
 * </p>
 *
 * <h3>工具层证据收集（根治 LLM 复述 JSON 脆弱性）</h3>
 * <ul>
 *   <li>原始文档片段由 {@link SearchTools} 在 @Tool 执行时直接收集并分配 sourceId</li>
 *   <li>LLM 只输出选中的 {@code selections}（sourceId + score + relevanceRank），
 *       不复述文档内容 — 消除未转义引号、输出截断、token 浪费</li>
 *   <li>Evidence 由 Java 代码从收集结果组装，content 忠于文档原文</li>
 *   <li>LLM 输出不可用时降级：直接采用全部收集结果（按相似度分排序）</li>
 * </ul>
 */
@Service
public class LocalScoutAgent {

    private static final Logger log = LoggerFactory.getLogger(LocalScoutAgent.class);

    /** 本地知识库证据基础评分（知识库来源可信度最高） */
    private static final double LOCAL_BASE_SCORE = 0.92;

    /** 降级路径最多采用的收集结果数 */
    private static final int MAX_FALLBACK_EVIDENCE = 10;

    private final ChatClient chatClient;
    private final SearchTools searchTools;
    private final JsonParseUtils jsonUtils;
    private final String systemPrompt;
    private final String userPromptTemplate;

    /** Fallback: LLM JSON 解析失败时的空选择（触发收集结果降级） */
    private static final SelectionListWrapper FALLBACK =
        new SelectionListWrapper(Collections.emptyList());

    public LocalScoutAgent(
        @Qualifier("localScoutClient") ChatClient chatClient,
        SearchTools searchTools,
        JsonParseUtils jsonUtils,
        DynamicPromptService dynamicPromptService
    ) {
        this.chatClient = chatClient;
        this.searchTools = searchTools;
        this.jsonUtils = jsonUtils;
        String fullTemplate = dynamicPromptService.getTemplateContent("local-scout");
        PromptParts parts = PromptSplitUtils.split(fullTemplate);
        this.systemPrompt = parts.system();
        this.userPromptTemplate = parts.user();
    }

    /**
     * 执行本地知识库检索取证.
     * <p>
     * Planner 的搜索计划作为 prompt 上下文指引 LLM，LLM 通过 ToolCallingAdvisor
     * 自主决定如何调用 {@code localSearch} 工具。原始文档片段在工具层收集，
     * LLM 仅输出筛选结论（sourceId 列表）。多租户隔离由工具内部自动处理。
     * </p>
     *
     * @param query             原始研究查询
     * @param searchPlanQueries 搜索查询词列表（作为指引）
     * @param tenantId          租户 ID（传递给 prompt 供 LLM 参考）
     * @return 结构化的本地知识库证据列表
     */
    public List<Evidence> search(String query, List<String> searchPlanQueries, String tenantId) {
        if (searchPlanQueries == null || searchPlanQueries.isEmpty()) {
            log.warn("[LocalScout] 无搜索查询词，跳过本地检索");
            return List.of();
        }

        log.info("[LocalScout] 开始本地检索: query='{}', {} 个搜索指引, tenantId={}",
            query, searchPlanQueries.size(), tenantId);

        String queriesContext = String.join("\n", searchPlanQueries.stream()
            .map(q -> "- " + q).toList());

        // 开启工具层证据收集并绑定租户 ID（收集器为 ThreadLocal，@Tool 执行与本方法同线程，
        // 并发会话天然隔离——禁止用单例字段暂存 tenantId，曾导致跨租户泄漏风险）
        searchTools.beginCollection("LOCAL", tenantId);
        String raw = null;
        Map<String, CollectedSource> collected;
        try {
            raw = chatClient.prompt()
                .advisors(a -> a.param("agent", "LocalScout").param("tier", "flash")
                    .param("skipPiiMask", true))
                .system(systemPrompt)
                .user(userPromptTemplate
                    .replace("{{query}}", query)
                    .replace("{{searchPlanQueries}}", queriesContext)
                    .replace("{{tenantId}}", tenantId))
                .call()
                .content();
        } catch (Exception ex) {
            log.warn("[LocalScout] LLM 调用失败，尝试用已收集结果降级: {}", ex.getMessage());
        } finally {
            collected = searchTools.endCollection();
        }

        SelectionListWrapper result = raw != null
            ? jsonUtils.safeParse(raw, SelectionListWrapper.class, FALLBACK, "LocalScout")
            : FALLBACK;

        // 诊断：LLM 输出了 selections 但 sourceId 为 null 时，打印原始输出定位根因
        if (result.selections() != null && !result.selections().isEmpty()
            && result.selections().stream().anyMatch(s -> s.sourceId() == null)) {
            log.warn("[LocalScout] LLM 输出的 selections 包含 null sourceId，原始输出前500字符: {}",
                raw != null ? raw.substring(0, Math.min(500, raw.length())) : "null");
        }

        List<Evidence> evidences = toEvidences(result.selections(), collected);
        if (evidences.isEmpty() && !collected.isEmpty()) {
            log.warn("[LocalScout] LLM 未返回有效选择，降级采用全部收集结果: {} 条",
                collected.size());
            evidences = fallbackEvidences(collected);
        }
        log.info("[LocalScout] 检索完成: 收集 {} 条原始结果, 产出 {} 条证据",
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
                log.warn("[LocalScout] LLM 引用了不存在的 sourceId（已跳过）: {}", sel.sourceId());
                continue;
            }
            evidences.add(new Evidence(
                src.sourceId(), SourceType.LOCAL, src.url(), src.title(), src.content(),
                LOCAL_BASE_SCORE, // 本地知识库基础评分最高
                sel.relevanceRank(), src.domain(), LocalDateTime.now()));
        }
        return evidences;
    }

    /**
     * 降级路径：LLM 输出不可用时直接采用收集结果，按向量相似度分排序.
     */
    private List<Evidence> fallbackEvidences(Map<String, CollectedSource> collected) {
        List<CollectedSource> sorted = collected.values().stream()
            .sorted((a, b) -> Double.compare(b.baseScore(), a.baseScore()))
            .limit(MAX_FALLBACK_EVIDENCE)
            .toList();
        List<Evidence> evidences = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            CollectedSource src = sorted.get(i);
            evidences.add(new Evidence(
                src.sourceId(), SourceType.LOCAL, src.url(), src.title(), src.content(),
                LOCAL_BASE_SCORE, i + 1, src.domain(), LocalDateTime.now()));
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
