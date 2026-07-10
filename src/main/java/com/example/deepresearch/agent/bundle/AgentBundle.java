package com.example.deepresearch.agent.bundle;

import com.example.deepresearch.common.observability.TokenTrackingAdvisor;
import com.example.deepresearch.security.PiiMaskingAdvisor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 集合配置 — 两层模型的 ChatClient Bean 装配.
 * <p>
 * 基于 DeepSeek V4 的两层模型策略：
 * <ul>
 *   <li><b>Core 层</b> ({@code DEEPSEEK_V4_PRO}): 用于 Planner、Analyst、Writer、Reflect，
 *       需要强推理和创造力，temperature 0.3~0.4</li>
 *   <li><b>Tool 层</b> ({@code DEEPSEEK_V4_FLASH}): 用于 IntentRouter、WebScout、LocalScout、
 *       EvidenceJudge，任务相对机械，速度快、成本低，temperature 0.0~0.4</li>
 * </ul>
 * </p>
 *
 * <h3>Spring AI 2.0 API 说明</h3>
 * <p>
 * {@link ChatClient.Builder#defaultOptions} 接受 {@code ChatOptions.Builder}（未 build 的状态），
 * ChatClient 内部会调用 {@code .build()}。因此传入时<strong>不要</strong>调用 {@code .build()}。
 * </p>
 *
 * <h3>每个 Agent 的 ChatClient 配置</h3>
 * <table>
 *   <tr><th>Agent</th><th>层级</th><th>Temperature</th><th>理由</th></tr>
 *   <tr><td>IntentRouter</td><td>Flash</td><td>0.0</td><td>路由判断需绝对确定</td></tr>
 *   <tr><td>Planner</td><td>Pro</td><td>0.3</td><td>需创意但不能发散</td></tr>
 *   <tr><td>WebScout</td><td>Flash</td><td>0.4</td><td>平衡覆盖率与相关性</td></tr>
 *   <tr><td>LocalScout</td><td>Flash</td><td>0.4</td><td>同上</td></tr>
 *   <tr><td>EvidenceJudge</td><td>Flash</td><td>0.2</td><td>评分需严格标准</td></tr>
 *   <tr><td>Analyst</td><td>Pro</td><td>0.3</td><td>逻辑严谨</td></tr>
 *   <tr><td>Reflect</td><td>Pro</td><td>0.3</td><td>需要针对性强</td></tr>
 *   <tr><td>Writer</td><td>Pro</td><td>0.4</td><td>需文采和流畅度</td></tr>
 *   <tr><td>Eval</td><td>Flash</td><td>0.05</td><td>评估需极低随机性</td></tr>
 * </table>
 */
@Configuration
public class AgentBundle {

    private static final Logger log = LoggerFactory.getLogger(AgentBundle.class);

    private final PiiMaskingAdvisor piiMaskingAdvisor;
    private final TokenTrackingAdvisor tokenTrackingAdvisor;

    public AgentBundle(PiiMaskingAdvisor piiMaskingAdvisor,
                       TokenTrackingAdvisor tokenTrackingAdvisor) {
        this.piiMaskingAdvisor = piiMaskingAdvisor;
        this.tokenTrackingAdvisor = tokenTrackingAdvisor;
    }

    // =========================== ChatClient Bean (每个 Agent 一个) ===========================
    //
    // 所有 ChatClient 共享同一个自动配置的 DeepSeekChatModel，
    // 通过 defaultOptions() 为每个 Agent 设置不同的 model 和 temperature。
    // 这确保: (1) API 层面模型正确切换 (2) 成本/延迟独立优化 (3) 配置简洁。
    //
    // PiiMaskingAdvisor 通过 defaultAdvisors() 注册到所有 ChatClient，
    // 在每次 LLM 调用前透明脱敏 PII。

    /** 创建带 PiiMasking + TokenTracking Advisor 的 ChatClient.Builder */
    private ChatClient.Builder createBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
            .defaultAdvisors(piiMaskingAdvisor, tokenTrackingAdvisor);
    }

    // --- Pro 层 Agent (deepseek-v4-pro) ---

    /** Planner — 任务拆解、大纲与搜索计划生成 (T=0.3, maxTokens=16384 支持更多查询) */
    @Bean("plannerClient")
    public ChatClient plannerClient(ChatModel chatModel) {
        log.info("注册 Planner ChatClient (Pro, T=0.3)");
        return createBuilder(chatModel)
            .defaultOptions(DeepSeekChatOptions.builder()
                .model(DeepSeekApi.ChatModel.DEEPSEEK_V4_PRO)
                .temperature(0.3)
                .maxTokens(16384))
            .build();
    }

    /** Analyst — 形成结论、评估完备性、识别缺口 (T=0.2, Flash 提速) */
    @Bean("analystClient")
    public ChatClient analystClient(ChatModel chatModel) {
        log.info("注册 Analyst ChatClient (Flash, T=0.2)");
        return createBuilder(chatModel)
            .defaultOptions(DeepSeekChatOptions.builder()
                .model(DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH)
                .temperature(0.2)
                .maxTokens(8192))
            .build();
    }

    /** Writer — 撰写 1500-2000 字深度研报、合法引用 (T=0.4, Pro 保证质量).
     *  <p>Flash 模型无法可靠生成结构化长报告（曾出现输出为空），回退到 Pro。</p> */
    @Bean("writerClient")
    public ChatClient writerClient(ChatModel chatModel) {
        log.info("注册 Writer ChatClient (Pro, T=0.4)");
        return createBuilder(chatModel)
            .defaultOptions(DeepSeekChatOptions.builder()
                .model(DeepSeekApi.ChatModel.DEEPSEEK_V4_PRO)
                .temperature(0.4)
                .maxTokens(8192))
            .build();
    }

    // --- Fallback ChatClient Bean（Pro 降级 Flash，温度与 Pro 一致） ---

    /** Planner 降级 — Flash 模型 (T=0.3，与 Pro 保持相同温度) */
    @Bean("plannerFallbackClient")
    public ChatClient plannerFallbackClient(ChatModel chatModel) {
        log.info("注册 Planner Fallback ChatClient (Flash, T=0.3)");
        return createBuilder(chatModel)
            .defaultOptions(DeepSeekChatOptions.builder()
                .model(DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH)
                .temperature(0.3)
                .maxTokens(16384))
            .build();
    }

    /** Writer 降级 — Flash 模型 (T=0.4，与 Pro 保持相同温度) */
    @Bean("writerFallbackClient")
    public ChatClient writerFallbackClient(ChatModel chatModel) {
        log.info("注册 Writer Fallback ChatClient (Flash, T=0.4)");
        return createBuilder(chatModel)
            .defaultOptions(DeepSeekChatOptions.builder()
                .model(DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH)
                .temperature(0.4)
                .maxTokens(8192))
            .build();
    }

    // --- Flash 层 Agent (deepseek-v4-flash) ---

    /** IntentRouter — 路由分发，判断 Direct 还是 Research (T=0.0，绝对确定) */
    @Bean("intentRouterClient")
    public ChatClient intentRouterClient(ChatModel chatModel) {
        log.info("注册 IntentRouter ChatClient (Flash, T=0.0)");
        return createBuilder(chatModel)
            .defaultOptions(DeepSeekChatOptions.builder()
                .model(DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH)
                .temperature(0.0)
                .maxTokens(1024))
            .build();
    }

    /** WebScout — 网络取证、相关性过滤、SourceID 分配 (T=0.4) */
    @Bean("webScoutClient")
    public ChatClient webScoutClient(ChatModel chatModel) {
        log.info("注册 WebScout ChatClient (Flash, T=0.4)");
        return createBuilder(chatModel)
            .defaultOptions(DeepSeekChatOptions.builder()
                .model(DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH)
                .temperature(0.4)
                .maxTokens(4096))
            .build();
    }

    /** LocalScout — 本地知识库取证、相关性过滤 (T=0.4) */
    @Bean("localScoutClient")
    public ChatClient localScoutClient(ChatModel chatModel) {
        log.info("注册 LocalScout ChatClient (Flash, T=0.4)");
        return createBuilder(chatModel)
            .defaultOptions(DeepSeekChatOptions.builder()
                .model(DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH)
                .temperature(0.4)
                .maxTokens(4096))
            .build();
    }

    /** EvalAgent — 报告质量评估 (Flash, T=0.05，评估需极低随机性；T=0.0 在部分模型上可能不稳定) */
    @Bean("evalClient")
    public ChatClient evalClient(ChatModel chatModel) {
        log.info("注册 EvalAgent ChatClient (Flash, T=0.05)");
        return createBuilder(chatModel)
            .defaultOptions(DeepSeekChatOptions.builder()
                .model(DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH)
                .temperature(0.05)
                .maxTokens(4096))
            .build();
    }

}
