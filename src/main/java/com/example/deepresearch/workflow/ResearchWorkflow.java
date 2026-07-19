package com.example.deepresearch.workflow;

import com.example.deepresearch.agent.analyst.AnalystAgent;
import com.example.deepresearch.agent.intent.IntentRouterAgent;
import com.example.deepresearch.agent.intent.IntentRouterAgent.RouteResult;
import com.example.deepresearch.agent.planner.PlannerAgent;
import com.example.deepresearch.agent.scout.LocalScoutAgent;
import com.example.deepresearch.agent.scout.WebScoutAgent;
import com.example.deepresearch.agent.writer.WriterAgent;
import com.example.deepresearch.api.dto.ProgressEvent;
import com.example.deepresearch.api.dto.ProgressEvent.ResearchStage;
import com.example.deepresearch.common.config.DeepResearchProperties;
import com.example.deepresearch.common.model.*;
import com.example.deepresearch.common.observability.WorkflowTracingHelper;
import com.example.deepresearch.memory.MemoryManager;
import com.example.deepresearch.rag.CitationValidator;
import com.example.deepresearch.service.ProgressEventPublisher;
import com.example.deepresearch.tool.EvidenceDeduplicationService;
import com.example.deepresearch.workflow.state.ResearchState;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import com.example.deepresearch.common.util.PromptSplitUtils;
import com.example.deepresearch.common.util.PromptSplitUtils.PromptParts;
import com.example.deepresearch.security.TenantContext;
import com.example.deepresearch.service.DynamicPromptService;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 研究工作流 — LangGraph4j StateGraph 编排核心（优化版）.
 * <p>
 * 将研究流程编译为可执行的 LangGraph4j 状态图。
 * 相比旧版，移除了 Judge Agent（代码级去重替代）和 Reflect 循环，
 * WebScout 改为全并行，目标总耗时 &lt; 2 分钟。
 * </p>
 *
 * <h3>工作流拓扑</h3>
 * <pre>
 * START → intent_route ──[direct]──→ direct_answer → END
 *              │
 *              └──[research]──→ plan → dual_search → filter → analyze → write → END
 * </pre>
 *
 * <h3>节点映射</h3>
 * <table>
 *   <tr><th>节点</th><th>Agent</th><th>说明</th></tr>
 *   <tr><td>intent_route</td><td>IntentRouterAgent</td><td>意图分类</td></tr>
 *   <tr><td>direct_answer</td><td>ChatClient 直接调用</td><td>简单回答</td></tr>
 *   <tr><td>plan</td><td>PlannerAgent</td><td>任务拆解+搜索计划</td></tr>
 *   <tr><td>dual_search</td><td>WebScoutAgent + LocalScoutAgent</td><td>并行双源检索</td></tr>
 *   <tr><td>filter</td><td>EvidenceDeduplicationService</td><td>代码级去重过滤</td></tr>
 *   <tr><td>analyze</td><td>AnalystAgent</td><td>结论+完备性评估</td></tr>
 *   <tr><td>write</td><td>WriterAgent + CitationValidator</td><td>撰写+引用校验</td></tr>
 * </table>
 */
@Component
public class ResearchWorkflow {

    private static final Logger log = LoggerFactory.getLogger(ResearchWorkflow.class);

    private final IntentRouterAgent intentRouter;
    private final PlannerAgent planner;
    private final WebScoutAgent webScout;
    private final LocalScoutAgent localScout;
    private final AnalystAgent analyst;
    private final WriterAgent writer;
    private final EvidenceDeduplicationService dedupService;
    private final CitationValidator citationValidator;
    private final ProgressEventPublisher progressPublisher;
    private final ExecutorService virtualThreadExecutor;
    private final DeepResearchProperties properties;
    private final ChatClient directAnswerClient;
    private final DynamicPromptService dynamicPromptService;
    private final MemoryManager memoryManager;
    private final WorkflowTracingHelper tracingHelper;

    /** 编译后的可执行工作流 */
    private volatile CompiledGraph<ResearchState> compiledGraph;

