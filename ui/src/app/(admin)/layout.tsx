'use client';

import { useAuthStore } from '@/stores/auth-store';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect } from 'react';

const ADMIN_LINKS = [
  { href: '/admin/prompts', label: 'Prompt 模板' },
  { href: '/admin/users', label: '用户管理' },
];

/**
 * 管理后台布局
 *
 * - 权限守卫：非 admin 用户自动重定向到首页
 * - 侧边导航 + 内容区
 */
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const { isAdmin, isAuthenticated } = useAuthStore();
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    if (!isAuthenticated) {
      router.replace('/login');
      return;
    }
    if (!isAdmin) {
      router.replace('/');
    }
  }, [isAuthenticated, isAdmin, router]);

  return (
    <div className="flex h-[calc(100vh-4rem)]">
      {/* 侧边导航 */}
      <aside className="w-56 border-r p-4 hidden md:block">
        <h2 className="font-semibold text-sm mb-4">管理后台</h2>
        <nav className="space-y-1">
          {ADMIN_LINKS.map(({ href, label }) => (
            <Link
              key={href}
              href={href}
              className={`block rounded-md px-3 py-2 text-sm transition-colors ${
                pathname.startsWith(href)
                  ? 'bg-secondary text-secondary-foreground font-medium'
                  : 'hover:bg-accent hover:text-accent-foreground'
              }`}
            >
              {label}
            </Link>
          ))}
        </nav>
      </aside>

      {/* 主内容 */}
      <main className="flex-1 overflow-y-auto p-6">{children}</main>
    </div>
  );
}
