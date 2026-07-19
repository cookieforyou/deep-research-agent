'use client';

import { use, Suspense, useState, useEffect } from 'react';
import dynamic from 'next/dynamic';
import { useRouter } from 'next/navigation';
import { useResearchSse } from '@/hooks/useResearchSse';
import { useReportData } from '@/hooks/useReportData';
import { useEvalData } from '@/hooks/useEvalData';
import { useNetworkStatus } from '@/hooks/useNetworkStatus';
import { SseStatusBadge } from '@/components/research/SseStatusBadge';
import { WorkflowTimeline } from '@/components/research/WorkflowTimeline';
import { CacheHitBanner } from '@/components/research/CacheHitBanner';
import { ResearchErrorView } from '@/components/research/ResearchErrorView';
import { ReportSkeleton } from '@/components/research/ReportSkeleton';
import { EvalScoreSkeleton } from '@/components/research/EvalScoreCard';
import { Sidebar, SidebarToggle } from '@/components/layout/Sidebar';
import { Separator } from '@/components/ui/separator';
import { Badge } from '@/components/ui/badge';
import { CheckCircle, WifiOff } from 'lucide-react';

const ReportViewer = dynamic(
  () => import('@/components/research/ReportViewer').then((m) => ({ default: m.ReportViewer })),
  { loading: () => <ReportSkeleton />, ssr: false },
);

const ReportOutline = dynamic(
  () => import('@/components/research/ReportOutline').then((m) => ({ default: m.ReportOutline })),
  { ssr: false },
);

const EvalScoreCard = dynamic(
  () => import('@/components/research/EvalScoreCard').then((m) => ({ default: m.EvalScoreCard })),
  { loading: () => <EvalScoreSkeleton />, ssr: false },
);

const EvalRadarChart = dynamic(
  () => import('@/components/research/EvalRadarChart').then((m) => ({ default: m.EvalRadarChart })),
  { ssr: false },
);

export default function ResearchDetailPage({
  params,
}: {
  params: Promise<{ sessionId: string }>;
}) {
  const { sessionId } = use(params);
  const router = useRouter();
  const { online } = useNetworkStatus();

  // SSE 始终立即连接（不等待 report 查询），确保 Sink 在后端推送事件前就绪
  const { events, status, connect, disconnect, isCompleted, hasError, isCacheHit } =
    useResearchSse(sessionId);

  // completionTick: SSE COMPLETED 到达时递增，触发 useReportData 重新拉取
  const [completionTick, setCompletionTick] = useState(0);
  useEffect(() => {
    if (isCompleted) setCompletionTick((t) => t + 1);
  }, [isCompleted]);

  const {
    data: reportData,
    isLoading: reportLoading,
    isError: reportError,
  } = useReportData(sessionId, true, completionTick);

  const report = reportData?.report || '';
  const metadata = reportData?.metadata;
  const isAlreadyCompleted = !!report;

  // 历史记录：报告加载完成后断开无用的 SSE
  useEffect(() => {
    if (isAlreadyCompleted) disconnect();
  }, [isAlreadyCompleted, disconnect]);

  const showReport = (isCompleted || isAlreadyCompleted || isCacheHit) && !reportLoading && !!report;

  const evalResult = useEvalData(sessionId, isCompleted || isAlreadyCompleted || isCacheHit);

  const handleRetry = () => {
    router.push('/');
  };

  return (
    <div className="flex h-[calc(100vh-4rem)]">
      <Sidebar
        title={showReport ? '评估与分析' : '研究上下文'}
        className="hidden lg:flex"
      >
        <div className="p-4 space-y-4">
          {!showReport ? (
            <>
              <div className="text-xs text-muted-foreground">
                已接收 {events.length} 个进度事件
              </div>
              <Separator />
            </>
          ) : (
            <>
              {metadata && (
                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                  <CheckCircle className="h-3.5 w-3.5 text-green-500 shrink-0" />
                  <span>{metadata.wordCount.toLocaleString()} 字</span>
                  {metadata.citationCount > 0 && (
                    <>
                      <span>·</span>
                      <span>{metadata.citationCount} 引用</span>
                    </>
                  )}
                </div>
              )}
              <Separator />

              {/* 深度研究：有引用 → 显示评估；简单问答：无引用 → 不显示评估 */}
              {(metadata?.citationCount ?? 0) > 0 ? (
                evalResult ? (
                  <div className="space-y-3">
                    <Suspense fallback={<EvalScoreSkeleton />}>
                      <EvalScoreCard evalResult={evalResult} />
                    </Suspense>
                    <Suspense fallback={null}>
                      <EvalRadarChart evalResult={evalResult} height={200} />
                    </Suspense>
                  </div>
                ) : (
                  <div className="space-y-2">
                    <p className="text-xs font-medium text-sidebar-foreground">评估中...</p>
                    <p className="text-xs text-muted-foreground">
                      AI 正在异步评估报告质量（5维评分），预计 10-30 秒完成。
                    </p>
                    <EvalScoreSkeleton />
                  </div>
                )
              ) : (
                <p className="text-xs text-muted-foreground">简单问答，无需评估</p>
              )}

              <Separator />
              <div className="space-y-1">
                <p className="text-xs font-medium text-sidebar-foreground px-1">报告大纲</p>
                <Suspense fallback={null}>
                  <ReportOutline report={report} />
                </Suspense>
              </div>
            </>
          )}
        </div>
      </Sidebar>

      <SidebarToggle />

      <main className="flex-1 overflow-y-auto">
        <div className="flex items-center justify-between px-6 py-3 border-b bg-background/50 backdrop-blur-sm sticky top-0 z-10">
          <h2 className="text-sm font-semibold">
            {showReport ? '研究报告' : '研究进度'}
          </h2>
          <div className="flex items-center gap-2">
            {!online && (
              <Badge variant="destructive" className="text-[10px] h-5 gap-1">
                <WifiOff className="h-3 w-3" />
                离线
              </Badge>
            )}
            {!isAlreadyCompleted && (
              <SseStatusBadge status={status} onReconnect={connect} />
            )}
          </div>
        </div>

        <div className={showReport ? 'p-6' : 'max-w-2xl mx-auto p-6 space-y-6'}>
          {isCacheHit && <CacheHitBanner />}

          {!isCacheHit && !showReport && (
            <div className="rounded-lg border bg-card p-4">
              <WorkflowTimeline events={events} />
            </div>
          )}

          {(isCompleted || isAlreadyCompleted) && reportLoading && (
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
                      {isCacheHit ? '缓存命中' : '研究完成'}
                    </p>
                    <p className="text-xs text-green-700/70 dark:text-green-400/70">
                      报告由 AI 多智能体协同生成，引用可点击溯源
                    </p>
                  </div>
                </div>
              </div>
              <Suspense fallback={<ReportSkeleton />}>
                <ReportViewer
                  report={report}
                  metadata={metadata}
                  sourceIndex={reportData?.sourceIndex}
                  findings={reportData?.findings}
                />
              </Suspense>
            </div>
          )}

          {hasError && (
            <ResearchErrorView
              message={events.find((e) => e.stage === 'ERROR')?.message}
              onRetry={handleRetry}
            />
          )}

          {!isCompleted && !hasError && !isCacheHit && !isAlreadyCompleted && events.length === 0 && (
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
