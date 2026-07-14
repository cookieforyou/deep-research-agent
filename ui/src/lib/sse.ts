/**
 * SSE (Server-Sent Events) 客户端封装
 *
 * 后端 SSE 格式（ProgressEventPublisher.java）:
 *   event: <stage.name().toLowerCase()>
 *   id: <sessionId>
 *   data: <ProgressEvent JSON>
 *
 * 心跳: 每 15 秒一行 comment: "heartbeat"，前端自动忽略
 *
 * 使用 @microsoft/fetch-event-source：
 *   - 比原生 EventSource 更好的错误处理
 *   - 支持 POST 和自定义 headers（JWT Authorization）
 *   - 可自定义重连策略
 */

import { fetchEventSource } from '@microsoft/fetch-event-source';
import type { ProgressEvent } from './types';
import { getToken } from './jwt';
import { SSE_RECONNECT_DELAYS, SSE_MAX_RECONNECT } from './constants';

// =========================== 类型 ===========================

interface SseCallbacks {
  /** 收到进度事件时调用 */
  onEvent: (event: ProgressEvent) => void;
  /** 研究完成或出错时调用 */
  onComplete: () => void;
  /** 连接出错（重连耗尽或致命错误） */
  onError: (error: Error) => void;
  /** 重连时调用 */
  onReconnecting?: (attempt: number, delayMs: number) => void;
}

interface SseConnection {
  /** 取消连接 */
  abort: () => void;
}

// =========================== 创建 SSE 连接 ===========================

export function createSseConnection(
  sessionId: string,
  callbacks: SseCallbacks,
): SseConnection {
  const controller = new AbortController();
  let reconnectCount = 0;
  let completed = false;

  function connect() {
    const token = getToken();

    fetchEventSource(`/api/research/${sessionId}/stream`, {
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        Accept: 'text/event-stream',
      },
      signal: controller.signal,
      openWhenHidden: true,

      onopen: async (response) => {
        if (!response.ok) {
          throw new Error(`SSE 连接失败: HTTP ${response.status}`);
        }
        // 连接成功，重置重试计数
        reconnectCount = 0;
      },

      onmessage: (msg) => {
        // 后端心跳为 comment 行，fetch-event-source 自动忽略（msg.event 为空且 msg.data 为空时）
        // 正常事件: event:<stage>, data:<JSON>
        if (!msg.data || msg.data.trim() === '') return;

        try {
          const event = JSON.parse(msg.data) as ProgressEvent;
          callbacks.onEvent(event);

          // 终端事件：断开连接
          if (event.stage === 'COMPLETED' || event.stage === 'ERROR') {
            completed = true;
            callbacks.onComplete();
            controller.abort();
          }
        } catch {
          // 非 JSON 数据（如心跳 comment），忽略
          console.debug('[SSE] 忽略非 JSON 消息:', msg.data.substring(0, 50));
        }
      },

      onerror: (err) => {
        // 已完成，不重连
        if (completed) {
          controller.abort();
          return;
        }

        // 达到最大重连次数
        if (reconnectCount >= SSE_MAX_RECONNECT) {
          callbacks.onError(new Error(`SSE 重连次数已达上限 (${SSE_MAX_RECONNECT})`));
          controller.abort();
          throw err; // 停止重连
        }

        // 指数退避重连
        reconnectCount++;
        const delay =
          SSE_RECONNECT_DELAYS[reconnectCount - 1] ||
          SSE_RECONNECT_DELAYS[SSE_RECONNECT_DELAYS.length - 1];

        console.warn(
          `[SSE] 重连中... (${reconnectCount}/${SSE_MAX_RECONNECT}, ${delay}ms 后重试)`,
        );
        callbacks.onReconnecting?.(reconnectCount, delay);

        // 返回延迟时间来自定义重试间隔
        // （fetch-event-source 默认 1s-30s 指数退避，我们覆盖为自定义策略）
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
