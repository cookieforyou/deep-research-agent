package com.example.deepresearch.agent.scout;

import com.example.deepresearch.common.model.Evidence;
import com.example.deepresearch.common.model.Evidence.SourceType;
import com.example.deepresearch.common.model.SearchResult;
import com.example.deepresearch.common.util.JsonParseUtils;
import com.example.deepresearch.common.util.PromptSplitUtils;
import com.example.deepresearch.common.util.PromptSplitUtils.PromptParts;
import com.example.deepresearch.tool.EvidenceScorer;
import com.example.deepresearch.tool.search.SearchTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网络侦察 Agent — 从搜索引擎获取外部证据.
 * <p>
 * 使用 deepseek-v4-flash (T=0.4)，平衡搜索结果覆盖率和相关性。
 * 对每条搜索查询词调用 Bocha Search API，然后由 LLM 过滤并结构化。
 * </p>
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>调用 Bocha Search API 获取原始搜索结果</li>
 *   <li>逐条规则引擎预评分（域名权威度）</li>
 *   <li>LLM 相关性过滤 + 结构化（提取关键信息为 Evidence）</li>
 *   <li>分配 Source ID（格式: WEB{序号}_{子序号}-{段落}）</li>
 * </ol>
 *
 * <h3>Source ID 分配规则</h3>
 * <pre>
 *   第 1 个搜索词的第 1 条结果 → WEB01_1
 *   第 1 个搜索词的第 2 条结果 → WEB01_2
 *   第 2 个搜索词的第 1 条结果 → WEB02_1
 * </pre>
 */
@Service
public class WebScoutAgent {

    private static final Logger log = LoggerFactory.getLogger(WebScoutAgent.class);

    private final ChatClient chatClient;
    private final JsonParseUtils jsonUtils;
    private final SearchTool searchTool;
    private final EvidenceScorer evidenceScorer;
    private final ExecutorService virtualThreadExecutor;
    private final String systemPrompt;
    private final String userPromptTemplate;

    /** Bocha API 并发限流（防止 429 Too Many Requests，降低并发减少重试） */
    private final Semaphore searchSemaphore = new Semaphore(4);

    public WebScoutAgent(
        @Qualifier("webScoutClient") ChatClient chatClient,
        JsonParseUtils jsonUtils,
        SearchTool searchTool,
        EvidenceScorer evidenceScorer,
        ExecutorService virtualThreadExecutor,
        ResourceLoader resourceLoader
    ) {
        this.chatClient = chatClient;
        this.jsonUtils = jsonUtils;
        this.searchTool = searchTool;
        this.evidenceScorer = evidenceScorer;
        this.virtualThreadExecutor = virtualThreadExecutor;
        String fullTemplate = loadPrompt(resourceLoader);
        PromptParts parts = PromptSplitUtils.split(fullTemplate);
        this.systemPrompt = parts.system();
        this.userPromptTemplate = parts.user();
    }

