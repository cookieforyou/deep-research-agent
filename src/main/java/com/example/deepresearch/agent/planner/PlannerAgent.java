package com.example.deepresearch.agent.planner;

import com.example.deepresearch.agent.bundle.ModelFallbackService;
import com.example.deepresearch.common.model.PlanResult;
import com.example.deepresearch.common.model.SearchPlan;
import com.example.deepresearch.common.util.JsonParseUtils;
import com.example.deepresearch.common.util.PromptSplitUtils;
import com.example.deepresearch.common.util.PromptSplitUtils.PromptParts;
import com.example.deepresearch.security.PiiMaskingService;
import com.example.deepresearch.service.DynamicPromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 规划 Agent — 将查询拆解为子问题、大纲和搜索计划.
 * <p>
 * 使用 deepseek-v4-pro (T=0.3)，平衡创意和确定性。
 * 当 Pro 模型不可用时自动降级到 Flash (T=0.3)。
 * 生成的搜索计划需要考虑资源预算，避免搜索次数过多。
 * </p>
 *
 * <h3>规划策略</h3>
 * <ul>
 *   <li>子问题 3-5 个，覆盖不同维度</li>
 *   <li>搜索计划按优先级排序（高优先级的先搜）</li>
 *   <li>大纲驱动：先定结构，再分配搜索任务</li>
 * </ul>
 */
@Service
public class PlannerAgent {

    private static final Logger log = LoggerFactory.getLogger(PlannerAgent.class);

    private final ChatClient chatClient;
    private final ChatClient fallbackClient;
    private final ModelFallbackService fallbackService;
    private final JsonParseUtils jsonUtils;
    private final DynamicPromptService dynamicPromptService;
    private final PiiMaskingService piiMaskingService;

    /** Fallback: 最简计划（不做拆解，直接搜索原始 query） */
    private static final PlanResult FALLBACK = new PlanResult(
        List.of("核心问题分析"),
        "# 研究报告\n\n## 核心发现\n\n## 详细分析\n\n## 结论与建议\n",
        List.of(new SearchPlan("Q0", "", "原始查询直接搜索", 1))
    );

    public PlannerAgent(
        @Qualifier("plannerClient") ChatClient chatClient,
        @Qualifier("plannerFallbackClient") ChatClient fallbackClient,
        ModelFallbackService fallbackService,
        JsonParseUtils jsonUtils,
        DynamicPromptService dynamicPromptService,
        PiiMaskingService piiMaskingService
    ) {
        this.chatClient = chatClient;
        this.fallbackClient = fallbackClient;
        this.fallbackService = fallbackService;
        this.jsonUtils = jsonUtils;
        this.piiMaskingService = piiMaskingService;
        this.dynamicPromptService = dynamicPromptService;
    }

    /**
     * 生成研究计划.
     *
     * @param query          用户原始查询
     * @param memoryContext  用户记忆上下文（偏好、历史）
     * @return 计划结果（子问题 + 大纲 + 搜索计划）
     */
    public PlanResult plan(String query, String memoryContext) {
        log.info("[Planner] 开始规划: query='{}'", piiMaskingService.tokenizeToString(query));

        try {
            // 每次调用时加载模板（DynamicPromptService 内置 1min TTL 缓存）→ 支持 DB 热更新免重启
            PromptParts parts = PromptSplitUtils.split(
                dynamicPromptService.getTemplateContent("planner"));

            String userPrompt = parts.user()
                .replace("{{query}}", query)
                .replace("{{memoryContext}}",
                    memoryContext != null ? memoryContext : "（无历史上下文）")
                .replace("{{current_time}}", java.time.LocalDateTime.now().toString());

            // .entity() 自动 JSON 解析 + 类型映射 + 自校正（降级逻辑内置于 ModelFallbackService）
            PlanResult result = fallbackService.callWithFallback(
                chatClient, fallbackClient, parts.system(), userPrompt, "Planner", PlanResult.class);

            log.debug("[Planner] LLM 解析完成: {} 个子问题, {} 个搜索计划",
                result.subQuestions() != null ? result.subQuestions().size() : 0,
                result.searchPlans() != null ? result.searchPlans().size() : 0);

            // 验证规划质量（LLM JSON 解析失败时字段可能为 null）
            if (result.subQuestions() == null || result.subQuestions().isEmpty()) {
                log.warn("[Planner] 未生成子问题，使用 fallback");
                return FALLBACK;
            }
            if (result.searchPlans() == null || result.searchPlans().isEmpty()) {
                log.warn("[Planner] 未生成搜索计划，使用 fallback");
                return FALLBACK;
            }

            log.info("[Planner] 规划完成: {} 个子问题, {} 个搜索计划",
                result.subQuestions().size(), result.searchPlans().size());
            return result;

        } catch (Exception e) {
            log.error("[Planner] 规划异常，返回 fallback", e);
            return FALLBACK;
        }
    }

}