    public ResearchWorkflow(
        IntentRouterAgent intentRouter, PlannerAgent planner,
        WebScoutAgent webScout, LocalScoutAgent localScout,
        AnalystAgent analyst, WriterAgent writer,
        EvidenceDeduplicationService dedupService,
        CitationValidator citationValidator,
        ProgressEventPublisher progressPublisher,
        ExecutorService virtualThreadExecutor,
        DeepResearchProperties properties,
        @org.springframework.beans.factory.annotation.Qualifier("directAnswerClient")
        ChatClient directAnswerClient,
        DynamicPromptService dynamicPromptService,
        MemoryManager memoryManager,
        WorkflowTracingHelper tracingHelper
    ) {
        this.intentRouter = intentRouter;
        this.planner = planner;
        this.webScout = webScout;
        this.localScout = localScout;
        this.analyst = analyst;
        this.writer = writer;
        this.dedupService = dedupService;
        this.citationValidator = citationValidator;
        this.progressPublisher = progressPublisher;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.properties = properties;
        this.directAnswerClient = directAnswerClient;
        this.dynamicPromptService = dynamicPromptService;
        this.memoryManager = memoryManager;
        this.tracingHelper = tracingHelper;
    }

    // =========================== 工作流编译 ===========================

    /**
     * 获取编译后的工作流（懒加载 + 双重检查锁）.
     */
    public CompiledGraph<ResearchState> getCompiledGraph() throws Exception {
        if (compiledGraph == null) {
            synchronized (this) {
                if (compiledGraph == null) {
                    compiledGraph = buildGraph().compile();
                    log.info("[ResearchWorkflow] 工作流编译完成");
                }
            }
        }
        return compiledGraph;
    }

    /**
     * 构建 LangGraph4j StateGraph.
     */
    private StateGraph<ResearchState> buildGraph() throws org.bsc.langgraph4j.GraphStateException {
        return new StateGraph<>(ResearchState.SCHEMA, ResearchState::new)
            // === 注册节点 ===
            .addNode("intent_route",  intentRouteNode())
            .addNode("direct_answer", directAnswerNode())
            .addNode("plan",          planNode())
            .addNode("dual_search",   dualSearchNode())
            .addNode("filter",        dedupFilterNode())
            .addNode("analyze",       analyzeNode())
            .addNode("write",         writeNode())

            // === 注册边 ===
            // 入口 → 意图路由
            .addEdge(START, "intent_route")

            // 意图路由：条件分支（direct vs research）
            .addConditionalEdges("intent_route", intentConditionalRouter(),
                Map.of("direct",   "direct_answer",
                       "research", "plan"))
            .addEdge("direct_answer", END)

            // 研究流程主线（单轮直线 DAG，无循环）
            .addEdge("plan", "dual_search")
            .addEdge("dual_search", "filter")
            .addEdge("filter", "analyze")
            .addEdge("analyze", "write")
            .addEdge("write", END);
    }

    // =========================== 上下文传播 ===========================

    /**
     * 在 CompletableFuture 子虚拟线程中恢复父线程上下文.
     * <p>
     * {@link CompletableFuture#supplyAsync} 创建新的虚拟线程，
     * ThreadLocal（TenantContext / MDC）不会自动传播。
     * 每个工作流节点在执行 Agent 调用前应调用此方法恢复上下文。
     * </p>
     */
    private void restoreContext(ResearchState state) {
        if (state.tenantId() != null) {
            TenantContext.setCurrentTenant(state.tenantId());
        }
        if (state.userId() != null) {
            TenantContext.setCurrentUser(state.userId());
        }
        // MDC traceId 由 WorkflowTracingHelper.observe() 在内部设置，此处无需重复
    }

    // =========================== 节点实现 ===========================

