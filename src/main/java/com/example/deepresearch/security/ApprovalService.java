package com.example.deepresearch.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 人工审批服务 — Human-in-the-Loop 写操作审批.
 * <p>
 * 对敏感操作（如取消订单、修改数据、发布报告等写操作）提供审批流程支持。
 * 当前项目以只读研究为主，此类作为架构预留接口供未来扩展。
 * 配合 {@code @Tool} 工具的 {@code ToolContext} 实现运行时审批判断。
 * </p>
 *
 * <h3>使用方式（未来扩展）</h3>
 * <pre>{@code
 * @Tool(description = "取消指定订单")
 * public String cancelOrder(String orderId, ToolContext context) {
 *     if (Boolean.TRUE.equals(context.getContext().get("requires_approval"))) {
 *         return approvalService.submitForApproval("CANCEL_ORDER", orderId);
 *     }
 *     orderRepo.cancel(orderId);
 *     return "订单 " + orderId + " 已取消";
 * }
 * }</pre>
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    /**
     * 提交写操作审批请求.
     *
     * @param action  操作类型标识（如 "CANCEL_ORDER", "PUBLISH_REPORT"）
     * @param details 操作详情
     * @return 审批提示信息
     */
    public String submitForApproval(String action, String details) {
        log.info("[Approval] 审批请求已提交: action={}, details={}", action, details);
        return String.format("ACTION_REQUIRED: 操作 [%s] 需要审批，已提交审批流。详情: %s", action, details);
    }
}
