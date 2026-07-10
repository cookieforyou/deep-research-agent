# CLAUDE.md — DeepResearch Multi-Agent 行业深度研究助手

> Claude Code 项目上下文文件。记录项目架构、约定和开发指南。

## 项目概述

基于 **Spring AI 2.0.0 GA + DeepSeek V4 + LangGraph4j** 的企业级 AI 多智能体深度研究系统。
核心能力：意图路由 → 任务规划 → 双源并行检索(Web+Local RAG) → 去重过滤 → 分析归纳 → 报告撰写 → 异步质量评估。

## 技术栈

| 组件 | 版本 | 用途 |
|:---|:---|:---|
| Spring Boot | 4.1.0 | 应用框架 |
| Spring AI | 2.0.0 GA | AI 集成（DeepSeek 原生） |
| LangGraph4j | 1.8.20 | Agent 工作流编排 |
| Java | 21+ | 运行环境（虚拟线程） |
| DeepSeek V4 | Pro + Flash | 两层 LLM 模型 |
| Milvus | 自建 | 向量数据库 + 语义记忆 |
| PostgreSQL | - | 关系型数据 + 长期记忆 |
| Redis | - | 短期记忆 + 语义缓存 |
| Bocha Search | - | AI 搜索引擎 |
| DashScope | text-embedding-v3 | 文本向量化（1024维） |
| Maven | - | 构建工具 |

## 项目结构

```
src/main/java/com/example/deepresearch/
├── DeepResearchApplication.java            # Spring Boot 主入口
├── api/                                    # 接口层
│   ├── controller/ResearchController.java  # REST + SSE
│   └── dto/                                # 请求/响应 DTO
├── agent/                                  # 智能体层（7 个 Agent）
│   ├── bundle/AgentBundle.java             # ChatClient Bean 配置（两层模型+降级）
│   ├── bundle/ModelFallbackService.java    # 模型降级服务 (Pro→Flash CircuitBreaker)
│   ├── intent/IntentRouterAgent.java       # 意图路由 (Flash T=0.0)
│   ├── planner/PlannerAgent.java           # 任务规划 (Pro T=0.3 → Flash fallback)
│   ├── scout/WebScoutAgent.java            # 网络取证 (Flash T=0.4) — 并行
│   ├── scout/LocalScoutAgent.java          # 本地 RAG 取证 (Flash T=0.4) — 并行
│   ├── analyst/AnalystAgent.java           # 分析归纳 (Flash T=0.2)
│   ├── writer/WriterAgent.java             # 报告撰写 (Pro T=0.4 → Flash fallback)
│   └── eval/EvalAgent.java                 # 异步报告质量评估 (Flash T=0.05)
├── workflow/                               # 编排层
│   ├── ResearchWorkflow.java               # LangGraph4j StateGraph 定义
│   └── state/ResearchState.java            # 状态定义（AgentState 子类）
├── memory/                                 # 记忆系统（三层架构）
│   ├── MemoryManager.java                  # 统一入口
│   ├── ShortTermMemoryService.java         # Redis 会话记忆
│   ├── SemanticMemoryService.java          # Milvus 语义记忆（L2 自生长）
│   ├── LongTermMemoryService.java          # PG 用户画像
│   ├── entity/                             # JPA 实体（UserProfile, ResearchHistory）
│   └── repository/                         # Spring Data 仓库
├── rag/                                    # RAG 检索
│   ├── VectorStoreService.java             # Milvus 封装（检索+插入+删除）
│   ├── MilvusConfig.java                   # Milvus 连接配置
│   ├── DocumentIngestionService.java       # 文档 ETL（L3 用户注入层）
│   └── CitationValidator.java              # 引用合法性校验
├── cache/                                  # 缓存层
│   └── SemanticCacheService.java           # 语义缓存（Milvus 向量相似度 + PG 报告检索）
├── tool/                                   # 工具层
│   ├── search/SearchTool.java              # 搜索接口
│   ├── search/BochaSearchTool.java         # Bocha 搜索实现
│   ├── search/FallbackSearchTool.java      # 降级搜索 (Tavily API)
│   ├── search/ResilientSearchTool.java     # 韧性搜索装饰器 (CircuitBreaker+Fallback)
│   ├── EvidenceScorer.java                 # 规则评分器
│   └── EvidenceDeduplicationService.java   # 代码级去重过滤
├── security/                               # 安全认证 + 输入防护
│   ├── SecurityConfig.java                 # WebFlux Security
│   ├── TenantJwtAuthenticationConverter.java
│   ├── TenantContext.java                  # ThreadLocal 租户上下文
│   ├── PiiMaskingService.java              # PII 可逆标记化（手机/身份证/邮箱/银行卡）
│   ├── PiiMaskingAdvisor.java              # Spring AI Advisor — ChatClient 透明拦截
│   ├── PromptInjectionChecker.java         # Prompt 注入检测（复合评分规则引擎）
│   └── SecurityLogService.java             # 安全事件日志（独立 SECURITY Logger）
├── service/                                # 业务服务
│   ├── ResearchOrchestratorService.java    # 研究编排入口
│   └── ProgressEventPublisher.java         # SSE 事件发布
└── common/                                 # 公共组件
    ├── config/                             # Spring 配置
    ├── exception/                          # 全局异常处理
    ├── model/                              # 领域模型（Record）
    ├── util/JsonParseUtils.java            # LLM JSON 安全解析
    ├── util/PromptSplitUtils.java          # Prompt System/User 分离工具
    ├── observability/TokenUsageTracker.java
    └── constant/AgentType.java

src/main/resources/
├── application.yml                         # 主配置（含 PII/注入/缓存/降级）
└── prompts/                                # 10 个 Prompt 模板（.st，含 2 个已废弃）
```

