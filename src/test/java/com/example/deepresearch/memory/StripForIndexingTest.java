package com.example.deepresearch.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SemanticMemoryService 索引前清洗逻辑单元测试.
 * <p>
 * 覆盖 2026-07-16 发现的语义索引污染问题：
 * 「参考资料」链接列表和正文引用 URL 被向量化，稀释检索语义。
 * </p>
 */
class StripForIndexingTest {

    @Test
    void stripsReferenceSection() {
        String report = """
            # 报告标题

            ## 结论
            正文结论内容。

            ---

            ## 参考资料

            1. [标题一](https://a.com/1) — *a.com*
            2. [标题二](https://b.com/2) — *b.com*
            """;

        String cleaned = SemanticMemoryService.stripForIndexing(report);

        assertThat(cleaned).doesNotContain("参考资料");
        assertThat(cleaned).doesNotContain("https://a.com/1");
        assertThat(cleaned).doesNotContain("---");
        assertThat(cleaned).contains("正文结论内容。");
    }

    @Test
    void restoresLinkedCitationsToPlainMarkers() {
        String report = "市场占比约50%[[WEB1]](https://www.askci.com/news/x.shtml)，"
            + "多源数据支持[[WEB76]](https://a.com/y)[[LOCAL3]](docs/z.pdf)。";

        String cleaned = SemanticMemoryService.stripForIndexing(report);

        assertThat(cleaned).isEqualTo("市场占比约50%[WEB1]，多源数据支持[WEB76][LOCAL3]。");
    }

    @Test
    void leavesPlainMarkersAndNormalLinksUntouched()  {
        // 未 linkify 的裸标记 与 普通 Markdown 链接（非引用）不受影响
        String report = "裸标记[WEB5]保留。普通链接[官网](https://example.com)保留。";

        String cleaned = SemanticMemoryService.stripForIndexing(report);

        assertThat(cleaned).isEqualTo(report);
    }

    @Test
    void handlesReportWithoutReferenceSection() {
        String report = "# 标题\n\n只有正文，没有参考资料章节。";

        assertThat(SemanticMemoryService.stripForIndexing(report)).isEqualTo(report);
    }
}
