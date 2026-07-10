package com.example.deepresearch.agent.intent;

import com.example.deepresearch.common.util.JsonParseUtils;
import com.example.deepresearch.common.util.PromptSplitUtils;
import com.example.deepresearch.common.util.PromptSplitUtils.PromptParts;
import com.example.deepresearch.security.PiiMaskingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * 意图路由 Agent — 判断用户查询应走 Direct Answer 还是 Deep Research 流程.
 * <p>
 * 这是 7 步研究流程的<strong>入口节点</strong>。使用 deepseek-v4-flash (T=0.0)
 * 确保分类结果绝对确定，不会因为随机性导致同一查询得出不同路由结论。
 * </p>
 *
 * <h3>路由决策依据</h3>
 * <ul>
 *   <li><b>Direct (直接回答)</b>: 简单事实查询、定义解释、单步计算、问候语等</li>
 *   <li><b>Research (深度研究)</b>: 需要多源信息、交叉验证、行业分析、趋势判断等</li>
 * </ul>
 */
@Service
public class IntentRouterAgent {

    private static final Logger log = LoggerFactory.getLogger(IntentRouterAgent.class);

    private final ChatClient chatClient;
    private final JsonParseUtils jsonUtils;
    private final String systemPrompt;
    private final String userPromptTemplate;
    private final PiiMaskingService piiMaskingService;

    public IntentRouterAgent(
        @Qualifier("intentRouterClient") ChatClient chatClient,
        JsonParseUtils jsonUtils,
        ResourceLoader resourceLoader,
        PiiMaskingService piiMaskingService
    ) {
        this.chatClient = chatClient;
        this.jsonUtils = jsonUtils;
        this.piiMaskingService = piiMaskingService;
        String fullTemplate = loadPrompt(resourceLoader);
        PromptParts parts = PromptSplitUtils.split(fullTemplate);
        this.systemPrompt = parts.system();
        this.userPromptTemplate = parts.user();
    }

    /**
     * 路由结果.
     */
    public record RouteResult(String intent, String reasoning) {
        public boolean isResearch() { return "research".equals(intent); }
        public boolean isDirect() { return "direct".equals(intent); }
    }

    /** 路由失败时的安全 Fallback: 默认走深度研究（宁可多做也不错做） */
    private static final RouteResult FALLBACK = new RouteResult(
        "research", "Fallback: JSON 解析失败，默认进入深度研究流程");

    /**
     * 判断查询意图.
     *
     * @param query 用户原始查询
     * @return 路由结果（direct 或 research）
     */
    public RouteResult route(String query) {
        log.debug("[IntentRouter] 路由判断: query='{}'", piiMaskingService.tokenizeToString(query));

        // 快速规则：极短查询且包含问候语 → direct
        if (isTrivialDirectQuery(query)) {
            log.debug("[IntentRouter] 快速规则命中 → direct");
            return new RouteResult("direct", "简单问候或确认型问题，快速路由");
        }

        try {
            // 构建 user message（仅包含查询数据）
            String userPrompt = userPromptTemplate.replace("{{query}}", query);

            // 使用 system/user 分离调用（架构级注入防护）
            String rawOutput = chatClient.prompt()
                .advisors(a -> a.param("agent", "IntentRouter").param("tier", "flash"))
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();

            log.debug("[IntentRouter] LLM 原始输出: {}", rawOutput);

            // 解析 JSON
            RouteResult result = jsonUtils.safeParse(rawOutput, RouteResult.class, FALLBACK, "IntentRouter");

            // 归一化 intent 值：LLM 可能输出 "deep_research" / "direct_answer" 等变体
            return normalizeIntent(result);

        } catch (Exception e) {
            log.error("[IntentRouter] 路由判断异常，返回 fallback", e);
            return FALLBACK;
        }
    }

    /**
     * 归一化 LLM 输出的 intent 值.
     * <p>
     * LLM 可能不严格遵循 prompt 约定的 "research"/"direct" 二值，
     * 输出诸如 "deep_research"、"direct_answer"、"research_mode" 等变体。
     * 此方法将各种变体映射回标准值，保证下游路由逻辑一致。
     * </p>
     */
    private RouteResult normalizeIntent(RouteResult result) {
        String intent = result.intent();
        if (intent == null) {
            return FALLBACK;
        }

        String normalized = intent.toLowerCase().trim();

        // 包含 "research" 的任何变体 → research
        if (normalized.contains("research")) {
            return new RouteResult("research", result.reasoning());
        }

        // 包含 "direct" 的任何变体 → direct
        if (normalized.contains("direct")) {
            return new RouteResult("direct", result.reasoning());
        }

        // 无法识别 → fallback（默认走 research，宁可多做也不错做）
        log.warn("[IntentRouter] 无法识别的 intent 值: '{}', 回退到 research", intent);
        return FALLBACK;
    }

    /**
     * 快速规则：无需 LLM 即可判定为 direct 的查询.
     * <p>
     * 减少不必要的 LLM 调用，节省成本和延迟。
     * </p>
     */
    private boolean isTrivialDirectQuery(String query) {
        if (query == null) return true;
        String q = query.trim().toLowerCase();
        return q.length() < 5  // 极短查询
            || q.matches("^(你好|hi|hello|谢谢|再见|bye|ok|好的|嗯|哦).*")  // 社交用语
            || q.matches("^(什么是|定义|解释一下|怎么读|翻译).{0,10}$");  // 简单定义类（短）
    }

    /**
     * 加载 Prompt 模板文件.
     */
    private String loadPrompt(ResourceLoader loader) {
        try {
            Resource resource = loader.getResource("classpath:prompts/intent-router.st");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[IntentRouter] 无法加载 prompt 模板文件", e);
            // 内置最小可用 Prompt（防止文件丢失导致系统不可用）
            return """
                你是一个查询意图分类器。判断用户查询属于哪种类型。

                查询: {{query}}

                请返回 JSON: {"intent": "direct" | "research", "reasoning": "判断理由"}
                - direct: 简单问答、定义、计算、问候
                - research: 需要多源信息、分析、比较、趋势判断
                """;
        }
    }
}
