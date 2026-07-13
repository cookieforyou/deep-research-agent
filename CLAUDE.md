# CLAUDE.md — DeepResearch Multi-Agent 行业深度研究助手

> Claude Code 项目上下文文件。记录项目架构、约定和开发指南。

## 项目概述

基于 **Spring AI 2.0.0 GA + DeepSeek V4 + LangGraph4j** 的企业级 AI 多智能体深度研究系统。

核心能力：意图路由 → 任务规划 → 双源并行检索(Web+Local RAG @Tool) → 去重过滤 → 分析归纳 → 报告撰写 → 异步质量评估。

全面对齐 Spring AI 2.0 企业级最佳实践（`@Tool` 工具调用、`.entity()` 结构化输出、Advisor 链治理、DynamicPrompt 热更新）。

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
| Prometheus | v3.3.0 | 指标采集 + 告警 |
| Grafana | 11.6.0 | 可视化仪表盘 |
| OpenTelemetry | Collector 0.123.0 | Metrics/Traces 导出 |
| Jaeger | 1.67.0 | 分布式追踪 UI |
| Resilience4j | 2.3.0 | 熔断/重试 + Micrometer 集成 |
| Maven | - | 构建工具 |

## 项目结构

```
src/main/java/com/example/deepresearch/
├── DeepResearchApplication.java                # Spring Boot 主入口
├── api/                                        # 接口层 (WebFlux + SSE)
│   ├── controller/ResearchController.java      # REST 研究接口
│   └── dto/                                    # ResearchRequest, ResearchResponse, ProgressEvent
├── agent/                                      # 智能体层（7 个 Agent + 工具/配置）
│   ├── bundle/AgentBundle.java                 # ChatClient Bean 工厂（5 Advisor 全链 + 两层模型）
│   ├── bundle/EnterpriseChatClientConfig.java  # 高级 ChatClient（含记忆+RAG 的 7 Advisor 完整链）
│   ├── bundle/ModelFallbackService.java        # 模型降级 (Pro→Flash CircuitBreaker + 泛型 entity)
│   ├── intent/IntentRouterAgent.java           # 意图路由 (Flash T=0.0, .entity)
│   ├── planner/PlannerAgent.java               # 任务规划 (Pro T=0.3 → Flash fallback, .entity)
│   ├── scout/WebScoutAgent.java                # 网络取证 (Flash T=0.4, LLM @Tool webSearch)
│   ├── scout/LocalScoutAgent.java              # 本地 RAG 取证 (Flash T=0.4, LLM @Tool localSearch)
│   ├── tool/SearchTools.java                   # @Tool 工具集 (webSearch + localSearch)
│   ├── analyst/AnalystAgent.java               # 分析归纳 (Flash T=0.2, .entity)
│   ├── writer/WriterAgent.java                 # 报告撰写 (Pro T=0.4 → Flash fallback, .entity)
│   └── eval/EvalAgent.java                     # 异步评估 (Flash T=0.05, .entity)
├── workflow/                                   # LangGraph4j 编排层
│   ├── ResearchWorkflow.java                   # StateGraph 定义（单轮 DAG，6 节点）
│   └── state/ResearchState.java                # 工作流状态（AgentState 子类）
├── memory/                                     # 三层记忆系统
│   ├── MemoryManager.java                      # 统一入口（三元组 Mono.zip）
│   ├── ShortTermMemoryService.java             # L1 Redis 会话记忆
│   ├── SemanticMemoryService.java              # L2 Milvus 语义记忆（自生长）
│   ├── LongTermMemoryService.java              # L3 PG 长期用户画像
│   ├── RedisChatMemoryAdapter.java             # Spring AI ChatMemory 适配器
│   ├── entity/                                 # JPA 实体（UserProfile, ResearchHistory, PromptTemplateEntity）
│   └── repository/                             # Spring Data 仓库（含 PromptTemplateRepository）
├── rag/                                        # RAG 检索增强
│   ├── VectorStoreService.java                 # Milvus SDK 封装（检索+插入+删除，租户隔离）
│   ├── MilvusVectorStoreAdapter.java           # Spring AI VectorStore 适配器
│   ├── AdvancedRagService.java                 # 高级 RAG（查询改写 + QuestionAnswerAdvisor）
│   ├── MilvusConfig.java                       # Milvus 连接配置
│   ├── DocumentIngestionService.java           # 文档 ETL（TikaDocumentReader + TokenTextSplitter）
│   └── CitationValidator.java                  # 引用合法性校验
├── cache/                                      # 缓存层
│   └── SemanticCacheService.java               # 语义缓存（Milvus 相似度 → PG 报告检索）
├── tool/                                       # 搜索工具层
│   ├── search/SearchTool.java                  # 搜索接口
│   ├── search/BochaSearchTool.java             # Bocha AI 搜索
│   ├── search/FallbackSearchTool.java          # 降级搜索 (Tavily API)
│   ├── search/ResilientSearchTool.java         # 韧性搜索 (CircuitBreaker+Bocha→Tavily→纯LocalRAG)
│   ├── EvidenceScorer.java                     # 规则评分器（域名权威度）
│   └── EvidenceDeduplicationService.java       # 代码级去重过滤
├── security/                                   # 企业级安全防护
│   ├── SecurityConfig.java                     # WebFlux Security（JWT + 禁用 Session/CSRF）
│   ├── TenantJwtAuthenticationConverter.java   # JWT → TenantContext 转换
│   ├── TenantContext.java                      # ThreadLocal 多租户上下文
│   ├── TokenBudgetAdvisor.java                 # [200] Token 预算管控（Redis 分布式限流 100次/h/用户）
│   ├── PiiMaskingAdvisor.java                  # [300] PII 脱敏（BaseAdvisor 可逆标记化）
│   ├── PiiMaskingService.java                  # PII 正则检测 + ConcurrentHashMap Vault
│   ├── OutputGuardrailAdvisor.java             # [300] 输出安全护栏（敏感词拦截+兜底文案）
│   ├── AuditLogAdvisor.java                    # [100] 审计日志（AUDIT Logger）
│   ├── PromptInjectionChecker.java             # Prompt 注入检测（复合评分规则引擎）
│   ├── SecurityLogService.java                 # 安全事件日志（SECURITY Logger）
│   └── ApprovalService.java                    # HITL 人工审批预留接口
├── service/                                    # 业务服务
│   ├── ResearchOrchestratorService.java        # 研究编排入口（缓存检查+工作流启动）
│   ├── DynamicPromptService.java               # Prompt 动态管理（DB优先→缓存→classpath兜底，1min TTL）
│   └── ProgressEventPublisher.java             # SSE 事件发布
└── common/                                     # 公共组件
    ├── config/                                 # Spring 配置（App, DeepResearch, Jackson, VirtualThread, WebFlux, HttpClient, Observability）
    ├── constant/AgentType.java                 # Agent 枚举（7 个 Agent + modelTier）
    ├── exception/                              # 全局异常处理（GlobalExceptionHandler, ResearchException）
    ├── model/                                  # 领域模型 — Java Records（AnalysisResult, EvalResult, Evidence, Finding, PlanResult, SearchPlan, SearchResult, WriteResult, AuditFlag）
    ├── util/JsonParseUtils.java                # LLM JSON 安全解析（修复尾逗号/未闭合括号/中文引号）
    ├── util/PromptSplitUtils.java              # Prompt System/User 分离
    └── observability/                          # 可观测性（Metrics + Tracing + Token 监控）
        ├── TokenUsageTracker.java              # LLM Token/成本/延迟指标注册
        ├── TokenTrackingAdvisor.java           # BaseAdvisor — 透明拦截所有 ChatClient 调用
        ├── BusinessMetrics.java                # 业务指标集中注册（搜索/缓存/安全/工作流）
        └── WorkflowTracingHelper.java          # 工作流节点 Tracing（Span + MDC traceId）

observability/                                  # 可观测性基础设施（Docker Compose 一键部署）
├── docker-compose.yml                          # Prometheus + Grafana + OTel Collector + Jaeger
├── prometheus/
│   ├── prometheus.yml                          # 双源抓取（App 直连 /actuator/prometheus + OTel Collector）
│   └── rules/deepresearch-alerts.yml           # 11 条告警规则
├── grafana/
│   ├── datasources/datasource.yml              # Prometheus 数据源自动配置
│   └── dashboards/                             # 4 个仪表盘 JSON（46 面板，含 Spring AI 内置指标）
│       ├── llm-overview.json                   # LLM Token/成本/延迟 + Spring AI ChatClient 调用速率
│       ├── workflow-performance.json           # 工作流/搜索/缓存 + 工具调用/向量检索耗时
│       ├── security-monitoring.json            # PII/注入/CB/Eval + 搜索降级趋势
│       └── system-resources.json               # JVM/GC/HTTP/CPU/线程
└── otel-collector/otelcol-config.yml           # OTLP → Traces→Jaeger, Metrics→Prometheus

src/main/resources/
├── application.yml                             # 主配置（DeepSeek/OpenAI/Embedding/MCP/安全/缓存/降级/可观测性）
└── prompts/                                    # 8 个 Prompt 模板（.st，DynamicPromptService 数据库优先加载）
    ├── intent-router.st
    ├── planner.st
    ├── web-scout.st                            # @Tool 模式
    ├── local-scout.st                          # @Tool 模式
    ├── analyst.st
    ├── writer.st
    ├── direct-answer.st
    └── eval.st

docs/
├── spring-ai-2-0/                              # Spring AI 2.0 参考文档（6 个）
│   ├── introduction/                           # 介绍 + 深度分析 + 全景分析
│   └── practices/                              # 企业级落地最佳实践
├── optimization/                               # 优化记录
│   ├── Spring AI 2.0 项目适配优化清单.md          # 16 项优化分析
│   ├── Spring AI 2.0 项目优化实施方案与更新记录.md  # 8 轮实施记录 + 最终合规状态（38/38 ✅）
│   └── sql/init_prompt_templates.sql           # Prompt 模板 DB 初始化（8 模板，ON CONFLICT 可重复执行）
└── day0/
    ├── 可观测性功能开发实现报告.md
    └── 需求分析与技术实现报告.md
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
| intent_route | IntentRouterAgent | Flash T=0.0 | 意图分类 (direct/research)，`.entity(RouteResult.class)` |
| [cache] | SemanticCacheService | - | 语义缓存检查（Milvus 向量相似度 > 阈值 → PG 获取完整报告） |
| direct_answer | ChatClient 直接调用 | Flash | 简单回答（不走研究流程），DynamicPromptService 加载模板 |
| plan | PlannerAgent | Pro T=0.3 → Flash fallback | 任务拆解+搜索计划（接收 memoryContext），`.entity(PlanResult.class)` |
| dual_search | WebScoutAgent + LocalScoutAgent | Flash T=0.4 | 双源全并行检索（LLM 自主调用 @Tool webSearch/localSearch） |
| filter | EvidenceDeduplicationService | 代码级 | URL/标题去重+域名过滤+评分截断 |
| analyze | AnalystAgent | Flash T=0.2 | 结论形成+完备性评估，`.entity(AnalysisResult.class)` |
| write | WriterAgent + CitationValidator | Pro T=0.4 → Flash fallback | 报告撰写+引用合法性校验，`.entity(WriteResult.class)` |
| [async:eval] | EvalAgent | Flash T=0.05 | 5维度质量评估（相关性/连贯性/引用准确性/完备性/简洁性），`.entity(EvalResult.class)` |

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
- 所有 LLM JSON 输出通过 `.call().entity(Record.class)` 自动解析（Spring AI 2.0 结构化输出 + 自校正）
- `JsonParseUtils` 保留作为极端边缘情况兜底（修复尾逗号/未闭合括号/中文引号）
- Agent Bean 通过 `@Qualifier` 注入（pro/flash 两层）
- 并行检索使用 `CompletableFuture.supplyAsync()` + `ExecutorService`（虚拟线程）

### Agent 设计
- 每个 Agent 是独立的 `@Service`，封装一个 `ChatClient`
- 7 个 Agent 分属两层模型：2 个 Pro（Planner/Writer）+ 5 个 Flash（IntentRouter/WebScout/LocalScout/Analyst/Eval）
- 每个 Agent 的 Prompt 模板通过 `DynamicPromptService.getTemplateContent("id")` 加载（DB优先 + classpath兜底，1分钟缓存TTL，支持热更新）
- 每个 Agent 使用 `.call().entity(Record.class)` 实现结构化输出（替代手动 JSON 解析）
- WebScoutAgent 和 LocalScoutAgent 使用 LLM 自主调用 `@Tool`（webSearch/localSearch），工具通过 `AgentBundle.defaultTools()` 注册
- `ModelFallbackService` 提供泛型 `<T>` 降级方法，内置 CircuitBreaker 保护

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
- **企业级 Advisor 链**（AgentBundle 统一装配，所有 ChatClient 自动启用）：
  - `TokenBudgetAdvisor` [200] — Redis INCR+EXPIRE 分布式限流（100次/小时/用户）
  - `PiiMaskingAdvisor` [300] — 输入 PII 可逆标记化
  - `OutputGuardrailAdvisor` [300] — 输出敏感词拦截 + 安全兜底文案
  - `TokenTrackingAdvisor` [900] — LLM Token 用量追踪（Micrometer 指标）
  - `AuditLogAdvisor` [100] — AUDIT Logger 审计日志（agent/userId/status/latency）
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

### 可观测性

#### Metrics 指标体系

所有指标通过 Micrometer → Prometheus 暴露在 `/actuator/prometheus`，Grafana 仪表盘实时可视化。

| 指标类别 | 指标前缀 | 注册位置 | 录入方式 |
|:---|:---|:---|:---|
| LLM 调用 | `deepresearch.llm.*` | `TokenUsageTracker` | `TokenTrackingAdvisor`（BaseAdvisor，零侵入） |
| 搜索 | `deepresearch.search.*` | `BusinessMetrics` | `ResilientSearchTool` |
| 缓存 | `deepresearch.cache.*` | `BusinessMetrics` | `SemanticCacheService` |
| PII 脱敏 | `deepresearch.security.pii.*` | `BusinessMetrics` | `PiiMaskingService` |
| 注入检测 | `deepresearch.security.injection.*` | `BusinessMetrics` | `PromptInjectionChecker` |
| 工作流 | `deepresearch.workflow.*` | `BusinessMetrics` + `WorkflowTracingHelper` | `ResearchOrchestratorService` |
| Eval 评分 | `deepresearch.eval.score` | `ObservabilityConfig` (Gauge Bean) | `EvalAgent` |
| CB 状态 | `resilience4j.circuitbreaker.*` | resilience4j-micrometer 自动 | `CircuitBreakerRegistry` |

#### 分布式 Tracing

- `WorkflowTracingHelper` 使用 `Tracer.nextSpan()` 创建 Span（当 Tracer Bean 可用时），或生成 UUID 写入 MDC `%X{traceId}`（本地兼容模式）
- 所有 7 个工作流节点 + 搜索操作均已包裹
- traceId 贯穿日志 → Prometheus → Jaeger 全链路
- OTLP 导出端点: `http://localhost:4318`（需设置 `OTEL_METRICS_ENABLED=true`）

