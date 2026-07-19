package com.example.deepresearch.api.controller;

import com.example.deepresearch.api.dto.ProgressEvent;
import com.example.deepresearch.api.dto.ResearchRequest;
import com.example.deepresearch.api.dto.ResearchResponse;
import com.example.deepresearch.common.exception.ResearchException;
import com.example.deepresearch.common.exception.ResearchException.ErrorCode;
import com.example.deepresearch.security.PiiMaskingService;
import com.example.deepresearch.security.PromptInjectionChecker;
import com.example.deepresearch.security.PromptInjectionChecker.InjectionCheckResult;
import com.example.deepresearch.security.SecurityLogService;
import com.example.deepresearch.security.TenantJwtAuthenticationConverter;
import com.example.deepresearch.service.ProgressEventPublisher;
import com.example.deepresearch.service.ResearchOrchestratorService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * 研究 REST 控制器.
 * <p>
 * 提供研究任务的创建和 SSE 流式进度订阅接口。
 * 身份（userId / tenantId）从 JWT claims 提取，不接收客户端传值。
 * </p>
 */
@RestController
@RequestMapping("/api/research")
public class ResearchController {

    private static final Logger log = LoggerFactory.getLogger(ResearchController.class);

    private final ResearchOrchestratorService orchestrator;
    private final ProgressEventPublisher progressPublisher;
    private final PromptInjectionChecker injectionChecker;
    private final SecurityLogService securityLog;
    private final PiiMaskingService piiMaskingService;

    public ResearchController(
        ResearchOrchestratorService orchestrator,
        ProgressEventPublisher progressPublisher,
        PromptInjectionChecker injectionChecker,
        SecurityLogService securityLog,
        PiiMaskingService piiMaskingService
    ) {
        this.orchestrator = orchestrator;
        this.progressPublisher = progressPublisher;
        this.injectionChecker = injectionChecker;
        this.securityLog = securityLog;
        this.piiMaskingService = piiMaskingService;
    }

    /**
     * 发起研究任务.
     * <p>
     * POST /api/research
     * Content-Type: application/json
     *
     * <pre>{@code
     * {
     *   "query": "2026年中国新能源汽车市场趋势分析",
     *   "deepResearch": true
     * }
     * }</pre>
     *
     * userId / tenantId 从 JWT claims 提取。
     * 返回 sessionId，客户端应立即连接 SSE 流获取进度。
     * </p>
     */
    @PostMapping
    public ResponseEntity<ResearchResponse> startResearch(
        @Valid @RequestBody ResearchRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = TenantJwtAuthenticationConverter.resolveUserId(jwt);
        String tenantId = TenantJwtAuthenticationConverter.resolveTenantId(jwt);

        log.info("[API] 收到研究请求: query='{}', userId={}",
            piiMaskingService.tokenizeToString(request.query()), userId);

        // === Prompt 注入检测（在任何 Agent/LLM 调用之前） ===
        InjectionCheckResult checkResult = injectionChecker.check(request.query());
        if (checkResult.detected()) {
            String queryDigest = request.query().length() > 50
                ? request.query().substring(0, 50) + "..."
                : request.query();
            securityLog.logInjectionBlocked(userId, tenantId,
                checkResult.reason(), queryDigest);

            throw new ResearchException(ErrorCode.PROMPT_INJECTION_DETECTED,
                "请求被拒绝");
        }

        ResearchResponse response = orchestrator.startResearch(
            request.query(), request.deepResearch(), userId, tenantId);
        return ResponseEntity.accepted().body(response);
    }

    /**
     * SSE 流式进度订阅.
     * <p>
     * GET /api/research/{sessionId}/stream
     * Accept: text/event-stream
     * </p>
     */
    @GetMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ProgressEvent>> streamProgress(
        @PathVariable String sessionId
    ) {
        log.info("[API] SSE 客户端连接: sessionId={}", sessionId);

        return progressPublisher.getStream(sessionId)
            .map(event -> ServerSentEvent.<ProgressEvent>builder()
                .id(sessionId)
                .event(event.stage().name().toLowerCase())
                .data(event)
                .build())
            .mergeWith(Flux.interval(Duration.ofSeconds(15))
                .map(tick -> ServerSentEvent.<ProgressEvent>builder()
                    .comment("heartbeat")
                    .build()))
            .doFinally(signalType -> {
                log.info("[API] SSE 连接关闭: sessionId={}, signal={}", sessionId, signalType);
            });
    }

    /**
     * 查询研究状态（轮询方式，非推荐）.
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<ResearchResponse> getStatus(@PathVariable String sessionId) {
        return orchestrator.getSessionState(sessionId)
            .map(state -> {
                if (state.hasError()) {
                    return ResponseEntity.ok(ResearchResponse.error(sessionId, state.error()));
                }
                // 研究报告（深度研究）或直接回答（闲聊/事实查询）
                String report = state.finalReport();
                if (report == null || report.isEmpty()) {
                    report = state.directAnswer();
                }
                if (report != null && !report.isEmpty()) {
                    int wordCount = countWords(report);
                    int citationCount = state.evidencePool() != null ? state.evidencePool().size() : 0;
                    return ResponseEntity.ok(ResearchResponse.completed(
                        sessionId, report, wordCount, citationCount, state.iteration()));
                }
                return ResponseEntity.ok(ResearchResponse.inProgress(sessionId));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private static int countWords(String text) {
        if (text == null || text.isEmpty()) return 0;
        long chineseChars = text.codePoints()
            .filter(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)
            .count();
        return (int) chineseChars + text.split("\\s+").length;
    }
}
