package com.example.deepresearch.agent.eval;

import com.example.deepresearch.common.model.EvalResult;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 评估 Agent — 对生成的研报进行多维度质量评估.
 * <p>
 * 使用 deepseek-v4-flash (T=0.05)，评估任务需要极低随机性。
 * 在 Writer 完成后异步执行（fire-and-forget），失败不影响主流程。
 * </p>
 *
 * <h3>评估维度（1-5 分制）</h3>
 * <ul>
 *   <li><b>相关性 (Relevance)</b>: 报告是否准确回应了原始 query</li>
 *   <li><b>连贯性 (Coherence)</b>: 章节结构是否逻辑清晰、论证有无断层</li>
 *   <li><b>引用准确性 (Citation Accuracy)</b>: 引用 sourceId 是否全部合法</li>
 *   <li><b>完备性 (Completeness)</b>: 是否覆盖了 Planner 的全部子问题</li>
 *   <li><b>简洁性 (Conciseness)</b>: 是否避免了冗余和重复</li>
 * </ul>
 *
 * <h3>结果用途</h3>
 * <ul>
 *   <li>持久化到 {@code ResearchHistory.evalScores} JSON 字段</li>
 *   <li>滑动窗口告警：连续 10 次均分 &lt; 3.0 触发 WARN 日志</li>
 *   <li>长期趋势分析，指导 Prompt 模板迭代优化</li>
 * </ul>
 */
@Service
public class EvalAgent {

    private static final Logger log = LoggerFactory.getLogger(EvalAgent.class);

    /** 报告截断长度（评估只需看前几章：摘要+核心发现+结论） */
    private static final int MAX_REPORT_LENGTH = 4000;

    /** sourceIndex 最大条目数（减少 prompt 体积） */
    private static final int MAX_SOURCE_INDEX_ENTRIES = 20;

    /** sourceIndex 字符串最大长度 */
    private static final int MAX_SOURCE_INDEX_LENGTH = 500;

    private final ChatClient chatClient;
    private final JsonParseUtils jsonUtils;
    private final String systemPrompt;
    private final String userPromptTemplate;
    private final PiiMaskingService piiMaskingService;
    private final AtomicReference<Double> evalScoreGauge;

    public EvalAgent(
        @Qualifier("evalClient") ChatClient chatClient,
        JsonParseUtils jsonUtils,
        ResourceLoader resourceLoader,
        PiiMaskingService piiMaskingService,
        AtomicReference<Double> evalScoreGauge
    ) {
        this.chatClient = chatClient;
        this.jsonUtils = jsonUtils;
        this.piiMaskingService = piiMaskingService;
        this.evalScoreGauge = evalScoreGauge;
        String fullTemplate = loadPrompt(resourceLoader);
        PromptParts parts = PromptSplitUtils.split(fullTemplate);
        this.systemPrompt = parts.system();
        this.userPromptTemplate = parts.user();
    }