    /**
     * 节点 0: 意图路由.
     * <p>
     * 判断查询属于 Direct（直接回答）还是 Research（深度研究）。
     * 输出: intent = "direct" | "research"
     * </p>
     */
    private AsyncNodeAction<ResearchState> intentRouteNode() {
        return state -> CompletableFuture.supplyAsync(() -> {
            restoreContext(state);
            String sessionId = state.sessionId();
            return tracingHelper.observe("workflow.intent_route",
                Map.of("sessionId", sessionId),
                () -> {
                    progressPublisher.publish(sessionId,
                        ProgressEvent.started(sessionId, ResearchStage.INTENT_ROUTING,
                            "intent_route", "正在判断查询意图..."));

                    RouteResult result = intentRouter.route(state.query());

                    progressPublisher.publish(sessionId,
                        ProgressEvent.completed(sessionId, ResearchStage.INTENT_ROUTING,
                            "intent_route", "意图判断完成: " + result.intent()));

                    return Map.of(
                        "intent", result.intent(),
                        "messages", List.of("意图: " + result.intent() + ", 理由: " + result.reasoning())
                    );
                });
        }, virtualThreadExecutor);
    }

    /**
     * 节点 D: 直接回答（Direct Answer 支线）.
     * <p>
     * 对简单查询直接调用 LLM 生成简短回答，不走完整研究流程。
     * 使用 {@code prompts/direct-answer.st} Prompt 模板。
     * </p>
     */
    private AsyncNodeAction<ResearchState> directAnswerNode() {
        return state -> CompletableFuture.supplyAsync(() -> {
            restoreContext(state);
            String sessionId = state.sessionId();
            return tracingHelper.observe("workflow.direct_answer",
                Map.of("sessionId", sessionId),
                () -> {
                    progressPublisher.publish(sessionId,
                        ProgressEvent.started(sessionId, ResearchStage.PLANNING,
                            "direct_answer", "正在生成直接回答..."));

                    try {
                        // 加载 Direct Answer Prompt 模板（DB优先 + classpath兜底）
                        String promptTemplate = dynamicPromptService.getTemplateContent("direct-answer");

                        // 分离 system/user（架构级注入防护）
                        PromptParts parts = PromptSplitUtils.split(promptTemplate);
                        String userPrompt = parts.user().replace("{{query}}", state.query());

                        // 调用 LLM（system/user 分离，AgentBundle 全链 Advisor：
                        // PII 脱敏/限流/护栏/Token 追踪/审计——禁止在此现场 build ChatClient，
                        // 曾因只挂 TokenTrackingAdvisor 导致用户 PII 原文直发 DeepSeek API）
                        String directAnswer = directAnswerClient
                            .prompt()
                            .advisors(a -> a.param("agent", "DirectAnswer").param("tier", "flash"))
                            .system(parts.system())
                            .user(userPrompt)
                            .call()
                            .content();

                        // 截断过长回答（Direct 模式限制简洁性）
                        if (directAnswer != null && directAnswer.length() > 2000) {
                            directAnswer = directAnswer.substring(0, 2000) + "\n\n...（回答已截断）";
                        }

                        progressPublisher.publish(sessionId,
                            ProgressEvent.completed(sessionId, ResearchStage.COMPLETED,
                                "direct_answer", "回答生成完成"));

                        return Map.of("directAnswer",
                            directAnswer != null ? directAnswer : "抱歉，暂时无法回答。" );

                    } catch (Exception e) {
                        log.error("[direct_answer] LLM 调用失败", e);
                        return Map.of("directAnswer",
                            "抱歉，生成回答时出现错误。请重试或使用深度研究模式。");
                    }
                });
        }, virtualThreadExecutor);
    }

    /**
     * 节点 1: 任务规划.
     * <p>
     * 将查询拆解为子问题 + 报告大纲 + 搜索计划。
     * 输出: subQuestions, reportOutline, searchPlan
     * </p>
     */
    private AsyncNodeAction<ResearchState> planNode() {
        return state -> CompletableFuture.supplyAsync(() -> {
            restoreContext(state);
            String sessionId = state.sessionId();
            return tracingHelper.observe("workflow.plan",
                Map.of("sessionId", sessionId),
                () -> {
                    progressPublisher.publish(sessionId,
                        ProgressEvent.started(sessionId, ResearchStage.PLANNING,
                            "plan", "正在拆解研究问题、规划搜索路径..."));

                    PlanResult result = planner.plan(state.query(), state.memoryContext());

                    // 提取搜索查询词列表（供 dual_search 使用）
                    List<String> queries = result.searchPlans().stream()
                        .sorted(SearchPlan::compareByPriority)
                        .map(SearchPlan::query)
                        .toList();

                    progressPublisher.publish(sessionId,
                        ProgressEvent.completed(sessionId, ResearchStage.PLANNING,
                            "plan", String.format("规划完成: %d 个子问题, %d 个搜索计划",
                                result.subQuestions().size(), result.searchPlans().size())));

                    return Map.of(
                        "subQuestions", result.subQuestions(),
                        "reportOutline", result.reportOutline(),
                        "searchPlan", result.searchPlans(),
                        "messages", List.of("大纲: " + result.reportOutline())
                    );
                });
        }, virtualThreadExecutor);
    }

