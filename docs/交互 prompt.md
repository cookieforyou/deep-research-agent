我现在要使用 Spring AI 2.0 技术栈落地实现一个 AI Agent 项目 “DeepResearch多Agent行业深度研究助手”，已提供详细需求分析与技术实现报告：docs/DeepResearch 多 Agent 行业深度研究助手 -  
需求分析与技术实现报告.md。请仔细深度分析此报告，严格遵循执行落地实现。另外结合你的思考理解以及当下市面 AI Agent 项目最新落地情况（现在是 2026 年 7 月），对一些细节不明确的地方加  
以补充、扩展和优化。首先，咱们先不直接动手实现，先来需求确认交互：结合你对报告的理解，看下还有哪些实现细节不清晰的，直接问我并给出一些可行选项，由我一一确认，待你对此项目有 95%    
的理解程度后，告诉我，咱们就开始实现

Thought for 3s (ctrl+o to expand)

⏺ 我先仔细阅读需求分析报告，然后再与你进行需求确认交互。

Thought for 11s, read 1 file (ctrl+o to expand)

⏺ 让我再看看项目中已有的代码结构，了解当前的初始化状态。

Thought for 9s, searched for 1 pattern, read 1 file (ctrl+o to expand)

⏺ 让我先了解一下当前 Spring AI 2.0 的最新状况，确保我的建议是准确的。

⏺ Web Search("Spring AI 2.0 GA release 2026")
⎿  Did 1 search in 16s

⏺ Web Search("Spring Boot 4.x release 2026 latest version")
⎿  Did 1 search in 12s

Thought for 18s (ctrl+o to expand)

⏺ 现在我对报告有了深入理解。让我来梳理一下报告非常清晰的部分，以及需要你确认的关键决策点。
     
---
报告已有明确方案的部分 ✅

以下内容报告已经给出清晰方案，我直接采纳：

┌────────────┬───────────────────────────────────────────────────────────────────────────────┐
│    领域    │                                     方案                                      │
├────────────┼───────────────────────────────────────────────────────────────────────────────┤
│ 工作流     │ 7 步研究法（Intent → Plan → Dual-Search → Judge → Analyze → Reflect → Write） │
├────────────┼───────────────────────────────────────────────────────────────────────────────┤
│ Agent 角色 │ 8 个 Agent，Temperature 0.0~0.4                                               │
├────────────┼───────────────────────────────────────────────────────────────────────────────┤
│ 状态管理   │ Java Record + Builder 不可变模式                                              │
├────────────┼───────────────────────────────────────────────────────────────────────────────┤
│ 并行检索   │ Virtual Threads + CompletableFuture                                           │
├────────────┼───────────────────────────────────────────────────────────────────────────────┤
│ 引用校验   │ 正则提取 + 合法 ID 集合比对                                                   │
├────────────┼───────────────────────────────────────────────────────────────────────────────┤
│ 证据评分   │ 规则引擎前置 + LLM 微调（混合方案）                                           │
├────────────┼───────────────────────────────────────────────────────────────────────────────┤
│ JSON 解析  │ Jackson + BeanOutputConverter + Fallback                                      │
├────────────┼───────────────────────────────────────────────────────────────────────────────┤
│ 记忆系统   │ Short（Redis）+ Long（PG）+ Semantic（VectorStore）                           │
├────────────┼───────────────────────────────────────────────────────────────────────────────┤
│ 可观测性   │ OpenTelemetry + Micrometer Tracing + Token 监控                               │
├────────────┼───────────────────────────────────────────────────────────────────────────────┤
│ 容错降级   │ Resilience4j 重试 + 模型降级 + 搜索熔断                                       │
├────────────┼───────────────────────────────────────────────────────────────────────────────┤
│ 安全       │ 多租户 tenantId 隔离 + PII 脱敏 + Prompt 注入防护                             │
├────────────┼───────────────────────────────────────────────────────────────────────────────┤
│ API 交互   │ SSE 流式推送 + REST API                                                       │
└────────────┴───────────────────────────────────────────────────────────────────────────────┘

  ---
