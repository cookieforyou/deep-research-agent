package com.example.deepresearch.agent.analyst;

import com.example.deepresearch.common.model.AnalysisResult;
import com.example.deepresearch.common.model.Evidence;
import com.example.deepresearch.common.model.Finding;
import com.example.deepresearch.common.util.JsonParseUtils;
import com.example.deepresearch.common.util.PromptSplitUtils;
import com.example.deepresearch.common.util.PromptSplitUtils.PromptParts;
import com.example.deepresearch.service.DynamicPromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分析 Agent — 基于证据形成结论，评估证据完备性.
 * <p>
 * 使用 deepseek-v4-pro (T=0.3)，需要逻辑严谨的推理能力。
 * 这是 Reflect 循环的<strong>决策节点</strong>：
 * 如果证据不完备且未达最大迭代，触发 Reflect 补搜。
 * </p>
 *
 * <h3>分析维度</h3>
 * <ul>
 *   <li><b>结论形成</b>: 针对每个子问题，综合证据形成结论</li>
 *   <li><b>置信度评估</b>: 基于证据数量和质量评估结论的置信度</li>
 *   <li><b>完备性检查</b>: 检查是否覆盖了所有子问题维度</li>
 *   <li><b>缺口识别</b>: 识别信息缺口，为 Reflect 提供方向</li>
 * </ul>
 *
 * <h3>何时触发 Reflect</h3>
 * <ul>
 *   <li>任一子问题的结论置信度低于 0.6</li>
 *   <li>存在尚未覆盖的子问题维度</li>
 *   <li>关键数据点缺失（如市场规模的具体数字）</li>
 * </ul>
 */
@Service
public class AnalystAgent {

    private static final Logger log = LoggerFactory.getLogger(AnalystAgent.class);

    private final ChatClient chatClient;
    private final JsonParseUtils jsonUtils;
    private final String systemPrompt;
    private final String userPromptTemplate;

    /** Fallback: 接受当前证据，不做补充 */
    private static final AnalysisResult FALLBACK = new AnalysisResult(
        List.of(), false, List.of(), 0.5);

    public AnalystAgent(
        @Qualifier("analystClient") ChatClient chatClient,
        JsonParseUtils jsonUtils,
        DynamicPromptService dynamicPromptService
    ) {
        this.chatClient = chatClient;
        this.jsonUtils = jsonUtils;
        String fullTemplate = dynamicPromptService.getTemplateContent("analyst");
        PromptParts parts = PromptSplitUtils.split(fullTemplate);
        this.systemPrompt = parts.system();
        this.userPromptTemplate = parts.user();
    }

    /**
     * 进行分析归纳.
     *
     * @param query         原始研究查询
     * @param subQuestions  规划师拆解的子问题列表
     * @param evidencePool  裁判后的证据池
     * @return 分析结果（结论 + 完备性评估 + 缺口）
     */
    public AnalysisResult analyze(String query, List<String> subQuestions,
                                   List<Evidence> evidencePool) {
        log.info("[Analyst] 开始分析: {} 个子问题, {} 条证据",
            subQuestions != null ? subQuestions.size() : 0,
            evidencePool != null ? evidencePool.size() : 0);

        try {
            String userPrompt = userPromptTemplate
                .replace("{{query}}", query)
                .replace("{{subQuestions}}",
                    subQuestions != null ? String.join("\n", subQuestions) : "")
                .replace("{{evidencePool}}",
                    evidencePool != null ? buildEvidenceSummary(evidencePool) : "");

            // .entity() 自动 JSON 解析 + 类型映射 + 自校正
            AnalysisResult result = chatClient.prompt()
                .advisors(a -> a.param("agent", "Analyst").param("tier", "flash"))
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(AnalysisResult.class);
            log.debug("[Analyst] LLM 解析完成: {} 个结论", result.findings().size());

            // 分析质量日志
            double avgConfidence = result.findings().stream()
                .mapToDouble(Finding::confidence)
                .average()
                .orElse(0.0);
            log.info("[Analyst] 分析完成: {} 个结论, 平均置信度={}, 完备性={}, 需补搜={}",
                result.findings().size(), String.format("%.2f", avgConfidence),
                String.format("%.2f", result.completenessScore()), result.needsMoreResearch());

            if (result.needsMoreResearch()) {
                log.info("[Analyst] 识别信息缺口: {}", result.missingGaps());
            }

            return result;

        } catch (Exception e) {
            log.error("[Analyst] 分析异常，返回 fallback", e);
            return FALLBACK;
        }
    }

    /**
     * 构建证据摘要文本（用于 LLM 上下文窗口优化）.
     */
    private String buildEvidenceSummary(List<Evidence> pool) {
        StringBuilder sb = new StringBuilder();
        for (Evidence e : pool) {
            sb.append(String.format("[%s] %s (评分: %.2f, 来源: %s)\n  %s\n\n",
                e.sourceId(), e.title(), e.score(),
                e.domain() != null ? e.domain() : "unknown",
                e.content().length() > 200
                    ? e.content().substring(0, 200) + "..." : e.content()));
        }
        return sb.toString();
    }

}
