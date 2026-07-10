package com.example.deepresearch.agent.judge;

import com.example.deepresearch.common.model.AuditFlag;
import com.example.deepresearch.common.model.Evidence;
import com.example.deepresearch.common.model.JudgeResult;
import com.example.deepresearch.common.util.JsonParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 证据裁判 Agent — 对双源证据池进行评分、去重、冲突检测.
 * <p>
 * 使用 deepseek-v4-flash (T=0.2)，低温度确保评分标准严格一致。
 * 这是 7 步流程中<strong>质量控制的核心节点</strong>。
 * </p>
 *
 * <h3>裁判流程</h3>
 * <ol>
 *   <li><b>合并</b>: 将 WEB 和 LOCAL 证据合并到统一证据池</li>
 *   <li><b>去重</b>: 检测内容高度相似的证据，保留评分更高的</li>
 *   <li><b>冲突检测</b>: 识别两条证据描述同一事实但结论矛盾的情况</li>
 *   <li><b>评分微调</b>: 基于内容质量对规则引擎预评分做 LLM 微调</li>
 *   <li><b>来源索引</b>: 构建合法的 sourceId 集合（供 Writer 引用校验）</li>
 * </ol>
 */
// @Service — 已废弃，由 EvidenceDeduplicationService 替代（代码级去重）
public class EvidenceJudgeAgent {

    private static final Logger log = LoggerFactory.getLogger(EvidenceJudgeAgent.class);

    private final ChatClient chatClient;
    private final JsonParseUtils jsonUtils;
    private final String promptTemplate;

    /** Fallback: 裁判完全失败时使用（无源数据可用） */
    private static final JudgeResult EMPTY_FALLBACK = new JudgeResult(
        List.of(), List.of(), List.of());

    public EvidenceJudgeAgent(
        @Qualifier("judgeClient") ChatClient chatClient,
        JsonParseUtils jsonUtils,
        ResourceLoader resourceLoader
    ) {
        this.chatClient = chatClient;
        this.jsonUtils = jsonUtils;
        this.promptTemplate = loadPrompt(resourceLoader);
    }

    /**
     * 对证据池进行评审.
     *
     * @param query        原始研究查询（提供上下文）
     * @param webEvidence  Web Scout 收集的证据
     * @param localEvidence Local Scout 收集的证据
     * @return 评审结果（评分调整后的证据 + 审计标记 + 来源索引）
     */
    public JudgeResult judge(String query, List<Evidence> webEvidence,
                              List<Evidence> localEvidence) {
        // 步骤 0: 合并证据池（用 ArrayList 包装以便后续去重操作）
        List<Evidence> pool = new ArrayList<>();
        pool.addAll(webEvidence != null ? webEvidence : List.of());
        pool.addAll(localEvidence != null ? localEvidence : List.of());

        if (pool.isEmpty()) {
            log.warn("[EvidenceJudge] 证据池为空，跳过裁判");
            return EMPTY_FALLBACK;
        }

        log.info("[EvidenceJudge] 开始裁判: WEB={}, LOCAL={}, 合并={}",
            webEvidence != null ? webEvidence.size() : 0,
            localEvidence != null ? localEvidence.size() : 0,
            pool.size());

        try {
            // 构建证据池文本（供 LLM 分析）
            String evidenceText = buildEvidenceText(pool);

            String prompt = promptTemplate
                .replace("{{query}}", query)
                .replace("{{evidencePool}}", evidenceText);

            String rawOutput = chatClient.prompt().user(prompt).call().content();
            log.debug("[EvidenceJudge] LLM 输出: {}", rawOutput);

            // 解析 LLM 输出
            JudgeResult result = jsonUtils.safeParse(
                rawOutput, JudgeResult.class, null, "EvidenceJudge");

            // safeParse 返回 null 表示解析失败且无 fallback → 使用透传降级
            if (result == null) {
                log.warn("[EvidenceJudge] LLM 解析失败，降级为透传模式（保留全部 {} 条证据）", pool.size());
                result = buildPassThroughResult(pool);
            }

            log.info("[EvidenceJudge] 裁判完成: 有效证据={}, 审计标记={}",
                result.scoredEvidence().size(), result.flags().size());

            // 统计冲突和低分证据
            long conflictCount = result.flags().stream()
                .filter(f -> f.flagType() == AuditFlag.FlagType.CONFLICT)
                .count();
            long lowConfCount = result.flags().stream()
                .filter(f -> f.flagType() == AuditFlag.FlagType.LOW_CONFIDENCE)
                .count();
            if (conflictCount > 0 || lowConfCount > 0) {
                log.info("[EvidenceJudge] 质量问题: 冲突={}, 低可信度={}",
                    conflictCount, lowConfCount);
            }

            return result;

        } catch (Exception e) {
            log.error("[EvidenceJudge] 裁判异常，降级为透传模式（保留全部 {} 条证据）: {}",
                pool.size(), e.getMessage());
            return buildPassThroughResult(pool);
        }
    }

    /**
     * 构建透传降级结果 — 当 LLM 裁判失败时，保留所有证据并标记为"未裁判".
     * <p>
     * 这确保即使裁判环节出错，下游 Analyst 和 Writer 仍有证据可用，
     * 而非空证据池导致整个研究流程失效。
     * </p>
     */
    private JudgeResult buildPassThroughResult(List<Evidence> pool) {
        List<AuditFlag> flags = List.of(
            new AuditFlag("ALL", null, AuditFlag.FlagType.LOW_CONFIDENCE,
                "LLM 裁判失败，所有证据以原始评分透传，未经去重和冲突检测")
        );
        List<String> sourceIndex = pool.stream()
            .map(Evidence::sourceId)
            .distinct()
            .toList();
        return new JudgeResult(pool, flags, sourceIndex);
    }

    /**
     * 构建供 LLM 分析的证据文本.
     * <p>
     * 每条证据格式化为: [sourceId] 标题 | 域名 | 评分 | 内容摘要
     * </p>
     */
    private String buildEvidenceText(List<Evidence> pool) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pool.size(); i++) {
            Evidence e = pool.get(i);
            sb.append(String.format("[%s] %s\n  来源: %s (%s)\n  规则评分: %.2f\n  摘要: %s\n\n",
                e.sourceId(), e.title(),
                e.domain() != null ? e.domain() : "unknown",
                e.sourceType().name(),
                e.score(),
                e.content().length() > 300
                    ? e.content().substring(0, 300) + "..." : e.content()));
        }
        return sb.toString();
    }

    private String loadPrompt(ResourceLoader loader) {
        try {
            Resource resource = loader.getResource("classpath:prompts/evidence-judge.st");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[EvidenceJudge] 无法加载 prompt 模板", e);
            return """
                你是证据裁判专家。对以下证据池进行评分、去重和冲突检测。

                研究问题: {{query}}
                证据池:
                {{evidencePool}}

                返回 JSON: {"scoredEvidence": [...], "flags": [{"evidenceIdA":"...","evidenceIdB":null,"flagType":"LOW_CONFIDENCE","description":"..."}], "sourceIndex": [...]}
                """;
        }
    }
}