需要你确认的关键决策 🔑

以下 8 个问题将直接影响架构落地，请逐一确认：

  ---
Q1: LLM 模型选型

报告中代码示例用的是 OpenAiChatOptions（OpenAI），但正文提到了 qwen-max/qwen-plus（阿里 DashScope）做降级。Spring AI 2.0.0 GA（2026-06-12
刚发布）原生支持：OpenAI、Anthropic、DeepSeek、Google GenAI、Mistral、Ollama、Amazon Bedrock。

选项：
- A) DeepSeek 为主 — 性价比极高，Spring AI 2.0 原生支持，适合中文场景
- B) 阿里 DashScope (qwen-max/qwen-plus) 为主 — 国内合规，中文能力强
- C) OpenAI 为主 + 国内模型降级 — 英文场景最强，但成本高
- D) 多模型混合 — 不同 Agent 用不同模型（如 Planner 用强模型，Scout 用便宜模型）

  ---
Q2: 搜索引擎选型

报告提到 "Bocha Search API"，我需要确认这是什么服务？目前业界主流深度研究产品（如 OpenAI Deep Research、Gemini Deep Research）常用的搜索 API：

选项：
- A) Tavily Search API — AI Agent 专用搜索，结构化结果好，有深度研究模式
- B) Brave Search API — 独立索引，隐私友好，免费额度慷慨
- C) Bing Search API / Google Custom Search — 覆盖面最广
- D) SerpAPI — 聚合多引擎，结果稳定
- E) 国内搜索 API（如 Bocha/博查 Search）— 如果是国内产品，可能有备案合规需求

  ---
Q3: 向量数据库

Milvus（独立部署，性能强）vs PgVector（PostgreSQL 扩展，运维简单）。

选项：
- A) PgVector — 项目规模初期首选，零额外运维，PG 一个库搞定业务数据+向量
- B) Milvus — 如果需要百万级以上向量检索，性能更优
- C) 双模支持 — 抽象 VectorStore 接口，配置文件切换（PgVector 默认）

  ---
Q4: 工作流编排方案

报告中列举了 Spring StateMachine / Temporal / Camunda / Spring AI 2.0 Workflow API 多种方案。考虑到 Spring AI 2.0 的 ToolCallingAdvisor 已提升为一级 advisor
链，以及对虚拟线程的良好支持：

选项：
- A) 纯代码编排 — 利用 Java 21+ Virtual Threads + StructuredTaskScope，最简单灵活，适合当前复杂度
- B) Spring StateMachine — 有状态机可视化，但学习曲线陡，配置繁琐
- C) Spring AI 2.0 Workflow API + Advisor Chain — 如果 2.0 的 Workflow 已足够成熟
- D) Temporal — 如果需要分布式工作流、断点续跑、长时间任务管理

  ---
Q5: 前端方案

报告未提及 UI，但 SSE 实时推送需要一个展示层才有意义。

选项：
- A) 纯 API 后端 — 仅 REST + SSE，前端后续单独开发，先用 curl/Swagger 调试
- B) 轻量 Web UI — 一个简单的 Thymeleaf/Vue 单页面，包含研究输入框 + SSE 实时展示 + Markdown 渲染
- C) 完整前后端分离 — React/Vue SPA 独立项目，专业研究仪表板

  ---
Q6: 认证与多租户

报告提到了 tenantId 隔离和 PII 脱敏，但没有认证方案。

选项：
- A) 开发阶段无认证 — 先用硬编码 user/tenant 跑通流程，后续再加
- B) API Key 认证 — 简单的 API Key 方式，适合 B2B 服务
- C) Spring Security + JWT + OAuth2 — 完整认证体系，支持用户注册登录

  ---
Q7: 部署与环境

选项：
- A) 本地开发优先 — Docker Compose 一键启动（PG + Redis + App），验证完再考虑生产
- B) 直接 K8s 就绪 — 包含 Dockerfile + K8s YAML，面向生产
- C) Spring Boot 可执行 Jar — 最小化，环境依赖外部配置

  ---
