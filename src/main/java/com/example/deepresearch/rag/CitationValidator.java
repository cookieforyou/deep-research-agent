package com.example.deepresearch.rag;

import com.example.deepresearch.common.model.Evidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 引用合法性校验器 — 自动剔除 LLM 幻觉产生的虚假引用，渲染带链接的参考资料列表.
 * <p>
 * 大模型经常产生不存在的引用 ID（如 {@code [WEB99]} 但根本没有 WEB99）。
 * 本校验器在 Writer 输出后执行，提取所有引用标记，与合法的 sourceIndex 比对，
 * <strong>自动移除非法引用</strong>，并在文末渲染真实的参考资料列表（含标题+可点击链接）。
 * </p>
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>正则提取报告中的所有引用标记</li>
 *   <li>与合法 ID 集对比，移除非法引用</li>
 *   <li>从 evidencePool 查找对应的标题和 URL，渲染参考资料列表</li>
 * </ol>
 *
 * <h3>引用标记格式</h3>
 * <ul>
 *   <li>新版: {@code [WEB1]}, {@code [LOCAL3]} — 工具层分配的 sourceId</li>
 *   <li>旧版兼容: {@code [WEB01_1]}, {@code [WEB01_1-1]} — 历史数据</li>
 * </ul>
 */
@Component
public class CitationValidator {

    private static final Logger log = LoggerFactory.getLogger(CitationValidator.class);

    /** 匹配引用标记：[WEB1]/[LOCAL3] */
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(WEB|LOCAL)\\d+\\]");

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
     * 将报告正文中的引用标记转换为可点击的 Markdown 链接.
     * <p>
     * 从 evidencePool 按 sourceId 查找 URL，将 {@code [WEB12]} 替换为
     * {@code [WEB12](url)}。sourceId 在 evidencePool 中不存在时保留原标记。
     * </p>
     *
     * @param reportContent 报告正文
     * @param evidencePool  完整证据池（用于查找 URL）
     * @return 正文引用可点击的报告
     */
    public String linkifyBodyCitations(String reportContent, List<Evidence> evidencePool) {
        if (reportContent == null || evidencePool == null || evidencePool.isEmpty()) {
            return reportContent;
        }

        Map<String, String> urlLookup = evidencePool.stream()
            .filter(e -> e.url() != null && !e.url().isBlank())
            .collect(Collectors.toMap(Evidence::sourceId, Evidence::url, (a, b) -> a));

        if (urlLookup.isEmpty()) {
            return reportContent;
        }

        // 使用 Matcher.appendReplacement 逐次替换，避免多次 replace 产生的 O(n²)
        Matcher matcher = CITATION_PATTERN.matcher(reportContent);
        StringBuilder sb = new StringBuilder(reportContent.length() + 1024);
        while (matcher.find()) {
            String marker = matcher.group();            // e.g. "[WEB12]"
            String sourceId = marker.substring(1, marker.length() - 1); // e.g. "WEB12"
            String url = urlLookup.get(sourceId);
            if (url != null) {
                matcher.appendReplacement(sb,
                    Matcher.quoteReplacement("[" + marker + "](" + url + ")"));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(marker));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 在报告末尾追加真实的参考资料列表（含标题 + 可点击链接）.
     * <p>
     * 从 evidencePool 按 sourceId 查找对应的标题和 URL，
     * 渲染为 Markdown 链接格式。仅列出报告中实际引用的来源。
     * </p>
     *
     * @param reportContent 报告正文
     * @param evidencePool  完整证据池（含 title/url/domain）
     * @return 追加了参考资料列表的报告
     */
    public String appendReferenceList(String reportContent, List<Evidence> evidencePool) {
        if (evidencePool == null || evidencePool.isEmpty()) {
            return reportContent;
        }

        // 收集报告中实际引用的 sourceId（去重保序）
        Set<String> citedIds = new LinkedHashSet<>();
        Matcher matcher = CITATION_PATTERN.matcher(reportContent);
        while (matcher.find()) {
            citedIds.add(matcher.group().replace("[", "").replace("]", ""));
        }

        // 从证据池按 sourceId 查找对应 Evidence
        Map<String, Evidence> lookup = evidencePool.stream()
            .collect(Collectors.toMap(Evidence::sourceId, e -> e, (a, b) -> a, LinkedHashMap::new));

        // 按引用出现顺序渲染
        List<Evidence> cited = citedIds.stream()
            .map(lookup::get)
            .filter(Objects::nonNull)
            .toList();

        if (cited.isEmpty()) {
            return reportContent;
        }

        StringBuilder sb = new StringBuilder(reportContent);
        sb.append("\n\n---\n\n## 参考资料\n\n");

        for (int i = 0; i < cited.size(); i++) {
            Evidence e = cited.get(i);
            String title = e.title() != null && !e.title().isBlank()
                ? e.title() : e.sourceId();
            String url = e.url() != null && !e.url().isBlank()
                ? e.url() : "#";
            sb.append(String.format("%d. [%s](%s) — *%s*\n",
                i + 1, title, url, e.domain() != null ? e.domain() : ""));
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
