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
 * </p>
 *
 * <h3>API 端点</h3>
 * <table>
 *   <tr><th>方法</th><th>路径</th><th>说明</th></tr>
 *   <tr><td>POST</td><td>/api/research</td><td>发起研究任务</td></tr>
 *   <tr><td>GET</td><td>/api/research/{sessionId}/stream</td><td>SSE 进度流</td></tr>
 *   <tr><td>GET</td><td>/api/research/{sessionId}</td><td>查询研究状态</td></tr>
 * </table>
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
     * 返回 sessionId，客户端应立即连接 SSE 流获取进度。
     * </p>
     */
    @PostMapping
    public ResponseEntity<ResearchResponse> startResearch(
        @Valid @RequestBody ResearchRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        // === 身份解析：以 JWT claims 为准，请求体仅作兼容回退（可被客户端伪造） ===
        ResearchRequest effective = resolveIdentity(request, jwt);

        log.info("[API] 收到研究请求: query='{}', userId={}",
            piiMaskingService.tokenizeToString(effective.query()), effective.userId());

        // === Prompt 注入检测（在任何 Agent/LLM 调用之前） ===
        InjectionCheckResult checkResult = injectionChecker.check(effective.query());
        if (checkResult.detected()) {
            // 记录安全日志（不记录完整 query，防止日志注入）
            String queryDigest = effective.query().length() > 50
                ? effective.query().substring(0, 50) + "..."
                : effective.query();
            securityLog.logInjectionBlocked(effective.userId(), effective.tenantId(),
                checkResult.reason(), queryDigest);

            // 抛出异常 → GlobalExceptionHandler 统一返回 400
            // detail 不透露检测细节，只显示 "请求被拒绝"
            throw new ResearchException(ErrorCode.PROMPT_INJECTION_DETECTED,
                "请求被拒绝");
        }

        ResearchResponse response = orchestrator.startResearch(effective);
        return ResponseEntity.accepted().body(response);
    }

    /**
     * 解析生效身份：JWT claims 优先，请求体兼容回退.
     * <p>
     * 多租户隔离依赖 tenantId，请求体中的值可被客户端任意伪造，
     * 因此 {@code sub} / {@code tenant_id} claims 存在时强制以 JWT 为准；
     * claims 缺失时回退到请求体值（兼容旧客户端/内部调用）并 WARN。
     * JWT 与请求体声明不一致时记录 SECURITY 日志（越权尝试审计线索）。
     * </p>
     */
    private ResearchRequest resolveIdentity(ResearchRequest request, Jwt jwt) {
        // userId: Casdoor 特征（owner+name）→ owner/name，通用 IdP → sub
        String jwtUserId = jwt != null
            ? TenantJwtAuthenticationConverter.resolveUserId(jwt) : null;
        // tenant_id claim 优先，缺失时回退 owner（Casdoor 组织即租户）
        String jwtTenantId = jwt != null
            ? TenantJwtAuthenticationConverter.resolveTenantId(jwt) : null;

        String userId = request.userId();
        if (jwtUserId != null && !jwtUserId.isBlank()) {
            if (userId != null && !userId.isBlank() && !userId.equals(jwtUserId)) {
                securityLog.logIdentityMismatch("userId", jwtUserId, userId);
            }
            userId = jwtUserId;
        } else {
            log.warn("[API] JWT 缺少 sub claim，回退到请求体 userId={}（兼容模式）", userId);
        }

        String tenantId = request.tenantId();
        if (jwtTenantId != null && !jwtTenantId.isBlank()) {
            if (tenantId != null && !tenantId.isBlank() && !tenantId.equals(jwtTenantId)) {
                securityLog.logIdentityMismatch("tenantId", jwtTenantId, tenantId);
            }
            tenantId = jwtTenantId;
        } else {
            log.warn("[API] JWT 缺少 tenant_id claim，回退到请求体 tenantId={}（兼容模式）", tenantId);
        }

        return new ResearchRequest(request.query(), userId, tenantId, request.deepResearch());
    }

    /**
     * SSE 流式进度订阅.
     * <p>
     * GET /api/research/{sessionId}/stream
     * Accept: text/event-stream
     *
     * 事件格式:
     * <pre>{@code
     * event: planning
     * id: abc12345
     * data: {"sessionId":"abc12345","stage":"PLANNING","nodeName":"plan","percent":0.0,"message":"正在拆解研究问题..."}
     * }</pre>
     *
     * 客户端应保持连接直到收到 {@code event: done} 或连接关闭。
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
            // 心跳保活: 每 15 秒发送一个注释行，防止代理超时断连
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
     * <p>
     * 推荐使用 SSE 流式接口。此端点用于简单状态查询。
     * </p>
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<ResearchResponse> getStatus(@PathVariable String sessionId) {
        return orchestrator.getSessionState(sessionId)
            .map(state -> {
                if (state.hasError()) {
                    return ResponseEntity.ok(ResearchResponse.error(sessionId, state.error()));
                }
                String report = state.finalReport();
                if (report != null && !report.isEmpty()) {
                    return ResponseEntity.ok(ResearchResponse.completed(
                        sessionId, report, 0, 0, state.iteration()));
                }
                return ResponseEntity.ok(ResearchResponse.inProgress(sessionId));
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
