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
┌──────────────────────────────────────────────────────────┐
│                    API 层 (WebFlux / SSE)                │
├──────────────────────────────────────────────────────────┤
│              编排层 (LangGraph4j StateGraph)             │
│                                                          │
│  START → intent_route ──[direct]──→ direct_answer → END  │
│              │                                           │
│              └──[research]──→ plan → dual_search         │
│                                        │                 │
│                                        ▼                 │
│                                      filter              │
│                                        │                 │
│                                        ▼                 │
│                                     analyze              │
│                                        │                 │
│                                        ▼                 │
│                                      write → END         │
│                                                          │
│  (单轮 DAG，无循环；EvidenceJudge 已由代码级去重替代)    │
├──────────────────────────────────────────────────────────┤
│            智能体层 (8 个 Agent, 两层模型)               │
│   IntentRouter(Flash)  Planner(Pro)    WebScout(Flash)   │
│   LocalScout(Flash)    Analyst(Flash)   Writer(Pro)      │
│   Eval(Flash) [异步]   PreferenceExtractor(Flash) [异步] │
├──────────────────────────────────────────────────────────┤
│             记忆系统 (三层架构)                          │
│   L1 短期 (Redis)  L2 语义 (Milvus)  L3 长期 (PG)        │
├──────────────────────────────────────────────────────────┤
│        基础设施 (DeepSeek V4 / Milvus / PG / Redis)      │
│        可观测性 (Prometheus / Grafana / Jaeger)          │
└──────────────────────────────────────────────────────────┘
```

## 核心特性

- 🤖 **8 个专业 Agent**: 意图路由 → 任务规划 → 双源并行检索(Web+Local RAG @Tool) → 去重过滤 → 分析归纳 → 报告撰写 → 异步质量评估 + 异步偏好提取
- 🔧 **Spring AI 2.0 全面对齐**: `@Tool` 工具调用 + `.entity()` 结构化输出 + Advisor 链统一治理 + DynamicPrompt 数据库热更新
- 🛡️ **企业级 Advisor 链**: TokenBudget（分布式限流）→ PiiMask（输入脱敏）→ OutputGuard（输出护栏）→ TokenTrack（用量追踪）→ AuditLog（审计日志），全部 LLM 调用（含 direct_answer）自动启用
- 🎯 **工具层证据收集**: 搜索结果在 @Tool 执行时由 ThreadLocal 收集器直接收集并分配 sourceId，LLM 只输出筛选结论（sourceId+评分），Evidence 由 Java 组装——根治 LLM 复述 JSON 脆弱性，LLM 输出不可用时自动降级采用收集结果（零证据空转结构上不可能发生）
- 🔗 **可点击引用溯源**: 正文引用标记自动转 Markdown 链接（`[WEB12](url)`），文末参考资料列表含标题+URL+域名；引用合法性自动校验，幻觉引用自动移除
- ⚡ **语义缓存**: 相似 Query 直接返回历史报告（~1.5 秒 vs 完整流程 ~2 分钟），基于 Milvus 向量相似度 + PostgreSQL 报告检索
- 🧠 **三层记忆系统**: L1 Redis 短期会话记忆 + L2 Milvus 语义记忆（自生长，索引前自动剥离参考资料/URL 噪音） + L3 PostgreSQL 长期用户画像
- 👤 **用户偏好自增长**: PreferenceExtractorAgent 研究完成后异步提取稳定偏好（保守增量、受限 key 集），合并入画像并注入后续研究上下文
- 🔥 **Prompt 热更新**: 全部 Agent 调用时加载模板（DB 优先 + 1 分钟缓存 TTL），修改 `prompt_templates` 表后自动生效，无需重启
- 📡 **SSE 实时推送**: 细粒度进度事件（按 Agent 阶段推送，含 `CACHE_HIT`/`SEARCH_FALLBACK` 阶段）
- 🏢 **多租户硬隔离**: 身份以 JWT claims 为准（兼容 Casdoor 组织模型，请求体伪造记 SECURITY 日志）+ Milvus tenant_id 过滤 + 请求级 PII Vault + 按租户评估指标
- 🔒 **PII 可逆标记化**: 请求级 Vault——外部 API 零泄露（只见 `<PHONE_0>`）、响应无缝还原、跨请求/跨租户不可还原（端到端验证）
- 🚨 **零证据熔断**: 证据池为空时 SSE 警告 + 报告免责声明 + status=DEGRADED + 跳过语义缓存索引
- 📈 **可观测性**: OpenTelemetry Tracing + Token 成本监控 + Prometheus 指标采集 + Grafana 4 仪表盘 (46 面板，含 Spring AI 内置指标) + 11 条告警规则
- 🐳 **一键部署**: Docker Compose 启动完整可观测性栈 (Prometheus + Grafana + OTel Collector + Jaeger)
- 🛡️ **韧性**: Resilience4j 重试/熔断 + 搜索并发限流
- 🎯 **异步质量评估**: EvalAgent 5 维度评分（零引用代码级判低分），按租户滑动窗口告警
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
    "deepResearch": true
  }'

# 返回: {"sessionId":"a1b2c3d4","status":"IN_PROGRESS"}
```

