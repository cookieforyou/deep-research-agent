package com.example.deepresearch.security;

import com.example.deepresearch.common.config.DeepResearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PII 脱敏服务 — 基于可逆标记化的敏感信息保护.
 * <p>
 * 将 PII 替换为类型化令牌（如 {@code <PHONE_0>}、{@code <EMAIL_1>}），
 * 而非不可逆掩码（{@code 138****5678}）。令牌映射表（Vault）存储在内存中，
 * 支持在 LLM 响应中还原原始值。
 * </p>
 *
 * <h3>令牌格式</h3>
 * <ul>
 *   <li>{@code <PHONE_N>} — 手机号</li>
 *   <li>{@code <EMAIL_N>} — 邮箱</li>
 *   <li>{@code <ID_CARD_N>} — 身份证号</li>
 *   <li>{@code <BANK_CARD_N>} — 银行卡号</li>
 * </ul>
 *
 * <h3>安全属性</h3>
 * <ul>
 *   <li><b>确定性映射</b>: 同一原始值始终映射到同一令牌（保持实体共指）</li>
 *   <li><b>零信息泄露</b>: 令牌不包含原始数据的任何部分</li>
 *   <li><b>可逆</b>: 通过 {@link #restore(String)} 在 LLM 响应中还原</li>
 *   <li><b>Vault 隔离</b>: 令牌映射表仅存在于服务内存，永不发送到外部 API</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * <p>
 * 使用 {@link ConcurrentHashMap} 和 {@link AtomicInteger}，
 * 支持虚拟线程高并发场景。
 * </p>
 */
@Service
public class PiiMaskingService {

    private static final Logger log = LoggerFactory.getLogger(PiiMaskingService.class);

    // =========================== 预编译正则 ===========================

    /** 手机号: 1[3-9] + 9位数字，使用数字边界断言 */
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "(?<!\\d)(1[3-9]\\d{9})(?!\\d)");

    /** 身份证号: 18位（末位可为X），使用数字边界断言 */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile(
        "(?<!\\d)(\\d{17}[\\dXx])(?!\\d)");

    /** 邮箱: 标准邮箱格式 */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}");

    /** 银行卡号: 16-19位数字，使用数字边界断言 */
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile(
        "(?<!\\d)(\\d{16,19})(?!\\d)");

    // =========================== 令牌前缀 ===========================

    static final String PHONE_TOKEN = "<PHONE_";
    static final String EMAIL_TOKEN = "<EMAIL_";
    static final String ID_CARD_TOKEN = "<ID_CARD_";
    static final String BANK_CARD_TOKEN = "<BANK_CARD_";
    private static final String TOKEN_SUFFIX = ">";

    // =========================== Vault & Counters ===========================

    /** 令牌 → 原始值映射（线程安全） */
    private final ConcurrentHashMap<String, String> vault = new ConcurrentHashMap<>();

    /** 各类型令牌计数器 */
    private final AtomicInteger phoneCounter = new AtomicInteger(0);
    private final AtomicInteger emailCounter = new AtomicInteger(0);
    private final AtomicInteger idCardCounter = new AtomicInteger(0);
    private final AtomicInteger bankCardCounter = new AtomicInteger(0);

    private final boolean enabled;

    public PiiMaskingService(DeepResearchProperties properties) {
        this.enabled = properties.pii() != null && properties.pii().enabled();
    }

    // =========================== 公共 API ===========================

    /**
     * 标记化结果.
     */
    public record TokenizeResult(String tokenizedText, int tokenCount, List<String> tokenTypes) {
        public static TokenizeResult unchanged(String text) {
            return new TokenizeResult(text, 0, List.of());
        }
    }

    /**
     * 对文本中的 PII 进行标记化处理.
     *
     * @param text 原始文本
     * @return 标记化结果
     */
    public TokenizeResult tokenize(String text) {
        if (!enabled || text == null || text.isBlank()) {
            return TokenizeResult.unchanged(text != null ? text : "");
        }

        List<String> tokenTypes = new ArrayList<>();
        String result = text;

        // 手机号 → <PHONE_N>
        result = replaceWithToken(result, PHONE_PATTERN, PHONE_TOKEN, phoneCounter,
            "PHONE", tokenTypes);

        // 身份证 → <ID_CARD_N>
        result = replaceWithToken(result, ID_CARD_PATTERN, ID_CARD_TOKEN, idCardCounter,
            "ID_CARD", tokenTypes);

        // 邮箱 → <EMAIL_N>
        result = replaceWithToken(result, EMAIL_PATTERN, EMAIL_TOKEN, emailCounter,
            "EMAIL", tokenTypes);

        // 银行卡 → <BANK_CARD_N>
        result = replaceWithToken(result, BANK_CARD_PATTERN, BANK_CARD_TOKEN, bankCardCounter,
            "BANK_CARD", tokenTypes);

        int tokenCount = tokenTypes.size();
        if (tokenCount > 0) {
            log.debug("[PII] 标记化完成: {} 个令牌 → {}", tokenCount, tokenTypes);
        }

        return new TokenizeResult(result, tokenCount, tokenTypes);
    }

    /**
     * 便捷方法: 直接返回标记化后的字符串.
     */
    public String tokenizeToString(String text) {
        return tokenize(text).tokenizedText();
    }

    /**
     * 还原文本中的令牌为原始 PII 值.
     * <p>
     * 扫描文本中的 {@code <TYPE_N>} 模式，从 Vault 中查找对应原始值并替换。
     * 未找到的令牌保持原样。
     * </p>
     *
     * @param tokenizedText 包含令牌的文本（通常来自 LLM 响应）
     * @return 还原后的文本
     */
    public String restore(String tokenizedText) {
        if (tokenizedText == null || tokenizedText.isBlank() || vault.isEmpty()) {
            return tokenizedText;
        }

        // 匹配所有令牌格式: <TYPE_N>
        Pattern tokenPattern = Pattern.compile(
            "<(PHONE|EMAIL|ID_CARD|BANK_CARD)_\\d+>");

        Matcher matcher = tokenPattern.matcher(tokenizedText);
        StringBuffer restored = new StringBuffer();
        int restoredCount = 0;

        while (matcher.find()) {
            String token = matcher.group();
            String original = vault.get(token);
            if (original != null) {
                matcher.appendReplacement(restored, Matcher.quoteReplacement(original));
                restoredCount++;
            }
        }
        matcher.appendTail(restored);

        if (restoredCount > 0) {
            log.debug("[PII] 还原完成: {} 个令牌已还原", restoredCount);
        }

        return restored.toString();
    }

    /**
     * 获取 Vault 的不可变副本（用于调试/审计）.
     */
    public Map<String, String> getVaultSnapshot() {
        return Map.copyOf(vault);
    }

    /**
     * 清空 Vault 和计数器（用于测试/会话重置）.
     */
    public void reset() {
        vault.clear();
        phoneCounter.set(0);
        emailCounter.set(0);
        idCardCounter.set(0);
        bankCardCounter.set(0);
    }

    // =========================== 内部方法 ===========================

    /**
     * 使用正则匹配 PII 并替换为类型化令牌.
     */
    private String replaceWithToken(String text, Pattern pattern, String tokenPrefix,
                                     AtomicInteger counter, String piiType,
                                     List<String> tokenTypes) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String piiValue = matcher.group();
            // 确定性: 相同值 → 相同令牌
            String token = vault.computeIfAbsent(
                tokenPrefix + "v:" + piiValue,
                k -> tokenPrefix + counter.getAndIncrement() + TOKEN_SUFFIX
            );
            // 同时以令牌为 key 存储原始值（用于 restore）
            vault.putIfAbsent(token, piiValue);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(token));
            tokenTypes.add(piiType);
        }
        matcher.appendTail(sb);

        return sb.toString();
    }
}
