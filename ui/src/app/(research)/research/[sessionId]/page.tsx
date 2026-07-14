import { Sidebar, SidebarToggle } from '@/components/layout/Sidebar';

/**
 * 研究详情页（Phase 1 骨架，Phase 3 集成 SSE + Timeline）
 */
export default function ResearchDetailPage() {
  return (
    <div className="flex h-[calc(100vh-4rem)]">
      <Sidebar title="研究上下文" />
      <SidebarToggle />
      <main className="flex-1 overflow-y-auto p-6">
        <div className="flex items-center justify-center h-full">
          <div className="text-center space-y-3">
            <p className="text-4xl">🔬</p>
            <p className="text-muted-foreground">
              研究详情页 — Phase 3 集成 SSE 实时进度与工作流可视化
            </p>
          </div>
        </div>
      </main>
    </div>
  );
}
