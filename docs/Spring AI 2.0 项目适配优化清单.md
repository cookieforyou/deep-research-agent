# DeepResearch 项目 Spring AI 2.0 全面适配优化清单

> **基线**：Spring AI 2.0.0 GA + Spring Boot 4.1.0 + Java 21
> **分析日期**：2026-07-12
> **分析范围**：全部 Agent、Workflow、RAG、Security、Observability、Memory、Cache 模块

---

## 一、总览：已适配项（✅）与待优化项（🔴/🟡/🟢）

| # | 优化项 | 严重度 | 影响范围 | 预估工作量 |
|---|--------|--------|----------|-----------|
| 1 | 工具调用：引入 `@Tool` 注解 + `ToolCallingAdvisor` | 🔴 架构级 | 所有 Agent + 搜索工具 | 3-5天 |
| 2 | 结构化输出：使用 `.entity()` 替代手动 JSON 解析 | 🔴 架构级 | 7个Agent的JSON解析逻辑 | 3-4天 |
| 3 | RAG：引入 `QuestionAnswerAdvisor` 替代手动拼接 | 🔴 架构级 | LocalScoutAgent, RAG模块 | 2-3天 |
| 4 | 配置属性：移除 `.options` 冗余层级 | 🟡 配置 | application.yml | 0.5天 |
| 5 | ChatMemory：引入 `MessageChatMemoryAdvisor` | 🟡 架构 | ShortTermMemoryService | 1-2天 |
| 6 | Advisor 链：构建企业级统一 Advisor 链 | 🟡 架构 | AgentBundle, Security | 2-3天 |
| 7 | 可观测性：启用 Spring AI 2.0 内置观测 | 🟡 可观测性 | ObservabilityConfig, TokenTrackingAdvisor | 1-2天 |
| 8 | 模型路由：实现 SmartRoutingChatModel | 🟢 增强 | ModelFallbackService, AgentBundle | 2-3天 |
| 9 | Token 预算：实现 TokenBudgetAdvisor 限流 | 🟢 增强 | 新增 Advisor | 1-2天 |
| 10 | 高级 RAG：查询改写 + 重排序 | 🟢 增强 | RAG 模块 | 2-3天 |
| 11 | Prompt 管理：动态加载 + A/B 测试支持 | 🟢 增强 | 所有 Prompt 模板 | 2-3天 |
| 12 | Human-in-the-loop：写操作审批机制 | 🟢 增强 | 工具调用层 | 2-3天 |
| 13 | 文档处理：使用 Spring AI DocumentReader/Transformer | 🟢 增强 | DocumentIngestionService | 1-2天 |
| 14 | Eval 测试集：CI/CD 自动化评测 | 🟢 增强 | EvalAgent, 测试模块 | 2-3天 |
| 15 | 代码清理：移除废弃 Agent 枚举 | 🟢 清理 | AgentType.java | 0.5天 |
| 16 | 输出安全护栏：OutputGuardrailAdvisor | 🟢 增强 | 新增 Advisor | 1-2天 |

---

## 二、详细分析与优化方案

### 🔴 优化项 #1：工具调用 — 引入 `@Tool` 注解 + `ToolCallingAdvisor`

#### 现状问题

当前项目所有"工具"（搜索、证据评分、去重）均以普通 Java 方法实现，Agent 手动调用这些方法后在 prompt 中拼接结果，完全没有利用 Spring AI 2.0 的 `@Tool` 注解和 `ToolCallingAdvisor` 机制。

**关键文件**：
- `WebScoutAgent.java:163-207` — 手动调用 `searchTool.search()` → 拼接文本 → 喂给 LLM
- `LocalScoutAgent.java:140-185` — 手动调用 `vectorStoreService.similaritySearch()` → 拼接文本 → 喂给 LLM
- `SearchTool.java` — 自定义接口，非 Spring AI `ToolCallback`
- `ResilientSearchTool.java` — 自定义装饰器，非 Spring AI 工具调用链

#### Spring AI 2.0 最佳实践

```java
// ✅ 正确做法：使用 @Tool 注解定义工具
@Service
public class SearchTools {
    @Tool(description = "搜索互联网获取最新信息，返回相关结果列表")
    public List<SearchResult> webSearch(String query, int count) {
        return resilientSearchTool.search(query, count);
    }

    @Tool(description = "从企业内部知识库检索相关文档")
    public List<Document> localSearch(String query) {
        return vectorStoreService.similaritySearch(query, tenantId, 4, 0.7);
    }
}

// ChatClient 中通过 .tools() 方法注入
String response = chatClient.prompt()
    .user("分析2026年新能源汽车市场")
    .tools(searchTools)  // 框架自动处理工具调用循环
    .call()
    .content();
```

#### 优化方案

