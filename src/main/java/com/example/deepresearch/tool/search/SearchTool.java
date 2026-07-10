package com.example.deepresearch.tool.search;

import com.example.deepresearch.common.model.SearchResult;

import java.util.List;

/**
 * 搜索工具抽象接口.
 * <p>
 * 定义统一的搜索契约，方便在不同搜索引擎之间切换
 * （Bocha Search ↔ Tavily ↔ Brave ↔ Bing），
 * 以及支持 A/B 测试和熔断降级。
 * </p>
 *
 * <h3>实现类</h3>
 * <ul>
 *   <li>{@code BochaSearchTool} — 博查 AI 搜索（主力）</li>
 *   <li>{@code FallbackSearchTool} — 降级备用搜索</li>
 * </ul>
 */
public interface SearchTool {

    /**
     * 执行单次搜索.
     *
     * @param query 搜索查询词
     * @param count 返回结果数量上限
     * @return 搜索结果列表（按相关性排序）
     */
    List<SearchResult> search(String query, int count);

    /**
     * 批量搜索（对多个查询并行执行）.
     * <p>
     * 默认实现串行执行，实现类可覆盖为并行。
     * </p>
     *
     * @param queries 查询词列表
     * @param count   每个查询返回结果数量
     * @return 所有搜索结果的合并列表
     */
    default List<SearchResult> batchSearch(List<String> queries, int count) {
        return queries.stream()
            .flatMap(q -> search(q, count).stream())
            .toList();
    }

    /**
     * 检查搜索服务是否可用.
     */
    default boolean isAvailable() {
        try {
            search("test", 1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取搜索引擎名称（用于日志和来源标记）.
     */
    String getEngineName();
}
