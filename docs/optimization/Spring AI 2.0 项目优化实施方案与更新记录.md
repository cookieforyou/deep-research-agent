# DeepResearch 项目 Spring AI 2.0 优化实施方案与更新记录

> **基线**：Spring AI 2.0.0 GA + Spring Boot 4.1.0 + Java 21
> **前提文档**：[Spring AI 2.0 项目适配优化清单](Spring%20AI%202.0%20项目适配优化清单.md)
> **策略**：4 轮合并推进，每轮有明确验证点

---

## 一、为什么是 4 轮而不是 16 轮

16 个优化项中，真实依赖链只有 3 条：

```
#3 (VectorStore适配) ──→ #6 (Advisor统一链) ──→ #10 (高级RAG)
#5 (ChatMemory适配) ──→ #6 (Advisor统一链)
                        └──→ #9 (Token预算)
                        └──→ #16 (输出护栏)
#2 (.entity()) ──→ #1 (@Tool注解，受益于更干净的代码)
```

其余 11 项全部是独立项（纯配置、纯删除、纯新增），无依赖冲突。

**合并逻辑**：同一轮内的所有项修改的文件互不重叠，改动类型相同（全部是"新增"或全部是"机械替换"），可以一次性改完、一次性测试、一次性回滚。

---

## 二、四轮推进总览

```
Round 1 ──── 基础清理 + 机械替换（5项）
  │  改动：配置 / 删除 / 库替换 / 模式替换
  │  风险：零
  │  验证：完整工作流跑通即可
  ▼
Round 2 ──── 适配器 + Advisor 链基础层（5项）
  │  改动：全部是新增文件，不修改现有业务逻辑
  │  风险：极低（新旧两条线并行）
  │  验证：新配置的 ChatClient 可正常对话
  ▼
Round 3 ──── @Tool 工具调用重构（1项）
  │  改动：WebScoutAgent + LocalScoutAgent 内部重构
  │  风险：中（接口签名不变，但内部实现逻辑变化）
  │  验证：双源检索结果质量 + 研究耗时对比
  ▼
Round 4 ──── 增强特性收尾（5项）
  │  改动：全部是独立新功能
  │  风险：低
  │  验证：各项独立验证
  ▼
完成 ✅
```

---

## 三、Round 1 — 基础清理 + 机械替换

> **目标**：零风险打底，建立信心。改完项目立刻变干净。

### 纳入项（5 项合并）

| # | 优化项 | 改动类型 | 涉及文件 | 预计改动行数 |
|---|--------|----------|----------|-------------|
| 4 | 配置扁平化 | YAML 删行 | `application.yml` | -3 行 |
| 15 | 代码清理 | 删文件 + 删枚举 | 4 个 Java 文件 + 2 个 .st + 1 个枚举类 | 净删除 |
| 7 | 可观测性 | YAML 加配置 + 可选精简 Advisor | `application.yml`, `TokenTrackingAdvisor.java` | +8 行 |
| 2 | `.entity()` 替换 | 7 个 Agent 各改 3-5 行 | 7 个 Agent 类 | ~30 行净减 |
| 13 | 文档处理 | 加依赖 + 替换实现 | `pom.xml`, `DocumentIngestionService.java` | ~20 行 |

### 为什么这些可以合并

- **无互相依赖**：每个修改的文件都独立
- **改动类型一致**：全是"删/换/改配置"，不涉及新增架构
- **无接口变更**：所有 public 方法签名不变
- **Git diff 干净**：每一项都清晰可辨，真出问题可单独 revert

### 具体改动清单

#### #4 配置扁平化

**文件**：`src/main/resources/application.yml`

```yaml
# 改前（第 71-73 行）
deepseek:
  chat:
    options:
      tool-model: ${DEEPSEEK_TOOL_MODEL:deepseek-v4-flash}
      core-model: ${DEEPSEEK_CORE_MODEL:deepseek-v4-pro}
      max-tokens: 8192

# 改后
deepseek:
  chat:
    tool-model: ${DEEPSEEK_TOOL_MODEL:deepseek-v4-flash}
    core-model: ${DEEPSEEK_CORE_MODEL:deepseek-v4-pro}
    max-tokens: 8192
```

```yaml
# 改前（第 97-99 行）
openai:
  embedding:
    options:
      model: text-embedding-v3

# 改后
openai:
  embedding:
    model: text-embedding-v3
```

#### #15 代码清理

**删除文件**：
```
src/main/java/com/example/deepresearch/agent/judge/EvidenceJudgeAgent.java
src/main/java/com/example/deepresearch/agent/reflect/ReflectAgent.java
src/main/java/com/example/deepresearch/common/model/JudgeResult.java
src/main/java/com/example/deepresearch/common/model/ReflectResult.java
src/main/resources/prompts/evidence-judge.st
src/main/resources/prompts/reflect.st
```

**修改文件**：`AgentType.java` — 移除 `EVIDENCE_JUDGE` 和 `REFLECT` 枚举值

#### #7 可观测性

**文件**：`src/main/resources/application.yml`

```yaml
# 新增配置块
spring:
  ai:
    chat:
      observations:
        include-input: false   # 生产环境关闭
        include-output: false

management:
  observations:
    enable:
      spring.ai: true  # 启用 Spring AI 内置观测
```

**可选精简**：`TokenTrackingAdvisor.java` 的 Token 计数逻辑可与内置 `spring.ai.chat.model.token.usage` 指标对比，若内置指标满足需求则可停用自定义逻辑（保留 Advisor 骨架用于 sessionId 标签）。

#### #2 `.entity()` 结构化输出

**影响的 7 个 Agent**：

| Agent | 当前解析方式 | 改动后 |
|-------|-------------|--------|
| `IntentRouterAgent` | `jsonUtils.safeParse(raw, RouteResult.class, FALLBACK, "IntentRouter")` | `.call().entity(RouteResult.class)` |
| `PlannerAgent` | `jsonUtils.safeParse(raw, PlanResult.class, FALLBACK, "Planner")` | `.call().entity(PlanResult.class)` |
| `WebScoutAgent` | `jsonUtils.safeParse(raw, EvidenceListWrapper.class, ...)` | `.call().entity(EvidenceListWrapper.class)` |
| `LocalScoutAgent` | `jsonUtils.safeParse(raw, EvidenceListWrapper.class, ...)` | `.call().entity(EvidenceListWrapper.class)` |
| `AnalystAgent` | `jsonUtils.safeParse(raw, AnalysisResult.class, FALLBACK, "Analyst")` | `.call().entity(AnalysisResult.class)` |
| `WriterAgent` | `jsonUtils.safeParse(raw, WriteResult.class, FALLBACK, "Writer")` | `.call().entity(WriteResult.class)` |
| `EvalAgent` | `jsonUtils.safeParse(raw, EvalResult.class, FALLBACK, "Eval")` | `.call().entity(EvalResult.class)` |

