package com.example.deepresearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * DeepResearch Multi-Agent 行业深度研究助手 — Spring Boot 主入口.
 * <p>
 * 技术栈: Spring Boot 4.1.0 + Spring AI 2.0.0 + DeepSeek V4 + LangGraph4j.
 * 核心能力: 意图路由 → 任务规划 → 双源并行检索 → 证据裁判 → 分析归纳 → 反思补搜 → 报告撰写.
 * </p>
 *
 * <h3>架构要点</h3>
 * <ul>
 *   <li><b>模型分层</b>: 核心 Agent 使用 deepseek-v4-pro，工具 Agent 使用 deepseek-v4-flash</li>
 *   <li><b>并发模型</b>: Java 21 Virtual Threads + WebFlux 响应式</li>
 *   <li><b>工作流编排</b>: LangGraph4j StateGraph（带条件分支和循环）</li>
 *   <li><b>流式推送</b>: SSE 细粒度进度推送（按 Agent 阶段）</li>
 * </ul>
 *
 * @author DeepResearch Team
 * @see <a href="https://spring.io/projects/spring-ai">Spring AI 2.0</a>
 * @see <a href="https://github.com/langgraph4j/langgraph4j">LangGraph4j</a>
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.example.deepresearch.common.config")
public class DeepResearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeepResearchApplication.class, args);
    }
}
