'use client';

import { use } from 'react';
import { useRouter } from 'next/navigation';
import { useResearchSse } from '@/hooks/useResearchSse';
import { SseStatusBadge } from '@/components/research/SseStatusBadge';
import { WorkflowTimeline } from '@/components/research/WorkflowTimeline';
import { CacheHitBanner } from '@/components/research/CacheHitBanner';
import { ResearchErrorView } from '@/components/research/ResearchErrorView';
import { Sidebar, SidebarToggle } from '@/components/layout/Sidebar';
import { Separator } from '@/components/ui/separator';

/**
 * 研究详情页
 *
 * Phase 3: SSE 实时进度 + WorkflowTimeline 7 阶段可视化
 * Phase 4: ReportViewer 在完成后显示（替换完成占位）
 * Phase 5: Eval 分数 + Sidebar 上下文填充
 */
export default function ResearchDetailPage({
  params,
}: {
  params: Promise<{ sessionId: string }>;
}) {
  const { sessionId } = use(params);
  const router = useRouter();
  const { events, status, connect, isCompleted, hasError, isCacheHit } =
    useResearchSse(sessionId);

  // 重新研究：返回首页（可带已有查询）
  const handleRetry = () => {
    router.push('/');
  };

  return (
    <div className="flex h-[calc(100vh-4rem)]">
      {/* Sidebar — 研究上下文（Phase 4-5 填充实际研究信息） */}
      <Sidebar title="研究上下文" className="hidden lg:flex">
        <div className="p-4 space-y-4">
          <div>
            <h4 className="text-xs font-medium text-sidebar-foreground mb-1">会话 ID</h4>
            <code className="text-[10px] text-muted-foreground break-all">
              {sessionId}
            </code>
          </div>
          <div>
            <h4 className="text-xs font-medium text-sidebar-foreground mb-1">连接状态</h4>
            <SseStatusBadge status={status} onReconnect={connect} />
          </div>
          <div className="text-xs text-muted-foreground">
            已接收 {events.length} 个进度事件
          </div>
          <Separator />
          <div className="text-xs text-muted-foreground space-y-1">
            <p>📋 查询内容 — Phase 5</p>
            <p>📊 评估分数 — Phase 5</p>
            <p>📑 报告大纲 — Phase 4</p>
          </div>
        </div>
      </Sidebar>

      <SidebarToggle />

      {/* Main — 进度 + 报告 */}
      <main className="flex-1 overflow-y-auto">
        {/* 顶部状态栏 */}
        <div className="flex items-center justify-between px-6 py-3 border-b bg-background/50 backdrop-blur-sm sticky top-0 z-10">
          <h2 className="text-sm font-semibold">研究进度</h2>
          <SseStatusBadge status={status} onReconnect={connect} />
        </div>

        <div className="max-w-2xl mx-auto p-6 space-y-6">
          {/* 缓存命中 — 替代整个 Timeline */}
          {isCacheHit && (
            <CacheHitBanner
              message={events.find((e) => e.stage === 'CACHE_HIT')?.message}
            />
          )}

          {/* 工作流 Timeline — 核心可视化 */}
          {!isCacheHit && (
            <div className="rounded-lg border bg-card p-4">
              <WorkflowTimeline events={events} />
            </div>
          )}

          {/* 研究完成 — 报告渲染将在 Phase 4 实现 */}
          {isCompleted && (
            <div className="rounded-lg border border-green-200 bg-green-50 dark:bg-green-950/20 p-6 text-center">
              <p className="text-2xl mb-2">✅</p>
              <p className="text-sm font-medium text-green-700 dark:text-green-400">
                研究完成 — 报告渲染将在 Phase 4 实现
              </p>
              <p className="text-xs text-muted-foreground mt-1">
                {events.length} 个进度事件已接收 · SSE 连接已关闭
              </p>
            </div>
          )}

          {/* 研究失败 */}
          {hasError && (
            <ResearchErrorView
              message={events.find((e) => e.stage === 'ERROR')?.message}
              onRetry={handleRetry}
            />
          )}

          {/* 初始等待状态 */}
          {!isCompleted && !hasError && !isCacheHit && events.length === 0 && (
            <div className="flex items-center justify-center py-24">
              <div className="text-center space-y-4">
                <div className="h-10 w-10 animate-spin rounded-full border-4 border-primary border-t-transparent mx-auto" />
                <div>
                  <p className="text-muted-foreground">正在等待研究任务启动...</p>
                  <p className="text-xs text-muted-foreground mt-1">
                    后端 AI Agent 准备就绪后将推送实时进度
                  </p>
                </div>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