    /**
     * 节点 2: 双源并行检索.
     * <p>
     * 使用 Virtual Threads 并行执行 Web Scout 和 Local Scout 检索。
     * 输出: webEvidence, localEvidence
     * </p>
     */
    private AsyncNodeAction<ResearchState> dualSearchNode() {
        return state -> CompletableFuture.supplyAsync(() -> {
            restoreContext(state);
            String sessionId = state.sessionId();
            return tracingHelper.observe("workflow.dual_search",
                Map.of("sessionId", sessionId),
                () -> {
                    // 单轮模式：始终使用 Planner 的搜索计划
                    List<String> queries = state.searchPlan().stream()
                        .sorted(SearchPlan::compareByPriority)
                        .map(SearchPlan::query)
                        .filter(q -> q != null && !q.isEmpty())
                        .collect(Collectors.toList());

                    log.info("[dual_search] 开始双源检索: {} 个查询", queries.size());

                    // 并行执行 Web 和 Local 检索（CompletableFuture + Virtual Threads）
                    try {
                        // 捕获上下文以跨虚拟线程传播
                        String parentTenantId = state.tenantId();
                        String parentUserId = state.userId();
                        String parentTraceId = MDC.get("traceId");

                        // Web 搜索子任务
                        CompletableFuture<List<Evidence>> webFuture = CompletableFuture.supplyAsync(() -> {
                            // 恢复跨虚拟线程的上下文
                            TenantContext.setCurrentTenant(parentTenantId);
                            TenantContext.setCurrentUser(parentUserId);
                            if (parentTraceId != null) MDC.put("traceId", parentTraceId);

                            progressPublisher.publish(sessionId,
                                ProgressEvent.searching(sessionId, "Web", 0, queries.size()));
                            List<Evidence> results = webScout.search(state.query(), queries);

                            // 进度反馈
                            for (int i = 0; i < queries.size(); i++) {
                                final int idx = i;
                                progressPublisher.publish(sessionId,
                                    ProgressEvent.searching(sessionId, "Web", idx + 1, queries.size()));
                            }
                            return results;
                        }, virtualThreadExecutor);

                        // Local 检索子任务
                        CompletableFuture<List<Evidence>> localFuture = CompletableFuture.supplyAsync(() -> {
                            // 恢复跨虚拟线程的上下文
                            TenantContext.setCurrentTenant(parentTenantId);
                            TenantContext.setCurrentUser(parentUserId);
                            if (parentTraceId != null) MDC.put("traceId", parentTraceId);

                            progressPublisher.publish(sessionId,
                                ProgressEvent.searching(sessionId, "Local", 0, queries.size()));
                            List<Evidence> results = localScout.search(
                                state.query(), queries, state.tenantId());

                            for (int i = 0; i < queries.size(); i++) {
                                final int idx = i;
                                progressPublisher.publish(sessionId,
                                    ProgressEvent.searching(sessionId, "Local", idx + 1, queries.size()));
                            }
                            return results;
                        }, virtualThreadExecutor);

                        // 等待两个任务完成
                        CompletableFuture.allOf(webFuture, localFuture).join();

                        List<Evidence> webEvidence = webFuture.join();
                        List<Evidence> localEvidence = localFuture.join();

                        // SSE 通知：双源检索降级状态
                        if (webEvidence.isEmpty() && !localEvidence.isEmpty()) {
                            // 仅网络搜索不可用 → 纯 Local RAG 模式
                            progressPublisher.publish(sessionId,
                                ProgressEvent.completed(sessionId, ResearchStage.SEARCH_FALLBACK,
                                    "dual_search", "网络搜索暂时不可用，研究报告仅基于本地知识库生成"));
                        } else if (webEvidence.isEmpty() && localEvidence.isEmpty()) {
                            // 双源均不可用 → 报告仅基于 LLM 自身知识
                            progressPublisher.publish(sessionId,
                                ProgressEvent.completed(sessionId, ResearchStage.SEARCH_FALLBACK,
                                    "dual_search", "网络搜索和本地知识库均暂时不可用，报告将基于模型自身知识生成，建议稍后重试"));
                        }

                        progressPublisher.publish(sessionId,
                            ProgressEvent.completed(sessionId, ResearchStage.WEB_SEARCHING,
                                "dual_search", String.format("检索完成: WEB=%d, LOCAL=%d",
                                    webEvidence.size(), localEvidence.size())));

                        return Map.of(
                            "webEvidence", webEvidence,
                            "localEvidence", localEvidence
                        );

                    } catch (Exception e) {
                        log.error("[dual_search] 双源检索异常", e);
                        return Map.of(
                            "error", "双源检索失败: " + e.getMessage()
                        );
                    }
                });
        }, virtualThreadExecutor);
    }

