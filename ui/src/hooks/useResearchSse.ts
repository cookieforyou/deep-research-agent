'use client';

import { useEffect, useRef, useCallback, useState } from 'react';
import { createSseConnection } from '@/lib/sse';
import { useSseStore } from '@/stores/sse-store';
import type { ProgressEvent, SseConnectionStatus } from '@/lib/types';

interface UseResearchSseReturn {
  /** 当前会话的全部 SSE 事件 */
  events: ProgressEvent[];
  /** 连接状态 */
  status: SseConnectionStatus;
  /** 手动重连 */
  connect: () => void;
  /** 断开连接 */
  disconnect: () => void;
  /** 获取最新一条指定阶段的事件 */
  getLatestByStage: (stage: string) => ProgressEvent | undefined;
  /** 是否研究已完成 */
  isCompleted: boolean;
  /** 是否发生错误 */
  hasError: boolean;
  /** 是否缓存命中 */
  isCacheHit: boolean;
}

/**
 * 研究 SSE 连接 Hook
 *
 * 在组件挂载时自动建立 SSE 连接，接收后端推送的进度事件。
 * 组件卸载时自动断开。
 *
 * @param sessionId 研究会话 ID
 */
export function useResearchSse(sessionId: string): UseResearchSseReturn {
  const [status, setStatus] = useState<SseConnectionStatus>('idle');
  const abortRef = useRef<{ abort: () => void } | null>(null);
  const reconnectAttemptRef = useRef(0);

  const addEvent = useSseStore((s) => s.addEvent);
  const setConnected = useSseStore((s) => s.setConnected);
  const setDisconnected = useSseStore((s) => s.setDisconnected);
  const events = useSseStore((s) => s.eventsMap[sessionId] || []);

  const connect = useCallback(() => {
    // 先断开已有连接
    abortRef.current?.abort();

    setStatus('connecting');

    const { abort } = createSseConnection(sessionId, {
      onEvent: (event) => {
        setStatus('connected');
        addEvent(sessionId, event);
      },

      onComplete: () => {
        setStatus('idle');
        setDisconnected(sessionId);
      },

      onError: (error) => {
        setStatus('error');
        setDisconnected(sessionId);
        console.error('[useResearchSse] SSE 致命错误:', error.message);
      },

      onReconnecting: (attempt, delayMs) => {
        setStatus('connecting');
        reconnectAttemptRef.current = attempt;
        console.log(`[useResearchSse] 重连 ${attempt}, ${delayMs}ms 后重试`);
      },
    });

    abortRef.current = { abort };
    setConnected(sessionId);
  }, [sessionId, addEvent, setConnected, setDisconnected]);

  const disconnect = useCallback(() => {
    abortRef.current?.abort();
    setStatus('idle');
    setDisconnected(sessionId);
  }, [sessionId, setDisconnected]);

  // 组件挂载时自动连接，卸载时断开
  useEffect(() => {
    connect();
    return () => {
      abortRef.current?.abort();
      setDisconnected(sessionId);
    };
  }, [connect, sessionId, setDisconnected]);

  const getLatestByStage = useCallback(
    (stage: string): ProgressEvent | undefined => {
      return [...events].reverse().find((e) => e.stage === stage);
    },
    [events],
  );

  const isCompleted = events.some((e) => e.stage === 'COMPLETED');
  const hasError = events.some((e) => e.stage === 'ERROR');
  const isCacheHit = events.some((e) => e.stage === 'CACHE_HIT');

  return {
    events,
    status,
    connect,
    disconnect,
    getLatestByStage,
    isCompleted,
    hasError,
    isCacheHit,
  };
}
