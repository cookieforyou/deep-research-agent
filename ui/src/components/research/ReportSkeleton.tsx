import { Skeleton } from '@/components/ui/skeleton';

/**
 * 报告加载骨架屏
 *
 * 模拟 Markdown 报告的排版结构：
 * 标题 → 段落 → 子标题 → 段落 → 代码块
 */
export function ReportSkeleton() {
  return (
    <div className="space-y-6 p-2">
      {/* 报告标题 */}
      <Skeleton className="h-8 w-3/4" />
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-5/6" />

      {/* 第一章 */}
      <div className="space-y-3 pt-4">
        <Skeleton className="h-6 w-1/2" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-4/5" />
      </div>

      {/* 第二章 */}
      <div className="space-y-3 pt-2">
        <Skeleton className="h-6 w-2/5" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-3/4" />
      </div>

      {/* 表格占位 */}
      <div className="space-y-2">
        <Skeleton className="h-5 w-1/3" />
        <Skeleton className="h-32 w-full rounded-lg" />
      </div>

      {/* 更多段落 */}
      <div className="space-y-3 pt-2">
        <Skeleton className="h-6 w-1/2" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-2/3" />
      </div>

      {/* 代码块占位 */}
      <Skeleton className="h-48 w-full rounded-lg" />
    </div>
  );
}
