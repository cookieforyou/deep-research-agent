package com.example.deepresearch.service;

import com.example.deepresearch.agent.eval.EvalAgent;
import com.example.deepresearch.api.dto.ProgressEvent;
import com.example.deepresearch.api.dto.ResearchRequest;
import com.example.deepresearch.api.dto.ResearchResponse;
import com.example.deepresearch.api.dto.ProgressEvent.ResearchStage;
import com.example.deepresearch.cache.SemanticCacheService;
import com.example.deepresearch.cache.SemanticCacheService.CacheResult;
import com.example.deepresearch.common.config.DeepResearchProperties;
import com.example.deepresearch.common.model.EvalResult;
import com.example.deepresearch.memory.MemoryManager;
import com.example.deepresearch.security.PiiMaskingService;
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
import java.util.Map;
import java.util.Optional;
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
    private final ObjectMapper objectMapper;
    private final DeepResearchProperties properties;
    private final PiiMaskingService piiMaskingService;

    /** 活跃会话状态缓存（sessionId → 最新 ResearchState） */
    private final ConcurrentHashMap<String, ResearchState> activeSessions = new ConcurrentHashMap<>();

    /** 评估分数滑动窗口（用于告警检查） */
    private final Deque<Double> recentEvalScores = new ArrayDeque<>();

    public ResearchOrchestratorService(
        ResearchWorkflow researchWorkflow,
        ProgressEventPublisher progressPublisher,
        ExecutorService virtualThreadExecutor,
        MemoryManager memoryManager,
        SemanticCacheService semanticCache,
        EvalAgent evalAgent,
        ObjectMapper objectMapper,
        DeepResearchProperties properties,
        PiiMaskingService piiMaskingService
    ) {
        this.researchWorkflow = researchWorkflow;
        this.progressPublisher = progressPublisher;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.memoryManager = memoryManager;
        this.semanticCache = semanticCache;
        this.evalAgent = evalAgent;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.piiMaskingService = piiMaskingService;
    }

    /**
     * 启动研究任务.
     * <p>
     * 创建会话 → 异步启动工作流 → 返回 sessionId 供客户端订阅 SSE。
     * </p>
     *
     * @param request 研究请求
     * @return 会话信息（含 sessionId 和流订阅 URL）
     */
    public ResearchResponse startResearch(ResearchRequest request) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        String tenantId = request.tenantId() != null ? request.tenantId() : "default";

        log.info("[Orchestrator] 启动研究: sessionId={}, query='{}', userId={}, tenantId={}",
            sessionId, piiMaskingService.tokenizeToString(request.query()), request.userId(), tenantId);

        // 构造初始状态（memoryContext 留空，在虚拟线程中加载以避开 reactor 线程 block 限制）
        Map<String, Object> initialState = new HashMap<>();
        initialState.put("query",          request.query());
        initialState.put("userId",         request.userId());
        initialState.put("tenantId",       tenantId);
        initialState.put("sessionId",      sessionId);
        initialState.put("maxIterations",  1);
        initialState.put("iteration",      0);
        initialState.put("memoryContext",  "");

        // 将用户查询写入短期记忆（fire-and-forget，不影响主流程）
        memoryManager.addUserMessage(sessionId, request.query());

        // 在虚拟线程中加载记忆上下文并启动工作流
        // 关键：.block() 必须在虚拟线程上调用，reactor-http-nio 线程禁止阻塞
        final String userId = request.userId();
        final String query = request.query();
        CompletableFuture.runAsync(() -> {
            // === 语义缓存检查（在记忆加载前，避免重复网络调用） ===
            if (request.deepResearch()) {
                CacheResult cacheResult = checkSemanticCache(query, tenantId);
                if (cacheResult.hit()) {
                    handleCacheHit(sessionId, query, cacheResult);
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
     * 处理缓存命中 — 推送 SSE 事件并完成会话.
     * <p>
     * 缓存命中的事件流：
     * <ol>
     *   <li>CACHE_HIT 阶段事件（标记 source=cache）</li>
     *   <li>COMPLETED 阶段事件（附带报告摘要）</li>
     *   <li>progressPublisher.complete() 关闭 SSE 流</li>
     * </ol>
     * </p>
     */
    private void handleCacheHit(String sessionId, String query, CacheResult result) {
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
        progressPublisher.publish(sessionId, new ProgressEvent(
            sessionId, ResearchStage.COMPLETED, "done", 100.0,
            String.format("研究完成（来自缓存）: %d 字报告, 匹配查询='%s'",
                wordCount,
                result.matchedQuery() != null
                    ? result.matchedQuery().substring(0, Math.min(30, result.matchedQuery().length()))
                    : "null")));
        progressPublisher.complete(sessionId);

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
        try {
            // 获取编译后的工作流
            CompiledGraph<ResearchState> graph = researchWorkflow.getCompiledGraph();

            // 构建初始状态
            ResearchState initial = new ResearchState(initialState);
            activeSessions.put(sessionId, initial);

            // 执行工作流 (LangGraph4j invoke 接受 Map<String, Object> 初始状态)
            log.info("[Orchestrator] 工作流开始执行: sessionId={}", sessionId);
            Optional<ResearchState> result = graph.invoke(initialState);
            log.info("[Orchestrator] 工作流执行完成: sessionId={}", sessionId);

            // 处理结果
            if (result.isPresent()) {
                ResearchState finalState = result.get();
                activeSessions.put(sessionId, finalState);

                if (finalState.hasError()) {
                    progressPublisher.error(sessionId,
                        new RuntimeException(finalState.error()));
                } else {
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
                progressPublisher.error(sessionId,
                    new RuntimeException("工作流未返回有效状态"));
            }

        } catch (Exception e) {
            log.error("[Orchestrator] 工作流执行异常: sessionId={}", sessionId, e);
            progressPublisher.error(sessionId, e);
        } finally {
            activeSessions.remove(sessionId);
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
        try {
            String userId = state.userId();
            String tenantId = state.tenantId();
            String query = state.query();
            String report = state.finalReport();

            // 提取研究主题（截取 query 前 100 字符作为主题标签）
            String topic = query.length() > 100 ? query.substring(0, 100) : query;

            // 更新用户画像（兴趣、最近主题、研究计数）
            memoryManager.recordResearchCompletion(userId, tenantId, topic);

            // 持久化完整研究历史
            int citationCount = state.sourceIndex().size();
            memoryManager.recordResearchHistory(
                sessionId, userId, tenantId, query, report, wordCount,
                citationCount, state.iteration(), "COMPLETED");

            // 将研究报告向量化写入 Milvus 语义记忆库（L2 自生长层）
            memoryManager.indexResearchToSemanticMemory(sessionId, tenantId, query, report);

            // 将报告摘要写入短期记忆（供后续对话上下文）
            String summary = report != null && report.length() > 500
                ? report.substring(0, 500) + "..."
                : report != null ? report : "";
            if (!summary.isEmpty()) {
                memoryManager.addAssistantSummary(sessionId, summary);
            }

            log.info("[Orchestrator] 记忆持久化完成: sessionId={}", sessionId);

            // === 异步 LLM 评估（fire-and-forget，不阻塞主流程） ===
            triggerAsyncEval(sessionId, query, report, state);

        } catch (Exception e) {
            log.warn("[Orchestrator] 记忆持久化失败（不影响主流程）: {}", e.getMessage());
        }
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

        CompletableFuture.runAsync(() -> {
            try {
                EvalResult evalResult = evalAgent.evaluate(
                    query, state.subQuestions(), report, state.sourceIndex());

                // 仅当评估成功（非 fallback）时写入数据库
                if (evalResult != EvalResult.FALLBACK && evalResult.overallScore() > 0) {
                    String evalJson = objectMapper.writeValueAsString(evalResult);
                    memoryManager.updateEvalScores(sessionId, evalJson);

                    // 滑动窗口告警检查
                    checkEvalAlert(evalResult);

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
     * 滑动窗口告警检查.
     * <p>
     * 维护最近 N 次（默认 10 次）评估分数的滑动窗口。
     * 当窗口满且平均分低于阈值（默认 3.0）时，触发 WARN 日志。
     * 这表示 Prompt 模板可能需要优化。
     * </p>
     */
    private void checkEvalAlert(EvalResult result) {
        int windowSize = properties.eval().alertWindowSize();
        double threshold = properties.eval().alertThreshold();

        recentEvalScores.addLast(result.overallScore());
        while (recentEvalScores.size() > windowSize) {
            recentEvalScores.removeFirst();
        }

        if (recentEvalScores.size() >= windowSize) {
            double avg = recentEvalScores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
            if (avg < threshold) {
                log.warn("[Orchestrator] ⚠️ 评估告警: 最近 {} 次评估平均分={}，低于阈值 {}，" +
                    "建议检查 Prompt 模板质量",
                    windowSize, String.format("%.2f", avg), threshold);
            }
        }
    }
}