**改动模式**（以 IntentRouterAgent 为例）：

```java
// 改前
String rawOutput = chatClient.prompt()
    .advisors(a -> a.param("agent", "IntentRouter").param("tier", "flash"))
    .system(systemPrompt)
    .user(userPrompt)
    .call()
    .content();
RouteResult result = jsonUtils.safeParse(rawOutput, RouteResult.class, FALLBACK, "IntentRouter");
return normalizeIntent(result);

// 改后
try {
    RouteResult result = chatClient.prompt()
        .advisors(a -> a.param("agent", "IntentRouter").param("tier", "flash"))
        .system(systemPrompt)
        .user(userPrompt)
        .call()
        .entity(RouteResult.class);
    return normalizeIntent(result);
} catch (Exception e) {
    log.error("[IntentRouter] 路由判断异常，返回 fallback", e);
    return FALLBACK;
}
```

**注意**：`.entity()` 内部有自校正机制，如果 LLM 返回的 JSON 格式不符合目标类型，框架会自动重新请求。但仍需保留 try-catch + fallback 兜底。

#### #13 文档处理标准化

**文件**：`pom.xml`

```xml
<!-- 新增依赖 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-tika-document-reader</artifactId>
</dependency>
```

**文件**：`DocumentIngestionService.java`

```java
// 改前：仅支持纯文本 + 自定义 chunkText()
public int ingestDocument(String rawContent, String fileName, String tenantId) {
    List<String> chunks = chunkText(rawContent, 1000, 200);
    // ...
}

// 改后：支持 PDF/Word/HTML/Markdown + TokenTextSplitter
private final TokenTextSplitter splitter = new TokenTextSplitter(800, 200, 5, 10000, true);

public int ingestDocument(InputStream input, String fileName, String tenantId) {
    DocumentReader reader = new TikaDocumentReader(input);
    List<Document> docs = reader.get();
    List<Document> chunks = splitter.apply(docs);
    chunks.forEach(doc -> {
        doc.getMetadata().put("file_name", fileName);
        doc.getMetadata().put("tenant_id", tenantId);
    });
    vectorStoreService.insertDocuments(chunks, tenantId);
    return chunks.size();
}
```

### Round 1 验证标准

```bash
# 1. 应用正常启动
mvn spring-boot:run

# 2. 发起一次完整深度研究
curl -X POST http://localhost:8080/api/research \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"query":"2026年中国新能源汽车市场趋势","userId":"test","deepResearch":true}'

# 3. 订阅 SSE 流确认所有阶段正常推进
curl -N http://localhost:8080/api/research/{sessionId}/stream

# 4. 确认 Metrics 端点正常
curl http://localhost:8080/actuator/prometheus | grep spring.ai
```

---

## 四、Round 2 — 适配器 + Advisor 链基础层

> **目标**：搭好 Spring AI 2.0 标准架构骨架，与现有代码双轨并行。

### 纳入项（5 项合并）

| # | 优化项 | 改动类型 | 新增文件数 |
|---|--------|----------|-----------|
| 3 | VectorStore 适配器 | 新增 implements 类 | 1 |
| 5 | ChatMemory 适配器 | 新增 implements 类 | 1 |
| 6 | Advisor 统一链 | 新增配置类 | 1 |
| 9 | Token 预算 | 新增 Advisor | 1 |
| 16 | 输出安全护栏 | 新增 Advisor | 1 |

### 为什么可以合并

- **全部是新增文件**：5 个新文件 + 0 个现有文件修改
- **新旧双轨并行**：新配置类 `EnterpriseChatClientConfig` 与现有 `AgentBundle` 并存，现有 Agent 仍走旧配置，新增的 ChatClient Bean 走新配置
- **零回滚成本**：真出问题，删掉 5 个新文件即可

### 具体改动清单

#### #3 VectorStore 适配器

**新增文件**：`src/main/java/com/example/deepresearch/rag/MilvusVectorStoreAdapter.java`

```java
/**
 * Milvus VectorStore 适配器 — 将现有 VectorStoreService 包装为 Spring AI 标准接口.
 * <p>
 * 不修改 VectorStoreService 的任何代码，纯适配器模式。
 * QuestionAnswerAdvisor 依赖此接口实现自动 RAG 检索。
 */
@Component
public class MilvusVectorStoreAdapter implements VectorStore {

    private final VectorStoreService vectorStoreService;
    private final TenantContext tenantContext;

    @Override
    public void add(List<Document> documents) {
        vectorStoreService.insertDocuments(documents, tenantContext.getCurrentTenantId());
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        String tenantId = tenantContext.getCurrentTenantId();
        String filterExpr = request.getFilterExpression() != null
            ? request.getFilterExpression().toString() : null;
        return vectorStoreService.similaritySearch(
            request.getQuery(), tenantId, filterExpr,
            request.getTopK(), request.getSimilarityThreshold());
    }

    @Override
    public void delete(List<String> idList) {
        vectorStoreService.deleteDocuments(idList, tenantContext.getCurrentTenantId());
    }

    // 可选实现
    @Override
    public Optional<Document> get(String id) { return Optional.empty(); }
}
```

#### #5 ChatMemory 适配器

**新增文件**：`src/main/java/com/example/deepresearch/memory/RedisChatMemoryAdapter.java`

```java
/**
 * Redis ChatMemory 适配器 — 将现有 ShortTermMemoryService 包装为 Spring AI 标准接口.
 */
@Component
public class RedisChatMemoryAdapter implements ChatMemory {

    private final ShortTermMemoryService shortTermMemory;

    @Override
    public void add(String conversationId, List<Message> messages) {
        for (Message msg : messages) {
            String role = msg instanceof UserMessage ? "user" : "assistant";
            shortTermMemory.addMessage(conversationId, role, msg.getText())
                .doOnError(e -> log.warn("ChatMemory 写入失败: {}", e.getMessage()))
                .subscribe();
        }
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        // 从 Redis 读取最近 N 条 — 可通过 ShortTermMemoryService 扩展实现
        return shortTermMemory.getRecentMessages(conversationId, lastN)
            .map(msg -> msg.getRole().equals("user")
                ? new UserMessage(msg.getContent())
                : new AssistantMessage(msg.getContent()))
            .collectList().blockOptional().orElse(List.of());
    }

    @Override
    public void clear(String conversationId) {
        shortTermMemory.clear(conversationId);
    }
}
```

> **注意**：需要 `ShortTermMemoryService` 增加 `getRecentMessages()` 和 `clear()` 方法。

#### #6 Advisor 统一链

**新增文件**：`src/main/java/com/example/deepresearch/agent/bundle/EnterpriseChatClientConfig.java`

