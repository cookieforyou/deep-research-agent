'use client';

import { useEffect } from 'react';
import { useAuthStore } from '@/stores/auth-store';
import { usePathname, useRouter } from 'next/navigation';

/**
 * 认证 Hook — 组件挂载时初始化认证状态。
 *
 * 未认证用户自动重定向到 /login。
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
    if (pathname === '/login') return;

    if (!isAuthenticated) {
      router.replace('/login');
    }
  }, [requireAuth, isAuthenticated, router, pathname]);

  return useAuthStore();
}