> **身份说明**：userId/tenantId 以 JWT claims 为准（`sub`/`tenant_id`，兼容 Casdoor：`owner/name` 组合 + `owner` 即租户），请求体中的同名字段仅在 claim 缺失时作兼容回退；与 JWT 不一致时记 SECURITY 日志并以 JWT 为准。
>
> Casdoor 测试 token 获取（password grant，Application 需勾选 `Password` grant type、Token format 选 `JWT`）：
> ```bash
> curl -X POST "https://<casdoor-host>/api/login/oauth/access_token" \
>   -d "grant_type=password" -d "client_id=<id>" -d "client_secret=<secret>" \
>   -d "username=<用户名>" -d "password=<密码>" -d "scope=read"
> ```

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
| DirectAnswer | Flash | 0.3 | direct 意图快速回答（AgentBundle 全链 Advisor） |
| Planner | Pro | 0.3 | 任务拆解+大纲+搜索计划，Pro→Flash CircuitBreaker 降级 |
| WebScout | Flash | 0.4 | 网络取证 — LLM 自主调用 @Tool webSearch，只输出 selections（sourceId+评分），Evidence 由工具层收集结果组装 |
| LocalScout | Flash | 0.4 | 知识库取证 — LLM 自主调用 @Tool localSearch，同 selections 模式 |
| Analyst | Flash | 0.2 | 结论形成+完备性评估，`.entity(AnalysisResult.class)` |
| Writer | Pro | 0.4 | 研报撰写+引用校验+引用链接化，Pro→Flash CircuitBreaker 降级 |
| Eval | Flash | 0.05 | 异步报告质量评估（5维度评分，零引用代码级判低分），`.entity(EvalResult.class)` |
| PreferenceExtractor | Flash | 0.1 | 异步用户偏好提取（保守增量，受限 6 key），合并入长期画像 |

> 注：EvidenceJudge 已由 `EvidenceDeduplicationService`（代码级去重过滤）替代，Reflect 循环已移除（单轮模式）。Eval 与 PreferenceExtractor 在 Writer 完成后异步执行（fire-and-forget，仅 research 意图）。
> 安全防护：5 个 Advisor 企业级链（TokenBudget + PiiMask + OutputGuard + TokenTrack + AuditLog，含 direct_answer 在内的全部 LLM 调用继承）+ PromptInjectionChecker（Controller 前置拦截）+ System/User 消息分离（架构级防护）。

### 安全配置

