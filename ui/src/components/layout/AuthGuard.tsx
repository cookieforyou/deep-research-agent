'use client';

import { useEffect, useState } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { useAuthStore } from '@/stores/auth-store';

/**
 * AuthGuard — 认证守卫组件。
 *
 * 包裹在根 layout 中，对所有页面生效。
 * - 未认证用户重定向到 /login
 * - /login 页面本身不受守卫
 * - 使用 initialized 标志避免初始化竞态（先重定向再发现已登录）
 */
export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { isAuthenticated, initFromStorage } = useAuthStore();
  const [initialized, setInitialized] = useState(false);

  // 第一步：从 localStorage 恢复认证状态（同步操作）
  useEffect(() => {
    initFromStorage();
    setInitialized(true);
  }, [initFromStorage]);

  // 第二步：初始化完成后，未认证且不在登录页则重定向
  useEffect(() => {
    if (!initialized) return;
    if (pathname === '/login') return;
    if (!isAuthenticated) {
      router.replace('/login');
    }
  }, [initialized, isAuthenticated, pathname, router]);

  // 初始化中 → 不渲染（避免闪烁）
  if (!initialized) return null;
  // 未认证且不在登录页 → 不渲染（等重定向）
  if (!isAuthenticated && pathname !== '/login') return null;

  return <>{children}</>;
}