Q8: 知识库初始内容

Local RAG 需要向量化的文档，初始知识库怎么来？

选项：
- A) 先用公开数据集 — 导入行业报告、企业年报等公开 PDF，验证流程
- B) 内置文档导入工具 — 提供 API/管理界面让用户自己上传文档（PDF/Word/Markdown）
- C) 先 Mock — Local Scout 先返回模拟数据，后续再接入真实向量检索

  ---
请逐一回答这 8 个问题（直接回复 "Q1: A, Q2: ..." 即可），也可以对某个问题给出你自己的方案。确认完毕后我来做最终的架构对齐，然后开始实现。

✻ Cogitated for 1m 25s

❯ Q1: A；Q2: E；Q3: B (Milvus 我自己买了服务器已经搭好了)；Q4: 这里能不能使用 LangGraph4j，会不会更好，给出你的建议；Q5: C (先不急写前端，把后端搞好咱们再进行)；Q6: C；Q7: C         
(所有依赖 INFRA 我已在服务器搭好了)；Q8: A (有没有更好方案)。这些是我的答案，然后请再 review 一次，看下哪些技术点的选择有待调整，我们不必完全按照分析报告里的来，我的核心目标是使用
Java 技术栈 Spring AI 2.0，其他技术组件我们可以讨论商量

Thought for 7s (ctrl+o to expand)

⏺ 让我先对 LangGraph4j 和几个关键技术点做一下调研，然后给你一个全面的建议。

⏺ Web Search("LangGraph4j Java library 2026 latest release GitHub")
⎿  Did 1 search in 13s

⏺ Web Search("Bocha Search API 博查搜索 Java SDK 2026")
⎿  Did 1 search in 19s

⏺ Web Search("DeepSeek Spring AI 2.0 integration 2026")
⎿  Did 1 search in 15s

Thought for 22s (ctrl+o to expand)

⏺ 调研结果出来了，下面是我的全面分析和建议。
     
---
调研结论 + 推荐方案

🔑 关键发现

1. Spring AI 2.0.0 GA 已于 2026-06-12 发布，原生内置 DeepSeek 支持，无需走 OpenAI 兼容层：

  <!-- 直接使用 DeepSeek 原生 starter，不再需要 OpenAI 兼容桥接 -->
  <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-starter-model-deepseek</artifactId>
  </dependency>

这意味着报告中所有 OpenAiChatOptions 应替换为 DeepSeekChatOptions，配置 key 从 spring.ai.openai.* 变为 spring.ai.deepseek.*。

2. Bocha Search 是标准 RESTful API（api.bocha.cn/v1/web-search），无 Java SDK 但封装简单，且已有 MCP Server 可直接被 Spring AI 2.0 的 MCP 客户端消费。

3. LangGraph4j v1.8.20（2026-06-27 更新），MIT 协议，GitHub 1,779 stars，每 2-3 周一个 release，非常活跃。

  ---
📊 Q4 核心决策：编排方案对比

