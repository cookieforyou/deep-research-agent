'use client';

import { useInfiniteQuery } from '@tanstack/react-query';
import { historyApi } from '@/lib/api';
import { useAuthStore } from '@/stores/auth-store';
import type { ResearchHistoryItem, PaginatedResponse, EvalResult } from '@/lib/types';

/**
 * 研究历史 Hook
 *
 * 一次拉取全部数据（分页自动累积），过滤/排序/分页全部在前端完成。
 * 不再因筛选条件变化触发后端 API 调用。
 */
export function useHistoryList() {
  const { userId, tenantId } = useAuthStore();

  return useInfiniteQuery<PaginatedResponse<ResearchHistoryItem>>({
    queryKey: ['history', userId, tenantId],
    queryFn: ({ pageParam = 0 }) =>
      historyApi.list({
        page: pageParam as number,
        size: 50,
        sortBy: 'createdAt',
        sortDir: 'desc',
      }),
    getNextPageParam: (lastPage) => (!lastPage.last ? lastPage.number + 1 : undefined),
    initialPageParam: 0,
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    retry: 1,
  });
}

/**
 * 从 evalScores JSON string 解析评估结果
 */
export function parseEvalScores(json: string | undefined): EvalResult | null {
  if (!json) return null;
  try {
    const parsed = JSON.parse(json) as Record<string, unknown>;
    return {
      relevance: (parsed.relevance as number) ?? 0,
      coherence: (parsed.coherence as number) ?? 0,
      citationAccuracy: (parsed.citationAccuracy as number) ?? 0,
      completeness: (parsed.completeness as number) ?? 0,
      conciseness: (parsed.conciseness as number) ?? 0,
      overallScore: (parsed.overallScore as number) ?? 0,
      summary: (parsed.summary as string) ?? '',
    };
  } catch {
    return null;
  }
}

/** 客户端过滤/排序参数 */
export interface ClientFilters {
  keyword?: string;
  status?: string;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
  startDate?: string;
  endDate?: string;
  minScore?: number;
}

/**
 * 对已拉取的 items 做客户端过滤 + 排序 + 分页。
 * 返回与 react-query 兼容的分页结构。
 */
export function applyClientFilters(
  items: ResearchHistoryItem[],
  filters: ClientFilters,
  page: number,
  size: number,
): { items: ResearchHistoryItem[]; total: number } {
  let filtered = [...items];

  // 关键词搜索
  if (filters.keyword) {
    const kw = filters.keyword.toLowerCase();
    filtered = filtered.filter((item) => item.query.toLowerCase().includes(kw));
  }

  // 状态筛选
  if (filters.status) {
    filtered = filtered.filter((item) => item.status === filters.status);
  }

  // 日期范围
  if (filters.startDate) {
    const start = new Date(filters.startDate).getTime();
    filtered = filtered.filter((item) => new Date(item.createdAt).getTime() >= start);
  }
  if (filters.endDate) {
    const end = new Date(filters.endDate).getTime() + 86400000; // 含当天
    filtered = filtered.filter((item) => new Date(item.createdAt).getTime() < end);
  }

  // 最低评分
  if (filters.minScore !== undefined && filters.minScore > 0) {
    filtered = filtered.filter((item) => {
      const scores = parseEvalScores(item.evalScores);
      return scores && scores.overallScore >= filters.minScore!;
    });
  }

  // 排序
  const dir = filters.sortDir === 'asc' ? 1 : -1;
  switch (filters.sortBy) {
    case 'wordCount':
      filtered.sort((a, b) => (a.wordCount - b.wordCount) * dir);
      break;
    case 'overallScore':
      filtered.sort((a, b) => {
        const sa = parseEvalScores(a.evalScores);
        const sb = parseEvalScores(b.evalScores);
        return ((sa?.overallScore ?? 0) - (sb?.overallScore ?? 0)) * dir;
      });
      break;
    case 'createdAt':
    default:
      filtered.sort((a, b) => (new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()) * dir);
      break;
  }

  // 分页切片
  const total = filtered.length;
  const start = page * size;
  const paged = filtered.slice(start, start + size);

  return { items: paged, total };
}
