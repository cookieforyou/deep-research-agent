package com.example.deepresearch.api.controller;

import com.example.deepresearch.memory.MemoryManager;
import com.example.deepresearch.memory.entity.ResearchHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 研究历史 API 控制器。
 *
 * 提供研究历史的分页查询、详情查看和删除功能。
 * 端点:
 *   GET    /api/history?userId=&tenantId=&page=&size=&status=&keyword=&sortBy=&sortDir=
 *   GET    /api/history/{sessionId}
 *   DELETE /api/history/{sessionId}
 */
@RestController
@RequestMapping("/api/history")
public class ResearchHistoryController {

    private static final Logger log = LoggerFactory.getLogger(ResearchHistoryController.class);

    private final MemoryManager memoryManager;

    public ResearchHistoryController(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    /**
     * GET /api/history
     * 分页查询研究历史，支持搜索、筛选和排序。
     * 列表查询不返回报告全文（report 字段置空）。
     */
    @GetMapping
    public ResponseEntity<Page<ResearchHistory>> listHistory(
        @RequestParam String userId,
        @RequestParam String tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir
    ) {
        log.info("[History] 查询研究历史: userId={}, tenantId={}, page={}, size={}, status={}, keyword={}, sortBy={}",
            userId, tenantId, page, size, status, keyword, sortBy);

        try {
            Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
            PageRequest pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

            // 使用 LongTermMemoryService 的分页查询（需要扩展）
            List<ResearchHistory> allRecords = memoryManager.getResearchHistory(userId, tenantId);

            // 应用筛选
            if (status != null && !status.isEmpty()) {
                allRecords = allRecords.stream()
                    .filter(h -> status.equalsIgnoreCase(h.getStatus()))
                    .collect(Collectors.toList());
            }
            if (keyword != null && !keyword.isEmpty()) {
                String kw = keyword.toLowerCase();
                allRecords = allRecords.stream()
                    .filter(h -> h.getQuery() != null && h.getQuery().toLowerCase().contains(kw))
                    .collect(Collectors.toList());
            }

            // 排序（内存排序）
            Comparator<ResearchHistory> comparator = switch (sortBy) {
                case "wordCount" -> Comparator.comparingInt(ResearchHistory::getWordCount);
                case "citationCount" -> Comparator.comparingInt(ResearchHistory::getCitationCount);
                default -> Comparator.comparing(ResearchHistory::getCreatedAt);
            };
            if (direction == Sort.Direction.DESC) {
                comparator = comparator.reversed();
            }
            allRecords.sort(comparator);

            // 分页
            int totalElements = allRecords.size();
            int start = Math.min(page * size, totalElements);
            int end = Math.min(start + size, totalElements);
            List<ResearchHistory> pageContent = allRecords.subList(start, end);

            // 列表查询清除 report 字段（节省带宽）
            pageContent.forEach(h -> h.setReport(null));

            Page<ResearchHistory> result = new PageImpl<>(
                pageContent, pageable, totalElements
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[History] 查询失败: {}", e.getMessage(), e);
            // 返回空结果而非 500，优雅降级
            return ResponseEntity.ok(Page.empty());
        }
    }

    /**
     * GET /api/history/{sessionId}
     * 获取单条研究历史详情（含完整报告和评估分数）。
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<?> getDetail(@PathVariable String sessionId) {
        log.info("[History] 获取详情: sessionId={}", sessionId);

        try {
            var history = memoryManager.getResearchBySessionId(sessionId);
            if (history.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(history.get());
        } catch (Exception e) {
            log.error("[History] 获取详情失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("获取研究历史详情失败: " + e.getMessage());
        }
    }

    /**
     * DELETE /api/history/{sessionId}
     * 删除研究记录。
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> delete(@PathVariable String sessionId) {
        log.info("[History] 删除记录: sessionId={}", sessionId);
        try {
            memoryManager.deleteResearchHistory(sessionId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("[History] 删除失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
