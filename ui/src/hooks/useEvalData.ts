'use client';

import { useState, useEffect, useRef } from 'react';
import { historyApi } from '@/lib/api';
import { parseEvalScores } from './useHistoryList';
import type { EvalResult } from '@/lib/types';

/**
 * 异步获取评估数据（仅深度研究有评估，直接回答无）。
 *
 * 使用手动 setInterval 轮询 history API，不依赖 React Query 的 refetchInterval
 * （refetchInterval 在频繁 re-render 下行为不可预测）。
 * 最多轮询 60 秒，评估获取到后立即停止。
 */
export function useEvalData(
  sessionId: string,
  enabled: boolean,
  maxDurationMs = 60_000,
) {
  const [data, setData] = useState<EvalResult | null>(null);
  const startRef = useRef<number | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!enabled) return;

    // 重置
    setData(null);
    startRef.current = Date.now();

    const poll = async () => {
      if (Date.now() - (startRef.current ?? 0) >= maxDurationMs) {
        if (timerRef.current) clearInterval(timerRef.current);
        return;
      }

      try {
        const detail = await historyApi.getDetail(sessionId);
        const result = parseEvalScores(detail.evalScores);
        if (result) {
          setData(result);
          if (timerRef.current) clearInterval(timerRef.current);
        }
      } catch {
        // 404 — not persisted yet, keep polling
      }
    };

    // 首次立即查询
    poll();
    // 之后每 5 秒轮询
    timerRef.current = setInterval(poll, 5000);

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [enabled, sessionId]);

  return data;
}

export function getEvalDisplayState(
  evalResult: EvalResult | null | undefined,
  reportLoaded: boolean,
): 'loading' | 'ready' | 'empty' {
  if (!reportLoaded) return 'empty';
  if (evalResult) return 'ready';
  return 'loading';
}
