package com.example.deepresearch.agent.scout;

import com.example.deepresearch.agent.tool.SearchTools;
import com.example.deepresearch.common.model.Evidence;
import com.example.deepresearch.common.model.Evidence.SourceType;
import com.example.deepresearch.common.util.JsonParseUtils;
import com.example.deepresearch.common.util.PromptSplitUtils;
import com.example.deepresearch.common.util.PromptSplitUtils.PromptParts;
import com.example.deepresearch.service.DynamicPromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 网络侦察 Agent — LLM 自主调用 webSearch 工具获取外部证据（Spring AI 2.0 @Tool 模式）.
 * <p>
 * 使用 deepseek-v4-flash (T=0.4)，通过 ToolCallingAdvisor 让 LLM 自主决定
 * 搜索关键词、时机和轮次。Planner 的搜索计划作为 prompt 中的上下文指引，
 * 而非硬性执行的批量搜索任务。
 * </p>
 *
 * <h3>重构要点（Spring AI 2.0）</h3>
 * <ul>
 *   <li>搜索操作通过 {@link SearchTools#webSearch} (@Tool 注解) 暴露给 LLM</li>
 *   <li>LLM 通过 ToolCallingAdvisor 自主调用工具，无需手动编排搜索流程</li>
 *   <li>LLM 输出通过 {@code .content()} + {@link JsonParseUtils#safeParse} 解析
 *       （单次调用，解析失败时在同一份文本上修复，不重打 LLM）</li>
 *   <li>search() 方法签名不变，LangGraph4j 工作流无需修改</li>
 * </ul>
 */
@Service
public class WebScoutAgent {

    private static final Logger log = LoggerFactory.getLogger(WebScoutAgent.class);

    private final ChatClient chatClient;
    private final SearchTools searchTools;
    private final JsonParseUtils jsonUtils;
    private final String systemPrompt;
    private final String userPromptTemplate;

    /** Fallback: LLM JSON 解析失败时返回空证据列表（不影响下游） */
    private static final EvidenceListWrapper FALLBACK = new EvidenceListWrapper(Collections.emptyList());

    public WebScoutAgent(
        @Qualifier("webScoutClient") ChatClient chatClient,
        SearchTools searchTools,
        JsonParseUtils jsonUtils,
        DynamicPromptService dynamicPromptService
    ) {
        this.chatClient = chatClient;
        this.searchTools = searchTools;
        this.jsonUtils = jsonUtils;
        String fullTemplate = dynamicPromptService.getTemplateContent("web-scout");
        PromptParts parts = PromptSplitUtils.split(fullTemplate);
        this.systemPrompt = parts.system();
        this.userPromptTemplate = parts.user();
    }

    /**
     * 执行网络搜索取证.
     * <p>
     * Planner 的搜索计划作为 prompt 上下文指引 LLM，LLM 通过 ToolCallingAdvisor
     * 自主决定如何调用 {@code webSearch} 工具、以什么关键词、搜多少条。
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

        try {
            // LLM 自主决定调用 webSearch 工具的时机和参数
            // ToolCallingAdvisor 自动处理工具调用循环
            // 注意：先取原始文本再用 safeParse 解析（内含 JSON 修复），而非 .entity()。
            // .entity() 失败时原始 content 不可恢复，只能重打一次 LLM+全部搜索（成本/延迟翻倍），
            // 且第二次输出大概率复现同类 JSON 缺陷。Prompt 模板已内置 JSON 格式说明。
            String raw = chatClient.prompt()
                .advisors(a -> a.param("agent", "WebScout").param("tier", "flash")
                    .param("skipPiiMask", true))
                .system(systemPrompt)
                .user(userPromptTemplate
                    .replace("{{query}}", query)
                    .replace("{{searchPlanQueries}}", queriesContext))
                //.tools(searchTools)  // searchTools 已在 AgentBundle 中通过 defaultTools 注册，这里无需注入
                .call()
                .content();

            EvidenceListWrapper result = jsonUtils.safeParse(raw,
                EvidenceListWrapper.class, FALLBACK, "WebScout");
            log.debug("[WebScout] LLM 解析完成: {} 条证据", result.evidences().size());

            return toEvidences(result);

        } catch (Exception ex) {
            log.error("[WebScout] 网络检索失败", ex);
            return List.of();
        }
    }

    /** 将 LLM 输出的简化 DTO 转换为领域模型 Evidence. */
    private List<Evidence> toEvidences(EvidenceListWrapper wrapper) {
        return wrapper.evidences().stream()
            .map(e -> new Evidence(
                e.sourceId(), SourceType.WEB, e.url(), e.title(), e.content(),
                e.score(), e.relevanceRank(), e.domain() != null ? e.domain() : "unknown",
                LocalDateTime.now()))
            .toList();
    }

    /**
     * LLM 输出的证据列表包装 — 使用简化 DTO 避免 LLM 生成不需要的字段（如 retrievedAt）.
     */
    public record EvidenceListWrapper(List<ScoutEvidence> evidences) {}

    /** 供 LLM 输出的简化证据 DTO（不含 retrievedAt，由 Java 代码设置） */
    record ScoutEvidence(
        String sourceId,
        String title,
        String url,
        String content,
        double score,
        int relevanceRank,
        String domain
    ) {}

}
