package com.example.deepresearch.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.stereotype.Component;

/**
 * 审计日志 Advisor — 记录每次 ChatClient 调用的请求摘要和响应结果.
 * <p>
 * 在 Advisor 链的最外层执行（order=100，最早注册最后执行），
 * 确保完整的请求-响应周期被记录。
 * 日志仅记录摘要信息，不记录完整的 prompt 或 response 内容以防止敏感数据泄露。
 * </p>
 */
@Component
public class AuditLogAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAdvisor.class);
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long startTime = System.currentTimeMillis();
        // 优先从 TenantContext 获取（跨虚拟线程传播），advisor context 作为 fallback
        String userId = TenantContext.getCurrentUser();
        if (userId == null) {
            userId = (String) request.context().getOrDefault("user_id", "anonymous");
        }
        String agent = (String) request.context().getOrDefault("agent", "unknown");

        // 执行调用链
        ChatClientResponse response = chain.nextCall(request);

        long elapsed = System.currentTimeMillis() - startTime;
        String status = response.chatResponse() != null ? "success" : "empty";
        AUDIT_LOG.info("[AUDIT] agent={}, userId={}, status={}, latency={}ms",
            agent, userId, status, elapsed);

        return response;
    }

    @Override
    public String getName() {
        return "AuditLogAdvisor";
    }

    @Override
    public int getOrder() {
        return 100;  // 最外层，最早注册最后执行
    }
}
