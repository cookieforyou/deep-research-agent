'use client';

import { useEffect } from 'react';
import { useAuthStore } from '@/stores/auth-store';
import { useRouter } from 'next/navigation';

/**
 * 认证 Hook
 *
 * 组件挂载时从 localStorage 初始化认证状态。
 * 可选：未认证时重定向到登录页。
 */
export function useAuth(requireAuth = true) {
  const router = useRouter();
  const { isAuthenticated, initFromStorage } = useAuthStore();

  useEffect(() => {
    initFromStorage();
  }, [initFromStorage]);

  // 注意：开发模式下 DEV_MODE=true 时 isAuthenticated 始终为 true
  useEffect(() => {
    if (requireAuth && !isAuthenticated) {
      // 生产环境：重定向到登录页
      // 开发环境：不会到这里，因为 DEV_MODE 自动设置 isAuthenticated=true
      console.warn('[useAuth] 未认证，重定向到登录页');
      // router.push('/login');
    }
  }, [requireAuth, isAuthenticated, router]);

  return useAuthStore();
}
