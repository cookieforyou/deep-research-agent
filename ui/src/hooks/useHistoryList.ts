'use client';

import { useInfiniteQuery } from '@tanstack/react-query';
import { historyApi } from '@/lib/api';
import { useAuthStore } from '@/stores/auth-store';
import type { ResearchHistoryItem, PaginatedResponse } from '@/lib/types';

interface HistoryFilters {
  keyword?: string;
  status?: string;
  sortBy?: string;
  sortDir?: string;
}

/**
 * 研究历史分页查询 Hook
 *
 * 使用 TanStack useInfiniteQuery 实现无限滚动加载。
 * 当前调用后端 /api/history 端点（Phase 5+ 需要后端实现）。
 * 后端未就绪时：返回空数据（不报错），页面显示空状态。
 */
export function useHistoryList(filters: HistoryFilters = {}) {
  const { userId, tenantId } = useAuthStore();

  return useInfiniteQuery<PaginatedResponse<ResearchHistoryItem>>({
    queryKey: ['history', userId, tenantId, filters],
    queryFn: ({ pageParam = 0 }) =>
      historyApi.list({
        userId,
        tenantId,
        page: pageParam as number,
        size: 20,
        ...filters,
      }),
    getNextPageParam: (lastPage) => (lastPage.hasNext ? lastPage.page + 1 : undefined),
    initialPageParam: 0,
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    retry: 1,
  });
}

/**
 * 从 evalScores JSON string 解析评估结果
 */
export function parseEvalScores(json: string | undefined): import('@/lib/types').EvalResult | null {
  if (!json) return null;
  try {
    return JSON.parse(json) as import('@/lib/types').EvalResult;
  } catch {
    return null;
  }
}
