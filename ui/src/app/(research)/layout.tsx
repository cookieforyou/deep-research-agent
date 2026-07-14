import { Sidebar, SidebarToggle } from '@/components/layout/Sidebar';

/**
 * 研究路由组共享布局
 *
 * - 左侧：可折叠侧边栏（桌面端）/ Sheet 底部面板（移动端）
 * - 右侧：主内容区
 *
 * 侧边栏内容由子页面通过 URL params 或 store 动态填充
 */
export default function ResearchLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-[calc(100vh-4rem)]">
      <Sidebar title="研究上下文" />
      <SidebarToggle />
      <main className="flex-1 overflow-y-auto">{children}</main>
    </div>
  );
}