```java
/**
 * 企业级 ChatClient 统一配置 — 基于 Spring AI 2.0 Advisor 链的声明式装配.
 * <p>
 * 与 AgentBundle 并行存在，不修改现有 Agent 的 ChatClient Bean。
 * 新开发的 Agent 或重构后的 Agent 可从此配置获取 ChatClient。
 */
@Configuration
public class EnterpriseChatClientConfig {

    @Bean("enterpriseChatClient")
    public ChatClient enterpriseChatClient(
            ChatClient.Builder builder,
            ChatModel chatModel,
            PiiMaskingAdvisor piiMask,
            TokenTrackingAdvisor tokenTrack,
            TokenBudgetAdvisor tokenBudget,
            OutputGuardrailAdvisor outputGuard,
            AuditLogAdvisor auditLog,
            ChatMemory chatMemory,
            VectorStore vectorStore) {

        return builder
            .defaultModel(chatModel)
            .defaultSystem("你是企业智能研究助手，请用中文回答。")
            .defaultAdvisors(
                tokenBudget,                                   // [200] Token预算检查
                piiMask,                                       // [300] 输入PII脱敏
                outputGuard,                                   // [300] 输出安全护栏
                new MessageChatMemoryAdvisor(chatMemory),       // [400] 对话记忆
                new QuestionAnswerAdvisor(vectorStore,          // [500] RAG检索
                    SearchRequest.builder()
                        .topK(5)
                        .similarityThreshold(0.7)
                        .build()),
                tokenTrack,                                    // [900] Token追踪
                auditLog                                       // [100] 审计日志
            )
            .build();
    }
}
```

#### #9 Token 预算 Advisor

**新增文件**：`src/main/java/com/example/deepresearch/security/TokenBudgetAdvisor.java`

```java
/**
 * Token 预算管控 Advisor — 用户配额限流 + Token 消耗追踪.
 * <p>
 * 基于 Redis 分布式限流器，在 ChatClient 调用前检查用户配额，
 * 调用后根据实际 Token 消耗扣减预算。
 */
@Component
public class TokenBudgetAdvisor implements CallAroundAdvisor {

    private final RedissonClient redisson;
    private final MeterRegistry meterRegistry;
    
    // 默认每用户每小时 100 次调用
    private static final int DEFAULT_HOURLY_LIMIT = 100;

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest request, CallAroundAdvisorChain chain) {
        String userId = (String) request.adviseContext().getOrDefault("user_id", "anonymous");

        // 前置：分布式限流
        RRateLimiter limiter = redisson.getRateLimiter("ai:budget:" + userId);
        limiter.trySetRate(RateType.OVERALL, DEFAULT_HOURLY_LIMIT, 1, RateIntervalUnit.HOURS);
        if (!limiter.tryAcquire(1)) {
            meterRegistry.counter("deepresearch.security.quota.exceeded",
                "user_id", userId).increment();
            throw new AiQuotaExceededException("AI 调用配额已用完，请稍后再试");
        }

        // 执行调用
        AdvisedResponse response = chain.nextAroundCall(request);

        // 后置：记录实际 Token 消耗
        Usage usage = response.response().getMetadata().getUsage();
        if (usage != null) {
            long total = (usage.getPromptTokens() != null ? usage.getPromptTokens() : 0)
                       + (usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0);
            meterRegistry.counter("deepresearch.token.consumed",
                "user_id", userId).increment(total);
        }

        return response;
    }

    @Override
    public String getName() { return "TokenBudgetAdvisor"; }
    @Override
    public int getOrder() { return 200; }  // 在模型调用前尽早执行
}
```

#### #16 输出安全护栏 Advisor

**新增文件**：`src/main/java/com/example/deepresearch/security/OutputGuardrailAdvisor.java`

```java
/**
 * 输出安全护栏 Advisor — LLM 响应后置安全校验.
 * <p>
 * 检查：敏感词、幻觉声明模式、竞品提及等。
 * 触发拦截时替换为安全兜底文案，并记录安全事件日志。
 */
@Component
public class OutputGuardrailAdvisor implements CallAroundAdvisor {

    private final SecurityLogService securityLog;
    
    // 敏感词列表（可配置化）
    private static final List<String> BLOCKED_PATTERNS = List.of(
        "系统提示词", "system prompt", "internal instructions"
    );

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest request, CallAroundAdvisorChain chain) {
        AdvisedResponse response = chain.nextAroundCall(request);

        String output = response.response().getResult().getOutput().getText();
        if (output == null) return response;

        for (String pattern : BLOCKED_PATTERNS) {
            if (output.toLowerCase().contains(pattern.toLowerCase())) {
                securityLog.logOutputBlocked(
                    (String) request.adviseContext().getOrDefault("user_id", "unknown"),
                    pattern);
                return replaceWithSafeResponse(response);
            }
        }
        return response;
    }

    private AdvisedResponse replaceWithSafeResponse(AdvisedResponse original) {
        // 替换为安全兜底文案
        return AdvisedResponse.from(original)
            .withModifiedOutput("抱歉，无法生成该内容。请重新描述您的问题。")
            .build();
    }

    @Override
    public String getName() { return "OutputGuardrailAdvisor"; }
    @Override
    public int getOrder() { return 300; }
}
```

### Round 2 验证标准

```bash
# 1. 确认新旧配置并存，应用正常启动
mvn spring-boot:run
# 日志应显示 AgentBundle 的 11 个 Bean + EnterpriseChatClientConfig 的 1 个 Bean

# 2. 使用新 enterpriseChatClient 进行简单对话
# (可写一个简单的测试端点或单元测试)

# 3. 确认 VectorStore 适配器正常工作
# QuestionAnswerAdvisor 使用适配器后的 RAG 检索结果与之前一致

# 4. 确认 Token 预算限流生效
# 短时间内大量调用应触发配额超限异常
```

---

## 五、Round 3 — `@Tool` 工具调用重构

> **目标**：这是唯一的架构变更项。搜索工具从手动调用改为 LLM 自主调用。

### 纳入项（1 项单改）

| # | 优化项 | 改动类型 | 涉及文件 |
|---|--------|----------|----------|
| 1 | @Tool 注解 | 新增工具类 + 重构 2 个 Agent | 3 个文件 |

### 为什么单独一轮

- 这是唯一需要改变 Agent 内部执行逻辑的项
- LLM 自主调用工具的延迟和 Token 消耗需要独立评估
- 出问题时可精确定位到这一项，不影响 Round 1-2 已完成的工作

### 具体改动清单

#### 新增：`SearchTools.java`

**文件**：`src/main/java/com/example/deepresearch/agent/tool/SearchTools.java`

