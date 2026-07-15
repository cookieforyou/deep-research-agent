package com.example.deepresearch.common.constant;

/**
 * Agent 类型枚举.
 * <p>
 * 定义系统中 8 个 Agent 的类型标识，用于日志、监控和配置。
 * </p>
 */
public enum AgentType {

    /** 意图路由器 (Flash, T=0.0) */
    INTENT_ROUTER("IntentRouter", "flash"),

    /** 规划师 (Pro, T=0.3) */
    PLANNER("Planner", "pro"),

    /** 网络侦察 (Flash, T=0.4) */
    WEB_SCOUT("WebScout", "flash"),

    /** 本地知识库侦察 (Flash, T=0.4) */
    LOCAL_SCOUT("LocalScout", "flash"),

    /** 分析师 (Pro, T=0.3) */
    ANALYST("Analyst", "pro"),

    /** 撰稿人 (Pro, T=0.4) */
    WRITER("Writer", "pro"),

    /** 评估器 (Flash, T=0.0) */
    EVAL("Eval", "flash"),

    /** 偏好提取器 (Flash, T=0.1) */
    PREFERENCE_EXTRACTOR("PreferenceExtractor", "flash");

    private final String displayName;
    private final String modelTier;  // "pro" or "flash"

    AgentType(String displayName, String modelTier) {
        this.displayName = displayName;
        this.modelTier = modelTier;
    }

    public String getDisplayName() { return displayName; }
    public String getModelTier() { return modelTier; }

    /** 是否使用 Pro 模型 */
    public boolean isProModel() { return "pro".equals(modelTier); }
}
