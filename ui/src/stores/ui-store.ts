'use client';

import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface UiState {
  /** 侧边栏展开/折叠（桌面端） */
  sidebarOpen: boolean;
  /** 切换侧边栏 */
  toggleSidebar: () => void;
  /** 设置侧边栏状态 */
  setSidebarOpen: (open: boolean) => void;
}

/**
 * UI 偏好状态管理
 *
 * 使用 zustand persist 中间件自动持久化到 localStorage。
 * 仅存储 UI 偏好，不包含服务端状态（TanStack Query 管理）。
 */
export const useUiStore = create<UiState>()(
  persist(
    (set) => ({
      sidebarOpen: true,

      toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),

      setSidebarOpen: (open) => set({ sidebarOpen: open }),
    }),
    {
      name: 'deepresearch-ui',
    },
  ),
);
