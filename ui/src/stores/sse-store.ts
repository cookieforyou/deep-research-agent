'use client';

import { create } from 'zustand';
import type { ProgressEvent } from '@/lib/types';

interface SseState {
  /** sessionId → ProgressEvent[] 事件缓冲区 */
  eventsMap: Record<string, ProgressEvent[]>;
  /** 当前活跃的 SSE 连接 sessionId 集合 */
  connections: string[];

  /** 追加一条进度事件（自动去重） */
  addEvent: (sessionId: string, event: ProgressEvent) => void;
  /** 获取指定会话的全部事件 */
  getEvents: (sessionId: string) => ProgressEvent[];
  /** 获取指定会话最新的一条指定阶段事件 */
  getLatestByStage: (sessionId: string, stage: string) => ProgressEvent | undefined;
  /** 标记连接已建立 */
  setConnected: (sessionId: string) => void;
  /** 标记连接已断开 */
  setDisconnected: (sessionId: string) => void;
  /** 清除指定会话的事件缓冲（保留连接状态） */
  clearEvents: (sessionId: string) => void;
  /** 清除指定会话的全部状态（事件+连接） */
  clearSession: (sessionId: string) => void;
}

/**
 * SSE 事件状态管理.
 *
 * 管理多个研究会话的 SSE 事件缓冲。
 * 事件数组上限 200 条，超出时丢弃最旧的非关键事件（背压保护）。
 *
 * 去重策略：对比最后一条事件的 stage + nodeName + timestamp，
 * 防止后端 replay buffer 在自动重连时产生重复事件。
 */
const MAX_EVENTS = 200;

/** 判断两个事件是否为重复（用于 replay 去重） */
function isDuplicateEvent(a: ProgressEvent, b: ProgressEvent): boolean {
  return a.stage === b.stage && a.nodeName === b.nodeName && a.timestamp === b.timestamp;
}

export const useSseStore = create<SseState>((set, get) => ({
  eventsMap: {},
  connections: [],

  addEvent: (sessionId, event) =>
    set((state) => {
      const current = state.eventsMap[sessionId] || [];
      // 去重：replay buffer 在自动重连时会将历史事件全量回放，
      // 与已有事件对比最后一条，避免重复追加
      const last = current[current.length - 1];
      if (last && isDuplicateEvent(last, event)) {
        return state;
      }
      let updated = [...current, event];
      // 背压保护：超出上限时保留关键事件 + 最近事件
      if (updated.length > MAX_EVENTS) {
        const critical = updated.filter(
          (e) => e.stage === 'COMPLETED' || e.stage === 'ERROR' || e.stage === 'CACHE_HIT',
        );
        const recent = updated.filter(
          (e) => e.stage !== 'COMPLETED' && e.stage !== 'ERROR' && e.stage !== 'CACHE_HIT',
        );
        updated = [...recent.slice(-(MAX_EVENTS - critical.length)), ...critical];
      }
      return {
        eventsMap: { ...state.eventsMap, [sessionId]: updated },
      };
    }),

  getEvents: (sessionId) => get().eventsMap[sessionId] || [],

  getLatestByStage: (sessionId, stage) => {
    const events = get().eventsMap[sessionId] || [];
    return [...events].reverse().find((e) => e.stage === stage);
  },

  setConnected: (sessionId) =>
    set((state) => ({
      connections: state.connections.includes(sessionId)
        ? state.connections
        : [...state.connections, sessionId],
    })),

  setDisconnected: (sessionId) =>
    set((state) => ({
      connections: state.connections.filter((id) => id !== sessionId),
    })),

  clearEvents: (sessionId) =>
    set((state) => ({
      eventsMap: { ...state.eventsMap, [sessionId]: [] },
    })),

  clearSession: (sessionId) =>
    set((state) => {
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const { [sessionId]: _removed, ...rest } = state.eventsMap;
      return {
        eventsMap: rest,
        connections: state.connections.filter((id) => id !== sessionId),
      };
    }),
}));
