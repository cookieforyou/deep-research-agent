import { HistoryList } from '@/components/history/HistoryList';

/**
 * 研究历史页
 *
 * Phase 5: 完整实现 — 搜索/筛选/排序/分页/评分预览。
 * 后端 API 未就绪时显示空状态，不报错。
 */
export default function HistoryPage() {
  return (
    <div className="p-6 max-w-5xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold">研究历史</h1>
        <p className="text-sm text-muted-foreground mt-2">
          查看和管理所有深度研究报告。搜索结果由后端 API 提供。
        </p>
      </div>
      <HistoryList />
    </div>
  );
}
