package com.example.deepresearch.security;

/**
 * 租户上下文 — ThreadLocal 持有当前请求的租户信息.
 * <p>
 * 在 JWT 认证时由 {@link TenantJwtAuthenticationConverter} 设置，
 * 确保整个请求链路中（Controller → Agent → RAG）都能获取 tenantId
 * 而不需要在每个方法参数中显式传递。
 * </p>
 *
 * <h3>多租户数据隔离</h3>
 * 所有 Milvus 向量检索和 PostgreSQL 查询
 * <strong>必须</strong>使用当前租户上下文进行过滤，
 * 防止数据跨租户泄漏。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * String tenantId = TenantContext.getCurrentTenant();
 * // 在 Milvus FilterExpression 中强制注入:
 * // "tenant_id == '" + tenantId + "'"
 * }</pre>
 *
 * <h3>重要</h3>
 * 请求处理完成后<strong>必须</strong>调用 {@link #clear()} 清理，
 * 防止 ThreadLocal 内存泄漏（虚拟线程场景下尤其重要）。
 */
public final class TenantContext {

    private static final ThreadLocal<String> TENANT_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_HOLDER = new ThreadLocal<>();

    private TenantContext() {
        // 工具类不允许实例化
    }

    /** 设置当前租户 ID */
    public static void setCurrentTenant(String tenantId) {
        TENANT_HOLDER.set(tenantId);
    }

    /** 获取当前租户 ID（可能为 null） */
    public static String getCurrentTenant() {
        return TENANT_HOLDER.get();
    }

    /** 设置当前用户 ID */
    public static void setCurrentUser(String userId) {
        USER_HOLDER.set(userId);
    }

    /** 获取当前用户 ID */
    public static String getCurrentUser() {
        return USER_HOLDER.get();
    }

    /**
     * 清理 ThreadLocal.
     * <p>
     * 必须在请求处理完成后调用，防止内存泄漏。
     * 虚拟线程场景下尤其重要——虚拟线程池会复用载体线程。
     * </p>
     */
    public static void clear() {
        TENANT_HOLDER.remove();
        USER_HOLDER.remove();
    }
}