```java
/**
 * 搜索工具集 — 使用 @Tool 注解暴露给 ToolCallingAdvisor.
 * <p>
 * 封装 Web 搜索和本地知识库检索为 Spring AI 2.0 标准工具。
 * LLM 通过 ToolCallingAdvisor 自主决定何时调用、以什么参数调用。
 */
@Component
public class SearchTools {

    private final ResilientSearchTool resilientSearchTool;
    private final VectorStoreService vectorStoreService;
    private final TenantContext tenantContext;

    @Tool(description = """
        搜索互联网获取最新公开信息。
        返回结果包含：标题、URL、摘要、发布时间。
        适用场景：查询最新新闻、市场数据、行业趋势等公开信息。""")
    public List<WebSearchResult> webSearch(
            @ToolParam(description = "搜索关键词，建议使用精确的关键词组合") String query,
            @ToolParam(description = "返回结果数量，默认10，最大15") int count) {
        return resilientSearchTool.search(query, Math.min(count, 15))
            .stream().map(WebSearchResult::from).toList();
    }

    @Tool(description = """
        从企业内部知识库检索相关文档。
        返回结果包含：文档内容片段、来源文件、相似度分数。
        适用场景：查询公司政策、产品文档、历史研究报告等内部资料。""")
    public List<DocSearchResult> localSearch(
            @ToolParam(description = "检索查询语句，建议使用专业术语") String query) {
        String tenantId = tenantContext.getCurrentTenantId();
        return vectorStoreService.similaritySearch(query, tenantId, 4, 0.7)
            .stream().map(DocSearchResult::from).toList();
    }

    // 工具返回类型（供 LLM 理解结果结构）
    public record WebSearchResult(String title, String url, String snippet, String date) {
        static WebSearchResult from(SearchResult r) {
            return new WebSearchResult(r.title(), r.url(), r.snippet(), r.publishDate());
        }
    }
    public record DocSearchResult(String content, String source, double score) {
        static DocSearchResult from(Document d) {
            return new DocSearchResult(
                d.getText(),
                d.getMetadata().getOrDefault("source", "unknown").toString(),
                ((Number) d.getMetadata().getOrDefault("score", 0.0)).doubleValue());
        }
    }
}
```

#### 重构：`WebScoutAgent`

```java
/**
 * 重构要点：
 * 1. 搜索操作不再手动调用 SearchTool，改为注入 SearchTools（@Tool 注解的 Bean）
 * 2. LLM 通过 ToolCallingAdvisor 自主调用 webSearch 工具
 * 3. LLM 输出使用 .entity() 解析（已在 Round 1 完成）
 * 4. search() 方法签名不变，LangGraph4j 工作流无需修改
 */
@Service
public class WebScoutAgent {

    private final ChatClient chatClient;
    private final SearchTools searchTools;

    public List<Evidence> search(String query, List<String> searchPlanQueries) {
        if (searchPlanQueries == null || searchPlanQueries.isEmpty()) {
            return List.of();
        }

        // 构建查询上下文（Planner 的搜索计划作为指引，而非硬性执行）
        String queriesContext = String.join("\n", searchPlanQueries.stream()
            .map(q -> "- " + q).toList());

        try {
            // LLM 自主决定调用 webSearch 工具的时机和参数
            // ToolCallingAdvisor 自动处理工具调用循环
            EvidenceListWrapper result = chatClient.prompt()
                .advisors(a -> a.param("agent", "WebScout").param("tier", "flash")
                    .param("skipPiiMask", true))
                .system(systemPrompt)
                .user(userPromptTemplate
                    .replace("{{query}}", query)
                    .replace("{{searchPlanQueries}}", queriesContext))
                .tools(searchTools)  // 注入 @Tool 工具
                .call()
                .entity(EvidenceListWrapper.class);  // Round 1 已替换

            return result.evidences().stream()
                .map(e -> new Evidence(e.sourceId(), SourceType.WEB, e.url(),
                    e.title(), e.content(), e.score(), e.relevanceRank(),
                    e.domain(), LocalDateTime.now()))
                .toList();

        } catch (Exception e) {
            log.error("[WebScout] 搜索取证异常", e);
            return List.of();
        }
    }
}
```

#### 重构：`LocalScoutAgent`

```java
/**
 * 重构要点：同 WebScoutAgent，搜索操作改为 LLM 自主调用 localSearch 工具。
 */
@Service
public class LocalScoutAgent {

    private final ChatClient chatClient;
    private final SearchTools searchTools;

    public List<Evidence> search(String query, List<String> searchPlanQueries, String tenantId) {
        if (searchPlanQueries == null || searchPlanQueries.isEmpty()) {
            return List.of();
        }

        String queriesContext = String.join("\n", searchPlanQueries.stream()
            .map(q -> "- " + q).toList());

        try {
            EvidenceListWrapper result = chatClient.prompt()
                .advisors(a -> a.param("agent", "LocalScout").param("tier", "flash")
                    .param("skipPiiMask", true))
                .system(systemPrompt)
                .user(userPromptTemplate
                    .replace("{{query}}", query)
                    .replace("{{searchPlanQueries}}", queriesContext)
                    .replace("{{tenantId}}", tenantId))
                .tools(searchTools)
                .call()
                .entity(EvidenceListWrapper.class);

            return result.evidences().stream()
                .map(e -> new Evidence(e.sourceId(), SourceType.LOCAL, e.url(),
                    e.title(), e.content(), 0.92, e.relevanceRank(),
                    e.domain(), LocalDateTime.now()))
                .toList();

        } catch (Exception e) {
            log.error("[LocalScout] 检索异常", e);
            return List.of();
        }
    }
}
```

#### AgentBundle 中注册 defaultTools

```java
// AgentBundle.java — webScoutClient 和 localScoutClient Bean 各加一行
@Bean("webScoutClient")
public ChatClient webScoutClient(ChatModel chatModel, SearchTools searchTools) {
    return createBuilder(chatModel)
        .defaultTools(searchTools)  // ← 新增：注册 @Tool 工具
        .defaultOptions(DeepSeekChatOptions.builder()
            .model(DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH)
            .temperature(0.4)
            .maxTokens(4096))
        .build();
}

@Bean("localScoutClient")
public ChatClient localScoutClient(ChatModel chatModel, SearchTools searchTools) {
    return createBuilder(chatModel)
        .defaultTools(searchTools)  // ← 新增：注册 @Tool 工具
        .defaultOptions(DeepSeekChatOptions.builder()
            .model(DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH)
            .temperature(0.4)
            .maxTokens(4096))
        .build();
}
```

### Round 3 验证标准

