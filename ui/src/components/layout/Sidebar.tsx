'use client';

import { cn } from '@/lib/utils';
import { useUiStore } from '@/stores/ui-store';
import { PanelLeftClose, PanelLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface SidebarProps {
  children?: React.ReactNode;
  /** 侧边栏标题 */
  title?: string;
  /** 自定义 className */
  className?: string;
}

/**
 * 通用侧边栏组件
 *
 * - 桌面端：可折叠的固定侧边栏（宽度 280px）
 * - 移动端：由父组件通过 Sheet 实现
 * - 使用 useUiStore 管理折叠状态
 */
export function Sidebar({ children, title, className }: SidebarProps) {
  const { sidebarOpen, toggleSidebar } = useUiStore();

  return (
    <aside
      className={cn(
        'hidden lg:flex flex-col border-r bg-sidebar-background transition-all duration-300',
        sidebarOpen ? 'w-[280px]' : 'w-0 overflow-hidden border-r-0',
        className,
      )}
    >
      {/* 标题栏 */}
      {title && (
        <div className="flex items-center justify-between px-4 py-3 border-b">
          <span
            className={cn(
              'font-semibold text-sm text-sidebar-foreground transition-opacity',
              !sidebarOpen && 'opacity-0',
            )}
          >
            {title}
          </span>
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7"
            onClick={toggleSidebar}
            aria-label={sidebarOpen ? '收起侧边栏' : '展开侧边栏'}
          >
            {sidebarOpen ? (
              <PanelLeftClose className="h-4 w-4" />
            ) : (
              <PanelLeft className="h-4 w-4" />
            )}
          </Button>
        </div>
      )}

      {/* 内容区域 */}
      <div
        className={cn(
          'flex-1 overflow-y-auto transition-opacity',
          !sidebarOpen && 'opacity-0 pointer-events-none',
        )}
      >
        {children}
      </div>
    </aside>
  );
}

/**
 * 仅折叠按钮 — 当侧边栏收起时在内容区显示
 */
export function SidebarToggle() {
  const { sidebarOpen, toggleSidebar } = useUiStore();

  if (sidebarOpen) return null;

  return (
    <Button
      variant="outline"
      size="icon"
      className="fixed left-4 top-[4.5rem] z-40 h-8 w-8 shadow-md"
      onClick={toggleSidebar}
      aria-label="展开侧边栏"
    >
      <PanelLeft className="h-4 w-4" />
    </Button>
  );
}
