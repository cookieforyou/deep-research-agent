'use client';

import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { researchApi } from '@/lib/api';
import { useAuthStore } from '@/stores/auth-store';
import { ApiError } from '@/lib/types';
import { toast } from 'sonner';

interface StartResearchInput {
  query: string;
  deepResearch: boolean;
}

/**
 * 发起研究的 TanStack Mutation Hook
 *
 * 调用 POST /api/research → 获取 sessionId → 跳转到研究详情页
 *
 * 错误处理（对齐后端 GlobalExceptionHandler）:
 *   - 400: Prompt 注入检测 → "请求被拒绝，请修改查询内容后重试"
 *   - 429: 配额超限 → "调用配额已用完，请稍后再试"
 *   - 其他: 显示后端返回的 detail 消息
 *   - 网络错误: "网络错误，请检查连接后重试"
 */
export function useStartResearch() {
  const router = useRouter();
  const { userId, tenantId } = useAuthStore();

  return useMutation({
    mutationFn: (input: StartResearchInput) =>
      researchApi.startResearch({
        query: input.query,
        userId,
        tenantId,
        deepResearch: input.deepResearch,
      }),

    onSuccess: (data) => {
      // 跳转到研究详情页，SSE 连接由详情页的 useResearchSse 自动建立
      router.push(`/research/${data.sessionId}`);
    },

    onError: (error: Error) => {
      if (error instanceof ApiError) {
        switch (error.status) {
          case 400:
            toast.error('请求被拒绝，请修改查询内容后重试');
            break;
          case 429:
            toast.error('调用配额已用完，请稍后再试');
            break;
          case 500:
            toast.error(error.detail.detail || '服务器内部错误，请稍后重试');
            break;
          default:
            toast.error(error.message);
        }
      } else if (error.message?.includes('fetch')) {
        toast.error('网络错误，请检查连接后重试');
      } else {
        toast.error('发生未知错误，请稍后重试');
      }
    },
  });
}
