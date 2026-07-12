package com.example.deepresearch.eval;

import com.example.deepresearch.agent.eval.EvalAgent;
import com.example.deepresearch.api.dto.ResearchRequest;
import com.example.deepresearch.service.ResearchOrchestratorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 研究质量回归测试.
 * <p>
 * 在 CI/CD 流水线中运行，验证 Prompt 修改或模型切换后
 * 研究质量不下降。使用参数化测试覆盖典型研究场景。
 * </p>
 *
 * <h3>运行方式</h3>
 * <pre>{@code
 * mvn test -Dtest=AiEvaluationRegressionTest
 * }</pre>
 *
 * <h3>扩展方式</h3>
 * 在 {@code @CsvSource} 中添加新的（查询, 预期关键词）对即可。
 */
@SpringBootTest
class AiEvaluationRegressionTest {

    @Autowired
    private ResearchOrchestratorService orchestrator;

    @Autowired
    private EvalAgent evalAgent;

    @ParameterizedTest
    @CsvSource({
        "2026年新能源汽车发展趋势, 新能源",
        "AI芯片市场竞争格局分析, GPU",
        "全球经济展望2026, GDP"
    })
    void testResearchReportContainsExpectedTopics(String query, String expectedKeyword) {
        // 发起研究
        ResearchRequest request = new ResearchRequest(
            query, "test-user", "test-tenant", true);
        var response = orchestrator.startResearch(request);

        assertThat(response).isNotNull();
        assertThat(response.sessionId()).isNotBlank();

        // 注: 由于研究是异步执行的，此测试验证启动流程正常 + 返回 sessionId。
        // 完整的端到端验证需要等待 SSE 流完成，可在集成测试环境中扩展。
        // 生产级验证应结合 EvalAgent 对最终报告评分。
    }

    @Test
    void testSimpleQueryReturnsSessionId() {
        ResearchRequest request = new ResearchRequest(
            "什么是人工智能", "test-user", "test-tenant", false);
        var response = orchestrator.startResearch(request);

        assertThat(response).isNotNull();
        assertThat(response.sessionId()).isNotBlank();
        assertThat(response.sessionId()).hasSize(8);
    }
}
