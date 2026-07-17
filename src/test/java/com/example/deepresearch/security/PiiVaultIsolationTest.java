package com.example.deepresearch.security;

import com.example.deepresearch.common.config.DeepResearchProperties;
import com.example.deepresearch.common.observability.BusinessMetrics;
import com.example.deepresearch.security.PiiMaskingService.PiiVault;
import com.example.deepresearch.security.PiiMaskingService.TokenizeResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PII Vault 请求级隔离单元测试.
 * <p>
 * 覆盖 2026-07-17 修复的跨租户 PII 泄漏向量：全局 Vault 时代，
 * 会话 B 的响应中出现 {@code <PHONE_0>} 字面量即可还原出会话 A 的真实手机号。
 * </p>
 */
class PiiVaultIsolationTest {

    private final PiiMaskingService service = new PiiMaskingService(
        testProperties(), new BusinessMetrics(new SimpleMeterRegistry()));

    private static DeepResearchProperties testProperties() {
        // 仅启用 pii，其余配置块传 null（测试中不触达）
        return new DeepResearchProperties(
            null, null, null, null, null, null, null, null,
            new DeepResearchProperties.PiiConfig(true),
            null);
    }

    @Test
    void tokensAreNotRestorableAcrossVaults() {
        // 会话 A：手机号被标记化进 A 的 Vault
        PiiVault vaultA = new PiiVault();
        TokenizeResult resultA = service.tokenize("联系电话 13812345678", vaultA);
        assertThat(resultA.tokenizedText()).contains("<PHONE_0>");

        // 会话 B：LLM 响应中出现相同令牌字面量（注入/幻觉）
        PiiVault vaultB = new PiiVault();
        String restoredInB = service.restore("请拨打 <PHONE_0> 咨询", vaultB);

        // B 的 Vault 里没有该令牌 → 不还原，A 的手机号不泄漏
        assertThat(restoredInB).doesNotContain("13812345678");
        assertThat(restoredInB).contains("<PHONE_0>");
    }

    @Test
    void restoreWorksWithinSameVault() {
        PiiVault vault = new PiiVault();
        String tokenized = service.tokenize(
            "手机号 13812345678，邮箱 test@example.com", vault).tokenizedText();

        assertThat(tokenized).contains("<PHONE_0>").contains("<EMAIL_0>");

        String restored = service.restore(
            "已记录手机号 <PHONE_0> 和邮箱 <EMAIL_0>", vault);

        assertThat(restored).contains("13812345678").contains("test@example.com");
    }

    @Test
    void deterministicMappingWithinVault() {
        PiiVault vault = new PiiVault();
        String tokenized = service.tokenize(
            "主号 13812345678，备用也是 13812345678", vault).tokenizedText();

        // 同一值同一令牌（请求内确定性映射），计数器不重复递增
        assertThat(tokenized).isEqualTo("主号 <PHONE_0>，备用也是 <PHONE_0>");
    }

    @Test
    void countersStartFromZeroPerVault() {
        // 每个 Vault 独立从 0 计数（全局计数器时代会话间序号互相推进）
        PiiVault vaultA = new PiiVault();
        PiiVault vaultB = new PiiVault();

        String a = service.tokenize("13812345678", vaultA).tokenizedText();
        String b = service.tokenize("13987654321", vaultB).tokenizedText();

        assertThat(a).isEqualTo("<PHONE_0>");
        assertThat(b).isEqualTo("<PHONE_0>");
    }
}