| 功能 | 配置路径 | 实现 |
|:---|:---|:---|
| Advisor 链治理 | `AgentBundle.createBuilder()` | TokenBudget → PiiMask → OutputGuard → TokenTrack → AuditLog，全部 ChatClient Bean（含 directAnswerClient）自动继承 |
| 身份以 JWT 为准 | 无（默认开启） | Controller 层解析 claims 覆盖请求体（Casdoor：`owner/name`→userId、`owner`→tenantId 回退），不一致记 `IDENTITY_MISMATCH` SECURITY 日志 |
| PII 脱敏 | `deep-research.pii.enabled` | `PiiMaskingService` 正则检测（GB 11643/Luhn 校验过滤误匹配）→ `PiiMaskingAdvisor` 请求级 Vault 透明拦截 |
| 输出安全护栏 | 默认开启 | `OutputGuardrailAdvisor` 敏感词拦截 + 安全兜底文案替换 |
| Token 预算管控 | Redis 自动 | `TokenBudgetAdvisor` Redis INCR+EXPIRE 分布式限流（100次/小时/用户） |
| 审计日志 | 默认开启 | `AuditLogAdvisor` AUDIT Logger 记录 agent/userId/status/latency |
| Prompt 注入防护 | `deep-research.injection.*` | `PromptInjectionChecker` 复合评分规则引擎 → Controller 层 400 拦截 |
| System/User 分离 | 无（默认开启） | `PromptSplitUtils` + 全部 8 个 Agent 及 direct_answer `.system().user()` 调用 |

**PII 分离边界**（三段论，已端到端验证）:
```
用户输入 → 应用日志: <PHONE_0>         ← tokenizeToString()（一次性 Vault，不可还原）
        → DeepSeek API: <PHONE_0>    ← PiiMaskingAdvisor.before()（请求级 Vault）
        → 用户响应: 原文               ← after() 从本请求 Vault 还原
        → PG/Redis/Milvus: 原文       ← 内部受控存储保留原文，再注入 prompt 时二次脱敏
跨请求/跨租户: <PHONE_0> 字面量不可还原  ← Vault 请求级隔离（防注入诱导泄漏）
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

### 性能数据（2026-07-17 实测）

| 指标 | 数值 |
|:---|:---|
| 全流程耗时（首次研究） | ~3.5 分钟（10 个搜索计划，Writer Pro ~80-100s） |
| direct 快速回答 | ~5 秒（intent 路由 + Flash 回答） |
| 缓存命中耗时 | ~1.5 秒（跳过全部 Agent） |
| WebScout 工具循环 | ~35 秒（10-18 次搜索，收集 90-140 条原始结果 → LLM 精选 15 条） |
| 语义记忆索引 | ~2 秒（清洗后 3 chunks 向量化+写入，剥离约 60% 链接噪音） |
| 报告规模 | 1800-2900 字，13-17 个可点击引用 |

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

- `CLAUDE.md` — 项目开发指南（架构、约定、常见任务、完整修复记录与遗留问题清单）
- `docs/spring-ai-2-0/` — Spring AI 2.0 介绍、架构设计、最新特性与最佳实践（6 个文档）
- `docs/optimization/Spring AI 2.0 项目优化实施方案与更新记录.md` — 8 轮优化实施完整记录（4 轮初始 + P0-P3 后续修复），含 10.7 节最终合规状态
- `docs/optimization/sql/init_prompt_templates.sql` — Prompt 模板数据库初始化脚本（9 个模板，支持重复执行）

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
| 证据组装 | 工具层 ThreadLocal 收集器 + LLM selections | LLM 不复述搜索结果，根治 JSON 脆弱性/token 浪费；解析失败降级采用收集结果 |
| PII Vault | 请求级作用域（advisor context 传递） | 跨请求/跨租户不可还原，无界增长消除（禁止单例全局 Vault） |
| 身份解析 | JWT claims 优先（Casdoor owner/name 适配） | 请求体身份可伪造，仅作兼容回退 |
| 请求级状态 | 方法参数 / ThreadLocal / advisor context | 禁止单例 Bean 实例字段暂存（曾两起跨租户泄漏） |
| 去重过滤 | 代码级（URL+标题+域名） | 比 LLM Judge 快且零 token 成本 |
| 可观测性 | Micrometer + Prometheus + Grafana | Spring Boot Actuator 原生集成，零侵入 Token 追踪 |
| 追踪 | OpenTelemetry (OTLP) + Jaeger | 工作流全链路可观测 |
| 告警 | Prometheus Rules (11 条) | LLM 延迟/成本/安全/质量/工具调用全覆盖 |

## License

MIT
