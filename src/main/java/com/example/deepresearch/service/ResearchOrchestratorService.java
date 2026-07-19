package com.example.deepresearch.service;

import com.example.deepresearch.agent.eval.EvalAgent;
import com.example.deepresearch.agent.profile.PreferenceExtractorAgent;
import com.example.deepresearch.api.dto.ProgressEvent;
import com.example.deepresearch.api.dto.ResearchResponse;
import com.example.deepresearch.api.dto.ProgressEvent.ResearchStage;
import com.example.deepresearch.cache.SemanticCacheService;
import com.example.deepresearch.cache.SemanticCacheService.CacheResult;
import com.example.deepresearch.common.config.DeepResearchProperties;
import com.example.deepresearch.common.model.EvalResult;
import com.example.deepresearch.common.model.Evidence;
import com.example.deepresearch.common.model.Finding;
import com.example.deepresearch.common.observability.BusinessMetrics;
import com.example.deepresearch.common.observability.TokenUsageTracker;
import com.example.deepresearch.memory.MemoryManager;
import com.example.deepresearch.security.PiiMaskingService;
import com.example.deepresearch.security.TenantContext;
import com.example.deepresearch.workflow.ResearchWorkflow;
import com.example.deepresearch.workflow.state.ResearchState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.CompiledGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * 研究编排服务 — 对外统一的入口服务.
 * <p>
 * 负责：
 * <ul>
 *   <li>创建研究会话（生成 sessionId）</li>
 *   <li>构造初始 ResearchState</li>
 *   <li>异步启动 LangGraph4j 工作流</li>
 *   <li>管理工作流完成后处理</li>
 *   <li>通过 {@link ProgressEventPublisher} 推送进度</li>
 * </ul>
 * </p>
 *
 * <h3>会话生命周期</h3>
 * <pre>
 * POST /api/research → createSession → startWorkflow(async) → SSE push → complete/error
 * </pre>
 */
