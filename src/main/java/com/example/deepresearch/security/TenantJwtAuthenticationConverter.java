package com.example.deepresearch.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * JWT → Authentication 转换器（含租户上下文注入）.
 * <p>
 * 从 JWT Token 的 claims 中提取：
 * <ul>
 *   <li>userId: 优先 {@code owner/name}（Casdoor 特征），缺失时回退 {@code sub}</li>
 *   <li>tenantId: {@code tenant_id} claim 优先，缺失时回退 {@code owner}</li>
 *   <li>{@code scope} 或 {@code authorities} → 权限列表</li>
 * </ul>
 * 并将 tenantId 设置到 {@link TenantContext} 中，供后续 RAG 检索隔离使用。
 * </p>
 *
 * <h3>Casdoor 适配</h3>
 * <p>
 * Casdoor 的 JWT token format（= JWT）中 {@code sub} 是 UUID 主键，
 * {@code owner} 是用户所属组织（即租户），{@code name} 是用户名。
 * 因此 userId 和 tenantId 均需适配提取。
 * </p>
 */
public class TenantJwtAuthenticationConverter
    implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    private static final Logger log = LoggerFactory.getLogger(
        TenantJwtAuthenticationConverter.class);

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        // 从 JWT claims 提取身份信息
        String userId = resolveUserId(jwt);
        String tenantId = resolveTenantId(jwt);

        // 设置租户上下文（ThreadLocal，供 Milvus 检索过滤使用）
        if (tenantId != null && !tenantId.isEmpty()) {
            TenantContext.setCurrentTenant(tenantId);
            TenantContext.setCurrentUser(userId);
        }

        // 提取权限
        List<GrantedAuthority> authorities = extractAuthorities(jwt);

        log.debug("[Security] JWT 认证: userId={}, tenantId={}, authorities={}",
            userId, tenantId, authorities);

        JwtAuthenticationToken token = new JwtAuthenticationToken(
            jwt, authorities, userId);
        return Mono.just(token);
    }

    /**
     * 解析用户 ID：Casdoor 特征（同时有 {@code owner} + {@code name}）时
     * 构造 {@code owner/name}（可读、组织作用域内唯一）；通用 IdP 回退 {@code sub}.
     */
    public static String resolveUserId(Jwt jwt) {
        String owner = jwt.getClaimAsString("owner");
        String name = jwt.getClaimAsString("name");
        if (owner != null && !owner.isBlank() && name != null && !name.isBlank()) {
            return owner + "/" + name;
        }
        return jwt.getClaimAsString("sub");
    }

    /**
     * 解析租户 ID：{@code tenant_id} claim 优先，缺失时回退 {@code owner} claim.
     * <p>
     * Casdoor 等 IdP 没有原生 tenant_id claim，其多租户模型为组织（Organization），
     * JWT 完整格式（Token format = JWT）中以 {@code owner} 字段承载用户所属组织，
     * 组织即租户边界。
     * </p>
     */
    public static String resolveTenantId(Jwt jwt) {
        String tenantId = jwt.getClaimAsString("tenant_id");
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = jwt.getClaimAsString("owner");
        }
        return tenantId;
    }

    /**
     * 从 JWT 提取权限（scope 或 authorities claim）.
     */
    private List<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        // 从 scope claim 提取（OAuth2 标准）
        List<String> scopes = jwt.getClaimAsStringList("scope");
        if (scopes != null) {
            scopes.forEach(scope ->
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope)));
        }

        // 从 authorities claim 提取（自定义）
        List<String> roles = jwt.getClaimAsStringList("authorities");
        if (roles != null) {
            roles.forEach(role ->
                authorities.add(new SimpleGrantedAuthority(role)));
        }

        return authorities;
    }
}