┌─────────────────────┬──────────────────────────────────┬─────────────────────┬─────────────────────┬───────────────────┐
│        维度         │           LangGraph4j            │     纯代码编排      │ Spring StateMachine │     Temporal      │
├─────────────────────┼──────────────────────────────────┼─────────────────────┼─────────────────────┼───────────────────┤
│ AI Agent 原生度     │   ⭐⭐  ⭐⭐ 专为 AI Agent 设计  │   │ ⭐⭐ 需手写所有逻辑 │ ⭐⭐ 通用状态机         │ 通⭐⭐ 通用工作流   │
├─────────────────────┼──────────────────────────────────┼─────────────────────┼─────────────────────┼───────────────────┤
│ 条件分支+循环       │ ⭐     ⭐⭐ 原生 conditional edge │ ⭐   ⭐⭐ 手动 if/else │ ⭐   ⭐ ⭐⭐ 支持         │     ⭐⭐ 支持       │
├─────────────────────┼──────────────────────────────────┼─────────────────────┼─────────────────────┼───────────────────┤
│ Checkpoint/断点续跑 │ ⭐⭐⭐⭐     内置                    │ ⭐  需自己实现       │ ⭐⭐⭐    有           │ ⭐⭐⭐   ⭐⭐   最强   │
├─────────────────────┼──────────────────────────────────┼─────────────────────┼─────────────────────┼───────────────────┤
│ 可视化              │ ⭐⭐⭐⭐     graphviz 导出           │ ⭐  无               │ ⭐⭐⭐    有           │ ⭐⭐⭐⭐     有       │
├─────────────────────┼──────────────────────────────────┼─────────────────────┼─────────────────────┼───────────────────┤
│ 与 Spring AI 集成   │ ⭐⭐⭐⭐     良好                    │ ⭐⭐⭐⭐⭐      无缝     │ ⭐⭐⭐    一般         │ ⭐⭐   需适配       │
├─────────────────────┼──────────────────────────────────┼─────────────────────┼─────────────────────┼───────────────────┤
│ 学习曲线            │ ⭐⭐⭐    中等                      │ ⭐⭐⭐⭐⭐      无       │ ⭐  陡峭             │ ⭐⭐   陡峭         │
├─────────────────────┼──────────────────────────────────┼─────────────────────┼─────────────────────┼───────────────────┤
│ 运维复杂度          │ ⭐⭐⭐   ⭐⭐   嵌入式                │ ⭐⭐⭐⭐⭐      无       │ ⭐⭐⭐    需额外配置   │ ⭐  需独立集群     │
└─────────────────────┴──────────────────────────────────┴─────────────────────┴─────────────────────┴───────────────────┘

🎯 我的推荐：LangGraph4j

理由非常直接——它就是 Python LangGraph 的 Java 移植版，而 LangGraph 是当前业界 AI Agent 编排的事实标准（OpenAI、Google、Anthropic 的 Agent SDK 都借鉴了其 StateGraph 设计）。

对我们项目的 7 步研究流程，LangGraph4j 的优势：

1. StateGraph + conditional edge 完美映射 Reflect → Search 的条件循环（报告中那个 "reflect?" 判断分支）
2. 内置 checkpoint → 长研究任务可以暂停/恢复，用户关闭浏览器回来还能继续
3. Human-in-the-loop → 未来可以在 Planner 生成大纲后暂停，让用户确认再继续
4. 流式事件 → 每个 node 执行完都能推送进度事件，天然适配 SSE

报告中描述的 ResearchState Record + ResearchWorkflow 类，本质上就是在手写一个简化版的 StateGraph。用 LangGraph4j 会少写很多样板代码。

// 概念示意 - LangGraph4j 如何表达 7 步研究流程
StateGraph<ResearchState> graph = new StateGraph<>(...)
.addNode("intent_route", this::intentRoute)
.addNode("plan", this::plan)
.addNode("dual_search", this::dualSearch)
.addNode("judge", this::judgeEvidence)
.addNode("analyze", this::analyze)
.addNode("reflect", this::reflect)
.addNode("write", this::write)
.addNode("direct_answer", this::directAnswer)
.addEdge(START, "intent_route")
.addConditionalEdges("intent_route", this::routeIntent,
Map.of("direct", "direct_answer", "research", "plan"))
.addEdge("plan", "dual_search")
.addEdge("dual_search", "judge")
.addEdge("judge", "analyze")
.addConditionalEdges("analyze", this::shouldReflect,
Map.of("reflect", "reflect", "write", "write"))
.addEdge("reflect", "dual_search")  // 循环回去
.addEdge("write", END)
.compile();

  ---
🔄 Q8 知识库方案优化

纯公开数据集有个问题——和你的实际业务场景脱节。建议 三层渐进式方案：

