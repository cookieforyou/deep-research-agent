package com.example.deepresearch.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 安全事件日志服务.
 * <p>
 * 使用独立的 {@code SECURITY} Logger 和 SLF4J {@link Marker}
 * 记录 PII 脱敏和 Prompt 注入防护事件，支持安全审计和告警。
 * </p>
 *
 * <h3>日志存储</h3>
 * <ul>
 *   <li>Logger 名称: {@code SECURITY} — 可在 logback 配置中路由到独立文件</li>
 *   <li>Marker: {@code PII_MASKING} / {@code PROMPT_INJECTION} — 方便日志过滤</li>
 *   <li>queryDigest 只记录查询摘要（前 50 字符），防止日志注入和 PII 二次泄露</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * <p>
 * 无状态设计，SLF4J Logger 是线程安全的，可在虚拟线程中安全调用。
 * </p>
 */
@Service
public class SecurityLogService {

    /** 独立的安全日志 Logger */
    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("SECURITY");

    /** PII 标记化事件标记 */
    static final Marker PII_MARKER = MarkerFactory.getMarker("PII_TOKENIZATION");

    /** Prompt 注入拦截事件标记 */
    static final Marker INJECTION_MARKER = MarkerFactory.getMarker("PROMPT_INJECTION");

    /** 输出安全护栏拦截事件标记 */
    static final Marker OUTPUT_GUARD_MARKER = MarkerFactory.getMarker("OUTPUT_GUARDRAIL");

    /** 身份不一致事件标记（JWT claims 与请求体声明不符） */
    static final Marker IDENTITY_MARKER = MarkerFactory.getMarker("IDENTITY_MISMATCH");

    /**
     * 记录 PII 标记化事件.
     *
     * @param tokenCount  被标记化的 PII 数量
     * @param tokenTypes 标记化类型列表（PHONE, EMAIL, ID_CARD, BANK_CARD）
     */
    public void logPiiTokenized(int tokenCount, List<String> tokenTypes) {
        SECURITY_LOG.info(PII_MARKER,
            "[PII] 标记化事件: {} 处 PII 被替换为令牌, 类型: {}",
            tokenCount, tokenTypes);
    }

    /**
     * 记录 Prompt 注入拦截事件.
     * <p>
     * <strong>重要</strong>: queryDigest 只包含查询前 50 字符，
     * 防止完整恶意 payload 进入日志（日志注入防护）。
     * </p>
     *
     * @param userId      用户 ID
     * @param tenantId    租户 ID
     * @param reason      检测原因（如 "指令覆盖模式"、"关键词黑名单"）
     * @param queryDigest 查询摘要（前 50 字符 + "..."）
     */
    public void logInjectionBlocked(String userId, String tenantId, String reason, String queryDigest) {
        SECURITY_LOG.warn(INJECTION_MARKER,
            "[INJECTION] 检测到注入攻击: userId={}, tenantId={}, triggers='{}', queryDigest='{}'",
            userId, tenantId, reason, queryDigest);
    }

    /**
     * 记录输出安全护栏拦截事件.
     *
     * @param userId      用户 ID
     * @param matchedPattern 匹配到的敏感模式
     */
    public void logOutputBlocked(String userId, String matchedPattern) {
        SECURITY_LOG.warn(OUTPUT_GUARD_MARKER,
            "[OUTPUT_GUARD] LLM 输出被护栏拦截: userId={}, matchedPattern='{}'",
            userId, matchedPattern);
    }

    /**
     * 记录身份声明不一致事件.
     * <p>
     * JWT claims 中的身份与请求体声明的身份不一致时记录，
     * 以 JWT 为准（请求体值可被客户端伪造）。持续出现说明客户端
     * 存在越权尝试或集成配置错误，应触发安全审计。
     * </p>
     *
     * @param field     不一致的字段名（userId / tenantId）
     * @param jwtValue  JWT claims 中的值（生效值）
     * @param bodyValue 请求体声明的值（被忽略值）
     */
    public void logIdentityMismatch(String field, String jwtValue, String bodyValue) {
        SECURITY_LOG.warn(IDENTITY_MARKER,
            "[IDENTITY] 请求体身份声明与 JWT 不一致: field={}, jwt='{}', body='{}' — 以 JWT 为准",
            field, jwtValue, bodyValue);
    }
}