**步骤 1**：将搜索操作封装为 `@Tool` 方法

```java
// 新建: agent/tool/SearchTools.java
@Component
public class SearchTools {
    private final ResilientSearchTool resilientSearchTool;
    private final VectorStoreService vectorStoreService;
    private final TenantContext tenantContext;

    @Tool(description = "搜索互联网获取最新信息。返回标题、URL、摘要、发布时间等结构化结果")
    public List<SearchResult> webSearch(
        @ToolParam(description = "搜索关键词") String query,
        @ToolParam(description = "返回结果数量上限，默认10") int count) {
        return resilientSearchTool.search(query, count);
    }

    @Tool(description = "从企业内部知识库检索相关文档片段。返回文档内容、来源、相似度分数")
    public List<DocumentInfo> localSearch(
        @ToolParam(description = "检索查询语句") String query) {
        String tenantId = tenantContext.getCurrentTenantId();
        return vectorStoreService.similaritySearch(query, tenantId, 4, 0.7)
            .stream().map(this::toDocInfo).toList();
    }
}
```

**步骤 2**：重构 WebScoutAgent 和 LocalScoutAgent

移除手动搜索调用逻辑，改为让 LLM 通过 `ToolCallingAdvisor` 自动决定何时调用工具：

```java
// 重构后的 WebScoutAgent
@Service
public class WebScoutAgent {
    private final ChatClient chatClient;

    public List<Evidence> search(String query, SearchTools searchTools) {
        // LLM 自主决定调用 webSearch 工具，ToolCallingAdvisor 自动执行循环
        String rawOutput = chatClient.prompt()
            .system("你是网络取证专家。使用 webSearch 工具搜索信息...")
            .user("研究主题: " + query)
            .tools(searchTools)  // 注入工具，框架自动处理
            .call()
            .content();
        // ... 解析结构化输出
    }
}
```

**步骤 3**：在 AgentBundle 中注册工具

```java
@Bean
public ChatClient webScoutClient(ChatModel chatModel, SearchTools searchTools) {
    return ChatClient.builder(chatModel)
        .defaultTools(searchTools)  // 注册默认工具
        .defaultOptions(DeepSeekChatOptions.builder()
            .model(DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH)
            .temperature(0.4)
            .maxTokens(4096))
        .build();
}
```

**影响评估**：WebScoutAgent 和 LocalScoutAgent 的重构幅度较大，PlannerAgent 可能也需要调整以输出适合工具调用的搜索计划。

---

### 🔴 优化项 #2：结构化输出 — 使用 `.entity()` 替代手动 JSON 解析

#### 现状问题

当前所有 Agent 均使用 `JsonParseUtils.safeParse()` 手动解析 LLM 输出的 JSON 字符串，代码冗长且易出错：

**现状代码模式（每处 ~15-20 行）**：
```java
// IntentRouterAgent.java:88-101
String rawOutput = chatClient.prompt()
    .advisors(a -> a.param("agent", "IntentRouter"))
    .system(systemPrompt)
    .user(userPrompt)
    .call()
    .content();

RouteResult result = jsonUtils.safeParse(rawOutput, RouteResult.class, FALLBACK, "IntentRouter");
return normalizeIntent(result);
```

**涉及文件**（7 个 Agent 全部使用此模式）：
- `IntentRouterAgent.java:88-101`
- `PlannerAgent.java:82-116`
- `WebScoutAgent.java:183-194`
- `LocalScoutAgent.java:160-170`
- `AnalystAgent.java:85-101`
- `WriterAgent.java:103-121`
- `EvalAgent.java:105-122`

#### Spring AI 2.0 最佳实践

```java
// ✅ 正确做法：使用 .entity() 自动映射 + 自校正
RouteResult result = chatClient.prompt()
    .system(systemPrompt)
    .user(userPrompt)
    .call()
    .entity(RouteResult.class);  // 框架自动 JSON 解析 + 类型映射 + 自校正
```

#### 优化方案

**步骤 1**：确保所有返回类型为 Java Record

```java
// 现有的 Record 可直接用于 .entity()
public record RouteResult(String intent, String reasoning) {}
public record PlanResult(List<String> subQuestions, String reportOutline,
                          List<SearchPlan> searchPlans) {}
public record AnalysisResult(List<Finding> findings, boolean needsMoreResearch,
                              List<String> missingGaps, double completenessScore) {}
// ... 其他 Record 同理
```

**步骤 2**：替换每个 Agent 的调用方式

