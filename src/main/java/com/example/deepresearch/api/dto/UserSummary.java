package com.example.deepresearch.api.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.deepresearch.memory.entity.UserProfile;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 用户摘要 DTO — 管理员用户列表返回体。
 * 将 UserProfile 的 JSON TEXT 字段解析为结构化 Java 对象。
 */
public record UserSummary(
    String userId,
    String tenantId,
    int researchCount,
    List<String> interests,
    Map<String, Object> preferences,
    List<String> recentTopics,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static UserSummary from(UserProfile profile) {
        return new UserSummary(
            profile.getUserId(),
            profile.getTenantId(),
            profile.getResearchCount(),
            parseJsonArray(profile.getInterests()),
            parseJsonMap(profile.getPreferences()),
            parseJsonArray(profile.getRecentTopics()),
            profile.getCreatedAt(),
            profile.getUpdatedAt()
        );
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return Collections.emptyMap();
        }
        try {
            return mapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }
}
