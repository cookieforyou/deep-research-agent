-- Prompt 模板管理表 — 支持运行时热更新、版本管理和 A/B 测试
-- 配合 DynamicPromptService 使用，数据库优先 + classpath 文件兜底

CREATE TABLE IF NOT EXISTS prompt_templates (
    id VARCHAR(64) PRIMARY KEY,
    version INT NOT NULL DEFAULT 1,
    content TEXT NOT NULL,
    status VARCHAR(16) DEFAULT 'active',     -- active / inactive / deprecated
    ab_group VARCHAR(8),                      -- A / B / NULL（不参与实验）
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
