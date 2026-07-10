package com.example.deepresearch.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 虚拟线程配置.
 * <p>
 * Java 21+ 虚拟线程（Project Loom）专为 I/O 密集型任务设计，
 * 非常适合本项目中大量并行的 LLM API 调用和搜索请求场景。
 * Spring Boot 4.1 默认启用虚拟线程，这里显式注册 ExecutorService Bean
 * 供 {@code CompletableFuture.supplyAsync()} 等方法使用。
 * </p>
 *
 * <h3>为什么用虚拟线程而不是传统线程池</h3>
 * <ul>
 *   <li>虚拟线程极其轻量（~1KB），可同时运行数万个</li>
 *   <li>I/O 阻塞时自动让出载体线程，不浪费资源</li>
 *   <li>代码保持同步风格，降低异步编程复杂度</li>
 * </ul>
 */
@Configuration
public class VirtualThreadConfig {

    /**
     * 虚拟线程执行器.
     * <p>
     * 每次调用 {@code newVirtualThreadPerTaskExecutor()} 返回新实例，
     * 每个任务创建一个新虚拟线程，任务完成后线程销毁。
     * 适合任务生命周期独立的场景（如单次检索请求）。
     * </p>
     */
    @Bean
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
