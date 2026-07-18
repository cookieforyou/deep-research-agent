'use client';

import { useEffect, useRef, useCallback, useState } from 'react';
import { createSseConnection } from '@/lib/sse';
import { useSseStore } from '@/stores/sse-store';
import type { ProgressEvent, SseConnectionStatus } from '@/lib/types';

/** 稳定空数组引用，避免 zustand selector 每次返回新引用触发无限重渲染 */
const EMPTY_EVENTS: ProgressEvent[] = [];

interface UseResearchSseReturn {
  /** 当前会话的全部 SSE 事件 */
  events: ProgressEvent[];
  /** 连接状态 */
  status: SseConnectionStatus;
  /** 手动重连 */
  connect: () => void;
  /** 断开连接 */
  disconnect: () => void;
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
 * @param sessionId 研究会话 ID
 * @param enabled   是否启用 SSE（默认 true）。历史记录查看时应传 false。
 */
export function useResearchSse(
  sessionId: string,
  enabled = true,
): UseResearchSseReturn {
  const [status, setStatus] = useState<SseConnectionStatus>('idle');
  const abortRef = useRef<{ abort: () => void } | null>(null);

  const addEvent = useSseStore((s) => s.addEvent);
  const setConnected = useSseStore((s) => s.setConnected);
  const setDisconnected = useSseStore((s) => s.setDisconnected);
  const events = useSseStore((s) => s.eventsMap[sessionId] || EMPTY_EVENTS);

  const connect = useCallback(() => {
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

  // 组件挂载时自动连接（enabled=false 时跳过），卸载时断开
  useEffect(() => {
    if (!enabled) return;
    connect();
    return () => {
      abortRef.current?.abort();
      setDisconnected(sessionId);
    };
  }, [enabled, connect, sessionId, setDisconnected]);

  const isCompleted = events.some((e) => e.stage === 'COMPLETED');
  const hasError = events.some((e) => e.stage === 'ERROR');
  const isCacheHit = events.some((e) => e.stage === 'CACHE_HIT');

  return {
    events,
    status,
    connect,
    disconnect,
    isCompleted,
    hasError,
    isCacheHit,
  };
}