```java
// IntentRouterAgent — 重构后
public RouteResult route(String query) {
    if (isTrivialDirectQuery(query)) {
        return new RouteResult("direct", "简单问候或确认型问题，快速路由");
    }
    try {
        String userPrompt = userPromptTemplate.replace("{{query}}", query);
        return chatClient.prompt()
            .advisors(a -> a.param("agent", "IntentRouter").param("tier", "flash"))
            .system(systemPrompt)
            .user(userPrompt)
            .call()
            .entity(RouteResult.class);  // 替代 jsonUtils.safeParse()
    } catch (Exception e) {
        log.error("[IntentRouter] 路由判断异常，返回 fallback", e);
        return FALLBACK;
    }
}
```

**步骤 3**：移除 `JsonParseUtils` 在 Agent 层的依赖

`JsonParseUtils` 可保留作为底层工具（处理极端边缘情况），但 Agent 主流程不再依赖它。

**影响评估**：
- 每个 Agent 减少 5-10 行样板代码
- 无需手动维护 JSON fallback 默认值
- Spring AI 2.0 的自校正机制会自动重新请求格式错误的输出

---

### 🔴 优化项 #3：RAG — 引入 `QuestionAnswerAdvisor` 替代手动拼接

#### 现状问题

`LocalScoutAgent` 手动调用 `VectorStoreService.similaritySearch()` 获取文档，手动拼接为文本，再通过 user prompt 传给 LLM：

**现状代码**（LocalScoutAgent.java:140-185）：
```java
// 手动检索 → 手动拼接 → 手动注入 prompt
List<Document> docs = vectorStoreService.similaritySearch(searchQuery, tenantId, 4, 0.7);
StringBuilder docsText = new StringBuilder();
for (int i = 0; i < docs.size(); i++) {
    docsText.append(String.format("[%s] %s\n\n", docId, doc.getText()));
}
String userPrompt = userPromptTemplate
    .replace("{{documents}}", docsText.toString());
```

#### Spring AI 2.0 最佳实践

```java
// ✅ 正确做法：QuestionAnswerAdvisor 自动检索 + 注入上下文
@Service
public class RagService {
    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public String answerWithContext(String question) {
        return chatClient.prompt()
            .user(question)
            .advisors(new QuestionAnswerAdvisor(vectorStore,
                SearchRequest.builder()
                    .topK(5)
                    .similarityThreshold(0.7)
                    .build()))
            .call()
            .content();
    }
}
```

#### 优化方案

**步骤 1**：需要先实现 Spring AI 标准的 `VectorStore` 接口

当前 `VectorStoreService` 使用 Milvus Java SDK 直接操作，不是 Spring AI 的 `VectorStore` 接口。需要创建一个适配器：

```java
// 新建: rag/MilvusVectorStoreAdapter.java
@Component
public class MilvusVectorStoreAdapter implements VectorStore {
    private final VectorStoreService vectorStoreService;
    private final TenantContext tenantContext;

    @Override
    public void add(List<Document> documents) {
        String tenantId = tenantContext.getCurrentTenantId();
        vectorStoreService.insertDocuments(documents, tenantId);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        String tenantId = tenantContext.getCurrentTenantId();
        // 构建 FilterExpression（租户隔离 + 文档类型过滤）
        String filter = buildFilterExpression(request, tenantId);
        return vectorStoreService.similaritySearch(
            request.getQuery(), tenantId, filter,
            request.getTopK(), request.getSimilarityThreshold());
    }

    // ... 其他方法
}
```

**步骤 2**：修改 ChatClient 配置，注册 `QuestionAnswerAdvisor`

```java
// AgentBundle.java 中
@Bean("localScoutClient")
public ChatClient localScoutClient(ChatModel chatModel, VectorStore vectorStore) {
    return ChatClient.builder(chatModel)
        .defaultAdvisors(
            piiMaskingAdvisor,
            tokenTrackingAdvisor,
            new QuestionAnswerAdvisor(vectorStore,
                SearchRequest.builder()
                    .topK(4)
                    .similarityThreshold(0.7)
                    .build())
        )
        .defaultOptions(DeepSeekChatOptions.builder()
            .model(DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH)
            .temperature(0.4)
            .maxTokens(4096))
        .build();
}
```

**步骤 3**：简化 LocalScoutAgent

LLM 调用时不需要手动拼接文档内容，`QuestionAnswerAdvisor` 会自动检索相关文档并注入到 system prompt 中。

**影响评估**：
- `VectorStoreService` 需要适配为 Spring AI `VectorStore` 接口
- `LocalScoutAgent` 逻辑大幅简化
- `DocumentIngestionService` 可集成 Spring AI 的 `DocumentReader`/`DocumentTransformer`

---

### 🟡 优化项 #4：配置属性 — 移除 `.options` 冗余层级

#### 现状问题

`application.yml` 中 DeepSeek 和 OpenAI embedding 配置仍使用 `.options` 层级：

