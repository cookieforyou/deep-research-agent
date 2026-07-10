package com.example.deepresearch.common.exception;

/**
 * 研究流程异常.
 * <p>
 * 统一的研究流程异常类型，携带错误码和可选的 Agent 标识，
 * 便于全局异常处理器分类处理和 SSE 错误事件推送。
 * </p>
 */
public class ResearchException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String agentName;

    public ResearchException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.agentName = null;
    }

    public ResearchException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.agentName = null;
    }

    public ResearchException(ErrorCode errorCode, String agentName, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.agentName = agentName;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getAgentName() {
        return agentName;
    }

    /**
     * 研究流程错误码枚举.
     */
    public enum ErrorCode {
        /** LLM API 调用失败 */
        LLM_API_ERROR,
        /** LLM 返回 JSON 解析失败 */
        LLM_JSON_PARSE_ERROR,
        /** 搜索 API 调用失败 */
        SEARCH_API_ERROR,
        /** 向量检索失败 */
        VECTOR_SEARCH_ERROR,
        /** 工作流执行异常 */
        WORKFLOW_ERROR,
        /** 引用校验失败 */
        CITATION_ERROR,
        /** 请求参数校验失败 */
        VALIDATION_ERROR,
        /** 认证失败 */
        AUTHENTICATION_ERROR,
        /** 会话不存在 */
        SESSION_NOT_FOUND,
        /** 研究超时 */
        RESEARCH_TIMEOUT,
        /** 未知内部错误 */
        INTERNAL_ERROR,
        /** PII 脱敏处理异常 */
        PII_MASKING_ERROR,
        /** 检测到 Prompt 注入攻击 */
        PROMPT_INJECTION_DETECTED
    }
}
