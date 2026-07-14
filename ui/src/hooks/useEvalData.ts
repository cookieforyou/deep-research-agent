'use client';

import { useQuery } from '@tanstack/react-query';
import { historyApi } from '@/lib/api';
import { parseEvalScores } from './useHistoryList';
import type { EvalResult } from '@/lib/types';

/**
 * 异步获取评估数据
 *
 * 研究完成后，EvalAgent 异步执行评估（通常需要 10-30 秒）。
 * 此 Hook 轮询 GET /api/history/{sessionId}，检查 evalScores 是否已写入。
 *
 * 注意：依赖后端 B5.2 API。API 未实现时静默失败，返回 null。
 */
export function useEvalData(sessionId: string, enabled: boolean) {
  const { data } = useQuery({
    queryKey: ['eval', sessionId],
    queryFn: async () => {
      try {
        const detail = await historyApi.getDetail(sessionId);
        return parseEvalScores(detail.evalScores);
      } catch {
        // API 未实现时静默返回 null
        return null;
      }
    },
    enabled: !!sessionId && enabled,
    refetchInterval: (query) => {
      // 已获取到数据则停止轮询
      return query.state.data ? false : 5000;
    },
    staleTime: 0,
    retry: 0,
  });

  return data ?? null;
}

/**
 * 空状态检查：是否需要显示评估加载中
 */
export function getEvalDisplayState(
  evalResult: EvalResult | null | undefined,
  reportLoaded: boolean,
): 'loading' | 'ready' | 'empty' {
  if (!reportLoaded) return 'empty';
  if (evalResult) return 'ready';
  return 'loading';
}