```bash
# 1. 功能验证：发起完整深度研究，确认报告质量不下降
#    重点关注：证据数量、引用质量、研究总耗时

# 2. 对比测试：同一个 query 在旧版和新版下的报告质量
#    可选：用 EvalAgent 对两次结果评分对比

# 3. 工具调用日志检查
#    确认 webSearch / localSearch 被正确调用，参数合理

# 4. 耗时检查
#    确认 ToolCallingAdvisor 的工具调用循环未导致超时
```

---

## 六、Round 4 — 增强特性收尾

> **目标**：锦上添花，把企业级能力补全。

### 纳入项（5 项合并）

| # | 优化项 | 改动类型 | 新增文件数 |
|---|--------|----------|-----------|
| 8 | 模型路由 | 新增 ChatModel 实现类 | 1 |
| 10 | 高级 RAG | 新增查询改写 Service | 1 |
| 11 | Prompt 动态管理 | 新增 Service + DB 实体 | 3 |
| 12 | Human-in-the-loop | 新增审批 Service | 1 |
| 14 | Eval 测试集 | 新增测试类 | 1 |

### 为什么可以合并

- **全部是独立新功能**：互相之间零依赖
- **可选启用**：每一项都是 opt-in，默认不开启也不影响现有功能
- **改动隔离**：如果某项需要回滚，不影响其他 4 项

### 具体改动清单

#### #8 模型路由

**新增文件**：`src/main/java/com/example/deepresearch/agent/bundle/SmartRoutingChatModel.java`

实现 `ChatModel` 接口，根据 Prompt 复杂度自动选择 Pro/Flash 模型，内置 CircuitBreaker 熔断降级。在 `EnterpriseChatClientConfig` 中替换默认 ChatModel。

```java
@Component
public class SmartRoutingChatModel implements ChatModel {
    // 按优先级注册模型池
    // analyzeComplexity() → 选择层级 → CircuitBreaker 保护 → 返回结果
    // 失败自动降级到下一个候选模型
}
```

#### #10 高级 RAG

**新增文件**：`src/main/java/com/example/deepresearch/rag/AdvancedRagService.java`

在 `QuestionAnswerAdvisor` 基础上增加查询改写步骤：

```java
@Service
public class AdvancedRagService {
    // rewriteQuery() — 用轻量 Flash 模型改写用户模糊查询
    // 改写后的查询传给 QuestionAnswerAdvisor 提升检索精度
}
```

#### #11 Prompt 动态管理

**新增文件**：
- `src/main/java/com/example/deepresearch/memory/entity/PromptTemplateEntity.java`（JPA 实体）
- `src/main/java/com/example/deepresearch/memory/repository/PromptTemplateRepository.java`
- `src/main/java/com/example/deepresearch/service/DynamicPromptService.java`

**DB Schema**：
```sql
CREATE TABLE prompt_templates (
    id VARCHAR(64) PRIMARY KEY,
    version INT NOT NULL DEFAULT 1,
    content TEXT NOT NULL,
    status VARCHAR(16) DEFAULT 'active',
    ab_group VARCHAR(8),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
```

Fallback 机制：数据库不可用时自动回退到 classpath 下的 `.st` 文件。

#### #12 Human-in-the-loop

**新增文件**：`src/main/java/com/example/deepresearch/security/ApprovalService.java`

```java
@Service
public class ApprovalService {
    // submitForApproval() — 提交写操作审批
    // 当前项目无需写操作，预留接口供未来扩展
}
```

#### #14 Eval 测试集

**新增文件**：`src/test/java/com/example/deepresearch/eval/AiEvaluationRegressionTest.java`

```java
@SpringBootTest
class AiEvaluationRegressionTest {
    @ParameterizedTest
    @CsvSource({
        "2026年新能源汽车趋势, 新能源",
        "AI芯片市场格局, GPU",
        "全球经济展望2026, GDP"
    })
    void testResearchReportQuality(String query, String expectedKeyword) {
        // 发起研究 → 断言关键词出现 → EvalAgent 评分 ≥ 3.0
    }
}
```

### Round 4 验证标准

```bash
# 各项独立验证：

# #8 模型路由：分别发送简单和复杂 query，检查日志中模型选择是否正确
# #10 高级RAG：同一个查询在基础RAG和高级RAG下的检索结果对比
# #11 Prompt管理：通过数据库修改 prompt → 不重启即生效
# #12 HITL：调用写操作工具 → 返回 "需要审批" 提示
# #14 Eval测试：mvn test -Dtest=AiEvaluationRegressionTest
```

---

## 七、风险矩阵

| Round | 风险等级 | 最坏情况 | 回滚方式 | 回滚时间 |
|-------|---------|----------|----------|---------|
| 1 | 🟢 极低 | `.entity()` 解析失败率略高 | `git revert` 单 commit | 1 分钟 |
| 2 | 🟢 极低 | 适配器性能不达标 | 删 5 个新文件 | 1 分钟 |
| 3 | 🟡 中 | Tool calling 循环耗时过长 | `git revert` + 恢复旧 Agent 实现 | 5 分钟 |
| 4 | 🟢 低 | 某项新功能不可用 | 关闭对应配置项 | 即时 |

---

## 八、执行检查清单

```
Round 1 — 基础清理 + 机械替换
  □ #4  application.yml 移除 .options 层级
  □ #15 删除 EvidenceJudgeAgent + ReflectAgent + 废弃 prompt
  □ #15 AgentType 移除 EVIDENCE_JUDGE 和 REFLECT
  □ #7  application.yml 启用 spring.ai.chat.observations
  □ #2  IntentRouterAgent: safeParse() → .entity(RouteResult.class)
  □ #2  PlannerAgent: safeParse() → .entity(PlanResult.class)
  □ #2  WebScoutAgent: safeParse() → .entity(EvidenceListWrapper.class)
  □ #2  LocalScoutAgent: safeParse() → .entity(EvidenceListWrapper.class)
  □ #2  AnalystAgent: safeParse() → .entity(AnalysisResult.class)
  □ #2  WriterAgent: safeParse() → .entity(WriteResult.class)
  □ #2  EvalAgent: safeParse() → .entity(EvalResult.class)
  □ #13 pom.xml 加 spring-ai-tika-document-reader 依赖
  □ #13 DocumentIngestionService 换 TikaDocumentReader + TokenTextSplitter
  □ 运行验证测试，确认工作流正常

Round 2 — 适配器 + Advisor 链基础层
  □ #3  新增 MilvusVectorStoreAdapter
  □ #5  新增 RedisChatMemoryAdapter
  □ #5  ShortTermMemoryService 增加 getRecentMessages() + clear()
  □ #6  新增 EnterpriseChatClientConfig
  □ #9  新增 TokenBudgetAdvisor
  □ #16 新增 OutputGuardrailAdvisor
  □ #9  application.yml 加 Token 预算配置项
  □ 运行验证测试，确认新旧配置并存

Round 3 — @Tool 工具调用
  □ #1  新增 SearchTools（@Tool 注解）
  □ #1  重构 WebScoutAgent — LLM 自主调用工具
  □ #1  重构 LocalScoutAgent — LLM 自主调用工具
  □ #1  AgentBundle 注册 defaultTools
  □ 对比测试：新旧版本报告质量 + 耗时

Round 4 — 增强特性
  □ #8  新增 SmartRoutingChatModel
  □ #10 新增 AdvancedRagService（查询改写）
  □ #11 新增 PromptTemplateEntity + DynamicPromptService
  □ #11 创建 prompt_templates 表
  □ #12 新增 ApprovalService（预留）
  □ #14 新增 AiEvaluationRegressionTest
  □ 各项独立验证
```

