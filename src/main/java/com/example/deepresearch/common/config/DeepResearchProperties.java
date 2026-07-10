package com.example.deepresearch.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * DeepResearch 业务配置属性.
 * <p>
 * 映射 application.yml 中 {@code deep-research.*} 配置段，
 * 提供类型安全的配置访问。
 * </p>
 */
@ConfigurationProperties(prefix = "deep-research")
public record DeepResearchProperties(
    SearchConfig search,
    WorkflowConfig workflow,
    RagConfig rag,
    EvidenceScoringConfig evidenceScoring,
    MemoryConfig memory,
    CacheConfig cache,
    FallbackConfig fallback,
    EvalConfig eval,
    PiiConfig pii,
    InjectionConfig injection
) {

    /**
     * 搜索引擎配置.
     */
    public record SearchConfig(
        BochaConfig bocha,
        FallbackConfig fallback
    ) {
        public record BochaConfig(
            String apiKey,
            String baseUrl,
            int defaultCount,
            java.time.Duration timeout
        ) {}

        public record FallbackConfig(
            boolean enabled
        ) {}
    }

    /**
     * 研究工作流配置.
     */
    public record WorkflowConfig(
        int maxIterations,
        int maxEvidencePerSource,
        int minReportWords,
        java.time.Duration timeout
    ) {}

    /**
     * RAG 检索配置.
     */
    public record RagConfig(
        int topK,
        double similarityThreshold,
        int chunkSize,
        int chunkOverlap,
        java.util.List<String> supportedFormats
    ) {}

    /**
     * 证据评分规则配置.
     */
    public record EvidenceScoringConfig(
        double localKnowledgeBase,
        double governmentEdu,
        double mainstreamMedia,
        double generalWebsite,
        double unknownSource,
        java.util.List<String> authorityDomains
    ) {}

    /**
     * 记忆系统配置.
     */
    public record MemoryConfig(
        ShortTermConfig shortTerm,
        LongTermConfig longTerm
    ) {
        public record ShortTermConfig(
            int windowSize,
            java.time.Duration ttl
        ) {}

        public record LongTermConfig(
            int maxProfileEntries
        ) {}
    }

    /**
     * 语义缓存配置.
     */
    public record CacheConfig(
        boolean enabled,
        double similarityThreshold
    ) {}

    /**
     * 降级配置（模型降级 + 搜索降级）.
     */
    public record FallbackConfig(
        ModelFallbackConfig model,
        TavilyConfig tavily
    ) {
        /**
         * 模型降级配置 (Pro → Flash).
         */
        public record ModelFallbackConfig(
            boolean enabled,
            java.time.Duration maxWait
        ) {}

        /**
         * Tavily 备用搜索引擎配置.
         */
        public record TavilyConfig(
            String apiKey,
            String baseUrl
        ) {}
    }

    /**
     * LLM 评估配置.
     */
    public record EvalConfig(
        boolean enabled,
        double alertThreshold,
        int alertWindowSize
    ) {}

    /**
     * PII 脱敏配置.
     */
    public record PiiConfig(
        boolean enabled
    ) {
        public PiiConfig {
            // enabled defaults to true via application.yml
        }
    }

    /**
     * Prompt 注入防护配置.
     */
    public record InjectionConfig(
        boolean enabled,
        List<String> blockedKeywords,
        int maxQueryLength,
        double repetitionThreshold
    ) {
        public InjectionConfig {
            if (blockedKeywords == null) blockedKeywords = List.of();
            if (maxQueryLength <= 0) maxQueryLength = 2000;
            if (repetitionThreshold <= 0) repetitionThreshold = 0.7;
        }
    }
}
