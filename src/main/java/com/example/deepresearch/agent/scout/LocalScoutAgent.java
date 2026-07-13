package com.example.deepresearch.agent.scout;

import com.example.deepresearch.agent.tool.SearchTools;
import com.example.deepresearch.common.model.Evidence;
import com.example.deepresearch.common.model.Evidence.SourceType;
import com.example.deepresearch.common.util.PromptSplitUtils;
import com.example.deepresearch.common.util.PromptSplitUtils.PromptParts;
import com.example.deepresearch.service.DynamicPromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地知识库侦察 Agent — LLM 自主调用 localSearch 工具检索内部文档（Spring AI 2.0 @Tool 模式）.
 * <p>
 * 使用 deepseek-v4-flash (T=0.4)，通过 ToolCallingAdvisor 让 LLM 自主决定
 * 检索关键词和时机。Planner 的搜索计划作为 prompt 中的上下文指引，
 * 而非硬性执行的批量检索任务。多租户隔离由 {@link SearchTools#localSearch} 内部处理。
 * </p>
 *
 * <h3>重构要点（Spring AI 2.0）</h3>
 * <ul>
 *   <li>检索操作通过 {@link SearchTools#localSearch} (@Tool 注解) 暴露给 LLM</li>
 *   <li>LLM 通过 ToolCallingAdvisor 自主调用工具，无需手动编排检索流程</li>
 *   <li>LLM 输出使用 {@code .entity()} 自动解析（Round 1 已完成）</li>
 *   <li>search() 方法签名不变，LangGraph4j 工作流无需修改</li>
 * </ul>
 */
@Service
public class LocalScoutAgent {

    private static final Logger log = LoggerFactory.getLogger(LocalScoutAgent.class);

    private final ChatClient chatClient;
    private final SearchTools searchTools;
    private final String systemPrompt;
    private final String userPromptTemplate;

    public LocalScoutAgent(
        @Qualifier("localScoutClient") ChatClient chatClient,
        SearchTools searchTools,
        DynamicPromptService dynamicPromptService
    ) {
        this.chatClient = chatClient;
        this.searchTools = searchTools;
        String fullTemplate = dynamicPromptService.getTemplateContent("local-scout");
        PromptParts parts = PromptSplitUtils.split(fullTemplate);
        this.systemPrompt = parts.system();
        this.userPromptTemplate = parts.user();
    }

    /**
     * 执行本地知识库检索取证.
     * <p>
     * Planner 的搜索计划作为 prompt 上下文指引 LLM，LLM 通过 ToolCallingAdvisor
     * 自主决定如何调用 {@code localSearch} 工具。多租户隔离由工具内部自动处理。
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

        try {
            // LLM 自主决定调用 localSearch 工具的时机和参数
            // ToolCallingAdvisor 自动处理工具调用循环
            EvidenceListWrapper result = chatClient.prompt()
                .advisors(a -> a.param("agent", "LocalScout").param("tier", "flash")
                    .param("skipPiiMask", true))
                .system(systemPrompt)
                .user(userPromptTemplate
                    .replace("{{query}}", query)
                    .replace("{{searchPlanQueries}}", queriesContext)
                    .replace("{{tenantId}}", tenantId))
                .tools(searchTools)  // 注入 @Tool 工具
                .call()
                .entity(EvidenceListWrapper.class);  // Round 1 已替换 safeParse

            log.debug("[LocalScout] LLM 解析完成: {} 条证据", result.evidences().size());

            return result.evidences().stream()
                .map(e -> new Evidence(
                    e.sourceId(), SourceType.LOCAL, e.url(), e.title(), e.content(),
                    0.92,  // 本地知识库基础评分最高
                    e.relevanceRank(), e.domain(), LocalDateTime.now()))
                .toList();

        } catch (Exception e) {
            log.error("[LocalScout] 检索异常", e);
            return List.of();
        }
    }

    public record EvidenceListWrapper(List<Evidence> evidences) {}

}
