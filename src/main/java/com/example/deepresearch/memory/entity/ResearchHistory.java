package com.example.deepresearch.memory.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 研究历史 — 记录每次完整研究的元数据.
 * <p>
 * 用于知识库自生长层（L2）：每次研究产出自动入 Milvus 向量库。
 * 同时支持用户历史查询和统计分析。
 * </p>
 */
@Entity
@Table(name = "research_history", indexes = {
    @Index(name = "idx_history_user", columnList = "userId"),
    @Index(name = "idx_history_tenant", columnList = "tenantId"),
    @Index(name = "idx_history_created", columnList = "createdAt")
})
public class ResearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 会话 ID */
    @Column(nullable = false, length = 64, unique = true)
    private String sessionId;

    /** 用户 ID */
    @Column(nullable = false, length = 128)
    private String userId;

    /** 租户 ID */
    @Column(nullable = false, length = 128)
    private String tenantId;

    /** 原始研究查询 */
    @Column(nullable = false, length = 5000)
    private String query;

    /** 最终研报（Markdown 全文，TEXT 类型） */
    @Column(columnDefinition = "TEXT")
    private String report;

    /** 报告字数 */
    @Column(nullable = false)
    private int wordCount;

    /** 合法引用数 */
    private int citationCount;

    /** 反思迭代次数 */
    private int iterationCount;

    /** 状态: COMPLETED / ERROR */
    @Column(nullable = false, length = 32)
    private String status;

    /** 评估分数（JSON 格式，EvalAgent 异步写入，可为 null） */
    @Column(columnDefinition = "TEXT")
    private String evalScores;

    /** 证据池（JSON 数组，研究完成时写入，供前端引用溯源和证据抽屉使用）
     *  示例: [{"sourceId":"WEB01_1","title":"...","url":"...","domain":"...","score":0.85,...}]
     */
    @Column(columnDefinition = "TEXT")
    private String sourceIndex;

    /** 创建时间 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // =========================== 构造器 ===========================

    public ResearchHistory() {}

    public ResearchHistory(String sessionId, String userId, String tenantId,
                           String query, String report, int wordCount,
                           int citationCount, int iterationCount, String status,
                           String sourceIndex) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.tenantId = tenantId;
        this.query = query;
        this.report = report;
        this.wordCount = wordCount;
        this.citationCount = citationCount;
        this.iterationCount = iterationCount;
        this.status = status;
        this.sourceIndex = sourceIndex;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // =========================== Getter / Setter ===========================

    public Long getId() { return id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getReport() { return report; }
    public void setReport(String report) { this.report = report; }

    public int getWordCount() { return wordCount; }
    public void setWordCount(int wordCount) { this.wordCount = wordCount; }

    public int getCitationCount() { return citationCount; }
    public void setCitationCount(int citationCount) { this.citationCount = citationCount; }

    public int getIterationCount() { return iterationCount; }
    public void setIterationCount(int iterationCount) { this.iterationCount = iterationCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getEvalScores() { return evalScores; }
    public void setEvalScores(String evalScores) { this.evalScores = evalScores; }

    public String getSourceIndex() { return sourceIndex; }
    public void setSourceIndex(String sourceIndex) { this.sourceIndex = sourceIndex; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
