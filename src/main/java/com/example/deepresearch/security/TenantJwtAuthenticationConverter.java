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
 *   <li>{@code sub} → userId</li>
 *   <li>{@code tenant_id} → tenantId（多租户隔离）</li>
 *   <li>{@code scope} 或 {@code authorities} → 权限列表</li>
 * </ul>
 * 并将 tenantId 设置到 {@link TenantContext} 中，供后续 RAG 检索隔离使用。
 * </p>
 */
public class TenantJwtAuthenticationConverter
    implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    private static final Logger log = LoggerFactory.getLogger(
        TenantJwtAuthenticationConverter.class);

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        // 从 JWT claims 提取身份信息
        String userId = jwt.getClaimAsString("sub");
        String tenantId = jwt.getClaimAsString("tenant_id");

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
