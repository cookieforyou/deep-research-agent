package com.example.deepresearch.agent.writer;

import com.example.deepresearch.agent.bundle.ModelFallbackService;
import com.example.deepresearch.common.model.Evidence;
import com.example.deepresearch.common.model.Finding;
import com.example.deepresearch.common.model.WriteResult;
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

import java.util.Collections;
import java.util.List;

/**
 * 撰稿 Agent — 将研究结论整合为深度 Markdown 研报.
 * <p>
 * 使用 deepseek-v4-pro (T=0.4)，需要文采和流畅度。
 * 当 Pro 模型不可用时自动降级到 Flash (T=0.4)。
 * 目标产出 3000+ 字的结构化深度研报，精确引用每条来源。
 * </p>
 *
 * <h3>报告结构</h3>
 * <ol>
 *   <li>执行摘要（Executive Summary）</li>
 *   <li>研究背景与方法</li>
 *   <li>各子问题的详细分析（按大纲结构）</li>
 *   <li>跨维度综合讨论</li>
 *   <li>结论与建议</li>
 *   <li>参考资料（合法 sourceId 自动渲染）</li>
 * </ol>
 *
 * <h3>引用格式</h3>
 * <p>
 * 正文中使用 {@code [WEB01_1-1]} 或 {@code [LOCAL01_1-1]} 格式引用来源，
 * 文末自动生成参考资料列表。引用校验器（{@code CitationValidator}）
 * 会在输出后移除无效引用。
 * </p>
 */
@Service
public class WriterAgent {

    private static final Logger log = LoggerFactory.getLogger(WriterAgent.class);

    private final ChatClient chatClient;
    private final ChatClient fallbackClient;
    private final ModelFallbackService fallbackService;
    private final JsonParseUtils jsonUtils;
    private final DynamicPromptService dynamicPromptService;
    private final PiiMaskingService piiMaskingService;

    /** Fallback: 最简报告 */
    private static final WriteResult FALLBACK = new WriteResult(
        "# 研究未能完成\n\n抱歉，报告生成过程出现异常，请稍后重试。",
        Collections.emptyList(), 0, 1);

    public WriterAgent(
        @Qualifier("writerClient") ChatClient chatClient,
        @Qualifier("writerFallbackClient") ChatClient fallbackClient,
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
     * 撰写深度研究报告.
     *
     * @param query        原始研究查询
     * @param reportOutline Planner 生成的报告大纲
     * @param findings     分析师的结论列表
     * @param evidencePool 裁判后的证据池
     * @param sourceIndex  合法来源索引（用于引用渲染）
     * @return 撰写结果（报告正文 + 使用引用列表 + 字数统计）
     */
    public WriteResult write(String query, String reportOutline,
                              List<Finding> findings, List<Evidence> evidencePool,
                              List<String> sourceIndex) {
        log.info("[Writer] 开始撰写报告: query='{}', {} 个结论, {} 条证据",
            piiMaskingService.tokenizeToString(query), findings != null ? findings.size() : 0,
            evidencePool != null ? evidencePool.size() : 0);

        try {
            // 每次调用时加载模板（DynamicPromptService 内置 1min TTL 缓存）→ 支持 DB 热更新免重启
            PromptParts parts = PromptSplitUtils.split(
                dynamicPromptService.getTemplateContent("writer"));

            String userPrompt = parts.user()
                .replace("{{query}}", query)
                .replace("{{reportOutline}}",
                    reportOutline != null ? reportOutline : "# 研究报告")
                .replace("{{findings}}",
                    findings != null ? buildFindingsText(findings) : "")
                .replace("{{evidencePool}}",
                    evidencePool != null ? buildEvidenceText(evidencePool) : "")
                .replace("{{sourceIndex}}",
                    sourceIndex != null ? String.join(", ", sourceIndex) : "");

            // .entity() 自动 JSON 解析 + 类型映射 + 自校正（降级逻辑内置于 ModelFallbackService）
            WriteResult result = fallbackService.callWithFallback(
                chatClient, fallbackClient, parts.system(), userPrompt, "Writer", WriteResult.class);
            log.debug("[Writer] LLM 解析完成: {} 字, {} 个引用",
                result.reportContent() != null ? countWords(result.reportContent()) : 0,
                result.usedCitations() != null ? result.usedCitations().size() : 0);

            // 保护：LLM JSON 可能缺少 usedCitations 字段导致 null
            List<String> citations = result.usedCitations() != null
                ? result.usedCitations() : Collections.emptyList();
            String reportContent = result.reportContent() != null
                ? result.reportContent() : "";

            // 验证报告质量
            int wordCount = countWords(reportContent);
            log.info("[Writer] 报告完成: {} 字, {} 个引用, {} 个章节",
                wordCount, citations.size(), result.sectionCount());

            if (wordCount < 1500) {
                log.warn("[Writer] 报告字数不足 1500（当前 {}），可能需要优化 prompt 或模型参数", wordCount);
            }

            return new WriteResult(reportContent, citations, wordCount, result.sectionCount());

        } catch (Exception e) {
            log.error("[Writer] 撰写异常，返回 fallback", e);
            return FALLBACK;
        }
    }

    /**
     * 构建结论摘要文本.
     */
    private String buildFindingsText(List<Finding> findings) {
        StringBuilder sb = new StringBuilder();
        for (Finding f : findings) {
            sb.append(String.format("### %s\n**结论**: %s\n**推理**: %s\n**置信度**: %.2f\n**支撑证据**: %s\n\n",
                f.subQuestionId() != null ? f.subQuestionId() : "未知",
                f.conclusion() != null ? f.conclusion() : "",
                f.reasoning() != null ? f.reasoning() : "",
                f.confidence(),
                f.supportingEvidenceIds() != null
                    ? String.join(", ", f.supportingEvidenceIds())
                    : "无"));
        }
        return sb.toString();
    }

    /**
     * 构建证据摘要文本，包含 sourceId → 来源的映射.
     */
    private String buildEvidenceText(List<Evidence> pool) {
        StringBuilder sb = new StringBuilder();
        for (Evidence e : pool) {
            sb.append(String.format("[%s] %s\n  URL: %s\n  %s\n\n",
                e.sourceId() != null ? e.sourceId() : "?",
                e.title() != null ? e.title() : "",
                e.url() != null ? e.url() : "",
                e.content() != null
                    ? (e.content().length() > 300
                        ? e.content().substring(0, 300) + "..." : e.content())
                    : ""));
        }
        return sb.toString();
    }

    /**
     * 简单字数统计（按中文字符 + 英文单词估算）.
     */
    private int countWords(String text) {
        if (text == null || text.isEmpty()) return 0;
        long chineseChars = text.codePoints()
            .filter(cp -> {
                Character.UnicodeScript script = Character.UnicodeScript.of(cp);
                return script == Character.UnicodeScript.HAN;
            })
            .count();
        // 移除非英文字符，按空格分词计数
        StringBuilder sb = new StringBuilder();
        text.codePoints().forEach(cp -> {
            if (Character.isLetter(cp) || Character.isWhitespace(cp)) {
                sb.appendCodePoint(cp);
            }
        });
        String[] words = sb.toString().split("\\s+");
        long englishWords = (words.length == 1 && words[0].isEmpty()) ? 0 : words.length;
        return (int) (chineseChars + englishWords);
    }

}