┌───────────────┬─────────────────────────────────────────────────────────────────┬────────────────────────┐
│     层级      │                              内容                               │          目的          │
├───────────────┼─────────────────────────────────────────────────────────────────┼────────────────────────┤
│ L1 种子层     │ 公开行业报告（艾瑞、Gartner、IDC 等 PDF）+ 维基百科行业词条     │ 快速验证 RAG Pipeline  │
├───────────────┼─────────────────────────────────────────────────────────────────┼────────────────────────┤
│ L2 自生长层   │ 每次研究产出的报告自动入库，形成领域知识飞轮                    │ 越用越强，差异化竞争力 │
├───────────────┼─────────────────────────────────────────────────────────────────┼────────────────────────┤
│ L3 用户注入层 │ 提供 REST API 接收用户上传的 PDF/Word/Markdown，走 ETL Pipeline │ 企业定制化             │
└───────────────┴─────────────────────────────────────────────────────────────────┴────────────────────────┘

第一阶段只做 L1+L2，L3 的 API 接口预留但不实现完整前端。

  ---
📋 全部技术决策汇总 + 调整建议

以下是我对报告中方案的 review 结论，标注了保留/调整/新增：

┌─────┬──────────────────┬────────────────────────────┬────────────────────────────────────┬──────────────────────────────────────┐
│  #  │      决策点      │          报告方案          │              最终方案              │               变更理由               │
├─────┼──────────────────┼────────────────────────────┼────────────────────────────────────┼──────────────────────────────────────┤
│ 1   │ LLM              │ OpenAI/qwen 混用         D │D✅ DeSepSeek V4 原生                │ 性价比最高，Spring AI 2.0 第一方支持 │
├─────┼──────────────────┼────────────────────────────┼────────────────────────────────────┼──────────────────────────────────────┤
│ 2   │ 搜索             │ Bocha Search   B         B │B✅ Bocha Search（HTTP 封装 + MCP） │ 国内合规，API 完善                    │
├─────┼──────────────────┼────────────────────────────┼────────────────────────────────────┼──────────────────────────────────────┤
│ 3   │ 向量库           │ Milvus/PgVector 未定     M │M✅ Milvus（已部署）                 │ 已有基础设施                         │
├─────┼──────────────────┼────────────────────────────┼────────────────────────────────────┼──────────────────────────────────────┤
│ 4   │ 编排             │ Spring StateMachine / 手动 │ 🔄 LangGraph4j                     │ AI 原生，条件分支+循环天然支持       │
├─────┼──────────────────┼────────────────────────────┼────────────────────────────────────┼──────────────────────────────────────┤
│ 5   │ 前端             │ 未定                     后 │后✅ 后端优先，API 就绪后再做         │ 分阶段交付                           │
├─────┼──────────────────┼────────────────────────────┼────────────────────────────────────┼──────────────────────────────────────┤
│ 6   │ 认证             │ 未明确                   S │S✅ Spring Security + JWT + OAuth2│  │ 企业级标准                           │
├─────┼──────────────────┼────────────────────────────┼────────────────────────────────────┼──────────────────────────────────────┤
│ 7   │ 部署             │ 未定                     S │S✅ Spring Boot 可执行 Jar           │ 依赖 INFRA 已就绪                    │
├─────┼──────────────────┼────────────────────────────┼────────────────────────────────────┼──────────────────────────────────────┤
│ 8   │ 知识库           │ 未明确                     │ 🔄 三层渐进式（种子+自生长+注入）  │ 可持续发展                           │
├─────┼──────────────────┼────────────────────────────┼────────────────────────────────────┼──────────────────────────────────────┤
│ 9   │ 模型配置         │ OpenAiChatOptions          │ 🔄 DeepSeekChatOptions             │ 原生集成，避免兼容层                 │
├─────┼──────────────────┼────────────────────────────┼────────────────────────────────────┼──────────────────────────────────────┤
│ 10  │ 工具集成         │ 手动封装                   │ 🔄 Spring AI @Tool 注解 + MCP      │ 2.0 一级公民特性                     │
├─────┼──────────────────┼────────────────────────────┼────────────────────────────────────┼──────────────────────────────────────┤
│ 11  │ 结构化输出       │ BeanOutputConverter        │ 🔄 StructuredOutputAdvisor         │ 2.0 新增自纠错能力                   │
├─────┼──────────────────┼────────────────────────────┼────────────────────────────────────┼──────────────────────────────────────┤
│ 12  │ Spring Boot 版本 │ "4.x"                      │ 🔄 4.1.0（最新稳定版）             │ 明确版本号                           │
├─────┼──────────────────┼────────────────────────────┼────────────────────────────────────┼──────────────────────────────────────┤
│ 13  │ Java 版本        │ 未明确                     │ 🔄 Java 21（最低要求）             │ Spring AI 2.0 强制                   │
├─────┼──────────────────┼────────────────────────────┼────────────────────────────────────┼──────────────────────────────────────┤
│ 14  │ 构建工具         │ pom.xml                    │ ✅  Maven                           │ 报告已假定，保持不变                 │
└─────┴──────────────────┴────────────────────────────┴────────────────────────────────────┴──────────────────────────────────────┘

  ---
