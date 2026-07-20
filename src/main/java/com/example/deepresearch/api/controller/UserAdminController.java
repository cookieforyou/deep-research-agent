package com.example.deepresearch.api.controller;

import com.example.deepresearch.api.dto.UserSummary;
import com.example.deepresearch.memory.entity.UserProfile;
import com.example.deepresearch.memory.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * 用户管理 API 控制器（管理员专用）。
 * 只读仪表盘，查看所有用户的研究统计信息。
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private static final Logger log = LoggerFactory.getLogger(UserAdminController.class);

    private final UserProfileRepository repository;

    public UserAdminController(UserProfileRepository repository) {
        this.repository = repository;
    }

    /**
     * GET /api/admin/users
     * 分页查询全部用户画像（支持搜索）。
     *
     * @param page   页码（0-based）
     * @param size   每页条数
     * @param search 按 userId 或 tenantId 模糊搜索（可选）
     * @param sort   排序字段，默认 updatedAt
     */
    @GetMapping
    public Mono<ResponseEntity<Page<UserSummary>>> listUsers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "updatedAt") String sort
    ) {
        log.info("[Admin] 查询用户列表: page={}, size={}, search={}", page, size, search);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sort));
        Page<UserProfile> profiles;

        if (search != null && !search.isBlank()) {
            profiles = repository.findByUserIdContainingIgnoreCaseOrTenantIdContainingIgnoreCase(
                search, search, pageRequest);
        } else {
            profiles = repository.findAll(pageRequest);
        }

        Page<UserSummary> result = profiles.map(UserSummary::from);
        return Mono.just(ResponseEntity.ok(result));
    }
}
