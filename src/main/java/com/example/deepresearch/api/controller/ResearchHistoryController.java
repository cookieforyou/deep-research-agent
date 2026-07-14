package com.example.deepresearch.api.controller;

import com.example.deepresearch.api.dto.ResearchHistorySummary;
import com.example.deepresearch.memory.MemoryManager;
import com.example.deepresearch.memory.entity.ResearchHistory;
import com.example.deepresearch.memory.repository.ResearchHistoryRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 研究历史 API 控制器。
 * <p>
 * 使用 JPA Specification 实现数据库级分页、筛选和排序，
 * 避免内存中处理大量数据。
 */
@RestController
@RequestMapping("/api/history")
public class ResearchHistoryController {

    private static final Logger log = LoggerFactory.getLogger(ResearchHistoryController.class);

    private final MemoryManager memoryManager;
    private final ResearchHistoryRepository repository;

    public ResearchHistoryController(MemoryManager memoryManager,
                                      ResearchHistoryRepository repository) {
        this.memoryManager = memoryManager;
        this.repository = repository;
    }

    /**
     * GET /api/history
     * 分页查询研究历史，支持搜索、筛选和排序。
     * 返回 ResearchHistorySummary（不含报告全文）。
     */
    @GetMapping
    public ResponseEntity<Page<ResearchHistorySummary>> listHistory(
        @RequestParam String userId,
        @RequestParam String tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir
    ) {
        log.info("[History] 查询研究历史: userId={}, tenantId={}, page={}, size={}, status={}, keyword={}",
            userId, tenantId, page, size, status, keyword);

        try {
            Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
            PageRequest pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

            // JPA Specification 动态查询
            Specification<ResearchHistory> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.equal(root.get("userId"), userId));
                predicates.add(cb.equal(root.get("tenantId"), tenantId));
                if (status != null && !status.isEmpty()) {
                    predicates.add(cb.equal(root.get("status"), status));
                }
                if (keyword != null && !keyword.isEmpty()) {
                    predicates.add(cb.like(cb.lower(root.get("query")),
                        "%" + keyword.toLowerCase() + "%"));
                }
                return cb.and(predicates.toArray(new Predicate[0]));
            };

            Page<ResearchHistory> result = repository.findAll(spec, pageable);

            // 转换为 DTO
            Page<ResearchHistorySummary> summaries = result.map(ResearchHistorySummary::from);
            return ResponseEntity.ok(summaries);

        } catch (Exception e) {
            log.error("[History] 查询失败: {}", e.getMessage(), e);
            return ResponseEntity.ok(Page.empty());
        }
    }

    /**
     * GET /api/history/{sessionId}
     * 获取单条研究历史详情（含完整报告、评估分数、证据池）。
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<ResearchHistory> getDetail(
        @PathVariable String sessionId,
        @RequestParam(required = false) String userId,
        @RequestParam(required = false) String tenantId
    ) {
        log.info("[History] 获取详情: sessionId={}", sessionId);

        try {
            var historyOpt = memoryManager.getResearchBySessionId(sessionId);
            if (historyOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            ResearchHistory history = historyOpt.get();

            if (userId != null && !userId.isEmpty() &&
                !userId.equals(history.getUserId())) {
                log.warn("[History] 无权访问: sessionId={}, requestUser={}, owner={}",
                    sessionId, userId, history.getUserId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            return ResponseEntity.ok(history);

        } catch (Exception e) {
            log.error("[History] 获取详情失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * DELETE /api/history/{sessionId}
     * 删除研究记录。需提供 userId + tenantId 验证所有权。
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> delete(
        @PathVariable String sessionId,
        @RequestParam String userId,
        @RequestParam String tenantId
    ) {
        log.info("[History] 删除记录: sessionId={}", sessionId);

        try {
            var historyOpt = memoryManager.getResearchBySessionId(sessionId);
            if (historyOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            ResearchHistory history = historyOpt.get();

            if (!userId.equals(history.getUserId()) ||
                !tenantId.equals(history.getTenantId())) {
                log.warn("[History] 无权删除: sessionId={}, request={}/{}",
                    sessionId, userId, tenantId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            memoryManager.deleteResearchHistory(sessionId);
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.error("[History] 删除失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
