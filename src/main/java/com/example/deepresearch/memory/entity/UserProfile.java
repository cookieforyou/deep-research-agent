package com.example.deepresearch.memory.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户画像 — 长期记忆的持久化实体.
 * <p>
 * 存储跨会话的用户研究偏好、关注行业和历史主题，
 * 每次新研究启动时通过 Planner Agent 注入上下文。
 * </p>
 *
 * <h3>JSON 字段说明</h3>
 * <ul>
 *   <li>{@code interests} — 关注行业标签（如 ["AI", "新能源汽车", "半导体"]）</li>
 *   <li>{@code recentTopics} — 最近研究主题（最多 10 条）</li>
 *   <li>{@code preferences} — 偏好设置（JSON Map，如 {"reportLength": "detailed", "language": "zh-CN"}）</li>
 * </ul>
 */
@Entity
@Table(name = "user_profile", indexes = {
    @Index(name = "idx_user_tenant", columnList = "userId,tenantId", unique = true)
})
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户 ID（来自 JWT sub claim） */
    @Column(nullable = false, length = 128)
    private String userId;

    /** 租户 ID（多租户隔离） */
    @Column(nullable = false, length = 128)
    private String tenantId;

    /** 关注行业标签（JSON 数组） */
    @Column(columnDefinition = "TEXT")
    private String interests;

    /** 最近研究主题（JSON 数组，最多 10 条） */
    @Column(columnDefinition = "TEXT")
    private String recentTopics;

    /** 偏好设置（JSON Map） */
    @Column(columnDefinition = "TEXT")
    private String preferences;

    /** 累计研究次数 */
    @Column(nullable = false)
    private int researchCount;

    /** 创建时间 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // =========================== 构造器 ===========================

    public UserProfile() {}

    public UserProfile(String userId, String tenantId) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.interests = "[]";
        this.recentTopics = "[]";
        this.preferences = "{}";
        this.researchCount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // =========================== 生命周期回调 ===========================

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // =========================== Getter / Setter ===========================

    public Long getId() { return id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getInterests() { return interests; }
    public void setInterests(String interests) { this.interests = interests; }

    public String getRecentTopics() { return recentTopics; }
    public void setRecentTopics(String recentTopics) { this.recentTopics = recentTopics; }

    public String getPreferences() { return preferences; }
    public void setPreferences(String preferences) { this.preferences = preferences; }

    public int getResearchCount() { return researchCount; }
    public void setResearchCount(int researchCount) { this.researchCount = researchCount; }
    public void incrementResearchCount() { this.researchCount++; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
