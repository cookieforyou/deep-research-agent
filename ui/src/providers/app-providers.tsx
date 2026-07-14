'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState, useEffect } from 'react';
import { TooltipProvider } from '@/components/ui/tooltip';
import { useAuthStore } from '@/stores/auth-store';

/**
 * 认证初始化器 — 在 Provider 树最上层初始化 auth 状态
 */
function AuthInitializer({ children }: { children: React.ReactNode }) {
  const initFromStorage = useAuthStore((s) => s.initFromStorage);

  useEffect(() => {
    initFromStorage();
  }, [initFromStorage]);

  return <>{children}</>;
}

/**
 * AppProviders — 全局 Provider 组合
 *
 * 包含:
 *   - TanStack Query (QueryClientProvider)
 *   - TooltipProvider (Radix UI Tooltip)
 *   - AuthInitializer (JWT 恢复)
 */
export function AppProviders({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000, // 30 秒内不重新请求
            retry: 1, // 失败重试 1 次
            refetchOnWindowFocus: false,
          },
          mutations: {
            retry: 0, // mutation 不自动重试
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider delayDuration={300}>
        <AuthInitializer>{children}</AuthInitializer>
      </TooltipProvider>
    </QueryClientProvider>
  );
}