⚠️ 还有一个需要确认的问题

报告中 8 个 Agent 各有独立 Temperature，但用了同一个 ChatModel bean。如果全部用 DeepSeek，不同 Agent 是否可以共用同一个模型（如 deepseek-v4-pro），还是你希望对不同 Agent
使用不同规格的模型？

选项：
- A) 统一模型 — 全部 Agent 用 deepseek-v4-pro，仅通过 Temperature + System Prompt 区分行为（简单、省钱）
- B) 分层模型 — Planner/Analyst/Writer 等核心 Agent 用 deepseek-v4-pro，Scout/Judge 等工具型 Agent 用 deepseek-v4-flash（性价比最优，推荐）
- C) 按需配置 — 配置文件里每个 Agent 可独立指定模型，通过 application.yml 切换

  ---
如果以上全部确认没问题，我的理解度应该在 95%+ 了，告诉我你的选择就可以开始撸代码。

✻ Brewed for 1m 22s

❯ B) 分层模型。接下来的代码实现阶段我的硬性要求是：关键地方多写清晰地注释，复杂逻辑的地方注释可以稍微多点；然后文档要补全、详细、清晰，包括                                           
CLAUDE.md、README.md、以及修改原来的需求分析与技术实现报告（技术选型和实现已经变了很多）

Thought for 15s (ctrl+o to expand)

⏺ 好的，最终方案全部确认。让我先做一个完整的实施计划，然后逐步推进。

⏺ Entered plan mode
Claude is now exploring and designing an implementation approach.

Thought for 14s (ctrl+o to expand)

⏺ 项目是全新的 greenfield，无需代码探索。我直接进入设计阶段，启动 Plan agent 做架构设计。

⏺ Plan(设计完整实施计划)
⎿  Backgrounded agent (↓ to manage · ctrl+o to expand)

Thought for 4s (ctrl+o to expand)

⏺ Plan agent 在后台工作中。我基于已有的充分理解先开始编写实施计划。

⏺ Updated plan
⎿  /plan to preview

Thought for 1s (ctrl+o to expand)

⏺ 实施计划已编写完成，涵盖 10 个阶段从项目脚手架到文档交付。计划文件位于 plan file 中。

──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
Ready to code?

Here is Claude's plan:
╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌
DeepResearch Multi-Agent 行业深度研究助手 — 实施计划

Context

从零构建企业级 AI 多智能体深度研究系统，基于 Spring AI 2.0.0 GA + Spring Boot 4.1.0 + DeepSeek V4 + LangGraph4j 技术栈。原需求分析报告（docs/DeepResearch 多 Agent 行业深度研究助手
- 需求分析与技术实现报告.md）中的技术方案已做重大调整（OpenAI → DeepSeek 原生、手动编排 → LangGraph4j、模型分层等），需要在实施后更新该报告。

技术栈速览

