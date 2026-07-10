package com.example.deepresearch.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 引用合法性校验器 — 自动剔除 LLM 幻觉产生的虚假引用.
 * <p>
 * 大模型经常产生不存在的引用 ID（如 {@code [WEB99_1-1]} 但根本没有 WEB99）。
 * 本校验器在 Writer 输出后执行，提取所有引用标记，与合法的 sourceIndex 比对，
 * <strong>自动移除非法引用</strong>，并在文末渲染真实的参考资料列表。
 * </p>
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>正则提取报告中的所有引用标记（格式: WEB/LOC + 数字序列）</li>
 *   <li>与 sourceIndex 中的合法 ID 集对比</li>
 *   <li>移除非法引用（仅移除标记，保留周围文字）</li>
 *   <li>可选：在文末追加真实参考资料章节</li>
 * </ol>
 *
 * <h3>引用标记格式</h3>
 * <ul>
 *   <li>网络证据: {@code [WEB01_1]} 或 {@code [WEB01_1-1]}</li>
 *   <li>本地证据: {@code [LOCAL01_1]} 或 {@code [LOCAL01_1-1]}</li>
 *   <li>正则: {@code \[(WEB|LOCAL)\d+_\d+(-\d+)?\]}</li>
 * </ul>
 */
@Component
public class CitationValidator {

    private static final Logger log = LoggerFactory.getLogger(CitationValidator.class);

    /** 匹配引用标记: [WEB01_1], [WEB01_1-1], [LOCAL01_1], [LOCAL01_1-1] */
    private static final Pattern CITATION_PATTERN = Pattern.compile(
        "\\[(WEB|LOCAL)\\d+_\\d+(?:-\\d+)?\\]");

    /**
     * 校验报告中的引用合法性.
     *
     * @param reportContent 报告正文（Markdown）
     * @param sourceIndex   合法来源 ID 集合
     * @return 校验结果（清洗后的报告 + 统计信息）
     */
    public ValidationResult validate(String reportContent, List<String> sourceIndex) {
        if (reportContent == null || reportContent.isEmpty()) {
            return new ValidationResult(reportContent, 0, 0, List.of());
        }

        // 构建合法 ID 快速查找集合
        Set<String> validIds = new HashSet<>(sourceIndex);

        // 提取所有引用标记
        Matcher matcher = CITATION_PATTERN.matcher(reportContent);
        Set<String> foundCitations = new HashSet<>();
        Set<String> invalidCitations = new HashSet<>();
        int totalCitations = 0;

        while (matcher.find()) {
            String citation = matcher.group();
            totalCitations++;
            foundCitations.add(citation);

            // 检查合法性
            // 去掉方括号后匹配（sourceIndex 存储不带括号的格式）
            String cleanId = citation.substring(1, citation.length() - 1);
            if (!validIds.contains(cleanId)) {
                invalidCitations.add(citation);
                log.warn("[CitationValidator] 发现非法引用: {} (不在 sourceIndex 中)", citation);
            }
        }

        // 移除非法引用（将 [ILLEGAL_ID] 替换为空字符串）
        String cleanedReport = reportContent;
        for (String invalid : invalidCitations) {
            cleanedReport = cleanedReport.replace(invalid, "");
        }

        if (!invalidCitations.isEmpty()) {
            log.info("[CitationValidator] 移除了 {} 个幻觉引用: {}",
                invalidCitations.size(), invalidCitations);
        }

        int validCount = totalCitations - invalidCitations.size();
        return new ValidationResult(cleanedReport, totalCitations, validCount,
            invalidCitations.stream().toList());
    }

    /**
     * 在报告末尾追加合法的参考资料列表.
     * <p>
     * 可选功能：将 sourceIndex 中的每条证据格式化为参考文献条目。
     * </p>
     *
     * @param reportContent 报告正文
     * @param sourceIndex   合法来源索引
     * @return 追加了参考文献的报告
     */
    public String appendReferenceList(String reportContent, List<String> sourceIndex) {
        if (sourceIndex == null || sourceIndex.isEmpty()) {
            return reportContent;
        }

        StringBuilder sb = new StringBuilder(reportContent);
        sb.append("\n\n---\n\n## 参考资料\n\n");

        for (int i = 0; i < sourceIndex.size(); i++) {
            sb.append(String.format("%d. `[%s]`\n", i + 1, sourceIndex.get(i)));
        }

        return sb.toString();
    }

    /**
     * 引用校验结果.
     */
    public record ValidationResult(
        /** 清洗后的报告正文 */
        String cleanedReport,
        /** 引用总数（含非法） */
        int totalCitations,
        /** 合法引用数 */
        int validCitations,
        /** 被移除的非法引用列表 */
        List<String> removedCitations
    ) {}
}