#### Docker Compose 可观测性栈

```bash
cd observability && docker compose up -d
# 启动 4 个服务: Prometheus(:9090) + Grafana(:3000) + OTel Collector(:4318) + Jaeger(:16686)
```

#### 告警规则 (11 条)

| # | 告警名 | 严重度 | 条件 |
|:---|:---|:---|:---|
| 1 | `LLMLatencyHigh` | warning | P95 Pro > 60s |
| 1b | `LLMLatencyFlashHigh` | info | P95 Flash > 30s |
| 2 | `ResearchTokenCostHigh` | warning | 15min Token 速率 > 200K |
| 3 | `SearchFallbackRateHigh` | critical | Bocha→Tavily 降级率 > 50% |
| 4 | `CacheHitRateLow` | info | 命中率 < 10% |
| 5 | `PiiDetectionSpike` | warning | PII 脱敏 > 10/min |
| 6 | `InjectionDetectionSpike` | critical | 注入检测 > 5/min |
| 7 | `WorkflowExecutionFailure` | critical | 工作流 error 状态 |
| 8 | `EvalScoreLow` | warning | 评估分 < 3.0 |
| 9 | `CircuitBreakerOpen` | critical | CB OPEN > 2min |
| 10 | `ToolCallFailureRateHigh` | warning | @Tool 失败率 > 30% |
| 11 | `ChatClientErrorRateHigh` | warning | ChatClient 非成功状态 > 10% |

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
| 可观测性 Phase 1-3（Metrics + Tracing + Token 监控） | ✅ 已完成 — Prometheus + TokenTrackingAdvisor + WorkflowTracingHelper + BusinessMetrics | 2026-07-09 |
| PII 搜索结果误匹配（~27次/run） | ✅ 已修复 — 数学校验 (GB 11643 + Luhn) + skipPiiMask 外部数据跳过 | 2026-07-09 |
| Planner JSON 解析失败（LLM 尾逗号） | ✅ 已修复 — JsonParseUtils.repairCommonJsonIssues() 二次解析 | 2026-07-09 |
| 可观测性 Phase 4（Grafana + Docker Compose + 告警） | ✅ 已完成 — 4 仪表盘 46 面板 + 11 告警规则 + OTel Collector + Jaeger | 2026-07-10 |
| 搜索/PII/工作流 Metrics 未接入（BusinessMetrics 定义但遗漏调用） | ✅ 已修复 — ResilientSearchTool + PiiMaskingService + ResearchOrchestratorService 均已接入 | 2026-07-10 |
| `AtomicDouble` 编译错误 (Java 21 不存在) | ✅ 已修复 — 改用 `AtomicReference<Double>` | 2026-07-10 |
| WebScout JSON 解析失败（LLM 遗漏 value 双引号 — 中文括号 `【】` 所致） | ✅ 已修复 — `UNQUOTED_STRING_VALUE` 正则自动补全 | 2026-07-10 |
| WebScout JSON 解析失败（LLM 输出截断 — token 限制导致数组/字符串未闭合） | ✅ 已修复 — `closeUnclosedBrackets()` 栈逆序闭合括号+字符串 | 2026-07-10 |
| **Spring AI 2.0 全面对齐优化**（4轮16项） | ✅ 已完成 | 2026-07-12 |
| —— 配置扁平化 + 代码清理 + `.entity()` 替换 + 文档处理标准化 | ✅ 已完成 — Round 1 | 2026-07-12 |
| —— VectorStore/ChatMemory 适配 + Advisor 链 + Token预算 + 输出护栏 | ✅ 已完成 — Round 2 | 2026-07-12 |
| —— `@Tool` 注解 SearchTools + Scout Agent 重构 | ✅ 已完成 — Round 3 | 2026-07-12 |
| —— 高级RAG + Prompt动态管理 + HITL预留 + Eval测试集 | ✅ 已完成 — Round 4 | 2026-07-12 |
| **后续修复 P0-P3** | ✅ 已完成 | 2026-07-13 |
| —— P0: web-scout.st / local-scout.st 适配 @Tool 模式 + SQL 同步 | ✅ 已修复 | 2026-07-13 |
| —— P1: 全部 Agent 迁移到 DynamicPromptService（删 ~120 行） | ✅ 已修复 | 2026-07-13 |
| —— P2: AgentBundle 升级为 5 个 Advisor 全企业级链 | ✅ 已修复 | 2026-07-13 |
| —— P3: Grafana/Prometheus 补充 Spring AI 内置指标（46面板+11告警） | ✅ 已修复 | 2026-07-13 |
| `.tools(searchTools)` 重复注册导致 IllegalStateException | ✅ 已修复 — 移除 Agent 中重复调用，工具由 defaultTools 统一注册 | 2026-07-13 |

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
2. 在 `AgentBundle.java` 注册 ChatClient Bean（自动继承 5 个 Advisor）
3. 使用 `DynamicPromptService.getTemplateContent("id")` 加载 Prompt（DB优先 + classpath兜底）
4. 在 `prompts/` 下创建 `.st` Prompt 模板
5. 在 `docs/optimization/sql/init_prompt_templates.sql` 添加数据库初始化条目
6. 在 `ResearchWorkflow.java` 中添加 Node 和 Edge
7. 在 `AgentType.java` 添加枚举值

