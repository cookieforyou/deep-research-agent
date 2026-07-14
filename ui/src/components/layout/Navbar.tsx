'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useTheme } from 'next-themes';
import { useEffect, useState } from 'react';
import { Sun, Moon, Menu, History, Settings, LogOut, User } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useAuthStore } from '@/stores/auth-store';
import { useUiStore } from '@/stores/ui-store';

/** 导航项定义 */
const NAV_ITEMS = [
  { href: '/', label: '首页', exact: true },
  { href: '/history', label: '研究历史' },
];

export function Navbar() {
  const pathname = usePathname();
  const { theme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);
  const { userId, isAdmin, isAuthenticated, logout } = useAuthStore();
  const toggleSidebar = useUiStore((s) => s.toggleSidebar);

  // 防止 hydration mismatch
  useEffect(() => setMounted(true), []);

  const isActive = (href: string, exact = false) => {
    if (exact) return pathname === href;
    return pathname.startsWith(href);
  };

  return (
    <header className="sticky top-0 z-50 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div className="flex h-14 items-center px-4">
        {/* 左侧：Logo + 导航 */}
        <div className="flex items-center gap-6">
          {/* 移动端菜单按钮 */}
          <Button
            variant="ghost"
            size="icon"
            className="md:hidden"
            onClick={toggleSidebar}
          >
            <Menu className="h-5 w-5" />
          </Button>

          {/* Logo */}
          <Link href="/" className="flex items-center gap-2 font-semibold">
            <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-primary text-primary-foreground text-xs font-bold">
              DR
            </div>
            <span className="hidden sm:inline">DeepResearch</span>
          </Link>

          {/* 桌面导航 */}
          <nav className="hidden md:flex items-center gap-1">
            {NAV_ITEMS.map((item) => (
              <Link key={item.href} href={item.href}>
                <Button
                  variant={isActive(item.href, item.exact) ? 'secondary' : 'ghost'}
                  size="sm"
                  className="text-sm"
                >
                  {item.label}
                </Button>
              </Link>
            ))}
            {isAdmin && (
              <Link href="/admin/prompts">
                <Button
                  variant={isActive('/admin') ? 'secondary' : 'ghost'}
                  size="sm"
                  className="text-sm"
                >
                  <Settings className="h-4 w-4 mr-1" />
                  管理
                </Button>
              </Link>
            )}
          </nav>
        </div>

        {/* 右侧：主题 + 用户 */}
        <div className="ml-auto flex items-center gap-2">
          {/* 主题切换 */}
          {mounted && (
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
              aria-label="切换主题"
            >
              {theme === 'dark' ? (
                <Sun className="h-4 w-4" />
              ) : (
                <Moon className="h-4 w-4" />
              )}
            </Button>
          )}

          {/* 用户菜单 */}
          {isAuthenticated && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" className="rounded-full">
                  <User className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-48">
                <DropdownMenuLabel>
                  <div className="text-xs text-muted-foreground">{userId}</div>
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                <Link href="/history">
                  <DropdownMenuItem>
                    <History className="h-4 w-4 mr-2" />
                    研究历史
                  </DropdownMenuItem>
                </Link>
                {isAdmin && (
                  <Link href="/admin/prompts">
                    <DropdownMenuItem>
                      <Settings className="h-4 w-4 mr-2" />
                      Prompt 管理
                    </DropdownMenuItem>
                  </Link>
                )}
                <DropdownMenuSeparator />
                <DropdownMenuItem onClick={logout}>
                  <LogOut className="h-4 w-4 mr-2" />
                  退出登录
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          )}
        </div>
      </div>

      {/* 移动端导航（第二行） */}
      <nav className="flex md:hidden items-center gap-1 px-4 pb-2 overflow-x-auto">
        {NAV_ITEMS.map((item) => (
          <Link key={item.href} href={item.href}>
            <Button
              variant={isActive(item.href, item.exact) ? 'secondary' : 'ghost'}
              size="sm"
              className="text-xs whitespace-nowrap"
            >
              {item.label}
            </Button>
          </Link>
        ))}
        {isAdmin && (
          <Link href="/admin/prompts">
            <Button
              variant={isActive('/admin') ? 'secondary' : 'ghost'}
              size="sm"
              className="text-xs whitespace-nowrap"
            >
              <Settings className="h-3 w-3 mr-1" />
              管理
            </Button>
          </Link>
        )}
      </nav>
    </header>
  );
}