    /**
     * 节点 3: 证据去重过滤.
     * <p>
     * 合并双源证据，代码级去重过滤（替代 LLM Judge）。
     * 输出: evidencePool, sourceIndex
     * </p>
     */
    private AsyncNodeAction<ResearchState> dedupFilterNode() {
        return state -> CompletableFuture.supplyAsync(() -> {
            restoreContext(state);
            String sessionId = state.sessionId();
            return tracingHelper.observe("workflow.filter",
                Map.of("sessionId", sessionId),
                () -> {
                    progressPublisher.publish(sessionId,
                        ProgressEvent.started(sessionId, ResearchStage.JUDGING,
                            "filter", "正在去重过滤证据..."));

                    EvidenceDeduplicationService.DedupResult result = dedupService.deduplicate(
                        state.webEvidence(), state.localEvidence());

                    if (result.dedupedEvidence().isEmpty()) {
                        // 零证据熔断预警：证据池为空，报告将失去检索证据支撑
                        log.warn("[filter] 证据池为空 (web={}, local={})，报告将基于模型自身知识生成",
                            state.webEvidence().size(), state.localEvidence().size());
                        progressPublisher.publish(sessionId,
                            ProgressEvent.completed(sessionId, ResearchStage.SEARCH_FALLBACK,
                                "filter", "警告: 未获得任何有效证据，报告将基于模型自身知识生成（降级模式），可信度受限"));
                    }

                    progressPublisher.publish(sessionId,
                        ProgressEvent.completed(sessionId, ResearchStage.JUDGING,
                            "filter", String.format("去重完成: %d 条有效证据",
                                result.dedupedEvidence().size())));

                    return Map.of(
                        "evidencePool", result.dedupedEvidence(),
                        "sourceIndex", result.sourceIndex()
                    );
                });
        }, virtualThreadExecutor);
    }

    /**
     * 节点 4: 分析归纳.
     * <p>
     * 基于证据形成结论，评估完备性，识别信息缺口。
     * 输出: findings, needsMoreResearch, missingGaps
     * </p>
     */
    private AsyncNodeAction<ResearchState> analyzeNode() {
        return state -> CompletableFuture.supplyAsync(() -> {
            restoreContext(state);
            String sessionId = state.sessionId();
            return tracingHelper.observe("workflow.analyze",
                Map.of("sessionId", sessionId),
                () -> {
                    progressPublisher.publish(sessionId,
                        ProgressEvent.started(sessionId, ResearchStage.ANALYZING,
                            "analyze", "正在分析证据、形成结论..."));

                    AnalysisResult result = analyst.analyze(
                        state.query(), state.subQuestions(), state.evidencePool());

                    progressPublisher.publish(sessionId,
                        ProgressEvent.completed(sessionId, ResearchStage.ANALYZING,
                            "analyze", String.format("分析完成: %d 个结论, 完备性=%.0f%%",
                                result.findings().size(),
                                result.completenessScore() * 100)));

                    // 使用 HashMap 而非 Map.of()，因为 Map.of() 不接受 null 值
                    // 当 LLM JSON 解析部分失败时，某些字段可能为 null
                    Map<String, Object> analyzeOutput = new HashMap<>();
                    analyzeOutput.put("findings",
                        result.findings() != null ? result.findings() : List.of());
                    analyzeOutput.put("needsMoreResearch", result.needsMoreResearch());
                    analyzeOutput.put("missingGaps",
                        result.missingGaps() != null ? result.missingGaps() : List.of());
                    return analyzeOutput;
                });
        }, virtualThreadExecutor);
    }

