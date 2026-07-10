package com.example.deepresearch.common.exception;

import com.example.deepresearch.common.exception.ResearchException.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.net.URI;
import java.time.Instant;

/**
 * 全局异常处理器.
 * <p>
 * 将所有异常统一转换为 RFC 7807 {@link ProblemDetail} 格式，
 * 确保 API 错误响应结构化、可追溯。
 * </p>
 *
 * <h3>错误响应格式</h3>
 * <pre>{@code
 * {
 *   "type": "https://api.deepresearch.example/errors/llm-api-error",
 *   "title": "LLM API Error",
 *   "status": 502,
 *   "detail": "DeepSeek API 调用失败: 429 Too Many Requests",
 *   "instance": "/api/research",
 *   "timestamp": "2026-07-06T10:30:00Z"
 * }
 * }</pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_TYPE_PREFIX = "https://api.deepresearch.example/errors/";

    /**
     * 研究流程异常.
     */
    @ExceptionHandler(ResearchException.class)
    public ProblemDetail handleResearchException(ResearchException ex) {
        log.error("[{}] {} — agent={}", ex.getErrorCode(), ex.getMessage(), ex.getAgentName(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            mapToHttpStatus(ex.getErrorCode()), ex.getMessage());
        problem.setType(URI.create(ERROR_TYPE_PREFIX + toKebabCase(ex.getErrorCode().name())));
        problem.setTitle(ex.getErrorCode().name());
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("errorCode", ex.getErrorCode().name());
        if (ex.getAgentName() != null) {
            problem.setProperty("agent", ex.getAgentName());
        }
        return problem;
    }

    /**
     * 请求参数校验异常.
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public ProblemDetail handleValidationException(WebExchangeBindException ex) {
        log.warn("参数校验失败: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "请求参数校验失败");
        problem.setType(URI.create(ERROR_TYPE_PREFIX + "validation-error"));
        problem.setTitle("Validation Error");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("errors", ex.getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .toList());
        return problem;
    }

    /**
     * 通用异常兜底.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("未预期的内部错误", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误: " + ex.getMessage());
        problem.setType(URI.create(ERROR_TYPE_PREFIX + "internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * 将业务错误码映射为 HTTP 状态码.
     */
    private HttpStatus mapToHttpStatus(ErrorCode code) {
        return switch (code) {
            case LLM_API_ERROR, SEARCH_API_ERROR -> HttpStatus.BAD_GATEWAY;
            case LLM_JSON_PARSE_ERROR, WORKFLOW_ERROR, CITATION_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case VECTOR_SEARCH_ERROR -> HttpStatus.SERVICE_UNAVAILABLE;
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case AUTHENTICATION_ERROR -> HttpStatus.UNAUTHORIZED;
            case SESSION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RESEARCH_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            case PROMPT_INJECTION_DETECTED -> HttpStatus.BAD_REQUEST;
            case PII_MASKING_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * 将枚举名转为 kebab-case (如 LLM_API_ERROR → llm-api-error).
     */
    private String toKebabCase(String enumName) {
        return enumName.toLowerCase().replace('_', '-');
    }
}
