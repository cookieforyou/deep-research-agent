'use client';

import { useAuthStore } from '@/stores/auth-store';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

/**
 * 管理后台布局
 *
 * - 权限守卫：非 admin 用户自动重定向到首页
 * - 侧边导航 + 内容区
 */
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const { isAdmin, isAuthenticated } = useAuthStore();
  const router = useRouter();

  useEffect(() => {
    // 开发模式自动跳过
    if (process.env.NEXT_PUBLIC_DEV_MODE === 'true') return;
    if (isAuthenticated && !isAdmin) {
      router.replace('/');
    }
  }, [isAuthenticated, isAdmin, router]);

  return (
    <div className="flex h-[calc(100vh-4rem)]">
      {/* 侧边导航 */}
      <aside className="w-56 border-r p-4 hidden md:block">
        <h2 className="font-semibold text-sm mb-4">管理后台</h2>
        <nav className="space-y-1">
          <Link
            href="/admin/prompts"
            className="block rounded-md px-3 py-2 text-sm hover:bg-accent hover:text-accent-foreground"
          >
            Prompt 模板
          </Link>
          <Link
            href="/admin/users"
            className="block rounded-md px-3 py-2 text-sm hover:bg-accent hover:text-accent-foreground"
          >
            用户管理
          </Link>
        </nav>
      </aside>

      {/* 主内容 */}
      <main className="flex-1 overflow-y-auto p-6">{children}</main>
    </div>
  );
}
