# DeepResearch Multi-Agent 行业深度研究助手

[![Java](https://img.shields.io/badge/Java-21+-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0-blue)](https://spring.io/projects/spring-ai)
[![LangGraph4j](https://img.shields.io/badge/LangGraph4j-1.8.20-purple)](https://github.com/langgraph4j/langgraph4j)

基于 **Spring AI 2.0 + DeepSeek V4 + LangGraph4j** 的企业级 AI 多智能体深度研究系统。

全面对齐 Spring AI 2.0 企业级最佳实践（`@Tool` 工具调用、`.entity()` 结构化输出、Advisor 链治理、DynamicPrompt 热更新、Spring AI 内置指标可观测性）。

## 架构概览

```
用户请求 → POST /api/research
              │
              ▼
┌─────────────────────────────────────────────────────────┐
│                    API 层 (WebFlux / SSE)               │
├─────────────────────────────────────────────────────────┤
│              编排层 (LangGraph4j StateGraph)             │
│                                                         │
│  START → intent_route ──[direct]──→ direct_answer → END │
│              │                                          │
│              └──[research]──→ plan → dual_search        │
│                                        │                │
│                                        ▼                │
│                                      filter             │
│                                        │                │
│                                        ▼                │
│                                     analyze             │
│                                        │                │
│                                        ▼                │
│                                      write → END        │
│                                                         │
│  (单轮 DAG，无循环；EvidenceJudge 已由代码级去重替代)        │
├─────────────────────────────────────────────────────────┤
│            智能体层 (7 个 Agent, 两层模型)                 │
│   IntentRouter(Flash)  Planner(Pro)    WebScout(Flash)  │
│   LocalScout(Flash)    Analyst(Flash)   Writer(Pro)     │
│   Eval(Flash) [异步]                                     │
├─────────────────────────────────────────────────────────┤
│             记忆系统 (三层架构)                            │
│   L1 短期 (Redis)  L2 语义 (Milvus)  L3 长期 (PG)         │
├─────────────────────────────────────────────────────────┤
│        基础设施 (DeepSeek V4 / Milvus / PG / Redis)      │
│        可观测性 (Prometheus / Grafana / Jaeger)          │
└─────────────────────────────────────────────────────────┘
```

## 核心特性

- 🤖 **7 个专业 Agent**: 意图路由 → 任务规划 → 双源并行检索(Web+Local RAG @Tool) → 去重过滤 → 分析归纳 → 报告撰写 → 异步质量评估
- 🔧 **Spring AI 2.0 全面对齐**: `@Tool` 工具调用 + `.entity()` 结构化输出 + Advisor 链统一治理 + DynamicPrompt 数据库热更新
- 🛡️ **企业级 Advisor 链**: TokenBudget（分布式限流）→ PiiMask（输入脱敏）→ OutputGuard（输出护栏）→ TokenTrack（用量追踪）→ AuditLog（审计日志），所有 Agent 自动启用
- ⚡ **语义缓存**: 相似 Query 直接返回历史报告（~1.5 秒 vs 完整流程 ~135 秒），基于 Milvus 向量相似度 + PostgreSQL 报告检索
- 📊 **深度研报**: 2000+ 字 Markdown 结构化报告，精确引用溯源（Source ID）
- 🔍 **双源并行检索 @Tool**: LLM 自主调用 webSearch 和 localSearch 工具，ToolCallingAdvisor 自动编排搜索循环
- 🧠 **三层记忆系统**: L1 Redis 短期会话记忆 + L2 Milvus 语义记忆（自生长） + L3 PostgreSQL 长期用户画像
- 🔄 **语义记忆 L2 自生长**: 研究完成后报告自动向量化入库，下次研究时检索相似历史报告增强 Planner 上下文
- 🔥 **Prompt 热更新**: DynamicPromptService 数据库优先加载，修改 `prompt_templates` 表后 1 分钟内自动生效，无需重启
- ✨ **引用校验**: 自动检测并移除 LLM 幻觉产生的虚假引用
- 📡 **SSE 实时推送**: 细粒度进度事件（按 Agent 阶段推送，含 `CACHE_HIT` 阶段）
- 🏢 **多租户**: JWT 认证 + Milvus tenant_id 硬隔离
- 📈 **可观测性**: OpenTelemetry Tracing + Token 成本监控 + Prometheus 指标采集 + Grafana 4 仪表盘 (46 面板，含 Spring AI 内置指标) + 11 条告警规则
- 🐳 **一键部署**: Docker Compose 启动完整可观测性栈 (Prometheus + Grafana + OTel Collector + Jaeger)
- 🛡️ **韧性**: Resilience4j 重试/熔断 + 搜索并发限流
- 🎯 **异步质量评估**: EvalAgent 在 Writer 完成后异步评估报告质量（5 维度 1-5 分制），结果持久化并支持滑动窗口告警
- 🔄 **模型降级**: Pro 模型不可用时自动降级至 Flash（CircuitBreaker + Fallback），保证研究不中断
- 🔁 **搜索熔断**: Bocha 连续失败自动切换 Tavily 备用引擎，均不可用时进入纯 Local RAG 模式

## 快速开始

### 前置条件

- Java 21+
- Maven 3.9+
- [DeepSeek API Key](https://platform.deepseek.com/)
- [Bocha Search API Key](https://open.bochaai.com/)（用于网络搜索）
- 环境依赖: PostgreSQL, Redis, Milvus（若未搭建，长期/短期/语义记忆将优雅降级）

### 1. 配置环境变量

```bash
export DEEPSEEK_API_KEY=sk-your-key-here
export DASHSCOPE_API_KEY=your-dashscope-key   # 向量化（text-embedding-v3）
export BOCHA_API_KEY=your-bocha-key
export MILVUS_HOST=your-milvus-host
export MILVUS_PORT=19530
export PG_URL=jdbc:postgresql://localhost:5432/deep_research
export PG_USER=postgres
export PG_PASSWORD=your-password
export REDIS_HOST=localhost
```

### 2. 构建与启动

```bash
# 编译
mvn clean compile

# 启动（开发环境）
mvn spring-boot:run

# 构建可执行 Jar
mvn clean package -DskipTests
java -jar target/deep-research-agent-1.0.0-SNAPSHOT.jar
```

### 3. 发起研究请求

```bash
curl -X POST http://localhost:8080/api/research \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-jwt-token>" \
  -d '{
    "query": "2026年中国新能源汽车市场趋势分析",
    "userId": "user_001",
    "tenantId": "tenant_001",
    "deepResearch": true
  }'

# 返回: {"sessionId":"a1b2c3d4","status":"IN_PROGRESS"}
```

### 4. 启动可观测性栈（可选）

```bash
cd observability && docker compose up -d

# Prometheus: http://localhost:9090
# Grafana:    http://localhost:3000 (admin/admin)
# Jaeger:     http://localhost:16686 (启用 OTLP 后可见 trace)

# 启用 OTLP 追踪导出：
OTEL_METRICS_ENABLED=true mvn spring-boot:run
```

### 5. 订阅 SSE 进度流

```bash
curl -N http://localhost:8080/api/research/a1b2c3d4/stream \
  -H "Authorization: Bearer <your-jwt-token>"
```

SSE 事件示例：
```
event: planning
data: {"sessionId":"a1b2c3d4","stage":"PLANNING","nodeName":"plan","percent":0.0,"message":"正在拆解研究问题、规划搜索路径..."}

event: cache_hit
data: {"sessionId":"a1b2c3d4","stage":"CACHE_HIT","nodeName":"cache","percent":100.0,"message":"缓存命中 (相似度=80%, source=abc12345): 2026年中国新能源汽车市场趋势分析"}

event: completed
data: {"sessionId":"a1b2c3d4","stage":"COMPLETED","nodeName":"done","percent":100.0,"message":"研究完成（来自缓存）: 2136 字报告, 匹配查询='2026年中国新能源汽车市场趋势分析'"}
```

## 配置参考

### 模型分层

| Agent | 模型层 | Temperature | 职责 |
|:---|:---|:---|:---|
| IntentRouter | Flash | 0.0 | 意图分类 (direct/research)，`.entity(RouteResult.class)` |
| Planner | Pro | 0.3 | 任务拆解+大纲+搜索计划，Pro→Flash CircuitBreaker 降级 |
| WebScout | Flash | 0.4 | 网络取证 — LLM 自主调用 @Tool webSearch |
| LocalScout | Flash | 0.4 | 知识库取证 — LLM 自主调用 @Tool localSearch |
| Analyst | Flash | 0.2 | 结论形成+完备性评估，`.entity(AnalysisResult.class)` |
| Writer | Pro | 0.4 | 研报撰写+引用校验，Pro→Flash CircuitBreaker 降级 |
| Eval | Flash | 0.05 | 异步报告质量评估（5维度评分），`.entity(EvalResult.class)` |

> 注：EvidenceJudge 已由 `EvidenceDeduplicationService`（代码级去重过滤）替代，Reflect 循环已移除（单轮模式）。EvalAgent 在 Writer 完成后异步执行（fire-and-forget）。
> 安全防护：5 个 Advisor 企业级链（TokenBudget + PiiMask + OutputGuard + TokenTrack + AuditLog）+ PromptInjectionChecker（Controller 前置拦截）+ System/User 消息分离（架构级防护）。

### 安全配置

| 功能 | 配置路径 | 实现 |
|:---|:---|:---|
| Advisor 链治理 | `AgentBundle.createBuilder()` | TokenBudget → PiiMask → OutputGuard → TokenTrack → AuditLog，所有 Agent 自动继承 |
| PII 脱敏 | `deep-research.pii.enabled` | `PiiMaskingService` 正则检测 → `PiiMaskingAdvisor` ChatClient 透明拦截 |
| 输出安全护栏 | 默认开启 | `OutputGuardrailAdvisor` 敏感词拦截 + 安全兜底文案替换 |
| Token 预算管控 | Redis 自动 | `TokenBudgetAdvisor` Redis INCR+EXPIRE 分布式限流（100次/小时/用户） |
| 审计日志 | 默认开启 | `AuditLogAdvisor` AUDIT Logger 记录 agent/userId/status/latency |
| Prompt 注入防护 | `deep-research.injection.*` | `PromptInjectionChecker` 复合评分规则引擎 → Controller 层 400 拦截 |
| System/User 分离 | 无（默认开启） | `PromptSplitUtils` + 全部 7 个 Agent `.system().user()` 调用 |

**PII 分离边界**（三层验证）:
```
用户输入 → 应用日志: <PHONE_0>         ← PiiMaskingService.tokenizeToString()
        → DeepSeek API: <PHONE_0>    ← PiiMaskingAdvisor.before()
        → PG/Redis/Milvus: PII       ← 原文（内部存储 + after() 还原）
```

### 关键配置项 (`application.yml`)

```yaml
deep-research:
  workflow:
    max-iterations: 1          # 单轮模式（无反思循环）
    min-report-words: 1500     # 报告最低字数
    timeout: 120s              # 单次研究超时
  rag:
    top-k: 4                   # 向量检索结果数
    similarity-threshold: 0.7  # 相似度阈值
    chunk-size: 1000           # 文档分块大小
    chunk-overlap: 200         # 分块重叠
  evidence-scoring:
    local-knowledge-base: 0.92 # 本地知识库基础评分
    government-edu: 0.88       # 政府/教育机构评分
    mainstream-media: 0.72     # 主流媒体评分
    general-website: 0.58      # 一般网站评分
    unknown-source: 0.45       # 来源不明评分
  cache:
    enabled: true              # 语义缓存开关
    similarity-threshold: 0.80 # 缓存命中阈值（COSINE）
  eval:
    enabled: true              # 异步评估开关
    alert-threshold: 3.0       # 告警阈值（连续N次均分低于此值时WARN）
    alert-window-size: 10      # 告警滑动窗口大小
  fallback:
    model:
      enabled: true            # 模型降级开关 (Pro→Flash)
      max-wait: 30s            # Pro 调用最大等待时间
    tavily:
      api-key: ${TAVILY_API_KEY} # Tavily 备用搜索 API Key
```

### 性能数据（2026-07-08 实测）

| 指标 | 数值 |
|:---|:---|
| 全流程耗时（首次） | ~2 分 15 秒（8 个搜索查询） |
| 缓存命中耗时 | ~1.5 秒（跳过全部 Agent） |
| 双源检索耗时 | ~32 秒（Web 20s + Local 32s 并行） |
| 语义记忆检索 | ~1 秒（返回 5 条相似历史研究） |
| 语义记忆索引 | ~2.8 秒（5 个分块向量化+写入） |
| 记忆上下文大小 | 2068 字符（含 5 条历史相似研究） |
| 证据去重率 | 60 → 40 条（25% 去重） |

## API 文档

### 端点一览

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| POST | `/api/research` | 发起研究 |
| GET | `/api/research/{id}/stream` | SSE 进度流 |
| GET | `/api/research/{id}` | 查询状态 |
| GET | `/actuator/health` | 健康检查 |
| GET | `/actuator/prometheus` | Prometheus 指标端点 |
| GET | `/actuator/metrics` | 指标列表 |

## 项目文档

- `CLAUDE.md` — 项目开发指南（架构、约定、常见任务、修复记录）
- `docs/spring-ai-2-0/` — Spring AI 2.0 介绍、架构设计、最新特性与最佳实践（6 个文档）
- `docs/optimization/Spring AI 2.0 项目优化实施方案与更新记录.md` — 8 轮优化实施完整记录（4 轮初始 + P0-P3 后续修复），含 10.7 节最终合规状态
- `docs/optimization/sql/init_prompt_templates.sql` — Prompt 模板数据库初始化脚本（8 个模板，支持重复执行）

## 技术决策

| 决策 | 选择 | 理由 |
|:---|:---|:---|
| LLM | DeepSeek V4 原生 | Spring AI 2.0 第一方支持 |
| 编排 | LangGraph4j | Java 版 LangGraph，条件分支原生支持 |
| 模型策略 | 两层（Pro+Flash） | 核心推理用 Pro，工具型用 Flash |
| 状态管理 | AgentState + Channel | appender 语义天然适合跨迭代证据累积 |
| 并发 | Virtual Threads + CompletableFuture | Java 21 原生，代码同步风格+异步性能 |
| 搜索 | Bocha Search API | AI Agent 专用搜索 |
| 向量库 | Milvus | 自建，生产级性能 |
| 嵌入模型 | DashScope text-embedding-v3 | 1024 维，中英文，国内合规 |
| Prompt | DynamicPromptService（DB优先+classpath兜底） | 运行时热更新，1分钟缓存TTL |
| 引用校验 | 正则 + 合法 ID 集 | O(n) 确定性校验，不依赖 LLM |
| 去重过滤 | 代码级（URL+标题+域名） | 比 LLM Judge 快且零 token 成本 |
| 可观测性 | Micrometer + Prometheus + Grafana | Spring Boot Actuator 原生集成，零侵入 Token 追踪 |
| 追踪 | OpenTelemetry (OTLP) + Jaeger | 工作流全链路可观测 |
| 告警 | Prometheus Rules (11 条) | LLM 延迟/成本/安全/质量/工具调用全覆盖 |

## License

MIT