---

## 九、总结

| 指标 | 初始计划 | 最终实际 |
|------|---------|---------|
| 总轮次 | **4 轮** | **8 轮**（4 初始 + P0/P1/P2/P3 后续修复） |
| 本轮改动文件数 | 5-12 个 | 3-15 个 |
| 最终合规率 | — | **100%**（38/38 项） |
| 改动文件合计 | — | 14 新增 + 30 修改 + 7 删除 |
| 编译状态 | — | BUILD SUCCESS |

**核心原则**：
1. **新旧双轨并行**（Round 2）：新增配置不删除旧配置，零风险共存
2. **接口签名不变**（Round 1, 3）：Agent 的 public 方法签名不动，LangGraph4j 工作流无感知
3. **每轮可独立回滚**：Git commit 粒度为 Round 级，出问题一键 revert
4. **Round 1 建立信心**：最先做最安全的事，快速见效

---

## 十、实施完成总结与后续集成指南

> **首轮实施日期**：2026-07-12
> **后续修复日期**：2026-07-13
> **最终状态**：BUILD SUCCESS，全部 4 轮 + 4 项后续修复编译通过

### 10.1 改动总览

| Round | 新增文件 | 修改文件 | 删除文件 | 主题 |
|-------|---------|---------|---------|------|
| **1** | 0 | 10 | 6 | 配置扁平化 + 代码清理 + `.entity()` + 文档处理 |
| **2** | 7 | 2 | 0 | VectorStore 适配 + ChatMemory 适配 + Advisor 链 + Token 预算 + 输出护栏 |
| **3** | 1 | 3 | 0 | `@Tool` 工具调用重构（Scout Agent 内部） |
| **4** | 6 + 1 SQL | 0 | 1 | 模型路由(已删) + 高级 RAG + Prompt 管理 + HITL + Eval 测试 |
| **P0** | 0 | 3 | 0 | Prompt 模板适配 @Tool + SQL 同步 |
| **P1** | 0 | 8 | 0 | Agent 迁移 DynamicPromptService（删 ~120 行） |
| **P2** | 0 | 1 | 0 | AgentBundle 升级全企业级 Advisor 链 |
| **P3** | 0 | 3 | 0 | Grafana/Prometheus 补充 Spring AI 内置指标 |
| **合计** | **14 + 1 SQL** | **30** | **7** | — |

### 10.2 各轮完成项明细

#### Round 1 — 基础清理 + 机械替换 ✅

| # | 优化项 | 涉及文件 | 备注 |
|---|--------|----------|------|
| 4 | 配置扁平化 | `application.yml` | 移除 `deepseek.chat.options` 和 `openai.embedding.options` 冗余层级 |
| 15 | 代码清理 | `EvidenceJudgeAgent`, `ReflectAgent`, `JudgeResult`, `ReflectResult`, 2 个 `.st` | 净删 560 行 |
| 15 | 枚举清理 | `AgentType.java` | 移除 `EVIDENCE_JUDGE`、`REFLECT` |
| 7 | 可观测性 | `application.yml` | 启用 `spring.ai.chat.observations` + `management.observations.enable.spring.ai` |
| 2 | `.entity()` | `IntentRouterAgent`, `PlannerAgent`, `WebScoutAgent`, `LocalScoutAgent`, `AnalystAgent`, `WriterAgent`, `EvalAgent` | `safeParse()` → `.call().entity()`；`ModelFallbackService` 新增泛型 `<T>` 降级重载；`WriterAgent` 移除 `sanitizeRawOutput` 死代码 |
| 13 | 文档处理 | `pom.xml` + `DocumentIngestionService` | + `spring-ai-tika-document-reader`；`TikaDocumentReader` + `TokenTextSplitter.builder()` 替代自定义 `chunkText()` |

#### Round 2 — 适配器 + Advisor 链基础层 ✅

| # | 优化项 | 涉及文件（全部新增） | 备注 |
|---|--------|----------|------|
| 3 | VectorStore 适配器 | `MilvusVectorStoreAdapter` | `implements VectorStore`，包装 `VectorStoreService` |
| 5 | ChatMemory 适配器 | `RedisChatMemoryAdapter` | `implements ChatMemory`，适配 `ShortTermMemoryService` 响应式 API |
| 6 | Advisor 统一链 | `EnterpriseChatClientConfig` | 8 个 Advisor 链式装配，与 `AgentBundle` 双轨并行 |
| 9 | Token 预算 | `TokenBudgetAdvisor` | `CallAdvisor` 接口，Redis INCR+EXPIRE 分布式限流 |
| 16 | 输出护栏 | `OutputGuardrailAdvisor` | `CallAdvisor` 接口，敏感词拦截 + 安全兜底文案 |
| — | 审计日志 | `AuditLogAdvisor` | `CallAdvisor` 接口，`AUDIT` Logger 记录 agent/user/status/latency |
| — | 安全日志扩展 | `SecurityLogService.java`（修改） | + `logOutputBlocked()` 方法 |
| — | 依赖 | `pom.xml`（修改） | + `spring-ai-vector-store` + `spring-ai-vector-store-advisor` |

#### Round 3 — @Tool 工具调用重构 ✅

| # | 优化项 | 涉及文件 | 备注 |
|---|--------|----------|------|
| 1 | SearchTools | `SearchTools.java`（新增） | `@Tool` 注解封装 `webSearch` + `localSearch` |
| 1 | WebScoutAgent | `WebScoutAgent.java`（重写） | 230→130 行，移除手动并行搜索，LLM 自主调用 `@Tool` |
| 1 | LocalScoutAgent | `LocalScoutAgent.java`（重写） | 205→122 行，同上 |
| 1 | AgentBundle 注册 | `AgentBundle.java`（修改） | `webScoutClient` + `localScoutClient` 各加 `.defaultTools(searchTools)` |

#### Round 4 — 增强特性收尾 ✅