@Service
public class ResearchOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ResearchOrchestratorService.class);

    private final ResearchWorkflow researchWorkflow;
    private final ProgressEventPublisher progressPublisher;
    private final ExecutorService virtualThreadExecutor;
    private final MemoryManager memoryManager;
    private final SemanticCacheService semanticCache;
    private final EvalAgent evalAgent;
    private final PreferenceExtractorAgent preferenceExtractor;
    private final ObjectMapper objectMapper;
    private final DeepResearchProperties properties;
    private final PiiMaskingService piiMaskingService;
    private final BusinessMetrics businessMetrics;
    private final TokenUsageTracker tokenUsageTracker;

    /** 活跃会话状态缓存（sessionId → 最新 ResearchState） */
    private final ConcurrentHashMap<String, ResearchState> activeSessions = new ConcurrentHashMap<>();

    /** 评估分数滑动窗口（按租户隔离，避免多租户评分混合导致误报） */
    private final ConcurrentHashMap<String, Deque<Double>> tenantEvalWindows = new ConcurrentHashMap<>();

    public ResearchOrchestratorService(
        ResearchWorkflow researchWorkflow,
        ProgressEventPublisher progressPublisher,
        ExecutorService virtualThreadExecutor,
        MemoryManager memoryManager,
        SemanticCacheService semanticCache,
        EvalAgent evalAgent,
        PreferenceExtractorAgent preferenceExtractor,
        ObjectMapper objectMapper,
        DeepResearchProperties properties,
        PiiMaskingService piiMaskingService,
        BusinessMetrics businessMetrics,
        TokenUsageTracker tokenUsageTracker
    ) {
        this.researchWorkflow = researchWorkflow;
        this.progressPublisher = progressPublisher;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.memoryManager = memoryManager;
        this.semanticCache = semanticCache;
        this.evalAgent = evalAgent;
        this.preferenceExtractor = preferenceExtractor;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.piiMaskingService = piiMaskingService;
        this.businessMetrics = businessMetrics;
        this.tokenUsageTracker = tokenUsageTracker;
    }

    /**
     * 启动研究任务.
     * <p>
     * 创建会话 → 异步启动工作流 → 返回 sessionId 供客户端订阅 SSE。
     * </p>
     *
     * @param query       研究查询文本
     * @param deepResearch 是否深度研究模式
     * @param userId      用户 ID（由 Controller 从 JWT 提取）
     * @param tenantId    租户 ID（由 Controller 从 JWT 提取）
     * @return 会话信息（含 sessionId 和流订阅 URL）
     */
    public ResearchResponse startResearch(String query, boolean deepResearch,
                                           String userId, String tenantId) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);

        log.info("[Orchestrator] 启动研究: sessionId={}, query='{}', userId={}, tenantId={}",
            sessionId, piiMaskingService.tokenizeToString(query), userId, tenantId);

        // 构造初始状态（memoryContext 留空，在虚拟线程中加载以避开 reactor 线程 block 限制）
        Map<String, Object> initialState = new HashMap<>();
        initialState.put("query",          query);
        initialState.put("userId",         userId);
        initialState.put("tenantId",       tenantId);
        initialState.put("sessionId",      sessionId);
        initialState.put("maxIterations",  1);
        initialState.put("iteration",      0);
        initialState.put("memoryContext",  "");

        // 将用户查询写入短期记忆（fire-and-forget，不影响主流程）
        memoryManager.addUserMessage(sessionId, query);

        // 在虚拟线程中加载记忆上下文并启动工作流
        // 关键：.block() 必须在虚拟线程上调用，reactor-http-nio 线程禁止阻塞
        CompletableFuture.runAsync(() -> {
            // === 语义缓存检查（在记忆加载前，避免重复网络调用） ===
            if (deepResearch) {
                CacheResult cacheResult = checkSemanticCache(query, tenantId);
                if (cacheResult.hit()) {
                    handleCacheHit(sessionId, query, userId, tenantId, cacheResult);
                    return; // 跳过完整工作流
                }
            }
            // === 缓存检查结束 ===

            String memoryContext = loadMemoryContext(sessionId, userId, tenantId, query);
            if (!memoryContext.isEmpty()) {
                initialState.put("memoryContext", memoryContext);
                log.info("[Orchestrator] 记忆上下文已注入初始状态: {} 字符", memoryContext.length());
            }
            executeWorkflow(sessionId, initialState);
        }, virtualThreadExecutor);

        return ResearchResponse.inProgress(sessionId);
    }

    /**
     * 加载记忆上下文，失败时返回空字符串（优雅降级）.
     * 必须在虚拟线程上调用（内部使用 .block()）。
     */
    private String loadMemoryContext(String sessionId, String userId, String tenantId, String query) {
        try {
            String context = memoryManager.buildMemoryContext(sessionId, userId, tenantId, query)
                .block(java.time.Duration.ofSeconds(5));
            if (context != null && !context.isEmpty()) {
                log.info("[Orchestrator] 记忆上下文已加载: {} 字符", context.length());
                return context;
            }
        } catch (Exception e) {
            log.warn("[Orchestrator] 记忆上下文加载失败，跳过: {}", e.getMessage());
        }
        return "";
    }

    /**
     * 语义缓存检查（带异常保护，失败时返回空结果）.
     */
    private CacheResult checkSemanticCache(String query, String tenantId) {
        try {
            CacheResult result = semanticCache.checkCache(query, tenantId);
            if (result.hit()) {
                log.info("[Orchestrator] 语义缓存命中: score={}, matchedSessionId={}",
                    String.format("%.2f", result.score()), result.matchedSessionId());
            }
            return result;
        } catch (Exception e) {
            log.warn("[Orchestrator] 缓存检查异常（跳过）: {}", e.getMessage());
            return CacheResult.empty();
        }
    }

    /**
     * 处理缓存命中 — 推送 SSE 事件、持久化到 PG 并完成会话.
     * <p>
     * 缓存命中的完整流程：
     * <ol>
     *   <li>创建 SSE Sink + 推送 CACHE_HIT 事件</li>
     *   <li>推送 COMPLETED 事件 + 关闭 SSE 流</li>
     *   <li>将缓存报告持久化到 PG research_history（新 sessionId），
     *       确保前端通过 GET /api/history/{sessionId} 可获取完整报告</li>
     *   <li>写入 Redis 短期记忆（供后续对话上下文）</li>
     * </ol>
     * </p>
     *
     * @param sessionId 新会话 ID
     * @param query     用户查询
     * @param userId    用户 ID
     * @param tenantId  租户 ID
     * @param result    缓存命中结果（含完整报告 + 证据 + 结论）
     */
    private void handleCacheHit(String sessionId, String query, String userId,
                                 String tenantId, CacheResult result) {
        log.info("[Orchestrator] 缓存命中处理: sessionId={}, matchedQuery='{}'",
            sessionId,
            result.matchedQuery() != null
                ? result.matchedQuery().substring(0, Math.min(50, result.matchedQuery().length()))
                : "null");

        // 确保 SSE sink 已创建（供客户端订阅）
        progressPublisher.getOrCreateSink(sessionId);

        // 事件 1: 缓存命中通知
        progressPublisher.publish(sessionId, new ProgressEvent(
            sessionId, ResearchStage.CACHE_HIT, "cache",
            100.0,
            String.format("缓存命中 (相似度=%.0f%%, source=%s): %s",
                result.score() * 100, result.matchedSessionId(), query)));

        // 事件 2: 研究完成（附带缓存的报告）
        int wordCount = result.wordCount() > 0 ? result.wordCount() : countWords(result.report());
        int citationCount = result.citationCount();
        progressPublisher.publish(sessionId, new ProgressEvent(
            sessionId, ResearchStage.COMPLETED, "done", 100.0,
            String.format("研究完成（来自缓存）: %d 字报告, 匹配查询='%s'",
                wordCount,
                result.matchedQuery() != null
                    ? result.matchedQuery().substring(0, Math.min(30, result.matchedQuery().length()))
                    : "null")));
        progressPublisher.complete(sessionId);

        // 持久化到 PG：使前端 GET /api/history/{sessionId} 可获取完整报告
        // 注意：不调用 indexResearchToSemanticMemory，原报告已有 Milvus 索引
        try {
            memoryManager.recordResearchHistory(
                sessionId, userId, tenantId, query, result.report(), wordCount,
                citationCount, 0, "COMPLETED",
                result.sourceIndex(), result.findings());
            // 携带原会话的评估分数（若存在），供前端雷达图渲染
            if (result.evalScores() != null && !result.evalScores().isBlank()) {
                memoryManager.updateEvalScores(sessionId, result.evalScores());
            }
            log.info("[Orchestrator] 缓存命中 PG 持久化完成: sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("[Orchestrator] 缓存命中 PG 持久化失败（不影响 SSE 响应）: {}", e.getMessage());
        }

        // 将缓存的报告写入短期记忆（供后续对话上下文，fire-and-forget）
        String summary = result.report().length() > 500
            ? result.report().substring(0, 500) + "..."
            : result.report();
        memoryManager.addAssistantSummary(sessionId, summary);

        log.info("[Orchestrator] 缓存命中处理完成: sessionId={}, wordCount={}", sessionId, wordCount);
    }

    /**
     * 执行工作流并处理结果.
     */
    private void executeWorkflow(String sessionId, Map<String, Object> initialState) {
        long startTime = System.currentTimeMillis();
        try {
            // 获取编译后的工作流
            CompiledGraph<ResearchState> graph = researchWorkflow.getCompiledGraph();

            // 构建初始状态
            ResearchState initial = new ResearchState(initialState);
            activeSessions.put(sessionId, initial);

            // 执行工作流 (LangGraph4j invoke 接受 Map<String, Object> 初始状态)
            log.info("[Orchestrator] 工作流开始执行: sessionId={}", sessionId);
            Optional<ResearchState> result = graph.invoke(initialState);
            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[Orchestrator] 工作流执行完成: sessionId={}, duration={}ms", sessionId, durationMs);

            // 处理结果
            if (result.isPresent()) {
                ResearchState finalState = result.get();
                activeSessions.put(sessionId, finalState);

                if (finalState.hasError()) {
                    businessMetrics.recordWorkflowCompleted(
                        finalState.intent() != null ? finalState.intent() : "unknown",
                        "error", durationMs);
                    progressPublisher.error(sessionId,
                        new RuntimeException(finalState.error()));
                } else {
                    // 记录工作流成功指标
                    String intent = finalState.intent() != null ? finalState.intent() : "research";
                    businessMetrics.recordWorkflowCompleted(intent, "success", durationMs);

                    // 推送最终完成事件
                    int wordCount = countWords(finalState.finalReport());
                    progressPublisher.publish(sessionId, new ProgressEvent(
                        sessionId, ResearchStage.COMPLETED, "done", 100.0,
                        String.format("研究完成: %d 字报告", wordCount)));
                    progressPublisher.complete(sessionId);

                    // 回写记忆系统（异步 fire-and-forget，不影响 SSE 响应）
                    persistResearchMemory(sessionId, finalState, wordCount);
                }
            } else {
                businessMetrics.recordWorkflowCompleted("unknown", "error", durationMs);
                progressPublisher.error(sessionId,
                    new RuntimeException("工作流未返回有效状态"));
            }

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            businessMetrics.recordWorkflowCompleted("unknown", "error", durationMs);
            log.error("[Orchestrator] 工作流执行异常: sessionId={}", sessionId, e);
            progressPublisher.error(sessionId, e);
            activeSessions.remove(sessionId);
        } finally {
            // 统一清理会话级 Token 统计
            tokenUsageTracker.clearSession(sessionId);
        }
    }

    /**
     * 获取会话当前状态（用于轮询查询进度）.
     */
    public Optional<ResearchState> getSessionState(String sessionId) {
        return Optional.ofNullable(activeSessions.get(sessionId));
    }

    /**
     * 简单字数统计.
     */
    private int countWords(String text) {
        if (text == null || text.isEmpty()) return 0;
        long chineseChars = text.codePoints()
            .filter(cp -> {
                Character.UnicodeScript script = Character.UnicodeScript.of(cp);
                return script == Character.UnicodeScript.HAN;
            })
            .count();
        StringBuilder sb = new StringBuilder();
        text.codePoints().forEach(cp -> {
            if (Character.isLetter(cp) || Character.isWhitespace(cp)) {
                sb.appendCodePoint(cp);
            }
        });
        String[] words = sb.toString().split("\\s+");
        long englishWords = (words.length == 1 && words[0].isEmpty()) ? 0 : words.length;
        return (int) (chineseChars + englishWords);
    }

    /**
     * 将研究结果持久化到记忆系统（fire-and-forget，失败不影响主流程）.
     */
    private void persistResearchMemory(String sessionId, ResearchState state, int wordCount) {
        // direct 意图不走研究流程，无报告/证据/结论产出，跳过持久化
        // （避免在 research_history 写入空记录，污染历史查询和语义缓存）
        if (!state.isDeepResearch()) {
            // 仅将 direct 回答写入短期记忆（供后续对话上下文），不产生 PG/Milvus 记录
            String answer = state.directAnswer();
            if (answer != null && !answer.isBlank()) {
                memoryManager.addAssistantSummary(sessionId,
                    answer.length() > 500 ? answer.substring(0, 500) + "..." : answer);
            }
            log.debug("[Orchestrator] direct 会话跳过记忆持久化: sessionId={}", sessionId);
            return;
        }

        try {
            String userId = state.userId();
            String tenantId = state.tenantId();
            String query = state.query();
            String report = state.finalReport();

            // 提取研究主题（截取 query 前 100 字符作为主题标签）
            String topic = query.length() > 100 ? query.substring(0, 100) : query;

            // 更新用户画像（兴趣、最近主题、研究计数）
            memoryManager.recordResearchCompletion(userId, tenantId, topic);

            // 零证据降级判定：研究流程证据池为空说明报告仅基于模型自身知识（无引用支撑）
            // direct 意图不走检索流程，evidencePool 天然为空，不属于降级
            boolean degraded = state.isDeepResearch() && state.evidencePool().isEmpty();
            String status = degraded ? "DEGRADED" : "COMPLETED";

            // 持久化完整研究历史（仅包含 Writer 实际引用的证据）
            List<String> citedIds = state.citedSourceIds();
            int citationCount = citedIds.size();
            String sourceIndexJson = serializeCitedEvidence(state, citedIds);
            String findingsJson = serializeFindings(state);
            memoryManager.recordResearchHistory(
                sessionId, userId, tenantId, query, report, wordCount,
                citationCount, state.iteration(), status,
                sourceIndexJson, findingsJson);

            // 将研究报告向量化写入 Milvus 语义记忆库（L2 自生长层）
            // 降级报告跳过索引：避免污染语义缓存（相似查询会命中缓存直接返回该报告）
            if (degraded) {
                log.warn("[Orchestrator] 零证据降级报告，跳过语义记忆索引: sessionId={}", sessionId);
            } else {
                memoryManager.indexResearchToSemanticMemory(sessionId, tenantId, query, report);
            }

            // 将报告摘要写入短期记忆（供后续对话上下文）
            String summary = report != null && report.length() > 500
                ? report.substring(0, 500) + "..."
                : report != null ? report : "";
            if (!summary.isEmpty()) {
                memoryManager.addAssistantSummary(sessionId, summary);
            }

            log.info("[Orchestrator] 记忆持久化完成: sessionId={}", sessionId);

            // === 异步 LLM 评估（fire-and-forget，报告非空才有评估意义） ===
            // 空报告的零引用惩罚会把 1.0 分写入租户 gauge，误触发 EvalScoreLow 告警
            if (report != null && !report.isBlank()) {
                triggerAsyncEval(sessionId, query, report, state);
            }

            // === 异步偏好提取（fire-and-forget，不阻塞主流程） ===
            triggerAsyncPreferenceExtraction(sessionId, state);

        } catch (Exception e) {
            log.warn("[Orchestrator] 记忆持久化失败（不影响主流程）: {}", e.getMessage());
        }
    }

    /**
     * 异步触发偏好提取（fire-and-forget 模式）.
     * <p>
     * 研究完成后由 Flash 模型从本次查询 + 最近研究主题中提取稳定的用户偏好，
     * 代码级合并写入 {@code user_profile.preferences}。
     * 提取在独立虚拟线程中执行，失败仅 log.warn，不影响主流程。
     * </p>
     */
    private void triggerAsyncPreferenceExtraction(String sessionId, ResearchState state) {
        String userId = state.userId();
        String tenantId = state.tenantId();
        String query = state.query();

        CompletableFuture.runAsync(() -> {
            // 恢复跨虚拟线程的上下文
            TenantContext.setCurrentUser(userId);
            TenantContext.setCurrentTenant(tenantId);
            try {
                // 读取现有画像作为提取上下文（画像刚在 persistResearchMemory 中更新过）
                var profile = memoryManager.getUserProfile(userId, tenantId);
                String recentTopics = profile.map(p -> p.getRecentTopics()).orElse("[]");
                String existingPrefs = profile.map(p -> p.getPreferences()).orElse("{}");

                Map<String, String> newPrefs = preferenceExtractor.extract(
                    query, recentTopics, existingPrefs);

                if (!newPrefs.isEmpty()) {
                    memoryManager.mergeUserPreferences(userId, tenantId, newPrefs);
                    log.info("[Orchestrator] 偏好提取完成: sessionId={}, 新增/更新 {} 项偏好",
                        sessionId, newPrefs.size());
                } else {
                    log.debug("[Orchestrator] 偏好提取无新信号: sessionId={}", sessionId);
                }
            } catch (Exception e) {
                log.warn("[Orchestrator] 偏好提取失败（不影响主流程）: sessionId={}, error={}",
                    sessionId, e.getMessage());
            }
        }, virtualThreadExecutor);
    }

    /**
     * 异步触发 LLM 评估（fire-and-forget 模式）.
     * <p>
     * 评估在独立虚拟线程中执行，失败时仅 log.warn，不影响 SSE 响应和记忆持久化。
     * 评估完成后更新 ResearchHistory.evalScores 并检查告警阈值。
     * </p>
     */
    private void triggerAsyncEval(String sessionId, String query, String report,
                                   ResearchState state) {
        if (!properties.eval().enabled()) {
            log.debug("[Orchestrator] LLM 评估已禁用，跳过: sessionId={}", sessionId);
            return;
        }

        // 捕获上下文以跨虚拟线程传播
        String evalUserId = state.userId();
        String evalTenantId = state.tenantId();

        CompletableFuture.runAsync(() -> {
            // 恢复跨虚拟线程的上下文
            TenantContext.setCurrentUser(evalUserId);
            TenantContext.setCurrentTenant(evalTenantId);
            try {
                EvalResult evalResult = evalAgent.evaluate(
                    query, state.subQuestions(), report, state.sourceIndex());

                // 仅当评估成功（非 fallback）时写入数据库
                if (evalResult != EvalResult.FALLBACK && evalResult.overallScore() > 0) {
                    String evalJson = objectMapper.writeValueAsString(evalResult);
                    memoryManager.updateEvalScores(sessionId, evalJson);

                    // 滑动窗口告警检查（按租户隔离）
                    checkEvalAlert(evalTenantId, evalResult);

                    log.info("[Orchestrator] LLM 评估完成: sessionId={}, overallScore={}",
                        sessionId, String.format("%.1f", evalResult.overallScore()));
                } else {
                    log.warn("[Orchestrator] LLM 评估返回 fallback，跳过写入: sessionId={}",
                        sessionId);
                }
            } catch (Exception e) {
                log.warn("[Orchestrator] LLM 评估失败（不影响主流程）: sessionId={}, error={}",
                    sessionId, e.getMessage());
            }
        }, virtualThreadExecutor);
    }

    /**
     * 将 ResearchState 中的 evidencePool 序列化为 JSON 字符串.
     * <p>
     * 注意：这里序列化 evidencePool（完整 Evidence 对象列表）而非 sourceIndex（仅 ID 列表），
     * 以确保前端 EvidenceDrawer 和 CitationPopover 能获取完整的证据信息。
     * </p>
     */
    /**
     * 从证据池中筛选 Writer 实际引用的证据，序列化为 JSON。
     * 未引用的证据不入库，前端引用列表自动精准。
     */
    private String serializeCitedEvidence(ResearchState state, List<String> citedIds) {
        try {
            if (citedIds.isEmpty()) return "[]";
            Set<String> idSet = new LinkedHashSet<>(citedIds);
            List<Evidence> cited = state.evidencePool().stream()
                .filter(e -> idSet.contains(e.sourceId()))
                .toList();
            return objectMapper.writeValueAsString(cited);
        } catch (Exception e) {
            log.warn("[Orchestrator] 证据序列化失败: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * 将 ResearchState 中的 findings 序列化为 JSON 字符串.
     * <p>
     * 研究结论由 Analyst Agent 产出，包含结论文本、推理链条和支撑证据 ID，
     * 供前端 ReportViewer "关键发现" Tab 渲染。
     * </p>
     */
    private String serializeFindings(ResearchState state) {
        try {
            List<Finding> findings = state.findings();
            if (findings.isEmpty()) {
                return "[]";
            }
            return objectMapper.writeValueAsString(findings);
        } catch (Exception e) {
            log.warn("[Orchestrator] findings 序列化失败: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * 滑动窗口告警检查（按租户隔离）.
     * <p>
     * 为每个租户维护独立的评估分数滑动窗口（默认 10 次），
     * 避免多租户评分混合导致告警误报。
     * 当窗口满且平均分低于阈值（默认 3.0）时，触发 WARN 日志。
     * </p>
     *
     * @param tenantId 租户 ID（用于隔离评分窗口）
     * @param result   评估结果
     */
    private void checkEvalAlert(String tenantId, EvalResult result) {
        int windowSize = properties.eval().alertWindowSize();
        double threshold = properties.eval().alertThreshold();

        Deque<Double> scores = tenantEvalWindows.computeIfAbsent(
            tenantId, k -> new ArrayDeque<>());

        synchronized (scores) {
            scores.addLast(result.overallScore());
            while (scores.size() > windowSize) {
                scores.removeFirst();
            }

            if (scores.size() >= windowSize) {
                double avg = scores.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0);
                if (avg < threshold) {
                    log.warn("[Orchestrator] ⚠️ 评估告警 [tenant={}]: 最近 {} 次评估平均分={}，" +
                        "低于阈值 {}，建议检查 Prompt 模板质量",
                        tenantId, windowSize, String.format("%.2f", avg), threshold);
                }
            }
        }
    }
}
