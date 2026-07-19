'use client';

import { useState, useEffect, useRef } from 'react';
import { historyApi } from '@/lib/api';
import { parseEvalScores } from './useHistoryList';
import type { EvalResult } from '@/lib/types';

/** 报告完成后超过此时间仍无评估分数，视为评估不会生成 */
const EVAL_STALE_MS = 120_000; // 2 分钟

/**
 * 异步获取评估数据（仅深度研究有评估，直接回答无）。
 *
 * 使用手动 setInterval 轮询 history API。
 *
 * 智能停止策略：
 * - 获得有效 evalScores → 立即停止
 * - 报告 createdAt 距今 > 2 分钟且无 evalScores → 认定评估不会生成，立即停止
 * - 超过 maxDurationMs → 兜底停止
 */
export function useEvalData(
  sessionId: string,
  enabled: boolean,
  maxDurationMs = 60_000,
) {
  const [data, setData] = useState<EvalResult | null>(null);
  /** 评估已确定不会生成（历史报告无评分），用于 UI 区分"评估中"和"无评估" */
  const [unavailable, setUnavailable] = useState(false);
  const startRef = useRef<number | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!enabled) return;

    // 重置
    setData(null);
    setUnavailable(false);
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
        } else if (detail.createdAt) {
          // 报告完成超过 2 分钟仍无评估 → 认定评估不会生成
          const age = Date.now() - new Date(detail.createdAt).getTime();
          if (age > EVAL_STALE_MS) {
            setUnavailable(true);
            if (timerRef.current) clearInterval(timerRef.current);
          }
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

  return { data, unavailable };
}

export function getEvalDisplayState(
  evalResult: EvalResult | null | undefined,
  reportLoaded: boolean,
  unavailable?: boolean,
): 'loading' | 'ready' | 'empty' {
  if (!reportLoaded) return 'empty';
  if (evalResult) return 'ready';
  if (unavailable) return 'empty';
  return 'loading';
}
