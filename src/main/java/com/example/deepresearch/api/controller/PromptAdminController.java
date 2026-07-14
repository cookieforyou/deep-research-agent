package com.example.deepresearch.api.controller;

import com.example.deepresearch.api.dto.UpdatePromptRequest;
import com.example.deepresearch.memory.entity.PromptTemplateEntity;
import com.example.deepresearch.memory.repository.PromptTemplateRepository;
import com.example.deepresearch.service.DynamicPromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Prompt 模板管理 API 控制器（管理员专用）。
 *
 * 端点:
 *   GET    /api/admin/prompts
 *   GET    /api/admin/prompts/{id}
 *   PUT    /api/admin/prompts/{id}
 *   POST   /api/admin/prompts/{id}/reset
 *   POST   /api/admin/prompts/{id}/cache/invalidate
 */
@RestController
@RequestMapping("/api/admin/prompts")
public class PromptAdminController {

    private static final Logger log = LoggerFactory.getLogger(PromptAdminController.class);

    private final PromptTemplateRepository repository;
    private final DynamicPromptService promptService;

    public PromptAdminController(
        PromptTemplateRepository repository,
        DynamicPromptService promptService
    ) {
        this.repository = repository;
        this.promptService = promptService;
    }

    /**
     * GET /api/admin/prompts
     * 获取全部 Prompt 模板列表。
     */
    @GetMapping
    public ResponseEntity<List<PromptTemplateEntity>> listAll() {
        log.info("[Admin] 获取全部 Prompt 模板");
        List<PromptTemplateEntity> templates = repository.findAll();
        return ResponseEntity.ok(templates);
    }

    /**
     * GET /api/admin/prompts/{id}
     * 获取单个模板详情。
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        log.info("[Admin] 获取模板: id={}", id);
        Optional<PromptTemplateEntity> entity = repository.findById(id);
        if (entity.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(entity.get());
    }

    /**
     * PUT /api/admin/prompts/{id}
     * 更新模板内容/状态/AB分组。
     * 字段全部可选，只更新传入的字段。
     * @Version 乐观锁自动递增版本号。
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
        @PathVariable String id,
        @RequestBody UpdatePromptRequest request
    ) {
        log.info("[Admin] 更新模板: id={}, status={}, abGroup={}", id, request.status(), request.abGroup());

        try {
            Optional<PromptTemplateEntity> existing = repository.findById(id);
            if (existing.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            PromptTemplateEntity entity = existing.get();

            if (request.content() != null) {
                entity.setContent(request.content());
            }
            if (request.status() != null) {
                entity.setStatus(request.status());
            }
            if (request.abGroup() != null) {
                entity.setAbGroup(request.abGroup());
            }

            entity.setUpdatedAt(LocalDateTime.now());

            PromptTemplateEntity saved = repository.save(entity);

            // 更新后清除本地缓存，使下次读取立即生效（而非等待 1 分钟 TTL 到期）
            promptService.invalidateCache(id);

            log.info("[Admin] 模板已更新: id={}, version={}", saved.getId(), saved.getVersion());
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            log.error("[Admin] 更新模板失败: id={}, error={}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("更新模板失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/admin/prompts/{id}/reset
     * 重置模板内容为 classpath 默认值。
     */
    @PostMapping("/{id}/reset")
    public ResponseEntity<?> reset(@PathVariable String id) {
        log.info("[Admin] 重置模板: id={}", id);

        try {
            Optional<PromptTemplateEntity> existing = repository.findById(id);
            if (existing.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // 从 classpath 重新加载默认内容
            String defaultContent = promptService.loadFromClasspath(id);
            if (defaultContent.isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("无法从 classpath 加载默认模板: " + id);
            }

            PromptTemplateEntity entity = existing.get();
            entity.setContent(defaultContent);
            entity.setUpdatedAt(LocalDateTime.now());

            PromptTemplateEntity saved = repository.save(entity);
            promptService.invalidateCache(id);

            log.info("[Admin] 模板已重置: id={}, version={}", saved.getId(), saved.getVersion());
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            log.error("[Admin] 重置模板失败: id={}, error={}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("重置模板失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/admin/prompts/{id}/cache/invalidate
     * 强制刷新本地缓存（不修改数据库）。
     */
    @PostMapping("/{id}/cache/invalidate")
    public ResponseEntity<Void> invalidateCache(@PathVariable String id) {
        log.info("[Admin] 强制刷新缓存: id={}", id);
        promptService.invalidateCache(id);
        return ResponseEntity.ok().build();
    }
}
