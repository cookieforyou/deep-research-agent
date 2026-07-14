'use client';

import { use } from 'react';
import { useResearchSse } from '@/hooks/useResearchSse';
import { SseStatusBadge } from '@/components/research/SseStatusBadge';
import { Sidebar, SidebarToggle } from '@/components/layout/Sidebar';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';

/**
 * 研究详情页
 *
 * Phase 2: 骨架布局 + SSE 连接状态指示 + 事件调试信息
 * Phase 3: WorkflowTimeline 替换占位区域
 * Phase 4: ReportViewer 在完成后显示
 * Phase 5: Eval 分数 + Sidebar 上下文填充
 */
export default function ResearchDetailPage({
  params,
}: {
  params: Promise<{ sessionId: string }>;
}) {
  const { sessionId } = use(params);
  const { events, status, connect, isCompleted, hasError, isCacheHit } =
    useResearchSse(sessionId);

  return (
    <div className="flex h-[calc(100vh-4rem)]">
      {/* Sidebar — 研究上下文（Phase 5 填充实际研究信息） */}
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
        <div className="flex items-center justify-between px-6 py-3 border-b">
          <h2 className="text-sm font-semibold">研究详情</h2>
          <div className="flex items-center gap-2">
            {isCacheHit && (
              <Badge className="text-xs bg-green-500 hover:bg-green-500 text-white">
                缓存命中
              </Badge>
            )}
            <SseStatusBadge status={status} onReconnect={connect} />
          </div>
        </div>

        <div className="p-6">
          {/* Session 信息 */}
          <div className="mb-6 flex items-center gap-3 text-xs text-muted-foreground bg-muted/50 rounded-md px-3 py-2">
            <span className="font-mono">{sessionId}</span>
            <span>·</span>
            <span>进度事件: {events.length}</span>
            {events.length > 0 && (
              <>
                <span>·</span>
                <span>
                  最新: {events[events.length - 1]?.stage.toUpperCase()}
                </span>
              </>
            )}
          </div>

          {/* Phase 3: WorkflowTimeline 占位 */}
          <div className="rounded-lg border bg-card mb-6">
            <div className="p-6 text-center text-muted-foreground">
              <p className="text-2xl mb-2">🔄</p>
              <p className="text-sm font-medium">工作流进度 Timeline</p>
              <p className="text-xs mt-1">Phase 3 实现 7 阶段可视化</p>
              {events.length > 0 && (
                <div className="mt-4 space-y-1 text-xs">
                  <p className="font-medium mb-2">调试信息 — 最近 5 个事件:</p>
                  <div className="space-y-1 text-left max-w-md mx-auto font-mono">
                    {events.slice(-5).map((e, i) => (
                      <div
                        key={i}
                        className="text-muted-foreground bg-muted/50 rounded px-2 py-1"
                      >
                        <span className="text-primary">{e.stage}</span>
                        {' · '}
                        {e.nodeName}
                        {' · '}
                        {e.percent.toFixed(0)}%
                        <div className="text-[10px] opacity-60 truncate">
                          {e.message}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* 错误状态 */}
          {hasError && (
            <div className="rounded-lg border border-destructive/50 bg-destructive/5 p-6 text-center mb-6">
              <p className="text-lg mb-1">!</p>
              <p className="text-sm font-medium text-destructive">研究过程中发生错误</p>
              <p className="text-xs text-muted-foreground mt-1">
                请检查后端日志了解详情。您可以从首页重新发起研究。
              </p>
            </div>
          )}

          {/* 完成状态 */}
          {isCompleted && (
            <div className="rounded-lg border border-green-200 bg-green-50 dark:bg-green-950/20 p-6 text-center">
              <p className="text-lg mb-1">✅</p>
              <p className="text-sm font-medium text-green-700 dark:text-green-400">
                研究完成 — 报告渲染将在 Phase 4 实现
              </p>
              <p className="text-xs text-muted-foreground mt-1">
                {events.length} 个进度事件 · SSE 连接已关闭
              </p>
            </div>
          )}

          {/* 连接中 + 无事件（等待任务启动） */}
          {!isCompleted && !hasError && events.length === 0 && (
            <div className="flex items-center justify-center py-20">
              <div className="text-center space-y-3">
                <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent mx-auto" />
                <p className="text-muted-foreground">正在连接研究进度流...</p>
                <p className="text-xs text-muted-foreground">
                  SSE 连接建立后将实时推送工作流进度
                </p>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
