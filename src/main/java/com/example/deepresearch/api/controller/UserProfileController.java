package com.example.deepresearch.api.controller;

import com.example.deepresearch.memory.MemoryManager;
import com.example.deepresearch.memory.entity.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 用户画像 API 控制器。
 * 端点:
 *   GET /api/user/profile?userId=&tenantId=
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
     * GET /api/user/profile?userId=&tenantId=
     * 获取用户画像（研究统计、兴趣标签、偏好设置）。
     */
    @GetMapping("/profile")
    public ResponseEntity<UserProfile> getProfile(
        @RequestParam String userId,
        @RequestParam String tenantId
    ) {
        log.info("[User] 获取用户画像: userId={}, tenantId={}", userId, tenantId);

        try {
            var profile = memoryManager.getUserProfile(userId, tenantId);
            if (profile.isEmpty()) {
                // 用户尚未创建画像，返回空数据
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