## 工作流拓扑（单轮 DAG，无循环）

```
START → intent_route ──[direct]──→ direct_answer → END
            │
            └──[research]──→ plan → dual_search → filter → analyze → write → END
```

6 个节点 + 1 个条件路由，无 Reflect 循环，单轮完成。EvidenceJudge 已由代码级 `EvidenceDeduplicationService` 替代。Writer 完成后异步触发 EvalAgent 进行报告质量评估（fire-and-forget，不阻塞主流程）。

**语义缓存加速路径**：在 `plan` 之前插入缓存检查，命中时跳过全部 Agent 直接返回。

| 节点 | Agent | 模型 | 说明 |
|:---|:---|:---|:---|
| intent_route | IntentRouterAgent | Flash T=0.0 | 意图分类 (direct/research) |
| [cache] | SemanticCacheService | - | 语义缓存检查（Milvus 向量相似度 > 阈值 → PG 获取完整报告） |
| direct_answer | ChatClient 直接调用 | Flash | 简单回答（不走研究流程） |
| plan | PlannerAgent | Pro T=0.3 → Flash fallback | 任务拆解+搜索计划（接收 memoryContext） |
| dual_search | WebScoutAgent + LocalScoutAgent | Flash T=0.4 | 双源全并行检索（ResilientSearchTool: Bocha→Tavily→纯LocalRAG） |
| filter | EvidenceDeduplicationService | 代码级 | URL/标题去重+域名过滤+评分截断 |
| analyze | AnalystAgent | Flash T=0.2 | 结论形成+完备性评估 |
| write | WriterAgent + CitationValidator | Pro T=0.4 → Flash fallback | 报告撰写+引用合法性校验 |
| [async:eval] | EvalAgent | Flash T=0.05 | 5维度质量评估（相关性/连贯性/引用准确性/完备性/简洁性），结果写入 ResearchHistory |

## 三层记忆架构

| 层级 | 存储 | 范围 | 实现类 | 状态 |
|:---|:---|:---|:---|:---|
| L1 短期 | Redis | 单会话 | `ShortTermMemoryService` | ✅ 运行中 |
| L2 语义 | Milvus | 跨会话/租户 | `SemanticMemoryService` | ✅ 运行中 |
| L3 长期 | PostgreSQL | 跨会话/用户 | `LongTermMemoryService` | ✅ 运行中 |

**数据流**：
- 研究前：`MemoryManager.buildMemoryContext()` → 三元组 Mono.zip（短期+语义+长期）→ 注入 Planner
- 研究前：[缓存检查] `SemanticCacheService.checkCache()` → Milvus 高阈值相似度检索 → 命中时从 PG 获取完整报告直接返回
- 研究后：`MemoryManager.indexResearchToSemanticMemory()` → 报告分块 → 向量化 → 写入 Milvus

语义记忆使用 `doc_type == "research_history"` 与用户上传文档（L3 层）隔离。
语义缓存复用同一 Milvus 集合，通过 `session_id` 字段关联 PG 获取完整报告。

## 关键约定

