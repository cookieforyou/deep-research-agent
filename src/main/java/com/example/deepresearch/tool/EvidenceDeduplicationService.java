package com.example.deepresearch.tool;

import com.example.deepresearch.common.model.Evidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 证据去重与过滤服务 — 替代 LLM 驱动的 EvidenceJudgeAgent.
 * <p>
 * 使用纯代码级算法进行去重和过滤，执行时间 &lt;1ms（vs Judge 的 ~100s），
 * 且不会出现 LLM JSON 输出截断问题。
 * </p>
 *
 * <h3>过滤策略（按顺序）</h3>
 * <ol>
 *   <li><b>URL 精确去重</b>：相同 URL → 保留评分更高的</li>
 *   <li><b>标题 Jaccard 相似度去重</b>：标题相似度 &gt; 阈值 → 保留评分更高的</li>
 *   <li><b>低权威域名过滤</b>：黑名单域名直接移除（博客、个人站点等）</li>
 *   <li><b>评分阈值过滤</b>：score &lt; 0.45 的证据丢弃</li>
 *   <li><b>数量上限</b>：最多保留 40 条（按评分排序取 top-K）</li>
 * </ol>
 */
@Service
public class EvidenceDeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceDeduplicationService.class);

    /** 标题 Jaccard 相似度阈值（超过此值视为重复，0.60 比默认 0.80 更激进以捕获同主题变体标题） */
    private static final double TITLE_SIMILARITY_THRESHOLD = 0.60;

    /** 最低评分阈值（低于此值直接丢弃） */
    private static final double MIN_SCORE_THRESHOLD = 0.45;

    /** 最大保留证据数量 */
    private static final int MAX_EVIDENCE_COUNT = 40;

    /** 低权威域名黑名单 */
    private static final Set<String> LOW_AUTHORITY_DOMAINS = Set.of(
        "cnblogs.com", "php.cn", "csdn.net",
        "jianshu.com", "zhuanlan.zhihu.com",
        "blog.sina.com.cn", "blog.csdn.net"
    );

    /**
     * 去重结果.
     */
    public record DedupResult(
        List<Evidence> dedupedEvidence,
        List<String> sourceIndex
    ) {}

    /**
     * 对证据列表进行去重和过滤.
     *
     * @param webEvidence   网络检索证据
     * @param localEvidence 本地检索证据
     * @return 去重后的证据和来源索引
     */
    public DedupResult deduplicate(List<Evidence> webEvidence, List<Evidence> localEvidence) {
        // 合并
        List<Evidence> all = new ArrayList<>();
        if (webEvidence != null) all.addAll(webEvidence);
        if (localEvidence != null) all.addAll(localEvidence);

        int before = all.size();
        if (before == 0) {
            return new DedupResult(List.of(), List.of());
        }

        // 步骤 1: URL 精确去重（相同 URL 保留评分更高的）
        // 空 URL 的证据（如本地知识库文档）用 sourceId 作为去重键，防止被静默丢弃
        Map<String, Evidence> urlMap = new LinkedHashMap<>();
        for (Evidence e : all) {
            String key = (e.url() != null && !e.url().isBlank()) ? e.url() : e.sourceId();
            urlMap.merge(key, e, (a, b) -> a.score() >= b.score() ? a : b);
        }
        List<Evidence> afterUrl = new ArrayList<>(urlMap.values());
        int urlDupes = before - afterUrl.size();

        // 步骤 2: 标题相似度去重
        List<Evidence> afterTitle = new ArrayList<>();
        for (Evidence e : afterUrl) {
            boolean isDuplicate = false;
            for (int i = 0; i < afterTitle.size(); i++) {
                if (titleSimilarity(e.title(), afterTitle.get(i).title()) >= TITLE_SIMILARITY_THRESHOLD) {
                    // 保留评分更高的
                    if (e.score() > afterTitle.get(i).score()) {
                        afterTitle.set(i, e);
                    }
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                afterTitle.add(e);
            }
        }
        int titleDupes = afterUrl.size() - afterTitle.size();

        // 步骤 3: 低权威域名过滤
        List<Evidence> afterDomain = afterTitle.stream()
            .filter(e -> !isLowAuthority(e.domain()))
            .toList();
        int domainFiltered = afterTitle.size() - afterDomain.size();

        // 步骤 4: 评分阈值过滤
        List<Evidence> afterScore = afterDomain.stream()
            .filter(e -> e.score() >= MIN_SCORE_THRESHOLD)
            .toList();
        int scoreFiltered = afterDomain.size() - afterScore.size();

        // 步骤 5: 数量上限（按评分降序取 top-K）
        List<Evidence> finalResults = afterScore.stream()
            .sorted((a, b) -> Double.compare(b.score(), a.score()))
            .limit(MAX_EVIDENCE_COUNT)
            .toList();
        int capped = afterScore.size() - finalResults.size();

        // 构建 sourceIndex
        List<String> sourceIndex = finalResults.stream()
            .map(Evidence::sourceId)
            .distinct()
            .collect(Collectors.toList());

        log.info("[Dedup] {} → {} 条 (URL去重={}, 标题去重={}, 域名过滤={}, 评分过滤={}, 上限截断={})",
            before, finalResults.size(), urlDupes, titleDupes, domainFiltered, scoreFiltered, capped);

        return new DedupResult(finalResults, sourceIndex);
    }

    /**
     * 计算两个标题的 Jaccard 相似度.
     * <p>
     * 对中英文混合文本使用字符级 2-gram 分词，对纯英文使用单词级分词。
     * </p>
     */
    double titleSimilarity(String titleA, String titleB) {
        if (titleA == null || titleB == null) return 0.0;
        if (titleA.isEmpty() && titleB.isEmpty()) return 1.0;
        if (titleA.isEmpty() || titleB.isEmpty()) return 0.0;

        Set<String> tokensA = tokenize(titleA);
        Set<String> tokensB = tokenize(titleB);

        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(tokensA);
        intersection.retainAll(tokensB);

        Set<String> union = new HashSet<>(tokensA);
        union.addAll(tokensB);

        return (double) intersection.size() / union.size();
    }

    /**
     * 分词：中文用 2-gram 字符级，英文/数字用单词级.
     */
    private Set<String> tokenize(String text) {
        // 归一化：小写、去标点、合并空白
        String normalized = text.toLowerCase()
            .replaceAll("[\\p{Punct}—【】「」『』《》、。，；：？！…\"'（）\\[\\]]+", " ")
            .replaceAll("\\s+", " ")
            .trim();

        Set<String> tokens = new HashSet<>();

        // 提取英文单词（连续字母）
        java.util.regex.Matcher wordMatcher = java.util.regex.Pattern.compile("[a-z]{2,}").matcher(normalized);
        while (wordMatcher.find()) {
            tokens.add(wordMatcher.group());
        }

        // 中文字符 2-gram
        StringBuilder chineseChars = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            if (Character.isIdeographic(c)) {
                chineseChars.append(c);
            }
        }
        String cnText = chineseChars.toString();
        for (int i = 0; i < cnText.length() - 1; i++) {
            tokens.add(cnText.substring(i, i + 2));
        }

        // 保留数字（如年份）
        java.util.regex.Matcher numMatcher = java.util.regex.Pattern.compile("\\d{2,}").matcher(normalized);
        while (numMatcher.find()) {
            tokens.add(numMatcher.group());
        }

        return tokens;
    }

    /**
     * 检查域名是否为低权威来源.
     */
    private boolean isLowAuthority(String domain) {
        if (domain == null || domain.isBlank()) return false;
        String d = domain.toLowerCase().replaceAll("^www\\.", "");
        return LOW_AUTHORITY_DOMAINS.stream().anyMatch(d::contains);
    }
}
