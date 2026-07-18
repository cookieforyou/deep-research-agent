package com.example.deepresearch.api.controller;

import com.example.deepresearch.memory.MemoryManager;
import com.example.deepresearch.memory.entity.UserProfile;
import com.example.deepresearch.security.TenantJwtAuthenticationConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * 用户画像 API 控制器。
 * 身份（userId / tenantId）从 JWT claims 提取，不接受请求参数传入。
 * <p>
 * 端点:
 *   GET /api/user/profile
 * </p>
 */
@RestController
@RequestMapping("/api/user")
public class UserProfileController {

    private static final Logger log = LoggerFactory.getLogger(UserProfileController.class);

    private final MemoryManager memoryManager;

    public UserProfileController(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    /**
     * GET /api/user/profile
     * 获取当前 JWT 用户的画像（研究统计、兴趣标签、偏好设置）。
     */
    @GetMapping("/profile")
    public ResponseEntity<UserProfile> getProfile(@AuthenticationPrincipal Jwt jwt) {
        String userId = TenantJwtAuthenticationConverter.resolveUserId(jwt);
        String tenantId = TenantJwtAuthenticationConverter.resolveTenantId(jwt);

        log.info("[User] 获取用户画像: userId={}, tenantId={}", userId, tenantId);

        try {
            var profile = memoryManager.getUserProfile(userId, tenantId);
            if (profile.isEmpty()) {
                UserProfile emptyProfile = new UserProfile(userId, tenantId);
                return ResponseEntity.ok(emptyProfile);
            }
            return ResponseEntity.ok(profile.get());
        } catch (Exception e) {
            log.error("[User] 获取画像失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
