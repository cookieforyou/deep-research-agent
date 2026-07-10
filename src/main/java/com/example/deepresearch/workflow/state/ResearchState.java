package com.example.deepresearch.workflow.state;

import com.example.deepresearch.common.model.*;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 研究状态 — LangGraph4j 工作流中的全局状态对象.
 * <p>
 * 贯穿 7 步研究流程的不可变状态容器。每个 LangGraph4j Node 接收当前状态、
 * 返回增量更新 Map，框架根据 Channel 策略自动合并。
 * </p>
 *
 * <h3>Channel 策略说明</h3>
 * <ul>
 *   <li><b>base</b>: 最后一个写入者胜出（用于标量值如 query、intent）</li>
 *   <li><b>appender</b>: 追加到列表末尾（用于证据、发现等累积数据），
 *       每次 Reflect 循环中新增的证据不会覆盖已有证据</li>
 * </ul>
 *
 * <h3>状态流转示例</h3>
 * <pre>
 * START → intent("research") → plan(subQuestions=[...], searchPlan=[...])
 *   → dual_search(webEvidence=[...], localEvidence=[...]) → judge(evidencePool=[...])
 *   → analyze(findings=[...]) → [conditional: reflect|write]
 *   → reflect(newSearchQueries) → dual_search(追加 evidence) → ...
 *   → write(finalReport="...")
 * </pre>
 *
 * @see AgentState
 */
public class ResearchState extends AgentState {

    // =========================== Channel Schema 定义 ===========================

    /**
     * 状态字段的 Channel 定义.
     * <p>
     * 每个字段都声明了合并策略：
     * <ul>
     *   <li>{@link Channels#base} — 标量值，后写覆盖前写</li>
     *   <li>{@link Channels#appender} — 列表值，追加合并（用于跨迭代累积）</li>
     * </ul>
     * </p>
     */
    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
        // --- 输入参数 (base: 后写覆盖) ---
        Map.entry("query",           Channels.base(() -> "")),
        Map.entry("userId",          Channels.base(() -> "")),
        Map.entry("tenantId",        Channels.base(() -> "default")),
        Map.entry("sessionId",       Channels.base(() -> "")),
        Map.entry("memoryContext",   Channels.base(() -> "")),

        // --- 意图路由 (base) ---
        Map.entry("intent",          Channels.base(() -> "")),       // "direct" | "research"

        // --- 规划产出 (appender: 计划是列表) ---
        Map.entry("subQuestions",    Channels.appender(ArrayList::new)),
        Map.entry("reportOutline",   Channels.base(() -> "")),
        Map.entry("searchPlan",      Channels.appender(ArrayList::new)),

        // --- 双源检索产出 (appender: 每轮检索追加新证据) ---
        Map.entry("webEvidence",     Channels.appender(ArrayList::new)),
        Map.entry("localEvidence",   Channels.appender(ArrayList::new)),
        Map.entry("evidencePool",    Channels.appender(ArrayList::new)),

        // --- 去重过滤产出 (appender) ---
        Map.entry("sourceIndex",     Channels.appender(ArrayList::new)),

        // --- 分析产出 (appender) ---
        Map.entry("findings",        Channels.appender(ArrayList::new)),
        Map.entry("needsMoreResearch", Channels.base(() -> false)),
        Map.entry("missingGaps",     Channels.appender(ArrayList::new)),

        // --- 反思产出 (base: 每次反思生成新查询替换旧的) ---
        Map.entry("newSearchQueries", Channels.base(() -> new ArrayList<String>())),

        // --- 迭代控制 (base) ---
        Map.entry("iteration",        Channels.base(() -> 0)),
        Map.entry("maxIterations",    Channels.base(() -> 1)),

        // --- 输出产物 (base) ---
        Map.entry("directAnswer",     Channels.base(() -> "")),
        Map.entry("finalReport",      Channels.base(() -> "")),
        Map.entry("error",            Channels.base(() -> "")),

