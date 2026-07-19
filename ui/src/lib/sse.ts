/**
 * SSE (Server-Sent Events) 客户端封装
 *
 * 使用 @microsoft/fetch-event-source 处理 SSE 解析和重连，
 * 直连后端 :8080（绕过 Next.js 代理），JWT 通过 query param 传递避免 CORS preflight。
 */
import { fetchEventSource } from '@microsoft/fetch-event-source';
import type { ProgressEvent } from './types';
import { getToken } from './jwt';
import { SSE_RECONNECT_DELAYS, SSE_MAX_RECONNECT } from './constants';

interface SseCallbacks {
  onEvent: (event: ProgressEvent) => void;
  onComplete: () => void;
  onError: (error: Error) => void;
  onReconnecting?: (attempt: number, delayMs: number) => void;
}

interface SseConnection {
  abort: () => void;
}

export function createSseConnection(
  sessionId: string,
  callbacks: SseCallbacks,
): SseConnection {
  const controller = new AbortController();
  let reconnectCount = 0;
  let completed = false;

  function connect() {
    const token = getToken();
    const backend = process.env.NEXT_PUBLIC_BACKEND_URL || 'http://localhost:8080';
    let url = `${backend}/api/research/${sessionId}/stream`;
    if (token) {
      url += `?token=${encodeURIComponent(token)}`;
    }

    fetchEventSource(url, {
      headers: { Accept: 'text/event-stream' },
      signal: controller.signal,
      openWhenHidden: true,

      onopen: async (response) => {
        if (!response.ok) {
          throw new Error(`SSE 连接失败: HTTP ${response.status}`);
        }
        reconnectCount = 0;
      },

      onmessage: (msg) => {
        if (!msg.data || msg.data.trim() === '') return;

        try {
          const event = JSON.parse(msg.data) as ProgressEvent;
          callbacks.onEvent(event);

          if (event.stage === 'COMPLETED' || event.stage === 'ERROR') {
            completed = true;
            callbacks.onComplete();
            controller.abort();
          }
        } catch {
          // 非 JSON 数据（心跳等），忽略
        }
      },

      onerror: (err) => {
        if (completed) {
          controller.abort();
          return;
        }

        if (reconnectCount >= SSE_MAX_RECONNECT) {
          callbacks.onError(new Error(`SSE 重连次数已达上限 (${SSE_MAX_RECONNECT})`));
          controller.abort();
          throw err;
        }

        reconnectCount++;
        const delay =
          SSE_RECONNECT_DELAYS[reconnectCount - 1] ||
          SSE_RECONNECT_DELAYS[SSE_RECONNECT_DELAYS.length - 1];

        console.warn(`[SSE] 重连 ${reconnectCount}/${SSE_MAX_RECONNECT}, ${delay}ms 后重试`);
        callbacks.onReconnecting?.(reconnectCount, delay);
        return delay;
      },
    });
  }

  connect();

  return {
    abort: () => {
      completed = true;
      controller.abort();
    },
  };
}
