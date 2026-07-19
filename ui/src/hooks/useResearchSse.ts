'use client';

import { useEffect, useRef, useCallback, useState } from 'react';
import { createSseConnection } from '@/lib/sse';
import { useSseStore } from '@/stores/sse-store';
import type { ProgressEvent, SseConnectionStatus } from '@/lib/types';

const EMPTY_EVENTS: ProgressEvent[] = [];

interface UseResearchSseReturn {
  events: ProgressEvent[];
  status: SseConnectionStatus;
  connect: () => void;
  disconnect: () => void;
  isCompleted: boolean;
  hasError: boolean;
  isCacheHit: boolean;
  /** SSE 连接后超时未收到事件，需降级轮询 */
  timedOut: boolean;
}

/**
 * 研究 SSE 连接 Hook.
 *
 * 组件挂载时自动建立 SSE 连接，卸载时断开。
 *
 * 断线恢复机制（三层防护）：
 * 1. 后端 replay().limit(100) — 重连时回放全部历史事件
 * 2. 自动重连时清除已有事件 — 避免 replay 重复，由回放重建时间线
 * 3. 超时降级 — 连接后 5 秒无事件，通知上层切换轮询兜底
 */
export function useResearchSse(sessionId: string): UseResearchSseReturn {
  const [status, setStatus] = useState<SseConnectionStatus>('idle');
  const [timedOut, setTimedOut] = useState(false);
  const connRef = useRef<{ abort: () => void } | null>(null);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const addEvent = useSseStore((s) => s.addEvent);
  const setConnected = useSseStore((s) => s.setConnected);
  const setDisconnected = useSseStore((s) => s.setDisconnected);
  const clearEvents = useSseStore((s) => s.clearEvents);
  const events = useSseStore((s) => s.eventsMap[sessionId] || EMPTY_EVENTS);

  const clearTimeoutTimer = useCallback(() => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }
  }, []);

  const startTimeoutTimer = useCallback(() => {
    clearTimeoutTimer();
    timeoutRef.current = setTimeout(() => {
      console.warn('[useResearchSse] SSE 超时无事件，触发轮询降级');
      setTimedOut(true);
    }, 5000);
  }, [clearTimeoutTimer]);

  const doConnect = useCallback(() => {
    // 断开旧连接
    connRef.current?.abort();
    setStatus('connecting');
    setTimedOut(false);
    startTimeoutTimer();

    const conn = createSseConnection(sessionId, {
      onEvent: (event) => {
        setStatus('connected');
        clearTimeoutTimer();
        addEvent(sessionId, event);
      },
      onComplete: () => {
        setStatus('idle');
        setDisconnected(sessionId);
        clearTimeoutTimer();
      },
      onError: (error) => {
        setStatus('error');
        setDisconnected(sessionId);
        clearTimeoutTimer();
        console.error('[useResearchSse] SSE 致命错误:', error.message);
      },
      onReconnecting: (attempt, delayMs) => {
        setStatus('connecting');
        // 清除已有事件，由后端 replay buffer 回放重建，避免重复
        clearEvents(sessionId);
        console.log(`[useResearchSse] 重连 ${attempt}/${delayMs}ms，已清除事件等待回放`);
      },
    });

    connRef.current = conn;
    setConnected(sessionId);
  }, [sessionId, addEvent, setConnected, setDisconnected, clearEvents, startTimeoutTimer, clearTimeoutTimer]);

  const disconnect = useCallback(() => {
    connRef.current?.abort();
    connRef.current = null;
    setStatus('idle');
    setDisconnected(sessionId);
    clearTimeoutTimer();
  }, [sessionId, setDisconnected, clearTimeoutTimer]);

  useEffect(() => {
    doConnect();
    return () => {
      connRef.current?.abort();
      connRef.current = null;
      clearTimeoutTimer();
    };
  }, [doConnect, clearTimeoutTimer]);

  const isCompleted = events.some((e) => e.stage === 'COMPLETED');
  const hasError = events.some((e) => e.stage === 'ERROR');
  const isCacheHit = events.some((e) => e.stage === 'CACHE_HIT');

  return { events, status, connect: doConnect, disconnect, isCompleted, hasError, isCacheHit, timedOut };
}
