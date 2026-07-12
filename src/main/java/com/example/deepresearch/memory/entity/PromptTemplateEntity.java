package com.example.deepresearch.memory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * Prompt 模板实体 — 支持运行时热更新、版本管理和 A/B 测试.
 * <p>
 * 将 Prompt 从 classpath 下的 {@code .st} 文件迁移到数据库管理，
 * 运营人员可通过管理后台修改 Prompt 而无需重启应用。
 * 数据库不可用时自动回退到 classpath 文件（见 {@code DynamicPromptService}）。
 * </p>
 *
 * <h3>Schema（对应 DDL）</h3>
 * <pre>{@code
 * CREATE TABLE prompt_templates (
 *     id VARCHAR(64) PRIMARY KEY,
 *     version INT NOT NULL DEFAULT 1,
 *     content TEXT NOT NULL,
 *     status VARCHAR(16) DEFAULT 'active',
 *     ab_group VARCHAR(8),
 *     created_at TIMESTAMP DEFAULT NOW(),
 *     updated_at TIMESTAMP DEFAULT NOW()
 * );
 * }</pre>
 */
@Entity
@Table(name = "prompt_templates")
public class PromptTemplateEntity {

    /** 模板标识（如 "intent-router", "planner", "writer"） */
    @Id
    @Column(name = "id", length = 64)
    private String id;

    /** 版本号（乐观锁，每次更新 +1） */
    @Version
    @Column(name = "version")
    private Integer version;

    /** Prompt 模板内容（支持 StringTemplate 占位符 {{variable}}） */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 状态: active / inactive / deprecated */
    @Column(name = "status", length = 16)
    private String status;

    /** A/B 测试分组: A / B / null（不参与实验） */
    @Column(name = "ab_group", length = 8)
    private String abGroup;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public PromptTemplateEntity() {}

    // =========================== Getter/Setter ===========================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAbGroup() { return abGroup; }
    public void setAbGroup(String abGroup) { this.abGroup = abGroup; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