        // --- 消息历史 (appender) ---
        Map.entry("messages",         Channels.appender(ArrayList::new))
    );

    // =========================== 构造器 ===========================

    /**
     * 从初始数据构造 ResearchState.
     *
     * @param initData 初始键值对（至少包含 query、userId 等必填字段）
     */
    public ResearchState(Map<String, Object> initData) {
        super(initData);
    }

    // =========================== 类型安全访问器 ===========================

    /** 研究查询文本 */
    public String query()                { return this.<String>value("query").orElse(""); }
    /** 用户 ID */
    public String userId()               { return this.<String>value("userId").orElse(""); }
    /** 租户 ID（多租户隔离） */
    public String tenantId()             { return this.<String>value("tenantId").orElse("default"); }
    /** 会话 ID */
    public String sessionId()            { return this.<String>value("sessionId").orElse(""); }
    /** 记忆上下文（用户画像+历史对话，供 Planner 注入） */
    public String memoryContext()        { return this.<String>value("memoryContext").orElse(""); }
    /** 意图分类: "direct" | "research" */
    public String intent()               { return this.<String>value("intent").orElse(""); }
    /** 报告大纲（Markdown） */
    public String reportOutline()        { return this.<String>value("reportOutline").orElse(""); }
    /** 是否需要补充研究 */
    public boolean needsMoreResearch()   { return this.<Boolean>value("needsMoreResearch").orElse(false); }
    /** 当前反思迭代次数 */
    public int iteration()               { return this.<Integer>value("iteration").orElse(0); }
    /** 最大允许反思迭代次数 */
    public int maxIterations()           { return this.<Integer>value("maxIterations").orElse(1); }
    /** 直接回答（意图为 direct 时） */
    public String directAnswer()         { return this.<String>value("directAnswer").orElse(""); }
    /** 最终研报 Markdown */
    public String finalReport()          { return this.<String>value("finalReport").orElse(""); }
    /** 错误信息 */
    public String error()                { return this.<String>value("error").orElse(""); }

    // --- 列表类型访问器 ---

    /** 子问题列表 */
    @SuppressWarnings("unchecked")
    public List<String> subQuestions()   { return (List<String>) value("subQuestions").orElse(List.of()); }
    /** 搜索计划列表 */
    @SuppressWarnings("unchecked")
    public List<SearchPlan> searchPlan() { return (List<SearchPlan>) value("searchPlan").orElse(List.of()); }
    /** 网络检索证据 */
    @SuppressWarnings("unchecked")
    public List<Evidence> webEvidence()  { return (List<Evidence>) value("webEvidence").orElse(List.of()); }
    /** 本地知识库检索证据 */
    @SuppressWarnings("unchecked")
    public List<Evidence> localEvidence(){ return (List<Evidence>) value("localEvidence").orElse(List.of()); }
    /** 合并后的证据池（web + local 经裁判去重评分后） */
    @SuppressWarnings("unchecked")
    public List<Evidence> evidencePool() { return (List<Evidence>) value("evidencePool").orElse(List.of()); }
    /** 来源索引（sourceId 列表） */
    @SuppressWarnings("unchecked")
    public List<String> sourceIndex()    { return (List<String>) value("sourceIndex").orElse(List.of()); }
    /** 研究结论列表 */
    @SuppressWarnings("unchecked")
    public List<Finding> findings()      { return (List<Finding>) value("findings").orElse(List.of()); }
    /** 信息缺口描述 */
    @SuppressWarnings("unchecked")
    public List<String> missingGaps()    { return (List<String>) value("missingGaps").orElse(List.of()); }
    /** 新生成的搜索查询词 */
    @SuppressWarnings("unchecked")
    public List<String> newSearchQueries(){ return (List<String>) value("newSearchQueries").orElse(List.of()); }
    /** 对话消息历史 */
    @SuppressWarnings("unchecked")
    public List<Message> messages()      { return (List<Message>) value("messages").orElse(List.of()); }

    // =========================== 便捷判断方法 ===========================

    /**
     * 判断当前意图是否为深度研究.
     */
    public boolean isDeepResearch() {
        return "research".equals(intent());
    }

    /**
     * 判断是否已达到最大迭代次数.
     */
    public boolean isMaxIterationsReached() {
        return iteration() >= maxIterations();
    }

    /**
     * 判断是否有错误发生.
     */
    public boolean hasError() {
        return error() != null && !error().isEmpty();
    }

    /**
     * 获取当前迭代的显示标签（如 "Round 1/3"）.
     */
    public String iterationLabel() {
        return "Round " + (iteration() + 1) + "/" + maxIterations();
    }
}