```yaml
# application.yml:69-73 — 当前写法
deepseek:
  chat:
    options:             # ← Spring AI 2.0 已移除 .options 层级
      tool-model: deepseek-v4-flash
      core-model: deepseek-v4-pro
      max-tokens: 8192

# application.yml:97-99 — 当前写法
openai:
  embedding:
    options:             # ← 应移除
      model: text-embedding-v3
```

#### Spring AI 2.0 新写法

```yaml
# ✅ Spring AI 2.0 扁平化配置
deepseek:
  chat:
    tool-model: deepseek-v4-flash
    core-model: deepseek-v4-pro
    max-tokens: 8192

openai:
  embedding:
    model: text-embedding-v3
```

#### 优化方案

修改 `application.yml`，移除 `options:` 行，将子属性提升一级。同时检查 `DeepResearchProperties.java` 中对应的 `@ConfigurationProperties` 绑定是否需要同步调整。

---

### 🟡 优化项 #5：ChatMemory — 引入 `MessageChatMemoryAdvisor`

#### 现状问题

项目有自定义的三层记忆架构（`ShortTermMemoryService`、`SemanticMemoryService`、`LongTermMemoryService`），但没有使用 Spring AI 2.0 标准的 `ChatMemory` 接口和 `MessageChatMemoryAdvisor`：

**现状**：
- `ShortTermMemoryService` — 基于 Redis 的自定义消息存储
- `MemoryManager.buildMemoryContext()` — 手动聚合三层记忆 → 注入 Planner prompt
- 没有使用 `ChatMemory` 接口（如 `InMemoryChatMemory`、`RedisChatMemory`）

#### Spring AI 2.0 最佳实践

```java
// ✅ 标准 ChatMemory + MessageChatMemoryAdvisor
@Bean
public ChatMemory chatMemory() {
    return new InMemoryChatMemory();  // 生产可换 RedisChatMemory / CassandraChatMemory
}

@Bean
public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
    return builder
        .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
        .build();
}

// 使用时通过 advisor param 传递 conversationId
chatClient.prompt()
    .user(message)
    .advisors(spec -> spec
        .param("chat_memory_conversation_id", conversationId))
    .call()
    .content();
```

#### 优化方案

**步骤 1**：实现 `ChatMemory` 接口适配现有的 Redis 短期记忆

```java
// 新建: memory/RedisChatMemoryAdapter.java
@Component
public class RedisChatMemoryAdapter implements ChatMemory {
    private final ShortTermMemoryService shortTermMemory;

    @Override
    public void add(String conversationId, List<Message> messages) {
        // 适配现有 Redis 存储
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        // 从 Redis 读取最近 N 条消息
    }

    @Override
    public void clear(String conversationId) {
        // 清除会话记忆
    }
}
```

**步骤 2**：在 ChatClient 配置中注册 `MessageChatMemoryAdvisor`

```java
@Bean
public ChatClient plannerClient(ChatModel chatModel, ChatMemory chatMemory) {
    return ChatClient.builder(chatModel)
        .defaultAdvisors(
            piiMaskingAdvisor,
            new MessageChatMemoryAdvisor(chatMemory),  // 自动注入对话历史
            tokenTrackingAdvisor
        )
        .defaultOptions(...)
        .build();
}
```

**影响评估**：语义记忆和长期记忆仍可保留自定义实现（Spring AI 2.0 不提供这两层的标准接口），但短期对话记忆应切换到标准 `ChatMemory` 接口。

---

### 🟡 优化项 #6：Advisor 链 — 构建企业级统一 Advisor 链

#### 现状问题

当前 Advisor 注册分散、职责不完整：

**现状**（AgentBundle.java:70-73）：
```java
private ChatClient.Builder createBuilder(ChatModel chatModel) {
    return ChatClient.builder(chatModel)
        .defaultAdvisors(piiMaskingAdvisor, tokenTrackingAdvisor);  // 仅2个Advisor
}
```

#### Spring AI 2.0 企业级 Advisor 链最佳实践

完整的 Advisor 执行序应为：

```
请求流入
  ├─ [1] RateLimitAdvisor          (限流拦截，order=100)
  ├─ [2] TokenBudgetAdvisor        (Token预算检查，order=200)
  ├─ [3] InputSanitizeAdvisor      (输入脱敏，order=300) → 已有 PiiMaskingAdvisor
  ├─ [4] MessageChatMemoryAdvisor  (记忆注入，order=400)
  ├─ [5] QuestionAnswerAdvisor     (RAG检索，order=500)
  ├─ [6] ToolCallingAdvisor        (工具调用，order=1000，框架内置)
  ▼
LLM 调用
  ▼
  ├─ [6] ToolCallingAdvisor        (工具结果回传)
  ├─ [5] QuestionAnswerAdvisor     (RAG后置处理)
  ├─ [4] MessageChatMemoryAdvisor  (记忆保存)
  ├─ [3] OutputGuardrailAdvisor    (输出安全护栏)
  ├─ [2] TokenBudgetAdvisor        (Token预算扣减)
  └─ [1] AuditLogAdvisor           (审计日志落库)
```