### 代码风格
- **Java Records** 用于领域模型（不可变性 + Jackson 3 原生支持）
- **LangGraph4j AgentState** 用于工作流状态（Channel 语义）
- 所有 LLM JSON 输出通过 `JsonParseUtils.safeParse()` 解析
- Agent Bean 通过 `@Qualifier` 注入（pro/flash 两层）
- 并行检索使用 `CompletableFuture.supplyAsync()` + `ExecutorService`（虚拟线程）

### Agent 设计
- 每个 Agent 是独立的 `@Service`，封装一个 `ChatClient`
- 7 个 Agent 分属两层模型：2 个 Pro（Planner/Writer）+ 5 个 Flash（IntentRouter/WebScout/LocalScout/Analyst/Eval）
- 每个 Agent 有独立的 Prompt 模板文件（`prompts/*.st`）
- 每个 Agent 有 JSON Fallback 默认值（保证 LLM 解析失败不崩溃）
- WebScoutAgent 和 LocalScoutAgent 均使用全并行模式（虚拟线程）

### 工作流
- 使用 LangGraph4j StateGraph（非 Spring StateMachine）
- 节点返回 `Map<String, Object>` 增量更新（非完整 State）
- `Channels.appender()` 用于跨迭代累积字段
- 条件路由：intent（direct/research）
- **单轮模式**：无 reflect 循环，`maxIterations=1`

### 多租户
- `ResearchState.tenantId` 贯穿全流程
- Milvus 检索时 `FilterExpression` 强制注入 `tenant_id`
- JWT claims 中提取 `tenant_id` → `TenantContext` ThreadLocal
- 语义记忆额外过滤 `doc_type == "research_history"` 与知识库文档隔离

### 安全
- WebFlux 响应式安全（非 Servlet）
- JWT Bearer Token 无状态认证
- 禁用 Session 和 CSRF
- **PII 脱敏**: 基于 Spring AI `BaseAdvisor` 的可逆标记化（零泄露到 DeepSeek API，内部存储保留原文）
  - 支持类型: 手机号、身份证、邮箱、银行卡
  - `PiiMaskingAdvisor.before()` 标记化 → 外部 API 只看到 `<PHONE_0>` 等令牌
  - `PiiMaskingAdvisor.after()` 还原 → 用户体验无缝
  - `PiiMaskingService` 维护 ConcurrentHashMap Vault，确定性令牌映射
- **Prompt 注入防护**: Controller 层前置拦截 + 复合评分规则引擎
  - 7 类正则模式（中英文指令忽略/角色重定义/索取系统信息/DAN越狱）
  - 12 个可配置黑名单关键词
  - 长度异常 + 字符重复弱信号累积评分（阈值 0.5）
  - 检测到注入 → 400 Bad Request，不透露检测细节
- **架构级防护**: 所有 7 个 Agent + directAnswer 节点使用 `chatClient.prompt().system().user()` 分离，利用 DeepSeek V4 原生角色隔离

## 已知问题与修复记录

| 问题 | 状态 | 修复日期 |
|:---|:---|:---|
| `chunkText()` 死循环（末尾文本 < overlap 时 start 不前进） | ✅ 已修复 | 2026-07-07 |
| Milvus score `Float→Double` ClassCastException | ✅ 已修复 | 2026-07-07 |
| `PlannerAgent` 漏掉 `{{current_time}}` 替换 | ✅ 已修复 | 2026-07-07 |
| `LocalScoutAgent.milvusLikelyEmpty` 单例状态泄漏 | ✅ 已修复 | 2026-07-07 |
| `VectorStoreService` 批量 embedding API 兼容问题 | ✅ 改为逐个嵌入 | 2026-07-07 |
| Bocha 并发 6 过高导致 50% 重试 | ✅ 降为 Semaphore(4) | 2026-07-07 |
| `LocalScoutAgent` 串行执行性能差 | ✅ 改为全并行 | 2026-07-08 |
| 语义缓存在 `startResearch` 中集成 + Milvus schema 扩展 (session_id/query/chunk_index) | ✅ 已完成 | 2026-07-08 |
| 模型降级策略 (Pro→Flash) | ✅ 已完成 — ModelFallbackService + CircuitBreaker | 2026-07-08 |
| 搜索熔断+备用引擎 (Bocha→Tavily) | ✅ 已完成 — ResilientSearchTool + FallbackSearchTool(Tavily) | 2026-07-08 |
| LLM 评估 (EvalAgent) | ✅ 已完成 — Flash T=0.05 异步评估，5维度评分+滑动窗口告警 | 2026-07-09 |
| WriterAgent `usedCitations` NPE | ✅ 已修复 — LLM JSON 缺少字段时 null-safe 兜底 | 2026-07-09 |
| EvalAgent LLM 返回空内容 | ✅ 已修复 — 报告截断4000字+sourceIndex截断+T=0.05 | 2026-07-09 |
| PII 脱敏（可逆标记化） | ✅ 已完成 — PiiMaskingService + PiiMaskingAdvisor (Spring AI BaseAdvisor) | 2026-07-09 |
| Prompt 注入防护（规则引擎） | ✅ 已完成 — PromptInjectionChecker 复合评分 + Controller 前置拦截 | 2026-07-09 |
| System/User 消息分离 | ✅ 已完成 — 全部 7个 Agent + directAnswer 节点架构级防护 | 2026-07-09 |
| CHINESE_OUTPUT_SYSTEM_PATTERN 回溯缺陷 | ✅ 已修复 — `.{0,15}?` 替代嵌套交替，消除 Java regex 回溯bug | 2026-07-09 |

