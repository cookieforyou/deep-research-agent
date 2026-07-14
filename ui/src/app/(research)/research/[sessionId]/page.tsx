'use client';

import { use } from 'react';
import { useRouter } from 'next/navigation';
import { useResearchSse } from '@/hooks/useResearchSse';
import { useReportData } from '@/hooks/useReportData';
import { useEvalData } from '@/hooks/useEvalData';
import { SseStatusBadge } from '@/components/research/SseStatusBadge';
import { WorkflowTimeline } from '@/components/research/WorkflowTimeline';
import { CacheHitBanner } from '@/components/research/CacheHitBanner';
import { ResearchErrorView } from '@/components/research/ResearchErrorView';
import { ReportViewer } from '@/components/research/ReportViewer';
import { ReportOutline } from '@/components/research/ReportOutline';
import { ReportSkeleton } from '@/components/research/ReportSkeleton';
import { EvalScoreCard, EvalScoreSkeleton } from '@/components/research/EvalScoreCard';
import { EvalRadarChart } from '@/components/research/EvalRadarChart';
import { Sidebar, SidebarToggle } from '@/components/layout/Sidebar';
import { Separator } from '@/components/ui/separator';
import { CheckCircle } from 'lucide-react';

/**
 * 研究详情页
 *
 * Phase 3: SSE 实时进度 + WorkflowTimeline 7 阶段可视化
 * Phase 4: ReportViewer 报告渲染 + ReportOutline 大纲导航
 * Phase 5: Eval 分数展示 + 侧边栏评估
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

  // 研究完成后拉取完整报告
  const {
    data: reportData,
    isLoading: reportLoading,
    isError: reportError,
  } = useReportData(sessionId, isCompleted || isCacheHit);

  const report = reportData?.report || '';
  const metadata = reportData?.metadata;
  const showReport = (isCompleted || isCacheHit) && !reportLoading && report;

  // 异步拉取评估分数（轮询，5s 间隔）
  const evalResult = useEvalData(sessionId, isCompleted || isCacheHit);

  const handleRetry = () => {
    router.push('/');
  };

  return (
    <div className="flex h-[calc(100vh-4rem)]">
      {/* Sidebar */}
      <Sidebar
        title={showReport ? '评估与分析' : '研究上下文'}
        className="hidden lg:flex"
      >
        <div className="p-4 space-y-4">
          {!showReport ? (
            <>
              <div>
                <h4 className="text-xs font-medium text-sidebar-foreground mb-1">
                  会话 ID
                </h4>
                <code className="text-[10px] text-muted-foreground break-all">
                  {sessionId}
                </code>
              </div>
              <div>
                <h4 className="text-xs font-medium text-sidebar-foreground mb-1">
                  连接状态
                </h4>
                <SseStatusBadge status={status} onReconnect={connect} />
              </div>
              <div className="text-xs text-muted-foreground">
                已接收 {events.length} 个进度事件
              </div>
              <Separator />
              <div className="text-xs text-muted-foreground space-y-1">
                <p>📋 查询内容 — Phase 5</p>
                {metadata && (
                  <>
                    <p>
                      📝 {metadata.wordCount.toLocaleString()} 字 ·{' '}
                      {metadata.citationCount} 引用
                    </p>
                  </>
                )}
              </div>
            </>
          ) : (
            <>
              {/* 完成后：元信息 */}
              {metadata && (
                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                  <CheckCircle className="h-3.5 w-3.5 text-green-500 shrink-0" />
                  <span>{metadata.wordCount.toLocaleString()} 字</span>
                  <span>·</span>
                  <span>{metadata.citationCount} 引用</span>
                </div>
              )}
              <Separator />

              {/* 评估分数 */}
              {evalResult ? (
                <div className="space-y-3">
                  <EvalScoreCard evalResult={evalResult} />
                  <EvalRadarChart evalResult={evalResult} height={200} />
                </div>
              ) : (
                <div className="space-y-2">
                  <p className="text-xs font-medium text-sidebar-foreground">
                    评估中...
                  </p>
                  <p className="text-xs text-muted-foreground">
                    AI 正在异步评估报告质量（5维评分），预计 10-30 秒完成。
                  </p>
                  <EvalScoreSkeleton />
                </div>
              )}

              <Separator />

              {/* 大纲导航 */}
              <div className="space-y-1">
                <p className="text-xs font-medium text-sidebar-foreground px-1">
                  报告大纲
                </p>
                <ReportOutline report={report} />
              </div>
            </>
          )}
        </div>
      </Sidebar>

      <SidebarToggle />

      {/* Main */}
      <main className="flex-1 overflow-y-auto">
        <div className="flex items-center justify-between px-6 py-3 border-b bg-background/50 backdrop-blur-sm sticky top-0 z-10">
          <h2 className="text-sm font-semibold">
            {showReport ? '研究报告' : '研究进度'}
          </h2>
          <SseStatusBadge status={status} onReconnect={connect} />
        </div>

        <div className={showReport ? 'p-6' : 'max-w-2xl mx-auto p-6 space-y-6'}>
          {isCacheHit && <CacheHitBanner />}

          {!isCacheHit && !showReport && (
            <div className="rounded-lg border bg-card p-4">
              <WorkflowTimeline events={events} />
            </div>
          )}

          {(isCompleted || isCacheHit) && reportLoading && (
            <div className="max-w-3xl">
              <ReportSkeleton />
            </div>
          )}

          {reportError && (
            <div className="rounded-lg border border-destructive/50 bg-destructive/5 p-6 text-center">
              <p className="text-sm font-medium text-destructive">报告加载失败</p>
              <p className="text-xs text-muted-foreground mt-1">
                无法从后端获取完整报告，请稍后重试。
              </p>
            </div>
          )}

          {showReport && (
            <div className="max-w-4xl">
              <div className="rounded-lg border border-green-200 bg-green-50 dark:bg-green-950/20 p-4 mb-6">
                <div className="flex items-center gap-3">
                  <CheckCircle className="h-5 w-5 text-green-600 dark:text-green-400 shrink-0" />
                  <div>
                    <p className="text-sm font-medium text-green-800 dark:text-green-300">
                      研究完成
                    </p>
                    <p className="text-xs text-green-700/70 dark:text-green-400/70">
                      报告由 AI 多智能体协同生成，引用可点击溯源
                    </p>
                  </div>
                </div>
              </div>
              <ReportViewer report={report} metadata={metadata} />
            </div>
          )}

          {hasError && (
            <ResearchErrorView
              message={events.find((e) => e.stage === 'ERROR')?.message}
              onRetry={handleRetry}
            />
          )}

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