#### 优化方案

**步骤 1**：新增缺失的 Advisor
- `TokenBudgetAdvisor` — Token 预算管控 + 用户配额限流
- `OutputGuardrailAdvisor` — 输出安全护栏（敏感词过滤、幻觉检测）
- `AuditLogAdvisor` — 审计日志落库

**步骤 2**：创建统一的 `EnterpriseChatClientConfig` 配置类

```java
@Configuration
public class EnterpriseChatClientConfig {
    @Bean
    public ChatClient enterpriseChatClient(
            ChatClient.Builder builder,
            ChatModel chatModel,
            ChatMemory chatMemory,
            VectorStore vectorStore,
            PiiMaskingAdvisor piiMask,           // 已有
            TokenTrackingAdvisor tokenTrack,      // 已有
            TokenBudgetAdvisor tokenBudget,       // 新增
            OutputGuardrailAdvisor outputGuard,   // 新增
            AuditLogAdvisor auditLog) {           // 新增

        return builder
            .defaultModel(chatModel)
            .defaultSystem("你是企业智能研究助手，请用中文回答。")
            .defaultAdvisors(
                tokenBudget,                                // 预算检查
                piiMask,                                    // 输入脱敏
                outputGuard,                                // 输出护栏
                new MessageChatMemoryAdvisor(chatMemory),    // 对话记忆
                new QuestionAnswerAdvisor(vectorStore,       // RAG 检索
                    SearchRequest.builder()
                        .topK(5)
                        .similarityThreshold(0.7)
                        .build()),
                tokenTrack,                                 // Token追踪
                auditLog                                    // 审计日志
            )
            .build();
    }
}
```

**影响评估**：需要新增 2-3 个 Advisor 类，但架构会从"散装"升级为"统一链式治理"。

---

### 🟡 优化项 #7：可观测性 — 启用 Spring AI 2.0 内置观测

#### 现状问题

项目实现了大量的自定义指标和 Advisor（`TokenTrackingAdvisor`、`BusinessMetrics`、`TokenUsageTracker`、`WorkflowTracingHelper`），但 Spring AI 2.0 已经内置了完整的 Micrometer 观测能力，很多自定义代码可以移除或简化。

**Spring AI 2.0 自动暴露的指标**：

| 自动指标 | 类型 | 说明 |
|----------|------|------|
| `spring.ai.chat.client.duration` | Timer | ChatClient 总调用耗时 |
| `spring.ai.chat.model.duration` | Timer | 模型推理耗时 |
| `spring.ai.chat.model.token.usage` | Counter | Token 消耗量 |
| `spring.ai.tool.call.duration` | Timer | 工具调用耗时 |
| `spring.ai.vector.store.search.duration` | Timer | 向量检索耗时 |

#### 优化方案

**步骤 1**：在 `application.yml` 中启用 Spring AI 内置观测

```yaml
spring:
  ai:
    chat:
      observations:
        include-input: false   # 生产环境关闭防止隐私泄露
        include-output: false

management:
  observations:
    enable:
      spring.ai: true
```

**步骤 2**：评估哪些自定义指标可被内置指标替代

| 自定义指标 | 可替代性 | 建议 |
|-----------|----------|------|
| `TokenTrackingAdvisor` → `deepresearch.llm.*` | ✅ 可替代 | 用内置 `spring.ai.chat.model.token.usage` 替代 |
| `BusinessMetrics.recordSearchCall()` | ⚠️ 部分可替代 | 搜索非 Spring AI 内置，保留但简化 |
| `BusinessMetrics.recordCacheAccess()` | ⚠️ 保留 | 缓存指标非内置 |
| `WorkflowTracingHelper` 手动 Span | ✅ 可替代 | 内置 Observation 自动创建 Span |

**步骤 3**：简化 `TokenTrackingAdvisor`

可将其从自定义实现简化为对 Spring AI 内置指标的补充（如额外记录 sessionId 标签）。

---

### 🟢 优化项 #8：模型路由 — 实现 SmartRoutingChatModel

#### 现状问题

当前模型降级是二元切换（Pro→Flash），无智能路由：

- `ModelFallbackService` — 仅处理 Pro 调用失败 → 切换 Flash
- 没有根据任务复杂度选择模型的机制
- Planner/Writer 总是优先用 Pro（即使简单查询）

#### 优化方案

实现 `SmartRoutingChatModel` 实现 `ChatModel` 接口：