### 调试工作流
- 查看日志：`logs/research.log`，每个 Agent/Service 有独立日志前缀
- 设置 `logging.level.com.example.deepresearch=TRACE`
- 观察 SSE 事件流（`curl -N http://localhost:8080/api/research/{id}/stream`）
- LangGraph4j 支持 `.compile().getGraph()` 导出 DOT 格式可视化

### 调整 Prompt
- **推荐方式**：通过数据库热更新：`UPDATE prompt_templates SET content='新prompt...' WHERE id='writer'`，1 分钟内自动生效（缓存 TTL），无需重启
- **兜底方式**：直接编辑 `src/main/resources/prompts/*.st`，重启应用后生效
- Prompt 加载链：内存缓存（1min TTL）→ PostgreSQL `prompt_templates` 表 → classpath `.st` 文件兜底

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

### 启动可观测性栈
```bash
cd observability && docker compose up -d
# Prometheus: http://localhost:9090
# Grafana:    http://localhost:3000 (admin/admin)
# Jaeger:     http://localhost:16686
# 启用 OTLP 导出: OTEL_METRICS_ENABLED=true mvn spring-boot:run
```

### 验证 Metrics
```bash
# 检查 Prometheus 端点
curl -s http://localhost:8080/actuator/prometheus | grep deepresearch

# 检查 Gatling 指标采集
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {job, health}'

# 检查告警规则
curl -s http://localhost:9090/api/v1/rules | jq '.data.groups[].rules[] | {name, health}'
```

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
| `TAVILY_API_KEY` | Tavily 备用搜索 API Key | - |
| `OTEL_METRICS_ENABLED` | 启用 OTLP Metrics 导出 | false |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTel Collector 地址 | http://localhost:4318 |
| `OTEL_TRACING_SAMPLING` | Tracing 采样率 | 1.0 |