    /**
     * 节点 5: 报告撰写.
     * <p>
     * 整合所有结论和证据，生成带引用的深度 Markdown 研报。
     * 写完后再经过 CitationValidator 校验引用合法性。
     * 输出: finalReport
     * </p>
     */
    private AsyncNodeAction<ResearchState> writeNode() {
        return state -> CompletableFuture.supplyAsync(() -> {
            restoreContext(state);
            String sessionId = state.sessionId();
            return tracingHelper.observe("workflow.write",
                Map.of("sessionId", sessionId),
                () -> {
                    progressPublisher.publish(sessionId,
                        ProgressEvent.started(sessionId, ResearchStage.WRITING,
                            "write", "正在撰写深度研究报告..."));

                    // 步骤 1: LLM 生成报告
                    WriteResult result = writer.write(
                        state.query(), state.reportOutline(),
                        state.findings(), state.evidencePool(), state.sourceIndex());

                    // 步骤 2: 引用合法性校验
                    CitationValidator.ValidationResult validation =
                        citationValidator.validate(result.reportContent(), state.sourceIndex());

                    // 步骤 3: 正文引用标记 → 可点击链接（[WEB12] → [WEB12](url)）
                    String linkedReport = citationValidator.linkifyBodyCitations(
                        validation.cleanedReport(), state.evidencePool());

                    // 步骤 4: 追加参考资料列表（从 evidencePool 渲染标题+可点击链接）
                    String finalReport = citationValidator.appendReferenceList(
                        linkedReport, state.evidencePool());

                    // 零证据降级：报告头部注入免责声明，避免用户误信无证据支撑的内容
                    if (state.evidencePool().isEmpty()) {
                        finalReport = "> ⚠️ **降级提示**：本次研究未获得任何检索证据支撑，"
                            + "以下内容完全基于模型自身知识生成，数据与结论可能过时或不准确，请谨慎参考。\n\n"
                            + finalReport;
                    }

                    // 检查字数
                    if (result.wordCount() < properties.workflow().minReportWords()) {
                        log.warn("[write] 报告字数 {} 低于最低要求 {}",
                            result.wordCount(), properties.workflow().minReportWords());
                    }

                    // 步骤 5: 从报告中提取实际引用的 sourceId 列表（去重保序）
                    // 避免下游重复扫描报告全文
                    LinkedHashSet<String> citedIds = new LinkedHashSet<>();
                    Matcher citationMatcher = Pattern.compile("\\[(WEB|LOCAL)\\d+\\]")
                            .matcher(finalReport);
                    while (citationMatcher.find()) {
                        citedIds.add(citationMatcher.group()
                            .replace("[", "").replace("]", ""));
                    }

                    progressPublisher.publish(sessionId,
                        ProgressEvent.completed(sessionId, ResearchStage.COMPLETED,
                            "write", String.format("报告完成: %d 字, %d 合法引用",
                                result.wordCount(), citedIds.size())));

                    return Map.of(
                        "finalReport", finalReport,
                        "citedSourceIds", new ArrayList<>(citedIds),
                        "messages", List.of("报告生成完成: " + result.wordCount() + " 字")
                    );
                });
        }, virtualThreadExecutor);
    }

    // =========================== 条件路由 ===========================

    /**
     * 意图条件路由 → direct_answer 还是 plan.
     */
    private AsyncEdgeAction<ResearchState> intentConditionalRouter() {
        return state -> CompletableFuture.completedFuture(
            state.isDeepResearch() ? "research" : "direct");
    }

}