```java
@Component
public class SmartRoutingChatModel implements ChatModel {
    private final ChatModel proModel;     // deepseek-v4-pro
    private final ChatModel flashModel;   // deepseek-v4-flash
    private final CircuitBreakerRegistry cbRegistry;

    @Override
    public ChatResponse call(Prompt prompt) {
        ModelTier tier = analyzeComplexity(prompt);
        ChatModel target = tier == ModelTier.PREMIUM ? proModel : flashModel;

        CircuitBreaker cb = cbRegistry.circuitBreaker("llm-circuit-breaker");
        try {
            return cb.run(() -> target.call(prompt));
        } catch (Exception e) {
            // 降级逻辑：Pro → Flash → 抛出异常
            if (target == proModel) return flashModel.call(prompt);
            throw e;
        }
    }

    private ModelTier analyzeComplexity(Prompt prompt) {
        String text = extractText(prompt);
        boolean hasTools = prompt.getOptions() != null
            && !prompt.getOptions().getToolCallbacks().isEmpty();
        // 启发式：工具调用 + 长文本 → 用 Pro
        return (hasTools || text.length() > 4000)
            ? ModelTier.PREMIUM : ModelTier.STANDARD;
    }
}
```

---

### 🟢 优化项 #9：Token 预算 — 实现 TokenBudgetAdvisor

#### 现状问题

没有 Token 预算管控机制，恶意或异常调用可能造成成本失控。

#### 优化方案

```java
@Component
public class TokenBudgetAdvisor implements CallAroundAdvisor {
    private final RedissonClient redisson;
    private final TokenBudgetProperties budgetProps;

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest request, CallAroundAdvisorChain chain) {
        String userId = (String) request.adviseContext().get("user_id");

        // 前置检查：分布式限流（每小时N次）
        RRateLimiter limiter = redisson.getRateLimiter("ai:budget:" + userId);
        limiter.trySetRate(RateType.OVERALL, 100, 1, RateIntervalUnit.HOURS);
        if (!limiter.tryAcquire(1)) {
            throw new AiQuotaExceededException("AI调用配额已用完");
        }

        // 执行调用
        AdvisedResponse response = chain.nextAroundCall(request);

        // 后置：扣减 Token 预算
        Usage usage = response.response().getMetadata().getUsage();
        long tokens = usage.getPromptTokens() + usage.getCompletionTokens();
        tokenBudgetService.deduct(userId, tokens);

        return response;
    }

    @Override
    public int getOrder() { return 200; }  // 早期执行，在模型调用前
}
```

---

### 🟢 优化项 #10：高级 RAG — 查询改写 + 重排序

#### 现状问题

当前 RAG 是最基础的 Top-K 向量检索：
- 没有查询改写（口语化问题 → 专业检索词）
- 没有混合检索（仅向量，无 BM25 关键词）
- 没有重排序（仅依赖 Milvus 原始相似度分数）

#### 优化方案

在 `QuestionAnswerAdvisor` 基础上扩展：

```java
@Service
public class AdvancedRagService {
    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public String ask(String userQuery) {
        // Step 1: 查询改写（用轻量 Flash 模型）
        String rewritten = rewriteQuery(userQuery);

        // Step 2: 多路检索（向量 + 关键词可通过 Milvus 混合检索实现）
        // Step 3: 重排序（引入 Cohere Rerank 或自建 Cross-Encoder）

        return chatClient.prompt()
            .user(userQuery)
            .advisors(new QuestionAnswerAdvisor(vectorStore,
                SearchRequest.builder()
                    .query(rewritten)  // 使用改写后的查询
                    .topK(10)
                    .similarityThreshold(0.6)
                    .build()))
            .call()
            .content();
    }

    private String rewriteQuery(String original) {
        return chatClient.prompt()
            .system("将用户口语化问题改写为精确的检索查询语句。只输出改写后的查询。")
            .user(original)
            .call()
            .content();
    }
}
```

---

### 🟢 优化项 #11：Prompt 管理 — 动态加载 + A/B 测试

#### 现状问题

Prompt 模板以 `.st` 文件存储在 `classpath:prompts/`，每次修改需重启应用。

#### 优化方案

```java
@Service
public class DynamicPromptService {
    private final PromptTemplateRepository repository;  // PostgreSQL
    private final StringRedisTemplate redis;

    public Prompt getTemplate(String templateId, Map<String, Object> variables) {
        // 1. 先查 Redis 缓存
        String cached = redis.opsForValue().get("prompt:" + templateId);
        if (cached != null) {
            return new PromptTemplate(cached).create(variables);
        }

        // 2. 查数据库（支持运行时热更新）
        PromptTemplateEntity entity = repository.findActiveByTemplateId(templateId);
        redis.opsForValue().set("prompt:" + templateId,
            entity.getContent(), Duration.ofMinutes(5));

        return new PromptTemplate(entity.getContent()).create(variables);
    }
}
```

