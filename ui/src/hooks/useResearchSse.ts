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
}

/**
 * 研究 SSE 连接 Hook。
 *
 * 使用原生 fetch() + ReadableStream 解析 SSE 流，
 * 组件挂载时自动连接，卸载时断开。
 */
export function useResearchSse(sessionId: string): UseResearchSseReturn {
  const [status, setStatus] = useState<SseConnectionStatus>('idle');
  const connRef = useRef<{ abort: () => void } | null>(null);

  const addEvent = useSseStore((s) => s.addEvent);
  const setConnected = useSseStore((s) => s.setConnected);
  const setDisconnected = useSseStore((s) => s.setDisconnected);
  const events = useSseStore((s) => s.eventsMap[sessionId] || EMPTY_EVENTS);

  const doConnect = useCallback(() => {
    // 断开旧连接
    connRef.current?.abort();
    setStatus('connecting');

    const conn = createSseConnection(sessionId, {
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

    connRef.current = conn;
    setConnected(sessionId);
  }, [sessionId, addEvent, setConnected, setDisconnected]);

  const disconnect = useCallback(() => {
    connRef.current?.abort();
    connRef.current = null;
    setStatus('idle');
    setDisconnected(sessionId);
  }, [sessionId, setDisconnected]);

  useEffect(() => {
    doConnect();
    return () => {
      connRef.current?.abort();
      connRef.current = null;
    };
  }, [doConnect]);

  const isCompleted = events.some((e) => e.stage === 'COMPLETED');
  const hasError = events.some((e) => e.stage === 'ERROR');
  const isCacheHit = events.some((e) => e.stage === 'CACHE_HIT');

  return { events, status, connect: doConnect, disconnect, isCompleted, hasError, isCacheHit };
}