| # | 类 | 状态 | 集成方式 |
|---|----|------|----------|
| 8 | `SmartRoutingChatModel` | ❌ 已删除 | 与 DeepSeek 单 ChatModel 架构冲突，不适合当前项目 |
| 10 | `AdvancedRagService` | ✅ 就绪 | 见下文 10.3.1 |
| 11 | `DynamicPromptService` + `PromptTemplateEntity` + `PromptTemplateRepository` | ✅ 就绪 | 见下文 10.3.2 |
| — | `init_prompt_templates.sql` | ✅ 已生成 | 8 个 `.st` 文件全部入库，`ON CONFLICT DO UPDATE` 可重复执行 |
| 12 | `ApprovalService` | ⏸️ 预留 | 当前项目无需写操作，预留 `submitForApproval()` 接口供未来扩展 |
| 14 | `AiEvaluationRegressionTest` | ✅ 就绪 | `mvn test -Dtest=AiEvaluationRegressionTest` 自动拾取 |

### 10.3 就绪类后续集成指南

#### 10.3.1 AdvancedRagService — 高级 RAG（查询改写）⏸️ 可选

**位置**：`com.example.deepresearch.rag.AdvancedRagService`

**状态**：代码就绪，未接入 Agent 主流程。当前 `LocalScoutAgent` 使用 LLM 通过 `@Tool` 自主调用 `localSearch` 工具，查询改写功能可选启用（LLM 自主决定检索关键词已具备等效能力）。

**使用方式**（如需启用）：

```java
@Autowired
private AdvancedRagService advancedRagService;

// 单次调用即完成：查询改写 → 向量检索 → LLM 生成
String answer = advancedRagService.askWithRewriting("2026年新能源车咋样");
```

**建议接入点**：`LocalScoutAgent.search()` 方法内部，将用户原始查询先经过 `rewriteQuery()` 改写后再传给 LLM。

#### 10.3.2 DynamicPromptService — Prompt 动态管理 ✅ 已激活

**位置**：`com.example.deepresearch.service.DynamicPromptService`

**数据库**：`prompt_templates` 表（JPA `ddl-auto: update` 自动建表）

**初始化**：
```bash
psql -h localhost -U deep_research -d deep_research \
  -f docs/optimization/sql/init_prompt_templates.sql
```

**加载优先级**：内存缓存（1分钟TTL）→ PostgreSQL → classpath `prompts/*.st`（兜底）

**迁移状态**（2026-07-13）：全部 7 个 Agent + ResearchWorkflow.directAnswer 已完成迁移 ✅

| Agent | templateId | 状态 |
|-------|-----------|------|
| IntentRouterAgent | `intent-router` | ✅ 已迁移 |
| PlannerAgent | `planner` | ✅ 已迁移 |
| WebScoutAgent | `web-scout` | ✅ 已迁移 |
| LocalScoutAgent | `local-scout` | ✅ 已迁移 |
| AnalystAgent | `analyst` | ✅ 已迁移 |
| WriterAgent | `writer` | ✅ 已迁移 |
| EvalAgent | `eval` | ✅ 已迁移 |
| ResearchWorkflow.directAnswer | `direct-answer` | ✅ 已迁移 |

每个 Agent 的改动模式：删除 `ResourceLoader` 注入 → 注入 `DynamicPromptService` → `loadPrompt(resourceLoader)` 替换为 `dynamicPromptService.getTemplateContent("id")` → 删除 `loadPrompt()` 私有方法（合计删除 ~120 行样板代码）。

**热更新**：运营人员执行 `UPDATE prompt_templates SET content='新prompt...' WHERE id='writer'`，1 分钟内自动生效（缓存 TTL 过期），无需重启应用。

#### 10.3.3 EnterpriseChatClientConfig — 统一 Advisor 链 ✅ 已激活

**位置**：`com.example.deepresearch.agent.bundle.EnterpriseChatClientConfig`

**状态**：Bean 已注册（`enterpriseChatClient`），与 `AgentBundle` 双轨并行。**2026-07-13 更新**：`AgentBundle.createBuilder()` 已升级为全企业级 Advisor 链。

**`AgentBundle` Advisor 链**（2026-07-13 升级后）：
```
请求流入
  ├─ TokenBudgetAdvisor       [200] Token预算检查 + Redis分布式限流
  ├─ PiiMaskingAdvisor        [300] 输入PII脱敏
  ├─ OutputGuardrailAdvisor   [300] 输出安全护栏
  ├─ TokenTrackingAdvisor     [900] Token用量追踪
  └─ AuditLogAdvisor          [100] 审计日志
  ▼
LLM 调用
```

**`EnterpriseChatClientConfig` Advisor 链**（完整版，含记忆+RAG）：
```
请求流入
  ├─ TokenBudgetAdvisor       [200] Token预算检查
  ├─ PiiMaskingAdvisor        [300] 输入PII脱敏
  ├─ OutputGuardrailAdvisor   [300] 输出安全护栏
  ├─ MessageChatMemoryAdvisor [400] 对话记忆注入
  ├─ QuestionAnswerAdvisor    [500] RAG检索增强
  ├─ TokenTrackingAdvisor     [900] Token追踪
  └─ AuditLogAdvisor          [100] 审计日志
```

**双轨说明**：`AgentBundle` 的链适用于所有 Agent（不含记忆/RAG，因 IntentRouter/Eval 等 Agent 不需要），`EnterpriseChatClientConfig` 适用于需要对话记忆和 RAG 检索的场景。

#### 10.3.4 TokenBudgetAdvisor + OutputGuardrailAdvisor + AuditLogAdvisor ✅ 已激活

**位置**：`com.example.deepresearch.security`

**状态**：Bean 已注册，已于 2026-07-13 通过 P2 修复集成到 `AgentBundle.createBuilder()`，所有 Agent 的 ChatClient 自动启用。同时在 `EnterpriseChatClientConfig` 的 Advisor 链中装配。

**覆盖范围**：全部 9 个 ChatClient Bean（7 个 Agent + 2 个 Fallback Client）。

#### 10.3.5 MilvusVectorStoreAdapter + RedisChatMemoryAdapter ✅ 就绪

**位置**：`com.example.deepresearch.rag` / `com.example.deepresearch.memory`

**状态**：Bean 已注册，实现 Spring AI 标准接口。已在 `EnterpriseChatClientConfig` 中通过 `QuestionAnswerAdvisor` 和 `MessageChatMemoryAdvisor` 组装。`MilvusVectorStoreAdapter` 实现了 `VectorStore` 标准接口，可被任何依赖 `VectorStore` 的 Spring AI 组件直接使用（如 `QuestionAnswerAdvisor`、自定义 RAG 管道等）。

### 10.4 已解决项（原计划外 → 已修复）

> 修复日期：2026-07-13，分 P0–P3 四轮修复

