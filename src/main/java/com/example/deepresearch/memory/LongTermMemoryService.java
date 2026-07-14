package com.example.deepresearch.memory;

import com.example.deepresearch.memory.entity.ResearchHistory;
import com.example.deepresearch.memory.entity.UserProfile;
import com.example.deepresearch.memory.repository.ResearchHistoryRepository;
import com.example.deepresearch.memory.repository.UserProfileRepository;
import com.example.deepresearch.security.PiiMaskingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 长期记忆服务 — PostgreSQL 用户画像与历史记录.
 * <p>
 * 实现：
 * <ul>
 *   <li>用户画像的 CRUD（关注行业、偏好、历史主题）</li>
 *   <li>研究历史的持久化（用于知识库自生长层 L2）</li>
 *   <li>用户画像上下文构建（供 Planner Agent 注入）</li>
 * </ul>
 * </p>
 */
@Service
public class LongTermMemoryService {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryService.class);

    private final UserProfileRepository userProfileRepo;
    private final ResearchHistoryRepository historyRepo;
    private final ObjectMapper objectMapper;
    private final PiiMaskingService piiMaskingService;

    public LongTermMemoryService(UserProfileRepository userProfileRepo,
                                  ResearchHistoryRepository historyRepo,
                                  ObjectMapper objectMapper,
                                  PiiMaskingService piiMaskingService) {
        this.userProfileRepo = userProfileRepo;
        this.historyRepo = historyRepo;
        this.objectMapper = objectMapper;
        this.piiMaskingService = piiMaskingService;
    }

    // =========================== 用户画像 ===========================

    /**
     * 获取用户画像上下文（供 Planner Prompt 注入）.
     */
    @Transactional(readOnly = true)
    public String getUserProfileContext(String userId, String tenantId) {
        Optional<UserProfile> profileOpt = userProfileRepo
            .findByUserIdAndTenantId(userId, tenantId);

        if (profileOpt.isEmpty()) {
            log.debug("[LongMem] 无用户画像: userId={}, tenantId={}", userId, tenantId);
            return "";
        }

        UserProfile profile = profileOpt.get();
        StringBuilder sb = new StringBuilder();
        sb.append("用户关注行业: ").append(profile.getInterests()).append("\n");
        sb.append("历史研究主题: ").append(profile.getRecentTopics()).append("\n");
        sb.append("累计研究次数: ").append(profile.getResearchCount()).append("\n");

        // 附加偏好
        if (profile.getPreferences() != null && !profile.getPreferences().equals("{}")) {
            sb.append("用户偏好: ").append(profile.getPreferences());
        }

        log.debug("[LongMem] 画像已加载: userId={}, interests={}",
            userId, piiMaskingService.tokenizeToString(profile.getInterests()));
        return sb.toString();
    }

    /**
     * 更新用户兴趣标签（研究完成后调用）.
     */
    @Transactional
    public void updateUserInterests(String userId, String topic, String tenantId) {
        UserProfile profile = userProfileRepo
            .findByUserIdAndTenantId(userId, tenantId)
            .orElseGet(() -> {
                log.info("[LongMem] 创建新用户画像: userId={}, tenantId={}", userId, tenantId);
                return new UserProfile(userId, tenantId);
            });

        // 更新兴趣标签
        List<String> interests = parseJsonArray(profile.getInterests());
        if (!interests.contains(topic)) {
            interests.add(0, topic); // 最新的兴趣放在最前面
            if (interests.size() > 20) {
                interests = interests.subList(0, 20);
            }
            profile.setInterests(toJson(interests));
        }

        // 更新最近研究主题
        List<String> topics = parseJsonArray(profile.getRecentTopics());
        topics.add(0, topic);
        if (topics.size() > 10) {
            topics = topics.subList(0, 10);
        }
        profile.setRecentTopics(toJson(topics));

        // 增加研究计数
        profile.incrementResearchCount();

        userProfileRepo.save(profile);
        log.debug("[LongMem] 用户画像已更新: userId={}, interests={}", userId, safeInterests(interests));
    }

    private List<String> safeInterests(List<String> interests) {
        if (interests == null || interests.isEmpty()) return List.of();
        return interests.stream()
            .map(piiMaskingService::tokenizeToString)
            .toList();
    }

    // =========================== 研究历史 ===========================

    /**
     * 记录研究完成（L2 自生长层：研究产出回写）.
     */
    @Transactional
    public ResearchHistory recordResearch(String sessionId, String userId,
                                           String tenantId, String query,
                                           String report, int wordCount,
                                           int citationCount, int iterationCount,
                                           String status) {
        ResearchHistory history = new ResearchHistory(
            sessionId, userId, tenantId, query, report,
            wordCount, citationCount, iterationCount, status);

        ResearchHistory saved = historyRepo.save(history);
        log.info("[LongMem] 研究历史已记录: sessionId={}, words={}, status={}",
            sessionId, wordCount, status);
        return saved;
    }

    /**
     * 获取用户最近的研究记录.
     */
    @Transactional(readOnly = true)
    public List<ResearchHistory> getRecentHistory(String userId, String tenantId,
                                                    int limit) {
        return historyRepo.findByUserIdAndTenantIdOrderByCreatedAtDesc(
            userId, tenantId, PageRequest.of(0, limit));
    }

    /**
     * 按会话 ID 获取完整研究报告（供语义缓存使用）.
     *
     * @param sessionId 会话 ID
     * @return 研究历史记录（含完整报告 Markdown）
     */
    @Transactional(readOnly = true)
    public Optional<ResearchHistory> getResearchBySessionId(String sessionId) {
        return historyRepo.findBySessionId(sessionId);
    }

    /**
     * 更新研究历史的评估分数.
     * <p>
     * 在 EvalAgent 异步评估完成后调用，
     * 将评估结果 JSON 写入 {@code ResearchHistory.evalScores} 字段。
     * </p>
     *
     * @param sessionId      会话 ID
     * @param evalScoresJson 评估结果 JSON 字符串
     */
    @Transactional
    public void updateEvalScores(String sessionId, String evalScoresJson) {
        historyRepo.findBySessionId(sessionId).ifPresent(history -> {
            history.setEvalScores(evalScoresJson);
            historyRepo.save(history);
            log.info("[LongMem] 评估分数已更新: sessionId={}", sessionId);
        });
    }

    /**
     * 删除研究历史记录.
     *
     * @param sessionId 会话 ID
     */
    @Transactional
    public void deleteResearchHistory(String sessionId) {
        historyRepo.findBySessionId(sessionId).ifPresent(history -> {
            historyRepo.delete(history);
            log.info("[LongMem] 研究历史已删除: sessionId={}", sessionId);
        });
    }

    /**
     * 获取用户画像实体.
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 用户画像（Optional）
     */
    @Transactional(readOnly = true)
    public Optional<UserProfile> getUserProfile(String userId, String tenantId) {
        return userProfileRepo.findByUserIdAndTenantId(userId, tenantId);
    }

    // =========================== JSON 工具方法 ===========================

    @SuppressWarnings("unchecked")
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            log.warn("[LongMem] JSON 数组解析失败: {}", json);
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("[LongMem] JSON Map 解析失败: {}", json);
            return new HashMap<>();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("[LongMem] JSON 序列化失败", e);
            return "[]";
        }
    }
}