**数据库 Schema**：
```sql
CREATE TABLE prompt_templates (
    id VARCHAR(64) PRIMARY KEY,
    version INT NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(16) DEFAULT 'active',  -- active / ab_test / deprecated
    ab_group VARCHAR(8),                   -- A / B
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

---

### 🟢 优化项 #12：Human-in-the-loop — 写操作审批

#### 现状问题

当前项目没有工具写操作（所有操作为只读搜索/检索），但如果未来扩展（如自动生成报告并发布、自动发送邮件），需要审批机制。

#### 优化方案

```java
@Service
public class ApprovalToolService {
    @Tool(description = "发布研究报告到内部知识库（需要审批）")
    public String publishReport(String reportId, ToolContext context) {
        Boolean requiresApproval = (Boolean) context.getContext().get("requires_approval");
        if (Boolean.TRUE.equals(requiresApproval)) {
            return "ACTION_REQUIRED: 发布报告需要审批，已提交审批流。";
        }
        // 执行发布
        return "报告已成功发布";
    }
}
```

---

### 🟢 优化项 #13：文档处理 — 使用 Spring AI DocumentReader/Transformer

#### 现状问题

`DocumentIngestionService` 仅支持纯文本输入，PDF/Word 解析标记为 TODO，且使用自定义 `chunkText()` 方法分块。

#### 优化方案

引入 Spring AI 的文档处理组件：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-tika-document-reader</artifactId>
</dependency>
```

```java
@Service
public class DocumentIngestionService {
    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;  // Spring AI 内置

    public int ingestDocument(InputStream input, String fileName, String tenantId) {
        // 使用 TikaDocumentReader 自动识别 PDF/Word/HTML/Markdown
        DocumentReader reader = new TikaDocumentReader(input);
        List<Document> docs = reader.get();

        // 使用 Spring AI 内置的 TokenTextSplitter
        List<Document> chunks = splitter.apply(docs);

        // 添加元数据
        chunks.forEach(doc -> {
            doc.getMetadata().put("source", fileName);
            doc.getMetadata().put("tenant_id", tenantId);
        });

        vectorStore.add(chunks);
        return chunks.size();
    }
}
```

---

### 🟢 优化项 #14：Eval 测试集 — CI/CD 自动化评测

#### 现状问题

EvalAgent 仅在运行时异步执行，没有 CI/CD 中的自动化回归测试。每次 Prompt 修改或模型切换无法自动验证。

#### 优化方案

创建评估数据集和自动化测试：

```java
@SpringBootTest
class AiEvaluationRegressionTest {
    @Autowired
    private ChatClient chatClient;

    @ParameterizedTest
    @CsvSource({
        "2026年新能源汽车趋势, '新能源'",
        "AI芯片市场格局分析, 'GPU'",
        "全球经济展望2026, 'GDP'"
    })
    void testResearchQuality(String query, String expectedKeyword) {
        String report = chatClient.prompt()
            .user(query)
            .call()
            .content();

        // 基础断言：报告包含预期关键词
        assertThat(report).containsIgnoringCase(expectedKeyword);
        assertThat(report.length()).isGreaterThan(500);

        // LLM-as-a-Judge 评估
        EvalResult eval = evalAgent.evaluate(query, List.of(), report, List.of());
        assertThat(eval.overallScore()).isGreaterThanOrEqualTo(3.0);
    }
}
```

---

### 🟢 优化项 #15：代码清理 — 移除废弃 Agent 枚举

#### 现状问题

`AgentType.java` 仍包含已移出工作流的 Agent：
- `EVIDENCE_JUDGE` — 已被 `EvidenceDeduplicationService` 代码级去重替代
- `REFLECT` — Reflect 循环已移除（单轮模式）

#### 优化方案

```java
// 清理后的 AgentType.java
public enum AgentType {
    INTENT_ROUTER("IntentRouter", "flash"),
    PLANNER("Planner", "pro"),
    WEB_SCOUT("WebScout", "flash"),
    LOCAL_SCOUT("LocalScout", "flash"),
    ANALYST("Analyst", "pro"),
    WRITER("Writer", "pro"),
    EVAL("Eval", "flash");
    // 移除 EVIDENCE_JUDGE 和 REFLECT
}
```

同步删除：
- `prompts/evidence-judge.st`
- `prompts/reflect.st`
- `agent/judge/EvidenceJudgeAgent.java`
- `agent/reflect/ReflectAgent.java`
- `common/model/JudgeResult.java`
- `common/model/ReflectResult.java`

---

### 🟢 优化项 #16：输出安全护栏 — OutputGuardrailAdvisor

#### 现状问题

