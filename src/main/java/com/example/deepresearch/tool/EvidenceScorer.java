package com.example.deepresearch.tool;

import com.example.deepresearch.common.config.DeepResearchProperties.EvidenceScoringConfig;
import com.example.deepresearch.common.model.Evidence;
import com.example.deepresearch.common.model.Evidence.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 证据评分器 — 规则引擎前置打分.
 * <p>
 * 不完全依赖 LLM 评分，采用<strong>规则引擎前置 + LLM 微调</strong>的混合方案：
 * <ol>
 *   <li>先根据来源类型和域名做规则打分（确定性、低成本）</li>
 *   <li>EvidenceJudge Agent 基于规则分数做 LLM 微调（考虑内容质量）</li>
 * </ol>
 * </p>
 *
 * <h3>评分规则</h3>
 * <table>
 *   <tr><th>来源</th><th>基础分</th><th>匹配条件</th></tr>
 *   <tr><td>本地知识库</td><td>0.92</td><td>sourceType == LOCAL</td></tr>
 *   <tr><td>政府/教育</td><td>0.88</td><td>域名匹配 authorityDomains（如 *.gov.cn, *.edu.cn）</td></tr>
 *   <tr><td>主流媒体</td><td>0.72</td><td>域名包含 news/reuters/bloomberg 等关键词</td></tr>
 *   <tr><td>一般网站</td><td>0.58</td><td>有明确域名</td></tr>
 *   <tr><td>来源不明</td><td>0.45</td><td>域名无法识别</td></tr>
 * </table>
 */
public class EvidenceScorer {

    private static final Logger log = LoggerFactory.getLogger(EvidenceScorer.class);

    private final EvidenceScoringConfig config;

    /** 主流媒体域名关键词 */
    private static final List<String> MEDIA_KEYWORDS = List.of(
        "news", "reuters", "bloomberg", "bbc", "cnn",
        "wsj", "nytimes", "ft.com", "economist", "ap.org",
        "people", "xinhua", "china", "cctv", "sina", "163", "qq",
        "36kr", "huxiu", "jiemian", "caixin"
    );

    public EvidenceScorer(EvidenceScoringConfig config) {
        this.config = config;
    }

    /**
     * 对单条证据进行规则打分并返回更新评分后的副本.
     *
     * @param evidence 原始证据
     * @return 更新评分后的证据副本
     */
    public Evidence score(Evidence evidence) {
        double baseScore = calculateBaseScore(evidence);
        Evidence scored = evidence.withScore(baseScore);
        log.trace("证据评分: sourceId={}, domain={}, score={}",
            evidence.sourceId(), evidence.domain(), baseScore);
        return scored;
    }

    /**
     * 计算基础评分.
     */
    private double calculateBaseScore(Evidence evidence) {
        // 1. 本地知识库 → 最高评分
        if (evidence.sourceType() == SourceType.LOCAL) {
            return config.localKnowledgeBase();
        }

        // 2. 政府/教育机构域名
        if (matchesAuthorityDomain(evidence.domain())) {
            return config.governmentEdu();
        }

        // 3. 主流媒体
        if (isMainstreamMedia(evidence.domain())) {
            return config.mainstreamMedia();
        }

        // 4. 一般网站（有明确域名）
        if (evidence.domain() != null && !evidence.domain().isBlank()
            && !"unknown".equals(evidence.domain())) {
            return config.generalWebsite();
        }

        // 5. 来源不明
        return config.unknownSource();
    }

    /**
     * 检查域名是否匹配权威机构模式.
     */
    private boolean matchesAuthorityDomain(String domain) {
        if (domain == null) return false;
        for (String pattern : config.authorityDomains()) {
            // 简单通配符匹配: *.gov.cn → 检查域名以 .gov.cn 结尾
            // 或 gov.* → 检查域名包含 .gov.
            String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*");
            if (domain.matches(".*" + regex + ".*")
                || domain.endsWith(pattern.substring(1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查域名是否属于主流媒体.
     */
    private boolean isMainstreamMedia(String domain) {
        if (domain == null) return false;
        String lower = domain.toLowerCase();
        return MEDIA_KEYWORDS.stream().anyMatch(lower::contains);
    }
}
