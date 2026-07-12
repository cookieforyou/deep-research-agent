package com.example.deepresearch.service;

import com.example.deepresearch.memory.entity.PromptTemplateEntity;
import com.example.deepresearch.memory.repository.PromptTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态 Prompt 管理服务 — 数据库优先 + classpath 文件兜底.
 * <p>
 * Prompt 模板的加载顺序：
 * <ol>
 *   <li>内存缓存（1 分钟本地 TTL，减少 DB 查询）</li>
 *   <li>PostgreSQL {@code prompt_templates} 表（支持运行时热更新）</li>
 *   <li>classpath {@code prompts/*.st} 文件（应用启动时的安全兜底）</li>
 * </ol>
 * </p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * String rendered = dynamicPromptService.getPrompt("intent-router",
 *     Map.of("query", userQuery));
 * }</pre>
 */
@Service
public class DynamicPromptService {

    private static final Logger log = LoggerFactory.getLogger(DynamicPromptService.class);

    private final PromptTemplateRepository repository;
    private final ResourceLoader resourceLoader;

    /** 本地缓存: templateId → (内容, 过期时间) */
    private final Map<String, CacheEntry> localCache = new ConcurrentHashMap<>();
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    public DynamicPromptService(PromptTemplateRepository repository,
                                 ResourceLoader resourceLoader) {
        this.repository = repository;
        this.resourceLoader = resourceLoader;
    }

    /**
     * 获取 Prompt 模板内容（不替换变量）.
     *
     * @param templateId 模板标识（如 "intent-router", "planner"）
     * @return 模板原文
     */
    public String getTemplateContent(String templateId) {
        // 1. 检查本地缓存
        CacheEntry cached = localCache.get(templateId);
        if (cached != null && !cached.isExpired()) {
            return cached.content;
        }

        // 2. 尝试从数据库加载
        try {
            Optional<PromptTemplateEntity> entity =
                repository.findByIdAndStatus(templateId, "active");
            if (entity.isPresent()) {
                String content = entity.get().getContent();
                localCache.put(templateId, new CacheEntry(content));
                log.debug("[DynamicPrompt] 从数据库加载模板: {}", templateId);
                return content;
            }
        } catch (Exception e) {
            log.debug("[DynamicPrompt] 数据库查询失败，回退到 classpath: {} ({})",
                templateId, e.getMessage());
        }

        // 3. 回退到 classpath 文件
        return loadFromClasspath(templateId);
    }

    /**
     * 从 classpath 加载 Prompt 文件（兜底机制）.
     */
    private String loadFromClasspath(String templateId) {
        try {
            Resource resource = resourceLoader.getResource(
                "classpath:prompts/" + templateId + ".st");
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            localCache.put(templateId, new CacheEntry(content));
            log.debug("[DynamicPrompt] 从 classpath 加载模板: {}", templateId);
            return content;
        } catch (Exception e) {
            log.error("[DynamicPrompt] 无法加载模板: {}", templateId, e);
            return "";
        }
    }

    /**
     * 清除指定模板的本地缓存（数据库更新后调用）.
     */
    public void invalidateCache(String templateId) {
        localCache.remove(templateId);
        log.info("[DynamicPrompt] 缓存已失效: {}", templateId);
    }

    // =========================== 缓存条目 ===========================

    private static class CacheEntry {
        final String content;
        final long expireAt;

        CacheEntry(String content) {
            this.content = content;
            this.expireAt = System.currentTimeMillis() + CACHE_TTL.toMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