┌─────────────┬─────────────────────────────────────────────────────────────┐
│    组件     │                          版本/选型                          │
├─────────────┼─────────────────────────────────────────────────────────────┤
│ Spring Boot │ 4.1.0                                                       │
├─────────────┼─────────────────────────────────────────────────────────────┤
│ Spring AI   │ 2.0.0 GA                                                    │
├─────────────┼─────────────────────────────────────────────────────────────┤
│ LangGraph4j │ 1.8.20                                                      │
├─────────────┼─────────────────────────────────────────────────────────────┤
│ Java        │ 21                                                          │
├─────────────┼─────────────────────────────────────────────────────────────┤
│ LLM         │ DeepSeek V4 Pro (核心Agent) + DeepSeek V4 Flash (工具Agent) │
├─────────────┼─────────────────────────────────────────────────────────────┤
│ 搜索        │ Bocha Search API                                            │
├─────────────┼─────────────────────────────────────────────────────────────┤
│ 向量库      │ Milvus (用户自建)                                           │
├─────────────┼─────────────────────────────────────────────────────────────┤
│ 关系库      │ PostgreSQL                                                  │
├─────────────┼─────────────────────────────────────────────────────────────┤
│ 缓存        │ Redis                                                       │
├─────────────┼─────────────────────────────────────────────────────────────┤
│ 构建        │ Maven                                                       │
└─────────────┴─────────────────────────────────────────────────────────────┘

实施阶段（共 10 个 Phase）

Phase 1: 项目脚手架与基础设施

文件: pom.xml, application.yml, DeepResearchApplication.java, 目录结构

- Maven 多模块或单模块结构（建议单模块 + 清晰 package 分层）
- 所有 Spring AI 2.0 + LangGraph4j + DeepSeek 依赖
- application.yml：DeepSeek API 配置、Milvus 连接、PG/Redis、Bocha Search
- 基础配置类：Jackson 3 配置、虚拟线程执行器、全局异常处理

Phase 2: 领域模型 (Domain / State)

文件: workflow/state/ResearchState.java, agent/bundle/AgentBundle.java 及相关 record

- ResearchState — 不可变 record，含 Builder 模式（withXxx 方法），贯穿整个工作流
- 子模型 records：Evidence, Finding, SearchPlan, AuditFlag, Citation, SourceIndex
- AgentBundle — 聚合 8 个 ChatClient 的配置容器

Phase 3: Agent 层 — ChatClient 配置 + System Prompts

