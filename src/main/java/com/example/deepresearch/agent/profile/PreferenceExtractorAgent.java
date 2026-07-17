package com.example.deepresearch.agent.profile;

import com.example.deepresearch.common.util.JsonParseUtils;
import com.example.deepresearch.common.util.PromptSplitUtils;
import com.example.deepresearch.common.util.PromptSplitUtils.PromptParts;
import com.example.deepresearch.service.DynamicPromptService;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 偏好提取 Agent — 从用户研究行为中提取稳定偏好，填充 L3 长期画像的 preferences 字段.
 * <p>
 * 使用 deepseek-v4-flash (T=0.1)，在研究完成后异步执行（fire-and-forget），
 * 失败不影响主流程。提取结果由 {@code LongTermMemoryService} 代码级合并写入
 * {@code user_profile.preferences}，下次研究经 {@code getUserProfileContext()}
 * 注入 Planner 上下文实现个性化。
 * </p>
 *
 * <h3>提取原则</h3>
 * <ul>
 *   <li>保守提取：仅在信号明确（重复主题/显式表达）时输出，无信号返回空 Map</li>
 *   <li>受限 key 集：language / focus_industries / region_focus / time_horizon /
 *       report_style / analysis_angle（见 preference-extractor.st）</li>
 *   <li>增量输出：LLM 只返回新增/变化的 key，合并语义由代码层保证</li>
 * </ul>
 */
@Service
public class PreferenceExtractorAgent {

    private static final Logger log = LoggerFactory.getLogger(PreferenceExtractorAgent.class);

    private final ChatClient chatClient;
    private final JsonParseUtils jsonUtils;
    private final DynamicPromptService dynamicPromptService;

    /** Fallback: 解析失败时返回空偏好（不写库） */
    private static final PreferenceResult FALLBACK = new PreferenceResult(Map.of());

    public PreferenceExtractorAgent(
        @Qualifier("preferenceExtractorClient") ChatClient chatClient,
        JsonParseUtils jsonUtils,
        DynamicPromptService dynamicPromptService
    ) {
        this.chatClient = chatClient;
        this.jsonUtils = jsonUtils;
        this.dynamicPromptService = dynamicPromptService;
    }

    /**
     * 从本次查询和历史主题中提取用户偏好.
     *
     * @param query               本次研究查询
     * @param recentTopics        最近研究主题（JSON 数组字符串，来自 UserProfile）
     * @param existingPreferences 已有偏好（JSON Map 字符串，来自 UserProfile）
     * @return 新增/变化的偏好键值对（无可提取偏好时为空 Map）
     */
    public Map<String, String> extract(String query, String recentTopics,
                                        String existingPreferences) {
        try {
            // 每次调用时加载模板（DynamicPromptService 内置 1min TTL 缓存）→ 支持 DB 热更新免重启
            PromptParts parts = PromptSplitUtils.split(
                dynamicPromptService.getTemplateContent("preference-extractor"));

            String raw = chatClient.prompt()
                .advisors(a -> a.param("agent", "PreferenceExtractor").param("tier", "flash"))
                .system(parts.system())
                .user(parts.user()
                    .replace("{{query}}", query != null ? query : "")
                    .replace("{{recentTopics}}",
                        recentTopics != null && !recentTopics.isBlank() ? recentTopics : "[]")
                    .replace("{{existingPreferences}}",
                        existingPreferences != null && !existingPreferences.isBlank()
                            ? existingPreferences : "{}"))
                .call()
                .content();

            PreferenceResult result = jsonUtils.safeParse(raw,
                PreferenceResult.class, FALLBACK, "PreferenceExtractor");
            Map<String, String> prefs = result.preferences() != null
                ? result.preferences() : Map.of();

            log.debug("[PreferenceExtractor] 提取完成: {} 个偏好", prefs.size());
            return prefs;

        } catch (Exception ex) {
            log.warn("[PreferenceExtractor] 偏好提取失败（不影响主流程）: {}", ex.getMessage());
            return Map.of();
        }
    }

    /** LLM 输出的偏好结果包装.
     *  注意：全局 ObjectMapper 为 SNAKE_CASE，须显式声明驼峰命名（项目 record 惯例）
     */
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record PreferenceResult(Map<String, String> preferences) {}
}
