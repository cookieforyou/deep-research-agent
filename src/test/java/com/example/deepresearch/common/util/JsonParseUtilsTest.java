package com.example.deepresearch.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JsonParseUtils 修复规则单元测试.
 * <p>
 * 重点覆盖"字符串值内部未转义双引号"修复
 * （2026-07-15 WebScout 生产事故：搜索结果标题含 ASCII 引号导致证据池全空）。
 * </p>
 */
class JsonParseUtilsTest {

    private final JsonParseUtils jsonUtils = new JsonParseUtils(new ObjectMapper());

    /** 供解析的目标类型（模拟 Scout 的 EvidenceListWrapper 结构） */
    record Wrapper(List<Item> evidences) {}
    record Item(String sourceId, String title, String url) {}

    private static final Wrapper FALLBACK = new Wrapper(List.of());

    @Test
    void repairsUnescapedInnerQuotes_realProductionCase() {
        // 复现 research.log 12:05:16 的真实失败样例：title 值内含未转义 ASCII 引号
        String raw = """
            {
              "evidences": [
                {
                  "sourceId": "WEB01_1",
                  "title": "兆芯：从"信创刚需"迈向"市场优选"",
                  "url": "https://www.askci.com/news/chanye/20260123/xxx.shtml"
                }
              ]
            }
            """;

        Wrapper result = jsonUtils.safeParse(raw, Wrapper.class, FALLBACK, "test");

        assertThat(result.evidences()).hasSize(1);
        assertThat(result.evidences().getFirst().title())
            .isEqualTo("兆芯：从\"信创刚需\"迈向\"市场优选\"");
        assertThat(result.evidences().getFirst().sourceId()).isEqualTo("WEB01_1");
    }

    @Test
    void skipsMultiFieldLine_avoidsCorruptingValidFields() {
        // 一行多字段（含合法引号边界）不应被误转义；另一行的坏引号仍应修复
        String raw = """
            {
              "sourceId": "W1", "title": "同行双字段",
              "note": "他说"你好"然后离开",
            }
            """;

        @SuppressWarnings("unchecked")
        Map<String, Object> result = jsonUtils.safeParse(raw, Map.class, Map.of(), "test");

        assertThat(result)
            .containsEntry("sourceId", "W1")
            .containsEntry("title", "同行双字段")
            .containsEntry("note", "他说\"你好\"然后离开");
    }

    @Test
    void stillRepairsTrailingCommaAndMarkdownFence() {
        String raw = """
            ```json
            {
              "evidences": [
                {
                  "sourceId": "W1",
                  "title": "正常标题",
                  "url": "https://e.com",
                },
              ]
            }
            ```
            """;

        Wrapper result = jsonUtils.safeParse(raw, Wrapper.class, FALLBACK, "test");

        assertThat(result.evidences()).hasSize(1);
        assertThat(result.evidences().getFirst().title()).isEqualTo("正常标题");
    }

    @Test
    void stillClosesTruncatedJson() {
        // 模拟 token 截断：末尾条目被截断（extractJson 截取到最后一个 '}'，剩余结构未闭合）
        String raw = """
            {
              "evidences": [
                {"sourceId": "W1", "title": "ok", "url": "https://e.com"},
                {"sourceId": "W2", "title": "被截断
            """;

        Wrapper result = jsonUtils.safeParse(raw, Wrapper.class, FALLBACK, "test");

        assertThat(result.evidences()).hasSize(1);
        assertThat(result.evidences().getFirst().sourceId()).isEqualTo("W1");
    }

    @Test
    void returnsFallbackOnGarbage() {
        Wrapper result = jsonUtils.safeParse("完全不是 JSON 的内容", Wrapper.class, FALLBACK, "test");
        assertThat(result.evidences()).isEmpty();
    }

    @Test
    void validJsonUntouched() {
        String raw = """
            {"evidences": [{"sourceId": "W1", "title": "ok", "url": "https://e.com"}]}
            """;

        Wrapper result = jsonUtils.safeParse(raw, Wrapper.class, FALLBACK, "test");

        assertThat(result.evidences()).hasSize(1);
        assertThat(result.evidences().getFirst().title()).isEqualTo("ok");
    }
}