文件: agent/config/AgentConfig.java, src/main/resources/prompts/*.st

- 8 个 Agent 的 ChatClient Bean 定义
- 分层模型：4 个核心 Agent 用 deepseek-v4-pro，4 个工具 Agent 用 deepseek-v4-flash
- 独立 StringTemplate 格式 Prompt 模板文件
- BeanOutputConverter + StructuredOutputAdvisor 用于结构化输出
- 每个 Agent 的 Fallback 默认输出

Phase 4: 工具层 (Tools)

文件: tool/search/BochaSearchTool.java, tool/search/SearchTool.java 接口

- SearchTool 接口抽象（方便后续切换搜索引擎）
- BochaSearchTool — HTTP 封装 Bocha API（OkHttp/RestClient）
- FallbackSearchTool — 降级搜索
- Spring AI @Tool 注解方式注册工具
- Bocha MCP Server 集成（可选，作为备选方案）

Phase 5: RAG 层 (Milvus + ETL)

文件: rag/*

- Milvus VectorStore 配置
- DocumentIngestionService — 文档导入 ETL Pipeline（PDF/Word/Markdown → 分块 → 向量化 → 写入 Milvus）
- LocalScoutService — 向量相似度检索 + tenant_id 过滤
- CitationValidator — 引用合法性校验（正则提取 + 合法 ID 集比对）
- 知识库三层架构：种子数据加载器 + 研究产出自动回写 + 用户文档注入 API

Phase 6: 工作流编排 (LangGraph4j)

文件: workflow/*

- ResearchWorkflow — LangGraph4j StateGraph 定义
- 8 个 Node 实现类（每个 Agent 对应一个 node）
- 2 个条件路由：routeIntent（direct/research）、shouldReflect（reflect/write）
- SSE 事件发射器 — 每个 node 执行前后推送进度事件
- Checkpoint 支持（长任务暂停/恢复）

Phase 7: API 层 (WebFlux + SSE)

文件: api/*

- ResearchController — POST /api/research 发起研究，GET /api/research/{id}/stream SSE 流式进度
- DTO: ResearchRequest, ResearchResponse, ProgressEvent
- 全局异常处理器
- Swagger/OpenAPI 文档

Phase 8: 安全与多租户 (Spring Security)

文件: security/*

- Spring Security + JWT 无状态认证
- OAuth2 Resource Server 配置
- TenantFilter — 从 JWT 提取 tenantId 注入请求上下文
- Milvus 查询时强制 tenant_id 过滤
- PII 脱敏 Advisor

Phase 9: 记忆系统 (Memory)

文件: memory/*

- ShortTermMemoryService — Redis MessageWindowChatMemory
- LongTermMemoryService — PostgreSQL 用户画像/偏好存储
- SemanticMemoryService — Milvus 向量记忆检索
- Spring AI ChatMemory 抽象 + 自定义 Advisor 注入记忆上下文

Phase 10: 韧性、可观测性与文档

文件: common/observability/*, CLAUDE.md, README.md, 需求报告更新

- Resilience4j：LLM API 重试（429/5xx 指数退避）、搜索熔断
- 模型降级：pro → flash 自动切换
- OpenTelemetry + Micrometer Tracing
- Token 成本拦截器
- CLAUDE.md（项目开发指南）
- README.md（项目说明、快速启动、架构图）
- 更新 docs/DeepResearch 多 Agent 行业深度研究助手 - 需求分析与技术实现报告.md

  关键设计决策

  为什么用 LangGraph4j 而不是手写编排

    - StateGraph + conditional edge 原生支持循环（Reflect → Search），手写需要大量 if/else + while 样板代码
    - 内置 checkpoint 机制，长研究任务可暂停/恢复
    - 图结构可视化导出（graphviz），便于调试和文档
    - 与 Spring AI 集成良好，node 内部直接调用 ChatClient

  为什么用 Record 做 State

    - 不可变性保证线程安全（Virtual Threads 并发场景）
    - Builder 模式 withXxx 方法清晰表达状态转换
    - 序列化友好（Jackson 3 原生支持 Record）

  为什么两层模型

    - 核心 Agent（Planner/Analyst/Writer/Reflect）需要强推理能力 → v4-pro
    - 工具 Agent（Router/Scout/Judge）任务相对机械 → v4-flash 够用且省钱

  为什么 Prompt 独立文件

    - 修改 Prompt 不需要重新编译
    - 方便非开发人员调优
    - 版本管理友好（git diff 清晰）

  验证方案

    1. 单元测试: 每个 Agent 的 Fallback 行为、CitationValidator 正则、JSON 解析容错
    2. 集成测试: LangGraph4j 工作流端到端（用 Mock LLM 或测试环境 DeepSeek）
    3. 手动验证:
       - 启动应用 → POST /api/research 发起研究 → SSE 流观察进度 → 检查最终研报
       - 验证引用合法性（报告中不应出现幻觉引用 ID）
       - 验证 Reflect 循环触发（给出一个复杂 query，观察是否补搜）
    4. 性能验证: 虚拟线程并发检索耗时、Token 消耗统计

⏺ Auto mode lets Claude handle permission prompts automatically — Claude checks each tool call for risky actions and prompt injection before executing. Actions Claude
identifies as safe are executed, while actions Claude identifies as risky are blocked and Claude may try a different approach. Ideal for long-running tasks. Sessions are
slightly more expensive. Claude can make mistakes that allow harmful commands to run, it's recommended to only use in isolated environments. Shift+Tab to change mode.

Thought for 13s (ctrl+o to expand)

⏺ 计划已批准，开始实施。先创建任务清单，然后逐步推进。