### Milvus 集合 Schema Migration 注意
语义缓存功能需要 `session_id`、`query`、`chunk_index` 三个字段。如果 Milvus 集合是 2026-07-08 之前创建的，需重建：
```bash
# Milvus CLI 或 Python SDK:
# Collection("deep_research_kb").drop()
# 重启应用自动创建新版 schema（11 个字段含 3 个新字段）
```

## 常见任务

### 添加新 Agent
1. 在 `agent/` 下创建新包和 Service 类
2. 在 `AgentBundle.java` 注册 ChatClient Bean
3. 在 `prompts/` 下创建 `.st` Prompt 模板
4. 在 `ResearchWorkflow.java` 中添加 Node 和 Edge
5. 在 `AgentType.java` 添加枚举值

### 调试工作流
- 查看日志：`logs/research.log`，每个 Agent/Service 有独立日志前缀
- 设置 `logging.level.com.example.deepresearch=TRACE`
- 观察 SSE 事件流（`curl -N http://localhost:8080/api/research/{id}/stream`）
- LangGraph4j 支持 `.compile().getGraph()` 导出 DOT 格式可视化

### 调整 Prompt
- 直接编辑 `src/main/resources/prompts/*.st`，无需重新编译
- 重启应用后生效（或配置 Spring DevTools 热加载）

### 检查语义记忆
- Milvus 中 `doc_type == "research_history"` 的记录即语义记忆
- 每次研究完成后自动写入分块向量
- 下次研究时通过 `SemanticMemoryService.searchSimilarHistory()` 检索

### 调整 PII 脱敏规则
- 正则模式定义在 `PiiMaskingService.java` 中（`PHONE_PATTERN`、`ID_CARD_PATTERN`、`EMAIL_PATTERN`、`BANK_CARD_PATTERN`）
- 启用/禁用: `application.yml` → `deep-research.pii.enabled`
- 令牌格式: `<PHONE_N>`、`<EMAIL_N>`、`<ID_CARD_N>`、`<BANK_CARD_N>`
- Vault 通过 `PiiMaskingService.getVaultSnapshot()` 可查看当前令牌映射

### 调整 Prompt 注入规则
- 关键词黑名单: `application.yml` → `deep-research.injection.blocked-keywords`
- 正则模式: `PromptInjectionChecker.java` → 7 个 `*_PATTERN` 常量
- 评分阈值: 复合评分 ≥ 0.5 判定为注入（可在 `check()` 方法中调整）
- 强信号（指令覆盖模式）命中即拦截，不参与评分

## 环境变量

| 变量 | 说明 | 默认值 |
|:---|:---|:---|
| `DEEPSEEK_API_KEY` | DeepSeek API Key | - |
| `DASHSCOPE_API_KEY` | DashScope Embedding API Key | 同 DEEPSEEK |
| `MILVUS_HOST` | Milvus 服务地址 | localhost |
| `MILVUS_PORT` | Milvus 端口 | 19530 |
| `BOCHA_API_KEY` | Bocha 搜索 API Key | - |
| `PG_URL` | PostgreSQL 连接 URL | jdbc:postgresql://localhost:5432/deep_research |
| `REDIS_HOST` | Redis 服务地址 | localhost |
