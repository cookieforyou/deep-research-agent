'use client';

import { useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import { historyApi } from '@/lib/api';
import { parseEvalScores } from './useHistoryList';
import type { EvalResult } from '@/lib/types';

/**
 * 异步获取评估数据（仅深度研究有评估，直接回答无）。
 *
 * 研究完成后 EvalAgent 异步执行评估（通常 10-30 秒）。
 * 最多轮询 6 次（30 秒），超时后停止避免无限 history 404。
 *
 * @param sessionId      会话 ID
 * @param enabled        是否启用
 * @param maxAttempts    最大轮询次数（默认 6）
 */
export function useEvalData(
  sessionId: string,
  enabled: boolean,
  maxAttempts = 6,
) {
  const attemptRef = useRef(0);

  const { data } = useQuery<EvalResult | null>({
    queryKey: ['eval', sessionId],
    queryFn: async () => {
      try {
        const detail = await historyApi.getDetail(sessionId);
        const result = parseEvalScores(detail.evalScores);
        if (result) return result;
      } catch {
        // 404 — 研究未持久化或无评估数据
      }
      return null;
    },
    enabled: !!sessionId && enabled,
    refetchInterval: (query) => {
      if (query.state.data) return false; // 已获取到
      if (attemptRef.current >= maxAttempts) return false; // 超时
      attemptRef.current += 1;
      return 5000;
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
