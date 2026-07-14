'use client';

import { useEffect } from 'react';
import { useAuthStore } from '@/stores/auth-store';
import { usePathname, useRouter } from 'next/navigation';

/**
 * 认证 Hook — 组件挂载时初始化认证状态。
 *
 * 生产模式下未认证用户自动重定向到 /login。
 * 开发模式 (DEV_MODE=true) 跳过重定向。
 */
export function useAuth(requireAuth = true) {
  const router = useRouter();
  const pathname = usePathname();
  const { isAuthenticated, initFromStorage } = useAuthStore();

  useEffect(() => {
    initFromStorage();
  }, [initFromStorage]);

  useEffect(() => {
    if (!requireAuth) return;
    if (process.env.NEXT_PUBLIC_DEV_MODE === 'true') return;
    if (pathname === '/login') return;

    if (!isAuthenticated) {
      router.replace('/login');
    }
  }, [requireAuth, isAuthenticated, router, pathname]);

  return useAuthStore();
}