    /**
     * 执行网络搜索取证（并行版本）.
     * <p>
     * 所有搜索查询通过虚拟线程并行执行，墙钟时间 ≈ 最慢的单次查询。
     * </p>
     *
     * @param query            原始研究查询（提供上下文）
     * @param searchPlanQueries 规划师生成的搜索查询词列表
     * @return 结构化的网络证据列表
     */
    public List<Evidence> search(String query, List<String> searchPlanQueries) {
        if (searchPlanQueries == null || searchPlanQueries.isEmpty()) {
            log.warn("[WebScout] 无搜索查询词，跳过网络检索");
            return List.of();
        }

        log.info("[WebScout] 开始并行网络检索: {} 个查询词", searchPlanQueries.size());

        // 并行提交所有搜索任务
        AtomicInteger webIndex = new AtomicInteger(0);
        List<CompletableFuture<List<Evidence>>> futures = new ArrayList<>();

        for (String searchQuery : searchPlanQueries) {
            int index = webIndex.incrementAndGet();
            CompletableFuture<List<Evidence>> future = CompletableFuture.supplyAsync(() -> {
                log.debug("[WebScout] 搜索 WEB{}: '{}'", index, searchQuery);

                try {
                    // 步骤 1: 调用搜索引擎（Semaphore 限流，防止 429）
                    searchSemaphore.acquire();
                    List<SearchResult> rawResults;
                    try {
                        rawResults = searchTool.search(searchQuery, 10);
                    } finally {
                        searchSemaphore.release();
                    }
                    if (rawResults.isEmpty()) {
                        log.debug("[WebScout] WEB{} 无搜索结果", index);
                        return List.of();
                    }

                    // 步骤 2: LLM 相关性过滤 + 结构化提取
                    List<Evidence> processed = processSearchResults(
                        query, searchQuery, rawResults, index);

                    // 步骤 3: 规则引擎预评分
                    List<Evidence> scored = processed.stream()
                        .map(evidenceScorer::score)
                        .toList();

                    log.debug("[WebScout] WEB{} 提取 {} 条证据", index, scored.size());
                    return scored;

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("[WebScout] WEB{} 被中断: '{}'", index, searchQuery);
                    return List.of();
                } catch (Exception e) {
                    log.error("[WebScout] WEB{} 搜索失败: '{}'", index, searchQuery, e);
                    return List.of();
                }
            }, virtualThreadExecutor);
            futures.add(future);
        }

        // 等待所有并行任务完成
        List<Evidence> allEvidence = futures.stream()
            .map(CompletableFuture::join)
            .flatMap(List::stream)
            .toList();

        log.info("[WebScout] 并行网络检索完成: 共 {} 条证据", allEvidence.size());
        return allEvidence;
    }

    /**
     * 将原始搜索结果通过 LLM 处理为结构化 Evidence.
     */
    private List<Evidence> processSearchResults(
        String originalQuery, String searchQuery,
        List<SearchResult> rawResults, int webIndex
    ) {
        try {
            // 构建搜索结果文本（供 LLM 分析）
            StringBuilder resultsText = new StringBuilder();
            for (int i = 0; i < rawResults.size(); i++) {
                SearchResult r = rawResults.get(i);
                resultsText.append(String.format("[%d] %s\nURL: %s\n摘要: %s\n\n",
                    i + 1, r.title(), r.url(), r.snippet()));
            }

            // 构建 user prompt（仅包含查询数据）
            String userPrompt = userPromptTemplate
                .replace("{{query}}", originalQuery)
                .replace("{{searchQuery}}", searchQuery)
                .replace("{{results}}", resultsText.toString())
                .replace("{{webIndex}}", String.format("WEB%02d", webIndex));

            // 调用 LLM 过滤和结构化（system/user 分离），
            // .entity() 自动 JSON 解析 + 类型映射 + 自校正
            EvidenceListWrapper wrapper = chatClient.prompt()
                .advisors(a -> a.param("agent", "WebScout").param("tier", "flash").param("skipPiiMask", true))
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(EvidenceListWrapper.class);
            log.debug("[WebScout] LLM 解析完成: {} 条证据", wrapper.evidences().size());

            // 为每条 Evidence 补充 sourceType 和检索时间
            return wrapper.evidences().stream()
                .map(e -> new Evidence(
                    e.sourceId(), SourceType.WEB, e.url(), e.title(), e.content(),
                    e.score(), e.relevanceRank(), e.domain(), LocalDateTime.now()))
                .toList();

        } catch (Exception e) {
            log.error("[WebScout] 处理搜索结果异常", e);
            return List.of();
        }
    }

    /**
     * LLM 输出的证据列表包装.
     */
    public record EvidenceListWrapper(List<Evidence> evidences) {}

    private String loadPrompt(ResourceLoader loader) {
        try {
            Resource resource = loader.getResource("classpath:prompts/web-scout.st");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[WebScout] 无法加载 prompt 模板", e);
            return """
                你是网络取证专家。从以下搜索结果中提取与研究问题相关的证据。

                研究问题: {{query}}
                搜索词: {{searchQuery}}
                搜索结果:
                {{results}}

                返回 JSON: {"evidences": [{"sourceId":"{{webIndex}}_1","url":"...","title":"...","content":"...","score":0.7,"relevanceRank":1,"domain":"..."}]}
                """;
        }
    }
}