    /**
     * 对研究报告进行多维度质量评估.
     *
     * @param query         原始研究查询
     * @param subQuestions  Planner 拆解的子问题列表
     * @param report        最终报告 Markdown
     * @param sourceIndex   合法引用 ID 列表
     * @return 评估结果（5 维度评分 + 综合分 + 摘要）
     */
    public EvalResult evaluate(String query, List<String> subQuestions,
                                String report, List<String> sourceIndex) {
        log.info("[Eval] 开始评估报告: query='{}', {} 个子问题, {} 个合法引用",
            query != null ? piiMaskingService.tokenizeToString(
                query.substring(0, Math.min(50, query.length()))) : "null",
            subQuestions != null ? subQuestions.size() : 0,
            sourceIndex != null ? sourceIndex.size() : 0);

        try {
            // 截断报告以控制 token 成本（评估不需要全文）
            String truncatedReport = truncateReport(report);

            // 截断 sourceIndex（40 个条目约 2000 字符，对评估而言前 20 个足够）
            String truncatedSourceIndex = truncateSourceIndex(sourceIndex);

            String userPrompt = userPromptTemplate
                .replace("{{query}}", query != null ? query : "")
                .replace("{{subQuestions}}",
                    subQuestions != null && !subQuestions.isEmpty()
                        ? String.join("\n", subQuestions)
                        : "（无子问题列表）")
                .replace("{{sourceIndex}}", truncatedSourceIndex)
                .replace("{{report}}", truncatedReport);

            log.debug("[Eval] Prompt 构造完成: system={} 字符, user={} 字符",
                systemPrompt.length(), userPrompt.length());

            // .entity() 自动 JSON 解析 + 类型映射 + 自校正
            EvalResult result = chatClient.prompt()
                .advisors(a -> a.param("agent", "Eval").param("tier", "flash"))
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(EvalResult.class);

            if (result == null) {
                log.warn("[Eval] LLM 返回空内容 (userPromptSize={} 字符) — " +
                    "可能是 userPrompt 过大或模型暂时不可用，返回 fallback", userPrompt.length());
                return EvalResult.FALLBACK;
            }

            log.debug("[Eval] LLM 解析完成: overallScore={}", String.format("%.2f", result.overallScore()));

            // 验证评估质量
            if (result == EvalResult.FALLBACK) {
                log.warn("[Eval] 评估 JSON 解析失败，使用 fallback");
            } else {
                // 更新 Micrometer Gauge 供 Prometheus 告警规则使用
                evalScoreGauge.set(result.overallScore());
                log.info("[Eval] 评估完成: relevance={}, coherence={}, citationAccuracy={}, " +
                    "completeness={}, conciseness={}, overallScore={}",
                    String.format("%.1f", result.relevance()),
                    String.format("%.1f", result.coherence()),
                    String.format("%.1f", result.citationAccuracy()),
                    String.format("%.1f", result.completeness()),
                    String.format("%.1f", result.conciseness()),
                    String.format("%.2f", result.overallScore()));
            }

            return result;

        } catch (Exception e) {
            log.error("[Eval] 评估异常，返回 fallback", e);
            return EvalResult.FALLBACK;
        }
    }

    /**
     * 截断报告以控制上下文窗口.
     * <p>
     * 截取前 {@value MAX_REPORT_LENGTH} 字符。
     * 报告的评估主要依赖前几章（摘要、核心发现、结论），
     * 截断尾部不影响评估准确性。
     * </p>
     */
    private String truncateReport(String report) {
        if (report == null || report.isEmpty()) {
            return "（报告为空）";
        }
        if (report.length() <= MAX_REPORT_LENGTH) {
            return report;
        }
        log.debug("[Eval] 报告过长 ({} 字符)，截断至 {} 字符",
            report.length(), MAX_REPORT_LENGTH);
        return report.substring(0, MAX_REPORT_LENGTH) +
            "\n\n...（报告内容已截断，后续章节省略）";
    }

    /**
     * 截断 sourceIndex 以控制 prompt 体积.
     * <p>
     * 40 个 sourceId 约 2000 字符，对引用准确性评估而言前 20 个足够覆盖关键引用。
     * 同时限制总长度不超过 {@value MAX_SOURCE_INDEX_LENGTH} 字符。
     * </p>
     */
    private String truncateSourceIndex(List<String> sourceIndex) {
        if (sourceIndex == null || sourceIndex.isEmpty()) {
            return "（无引用）";
        }
        List<String> limited = sourceIndex.size() > MAX_SOURCE_INDEX_ENTRIES
            ? sourceIndex.subList(0, MAX_SOURCE_INDEX_ENTRIES)
            : sourceIndex;

        String joined = String.join(", ", limited);
        if (joined.length() > MAX_SOURCE_INDEX_LENGTH) {
            joined = joined.substring(0, MAX_SOURCE_INDEX_LENGTH) + "...";
        }

        if (sourceIndex.size() > MAX_SOURCE_INDEX_ENTRIES) {
            joined += String.format(" （共%d个引用，仅展示前%d个）",
                sourceIndex.size(), MAX_SOURCE_INDEX_ENTRIES);
        }
        return joined;
    }

    private String loadPrompt(ResourceLoader loader) {
        try {
            Resource resource = loader.getResource("classpath:prompts/eval.st");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[Eval] 无法加载 prompt 模板", e);
            return """
                你是研究报告质量评估专家。请对以下报告进行5维度评估（1-5分制）：
                相关性、连贯性、引用准确性、完备性、简洁性。

                原始query: {{query}}
                子问题: {{subQuestions}}
                合法引用: {{sourceIndex}}
                报告: {{report}}

                返回JSON: {"relevance": 3.0, "coherence": 3.0, "citationAccuracy": 3.0, "completeness": 3.0, "conciseness": 3.0, "overallScore": 3.0, "summary": "评估失败"}
                """;
        }
    }
}
