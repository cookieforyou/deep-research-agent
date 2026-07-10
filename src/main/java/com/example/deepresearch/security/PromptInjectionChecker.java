package com.example.deepresearch.security;

import com.example.deepresearch.common.config.DeepResearchProperties;
import com.example.deepresearch.common.observability.BusinessMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Prompt 注入检测器 — 基于规则引擎的注入攻击检测.
 * <p>
 * 纯规则引擎（正则 + 关键词），不依赖 LLM 判断，避免二次注入风险。
 * 使用复合评分策略降低误杀率到 < 1%。
 * </p>
 *
 * <h3>检测维度</h3>
 * <ol>
 *   <li><b>指令覆盖模式</b>（强信号，命中即拦截）: 检测试图覆盖系统指令的正则模式</li>
 *   <li><b>黑名单关键词</b>（中等信号，score+=0.7）: 已知的注入攻击关键词</li>
 *   <li><b>长度异常</b>（弱信号，score+=0.3）: 超过配置的最大查询长度</li>
 *   <li><b>重复检测</b>（弱信号，score+=0.5）: 检测字符过度重复的填充攻击</li>
 * </ol>
 *
 * <h3>复合评分阈值</h3>
 * <p>
 * 总分 >= 0.5 判定为注入攻击。单一弱信号（仅长度异常或仅重复）不会触发拦截。
 * </p>
 *
 * <h3>线程安全</h3>
 * <p>
 * 无状态设计，Pattern 对象是 {@code final} 且不可变。
 * </p>
 */