| 项 | 状态 | 修复日期 | 详情 |
|----|------|----------|------|
| **P0** web-scout.st / local-scout.st 适配 @Tool 模式 | ✅ 已修复 | 2026-07-13 | 重写两个 `.st` 模板：从"被动提取搜索结果"改为"主动调用 webSearch/localSearch 工具"；变量 `{{searchQuery}}`/`{{results}}`/`{{documents}}`/`{{webIndex}}`/`{{localIndex}}` → `{{query}}` + `{{searchPlanQueries}}`；新增工具使用说明和工作流程章节 |
| **P0** `init_prompt_templates.sql` 同步修复 | ✅ 已修复 | 2026-07-13 | SQL 中 web-scout 和 local-scout 模板内容同步更新为 @Tool 模式版本 |
| **P1** Agent Prompt 模板迁移到 DB | ✅ 已完成 | 2026-07-13 | 全部 7 个 Agent + ResearchWorkflow.directAnswer 从 `loadPrompt(ResourceLoader)` 迁移到 `DynamicPromptService.getTemplateContent()`，删除 ~120 行样板代码 |
| **P2** 全 Agent Advisor 链统一 | ✅ 已完成 | 2026-07-13 | `AgentBundle.createBuilder()` 升级为 5 个 Advisor（TokenBudget + PiiMask + OutputGuard + TokenTrack + AuditLog），所有 Agent 的 ChatClient 自动继承 |
| **P3** Grafana/Prometheus 补充 Spring AI 内置指标 | ✅ 已完成 | 2026-07-13 | llm-overview 和 workflow-performance 仪表盘各新增 2 个 Spring AI 内置指标面板；告警规则从 9 条增至 11 条（新增 ToolCallFailureRateHigh + ChatClientErrorRateHigh） |

### 10.5 编译验证

```
$ mvn compile
[INFO] BUILD SUCCESS
```

所有 4 轮 + 4 项后续修复编译通过，可随时启动测试。

---

### 10.6 后续修复详细记录（2026-07-13）

#### P0 — Prompt 模板适配 @Tool 模式

**问题**：`web-scout.st` 和 `local-scout.st` 仍使用旧变量（`{{searchQuery}}`、`{{results}}`、`{{documents}}`、`{{webIndex}}`、`{{localIndex}}`），与 Java 代码注入的 `{{searchPlanQueries}}` 不匹配，导致发送给 LLM 的 prompt 出现裸占位符文本。

**修复文件**（2 个）：
- `src/main/resources/prompts/web-scout.st` — 重写为 @Tool 模式：角色从"被动提取"改为"主动调用 webSearch 工具"；新增工具使用、工作流程章节；示例展示 Agent 调用工具的正确流程
- `src/main/resources/prompts/local-scout.st` — 同上，改为"主动调用 localSearch 工具"

**同步修复**：`docs/optimization/sql/init_prompt_templates.sql` 中 web-scout 和 local-scout 模板内容同步更新。

**验证**：`{{query}}` + `{{searchPlanQueries}}` 变量与 Java 代码 `.replace()` 完全对齐。

#### P1 — Agent 迁移到 DynamicPromptService

**问题**：全部 7 个 Agent + ResearchWorkflow.directAnswer 使用 `loadPrompt(ResourceLoader)` 从 classpath 加载，Prompt 修改需重启。

**修复文件**（8 个）：
- `agent/intent/IntentRouterAgent.java` — 模板 ID: `intent-router`
- `agent/planner/PlannerAgent.java` — 模板 ID: `planner`
- `agent/scout/WebScoutAgent.java` — 模板 ID: `web-scout`
- `agent/scout/LocalScoutAgent.java` — 模板 ID: `local-scout`
- `agent/analyst/AnalystAgent.java` — 模板 ID: `analyst`
- `agent/writer/WriterAgent.java` — 模板 ID: `writer`
- `agent/eval/EvalAgent.java` — 模板 ID: `eval`
- `workflow/ResearchWorkflow.java` — 模板 ID: `direct-answer`

**改动模式**（每个 Agent）：删除 `Resource`/`ResourceLoader`/`StandardCharsets` 导入 → 注入 `DynamicPromptService` → `loadPrompt(resourceLoader)` → `dynamicPromptService.getTemplateContent("id")` → 删除 `loadPrompt()` 私有方法。

**效果**：Prompt 热更新（1分钟缓存TTL）、净删 ~120 行样板代码。

#### P2 — AgentBundle 升级为全企业级 Advisor 链

**问题**：`AgentBundle.createBuilder()` 仅注册 `PiiMaskingAdvisor` + `TokenTrackingAdvisor`，TokenBudget/OutputGuardrail/AuditLog 未生效。

**修复文件**（1 个）：
- `agent/bundle/AgentBundle.java` — 注入 5 个 Advisor，`createBuilder()` 升级为全链

**效果**：所有 9 个 ChatClient Bean 自动继承 Token 预算管控、输出安全护栏、审计日志，无需逐个修改。

#### P3 — Grafana/Prometheus 补充 Spring AI 内置指标

**问题**：4 个仪表盘 44 个面板全部使用自定义 `deepresearch_*` 指标，Spring AI 2.0 内置的 `spring.ai.*` 指标未被利用。

**修复文件**（3 个）：
- `observability/grafana/dashboards/llm-overview.json` — +2 面板：ChatClient 调用速率（按 status）、Token 消耗速率（按 token_type）
- `observability/grafana/dashboards/workflow-performance.json` — +2 面板：工具调用耗时 P50/P95（按 tool_name）、向量检索耗时 P50/P95 + 调用速率
- `observability/prometheus/rules/deepresearch-alerts.yml` — +2 告警：`ToolCallFailureRateHigh`（@Tool 失败率 >30%）、`ChatClientErrorRateHigh`（ChatClient 非成功状态 >10%），总计 11 条

### 10.7 最终合规状态

| Round | 原始项 | 已修复 | 状态 |
|:------|:------|:------|:-----|
| Round 1 — 基础清理 | 5/5 | 5/5 | ✅ 100% |
| Round 2 — 适配器 + Advisor 链 | 9/9 | 9/9 | ✅ 100% |
| Round 3 — @Tool 代码 | 4/4 | 4/4 | ✅ 100% |
| Round 3 — @Tool Prompt 模板 | 0/2 | 2/2 | ✅ 已修复 P0 |
| Round 4 — 增强特性代码 | 6/6 | 6/6 | ✅ 100% |
| Round 4 — DynamicPromptService 集成 | 0/8 | 8/8 | ✅ 已修复 P1 |
| Round 4 — Advisor 链激活 | 0/1 | 1/1 | ✅ 已修复 P2 |
| 可观测性 — Spring AI 内置指标 | 0/3 | 3/3 | ✅ 已修复 P3 |
| **总计** | **28/28** | **38/38** | ✅ **100%** |


