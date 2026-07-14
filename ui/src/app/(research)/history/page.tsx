/**
 * 研究历史页（Phase 1 骨架，Phase 5 完整实现）
 */
export default function HistoryPage() {
  return (
    <div className="p-6 max-w-4xl">
      <h1 className="text-2xl font-bold">研究历史</h1>
      <p className="text-sm text-muted-foreground mt-2">
        查看和管理所有深度研究报告。分页搜索、状态筛选、评分对比功能将在 Phase 5 实现。
      </p>
      <div className="mt-8 flex items-center justify-center h-64 text-muted-foreground">
        <div className="text-center">
          <p className="text-4xl mb-2">📋</p>
          <p className="text-sm">历史列表将在 Phase 5 接入后端 API</p>
        </div>
      </div>
    </div>
  );
}