当前仅有输入侧 PII 脱敏（`PiiMaskingAdvisor`），没有输出侧的安全护栏（敏感词过滤、幻觉检测、竞品提及等）。

#### 优化方案

```java
@Component
public class OutputGuardrailAdvisor implements CallAroundAdvisor {
    private final List<GuardrailRule> rules;

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest request, CallAroundAdvisorChain chain) {
        AdvisedResponse response = chain.nextAroundCall(request);
        String output = response.response().getResult().getOutput().getText();

        for (GuardrailRule rule : rules) {
            GuardrailResult result = rule.check(output);
            if (result.isBlocked()) {
                log.warn("输出被护栏拦截: rule={}", rule.getName());
                return replaceWithSafeResponse(response, rule.getFallbackMessage());
            }
        }
        return response;
    }

    @Override
    public int getOrder() { return 300; }  // 输出侧优先
}
```

---

## 三、分阶段实施路线图

### Phase 1：基础适配（第 1-2 周）— 🔴 项优先

```
Week 1:
  ✅ #4  配置属性扁平化（0.5天）
  ✅ #15 代码清理：移除废弃 Agent（0.5天）
  ✅ #2  结构化输出：.entity() 替换手动 JSON 解析（3-4天）

Week 2:
  ✅ #1  工具调用：@Tool 注解 + ToolCallingAdvisor（3-5天）
```

**交付物**：Agent 代码量减少 ~30%，LLM JSON 解析可靠性提升。

### Phase 2：架构升级（第 3-4 周）— 🟡 项

```
Week 3:
  ✅ #3  RAG：QuestionAnswerAdvisor + VectorStore 适配（2-3天）
  ✅ #5  ChatMemory：MessageChatMemoryAdvisor（1-2天）

Week 4:
  ✅ #6  Advisor 链：统一 EnterpriseChatClientConfig（2-3天）
  ✅ #7  可观测性：启用 Spring AI 内置观测（1-2天）
```

**交付物**：RAG 架构标准化，Advisor 链统一治理，可观测性简化。

### Phase 3：增强优化（第 5-8 周）— 🟢 项

```
Week 5-6:
  ✅ #8  模型路由：SmartRoutingChatModel（2-3天）
  ✅ #9  Token 预算：TokenBudgetAdvisor（1-2天）
  ✅ #16 输出安全护栏：OutputGuardrailAdvisor（1-2天）

Week 7-8:
  ✅ #10 高级 RAG：查询改写 + 重排序（2-3天）
  ✅ #11 Prompt 动态管理（2-3天）
  ✅ #13 文档处理标准化（1-2天）
  ✅ #14 Eval 测试集（2-3天）
  ✅ #12 Human-in-the-loop（2-3天）
```

**交付物**：企业级 AI 平台，成本可控、安全合规、可度量。

---

## 四、风险与注意事项

1. **`.entity()` 迁移风险**：Spring AI 2.0 的自校正结构化输出依赖模型的 JSON 输出能力。DeepSeek V4 在 JSON 格式遵从方面表现良好，但仍需保留 Fallback 机制。

2. **`@Tool` 注解兼容性**：`ToolCallingAdvisor` 的工具调用循环会增加额外的 LLM 推理轮次（工具选择 → 调用 → 结果回传），可能增加延迟和 Token 消耗。需评估 Agent Loop 对研究总耗时的影响。

3. **`VectorStore` 适配**：Spring AI 2.0 原生支持 Milvus（`spring-ai-milvus-store`），但当前项目使用原始 `milvus-sdk-java`。如果切换为 Spring AI 的 Milvus VectorStore，需要数据迁移。

4. **向后兼容**：所有优化应逐步进行，每完成一个 Phase 就充分测试后再推进。不建议一次性全部重写。

5. **LangGraph4j 依赖**：项目仍重度依赖 LangGraph4j 做工作流编排，这在 Spring AI 2.0 生态中不是标准组件（Spring AI 2.0 倾向于用 Advisor 链 + ToolCallingManager 实现 Agent 编排）。未来可评估是否将工作流逻辑迁移到纯 Spring AI 2.0 方案。

---

## 五、结论

当前项目在**依赖管理、ChatClient 使用、MCP 集成、安全基础**方面已经很好地适配了 Spring AI 2.0。核心差距集中在三个架构级设计模式上：

1. **工具调用未使用 `@Tool` 机制**（最大差距）
2. **结构化输出未使用 `.entity()`**（最大代码简化机会）
3. **RAG 未使用 `QuestionAnswerAdvisor`**（最大架构改进机会）

按照上述三阶段路线图推进，项目将从一个"功能完善但偏自定义"的 AI 应用，升级为"全面遵循 Spring AI 2.0 企业级最佳实践"的标准 AI 平台，同时在代码简洁性、可维护性和可观测性方面获得显著提升。


