package com.example.deepresearch.agent.tool;

import com.example.deepresearch.agent.tool.SearchTools.CollectedSource;
import com.example.deepresearch.agent.tool.SearchTools.EvidenceCollector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SearchTools 证据收集器单元测试.
 * <p>
 * 覆盖工具层证据收集重构（2026-07-15）的核心语义：
 * sourceId 自增分配、按 dedupKey 去重复用、快照保持插入顺序。
 * </p>
 */
class EvidenceCollectorTest {

    @Test
    void assignsSequentialSourceIdsWithPrefix() {
        EvidenceCollector c = new EvidenceCollector("WEB");

        String id1 = c.register("https://a.com/1", "标题1", "https://a.com/1", "摘要1", "a.com", 0.0);
        String id2 = c.register("https://b.com/2", "标题2", "https://b.com/2", "摘要2", "b.com", 0.0);

        assertThat(id1).isEqualTo("WEB1");
        assertThat(id2).isEqualTo("WEB2");
    }

    @Test
    void reusesSourceIdForDuplicateKey() {
        // 同一 URL 被多个搜索词命中时应复用 sourceId，不产生重复条目
        EvidenceCollector c = new EvidenceCollector("WEB");

        String first = c.register("https://a.com/1", "标题", "https://a.com/1", "摘要", "a.com", 0.0);
        String second = c.register("https://a.com/1", "标题", "https://a.com/1", "摘要", "a.com", 0.0);

        assertThat(second).isEqualTo(first);
        assertThat(c.snapshot()).hasSize(1);
    }

    @Test
    void snapshotPreservesInsertionOrderAndContent() {
        EvidenceCollector c = new EvidenceCollector("LOCAL");
        c.register("doc1|前缀", "docs/a.pdf", "docs/a.pdf", "内容A", "internal", 0.88);
        c.register("doc2|前缀", "docs/b.pdf", "docs/b.pdf", "内容B", "internal", 0.75);

        Map<String, CollectedSource> snapshot = c.snapshot();

        assertThat(snapshot.keySet()).containsExactly("LOCAL1", "LOCAL2");
        CollectedSource first = snapshot.get("LOCAL1");
        assertThat(first.title()).isEqualTo("docs/a.pdf");
        assertThat(first.content()).isEqualTo("内容A");
        assertThat(first.baseScore()).isEqualTo(0.88);
    }

    @Test
    void contentWithQuotesSurvivesIntact() {
        // 根治性验证：含引号的标题/内容不再经过 LLM 复述，原文直达 Evidence
        EvidenceCollector c = new EvidenceCollector("WEB");
        String title = "兆芯：从\"信创刚需\"迈向\"市场优选\"";

        c.register("https://a.com", title, "https://a.com", "他说\"你好\"", "a.com", 0.0);

        CollectedSource src = c.snapshot().get("WEB1");
        assertThat(src.title()).isEqualTo(title);
        assertThat(src.content()).isEqualTo("他说\"你好\"");
    }

    @Test
    void snapshotIsIndependentCopy() {
        EvidenceCollector c = new EvidenceCollector("WEB");
        c.register("k1", "t", "u", "c", "d", 0.0);

        Map<String, CollectedSource> snap1 = c.snapshot();
        c.register("k2", "t2", "u2", "c2", "d2", 0.0);

        assertThat(snap1).hasSize(1);
        assertThat(c.snapshot()).hasSize(2);
        assertThat(List.copyOf(c.snapshot().keySet())).containsExactly("WEB1", "WEB2");
    }

    @Test
    void carriesTenantIdPerCollector() {
        // 租户 ID 随收集器实例隔离（替代曾导致跨租户泄漏的单例 volatile 字段）
        EvidenceCollector tenantA = new EvidenceCollector("LOCAL", "tenant_001");
        EvidenceCollector tenantB = new EvidenceCollector("LOCAL", "tenant_002");
        EvidenceCollector webNoTenant = new EvidenceCollector("WEB");

        assertThat(tenantA.tenantId()).isEqualTo("tenant_001");
        assertThat(tenantB.tenantId()).isEqualTo("tenant_002");
        assertThat(webNoTenant.tenantId()).isNull();
    }
}