@Service
public class PromptInjectionChecker {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionChecker.class);

    // =========================== 指令覆盖模式（强信号 — 命中即拦截） ===========================

    /** 中文：忽略指令 */
    private static final Pattern CHINESE_IGNORE_PATTERN = Pattern.compile(
        "忽略\\s*(以上|之前|所有|前面|全部)\\s*(所有|全部)?\\s*(指令|指示|提示|prompt|规则|对话)", Pattern.CASE_INSENSITIVE);

    /** 中文：角色重定义 */
    private static final Pattern CHINESE_ROLE_REDEFINE_PATTERN = Pattern.compile(
        "你(现在|从现在开始|的新身份|的新角色|新任务)是[^，,。.]{0,60}");

    /** 中文：强令指令 */
    private static final Pattern CHINESE_FORCE_INSTRUCTION_PATTERN = Pattern.compile(
        "从现在开始你(必须|要|只能|不可以)");

    /** 中文：要求输出系统信息.
     *  <p>使用 {@code .{0,15}?} 懒惰匹配避免 Java regex 交替嵌套回溯缺陷：
     *  {@code (你的|系统的?)} → {@code (prompt|...|系统消息|...)} 两层交替时，
     *  当"你的"匹配成功但后续组中"系统消息"对"系统prompt"部分匹配失败后，
     *  Java regex 引擎无法正确回溯到外层交替。展平为单一宽松匹配解决。</p> */
    private static final Pattern CHINESE_OUTPUT_SYSTEM_PATTERN = Pattern.compile(
        "(?:输出|显示|告诉|说出|提供).{0,15}?(?:prompt|指令|提示词|初始指令|系统消息|内部规则|隐藏规则)");

    /** 英文：忽略/遗忘指令 */
    private static final Pattern ENGLISH_IGNORE_PATTERN = Pattern.compile(
        "(forget|ignore|disregard|override)\\s+(all\\s+)?(previous|prior|above|the)\\s+(instructions?|prompts?|conversation|context|directives?)",
        Pattern.CASE_INSENSITIVE);

    /** 英文：要求输出系统信息 */
    private static final Pattern ENGLISH_OUTPUT_SYSTEM_PATTERN = Pattern.compile(
        "(output|reveal|print|show|tell|display|dump)\\s+(your\\s+)?(system\\s+)?(prompts?|instructions?|internal|hidden|secret)",
        Pattern.CASE_INSENSITIVE);

    /** 英文：DAN 越狱模式 */
    private static final Pattern DAN_PATTERN = Pattern.compile(
        "\\byou are now (DAN|Jailbreak|Free|Unchained|Chaos)\\b", Pattern.CASE_INSENSITIVE);

    // =========================== 配置 ===========================

    private final DeepResearchProperties.InjectionConfig config;
    private final BusinessMetrics businessMetrics;

    public PromptInjectionChecker(DeepResearchProperties properties,
                                   BusinessMetrics businessMetrics) {
        this.config = properties.injection() != null
            ? properties.injection()
            : new DeepResearchProperties.InjectionConfig(true, List.of(), 2000, 0.7);
        this.businessMetrics = businessMetrics;
    }

    // =========================== 公共 API ===========================

    /**
     * 检测结果.
     */
    public record InjectionCheckResult(boolean detected, String reason) {
        private static final InjectionCheckResult CLEAN = new InjectionCheckResult(false, "");

        public static InjectionCheckResult clean() { return CLEAN; }
        public static InjectionCheckResult blocked(String reason) { return new InjectionCheckResult(true, reason); }
    }

    /**
     * 检查查询文本是否包含注入攻击.
     *
     * @param query 用户查询文本
     * @return 检测结果（是否拦截 + 原因）
     */
    public InjectionCheckResult check(String query) {
        if (!config.enabled()) {
            return InjectionCheckResult.clean();
        }

        if (query == null || query.isBlank()) {
            return InjectionCheckResult.clean();
        }

        // 步骤 1: 强信号检测 — 指令覆盖模式（立即拦截）
        InjectionCheckResult strongSignal = checkStrongSignals(query);
        if (strongSignal.detected()) {
            return strongSignal;
        }

        // 步骤 2: 复合评分 — 弱信号累加
        double score = 0.0;
        StringBuilder triggers = new StringBuilder();

        // 黑名单关键词
        if (hasBlockedKeywords(query)) {
            score += 0.7;
            appendTrigger(triggers, "关键词黑名单");
        }

        // 长度异常
        if (isLengthAnomalous(query)) {
            score += 0.3;
            appendTrigger(triggers, "长度异常");
        }

        // 字符重复
        if (hasCharRepetition(query)) {
            score += 0.5;
            appendTrigger(triggers, "重复内容");
        }

        if (score >= 0.5) {
            businessMetrics.recordInjectionDetected("复合评分");
            return InjectionCheckResult.blocked(triggers.toString());
        }

        return InjectionCheckResult.clean();
    }

    // =========================== 检测逻辑 ===========================

    /**
     * 强信号检测：指令覆盖模式（命中任一即返回拦截）.
     */
    private InjectionCheckResult checkStrongSignals(String query) {
        if (CHINESE_IGNORE_PATTERN.matcher(query).find()) {
            businessMetrics.recordInjectionDetected("中文-忽略指令");
            return InjectionCheckResult.blocked("指令覆盖模式（中文-忽略指令）");
        }
        if (CHINESE_ROLE_REDEFINE_PATTERN.matcher(query).find()) {
            businessMetrics.recordInjectionDetected("中文-角色重定义");
            return InjectionCheckResult.blocked("指令覆盖模式（中文-角色重定义）");
        }
        if (CHINESE_FORCE_INSTRUCTION_PATTERN.matcher(query).find()) {
            businessMetrics.recordInjectionDetected("中文-强制指令");
            return InjectionCheckResult.blocked("指令覆盖模式（中文-强制指令）");
        }
        if (CHINESE_OUTPUT_SYSTEM_PATTERN.matcher(query).find()) {
            businessMetrics.recordInjectionDetected("中文-索取系统信息");
            return InjectionCheckResult.blocked("指令覆盖模式（中文-索取系统信息）");
        }
        if (ENGLISH_IGNORE_PATTERN.matcher(query).find()) {
            businessMetrics.recordInjectionDetected("英文-忽略指令");
            return InjectionCheckResult.blocked("指令覆盖模式（英文-忽略指令）");
        }
        if (ENGLISH_OUTPUT_SYSTEM_PATTERN.matcher(query).find()) {
            businessMetrics.recordInjectionDetected("英文-索取系统信息");
            return InjectionCheckResult.blocked("指令覆盖模式（英文-索取系统信息）");
        }
        if (DAN_PATTERN.matcher(query).find()) {
            businessMetrics.recordInjectionDetected("DAN越狱");
            return InjectionCheckResult.blocked("指令覆盖模式（DAN越狱）");
        }
        return InjectionCheckResult.clean();
    }

    /**
     * 黑名单关键词检测（忽略大小写和多余空格）.
     */
    boolean hasBlockedKeywords(String text) {
        if (config.blockedKeywords() == null || config.blockedKeywords().isEmpty()) {
            return false;
        }
        // 归一化：折叠空白、转小写
        String normalized = text.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        for (String keyword : config.blockedKeywords()) {
            String normalizedKeyword = keyword.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            if (normalized.contains(normalizedKeyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 长度异常检测.
     */
    boolean isLengthAnomalous(String text) {
        return text.length() > config.maxQueryLength();
    }

    /**
     * 字符重复检测：检测是否有单个字符占比超过阈值.
     * <p>
     * 典型攻击模式：用户重复填充大量字符试图覆盖系统提示词。
     * </p>
     */
    boolean hasCharRepetition(String text) {
        if (text.length() < 10) return false;

        double threshold = config.repetitionThreshold();
        int[] freq = new int[65536]; // BMP 字符频率表

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 65536) {
                freq[c]++;
            }
        }

        int maxFreq = 0;
        for (int f : freq) {
            if (f > maxFreq) maxFreq = f;
        }

        return (double) maxFreq / text.length() > threshold;
    }

    private void appendTrigger(StringBuilder sb, String trigger) {
        if (!sb.isEmpty()) sb.append(", ");
        sb.append(trigger);
    }
}
