'use client';

import { useState, useCallback, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { useHistoryList, applyClientFilters, type ClientFilters } from '@/hooks/useHistoryList';
import { HistorySearch } from './HistorySearch';
import { HistoryFilters } from './HistoryFilters';
import { HistoryCard } from './HistoryCard';
import { HistoryRow } from './HistoryRow';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { FileSearch, RefreshCw, ChevronDown } from 'lucide-react';

const PAGE_SIZE = 20;

/**
 * 研究历史列表
 *
 * 策略：一次拉取全部数据，关键词/状态/排序/日期/评分筛选全部在前端完成。
 * 不再因筛选条件变化触发后端 API 调用。
 */
export function HistoryList() {
  const router = useRouter();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [sortBy, setSortBy] = useState('createdAt');
  const [startDate, setStartDate] = useState<string | undefined>();
  const [endDate, setEndDate] = useState<string | undefined>();
  const [minScore, setMinScore] = useState<number | undefined>();
  const [page, setPage] = useState(0);

  const { data, isLoading, isError, fetchNextPage, hasNextPage, isFetchingNextPage, refetch } =
    useHistoryList();

  const filters: ClientFilters = useMemo(
    () => ({ keyword, status, sortBy, sortDir: 'desc', startDate, endDate, minScore }),
    [keyword, status, sortBy, startDate, endDate, minScore],
  );

  // 累积全部已拉取的 items
  const allItems = useMemo(
    () => data?.pages.flatMap((p) => p.content) || [],
    [data],
  );

  // 客户端过滤 + 排序 + 分页
  const { items: visibleItems, total: filteredTotal } = useMemo(
    () => applyClientFilters(allItems, filters, page, PAGE_SIZE),
    [allItems, filters, page],
  );

  // 筛选项变化时重置到第一页
  const wrappedSetKeyword = useCallback((kw: string) => { setKeyword(kw); setPage(0); }, []);
  const wrappedSetStatus = useCallback((s: string) => { setStatus(s); setPage(0); }, []);
  const wrappedSetSortBy = useCallback((s: string) => { setSortBy(s); setPage(0); }, []);
  const wrappedSetDateRange = useCallback((s?: string, e?: string) => { setStartDate(s); setEndDate(e); setPage(0); }, []);
  const wrappedSetMinScore = useCallback((s?: number) => { setMinScore(s); setPage(0); }, []);

  // 客户端分页是否有更多
  const hasMoreClient = (page + 1) * PAGE_SIZE < filteredTotal;

  // 是否还有更多服务端数据未拉取
  const hasMoreServer = hasNextPage;

  const handleLoadMore = useCallback(() => {
    if (hasMoreClient) {
      setPage((p) => p + 1);
    } else if (hasMoreServer) {
      fetchNextPage().then(() => setPage((p) => p + 1));
    }
  }, [hasMoreClient, hasMoreServer, fetchNextPage]);

  const showLoadMore = hasMoreClient || hasMoreServer;

  const isEmpty = !isLoading && allItems.length === 0;
  const isFilteredEmpty = !isLoading && allItems.length > 0 && visibleItems.length === 0;

  const handleDelete = useCallback(
    async (sessionId: string) => {
      try {
        const { historyApi } = await import('@/lib/api');
        const item = allItems.find((i) => i.sessionId === sessionId);
        if (!item) return;
        await historyApi.delete(sessionId, item.userId, item.tenantId);
        refetch();
      } catch {
        // 后端 API 调用失败时静默处理
      }
    },
    [refetch, allItems],
  );

  const handleReRun = useCallback(
    (sessionId: string) => {
      router.push(`/research/${sessionId}`);
    },
    [router],
  );

  return (
    <div className="space-y-4">
      {/* 搜索 + 筛选 */}
      <div className="flex flex-col sm:flex-row gap-3">
        <div className="flex-1">
          <HistorySearch onSearch={wrappedSetKeyword} value={keyword} />
        </div>
        <HistoryFilters
          status={status}
          sortBy={sortBy}
          startDate={startDate}
          endDate={endDate}
          minScore={minScore}
          onStatusChange={wrappedSetStatus}
          onSortChange={wrappedSetSortBy}
          onDateRangeChange={wrappedSetDateRange}
          onScoreRangeChange={wrappedSetMinScore}
        />
      </div>

      {/* 加载中 */}
      {isLoading && (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="flex items-center gap-4 p-4 border rounded-lg">
              <Skeleton className="h-5 flex-1" />
              <Skeleton className="h-5 w-16" />
              <Skeleton className="h-5 w-16" />
              <Skeleton className="h-5 w-20" />
            </div>
          ))}
        </div>
      )}

      {/* 错误 */}
      {isError && (
        <div className="flex flex-col items-center gap-3 py-12 text-center">
          <p className="text-sm text-muted-foreground">加载失败，请稍后重试</p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            <RefreshCw className="h-4 w-4 mr-2" />
            重新加载
          </Button>
        </div>
      )}

      {/* 空状态（无任何数据） */}
      {isEmpty && (
        <div className="flex flex-col items-center gap-3 py-16 text-center">
          <FileSearch className="h-12 w-12 text-muted-foreground/30" />
          <div>
            <p className="text-sm font-medium text-muted-foreground">还没有研究记录</p>
            <p className="text-xs text-muted-foreground mt-1">
              去首页发起第一次深度研究
            </p>
          </div>
          <Button variant="outline" size="sm" onClick={() => router.push('/')}>
            开始研究
          </Button>
        </div>
      )}

      {/* 筛选后无结果 */}
      {isFilteredEmpty && (
        <div className="flex flex-col items-center gap-3 py-12 text-center">
          <FileSearch className="h-12 w-12 text-muted-foreground/30" />
          <div>
            <p className="text-sm font-medium text-muted-foreground">
              没有找到匹配的研究记录
            </p>
            <p className="text-xs text-muted-foreground mt-1">
              尝试修改筛选条件
            </p>
          </div>
        </div>
      )}

      {/* 数据展示 */}
      {visibleItems.length > 0 && (
        <>
          {/* 桌面端：表格 */}
          <div className="hidden md:block rounded-lg border overflow-hidden">
            <table className="w-full">
              <thead className="bg-muted/50">
                <tr>
                  <th className="text-left text-xs font-medium text-muted-foreground py-2 px-4">
                    查询
                  </th>
                  <th className="text-left text-xs font-medium text-muted-foreground py-2 px-2 w-[80px]">
                    状态
                  </th>
                  <th className="text-left text-xs font-medium text-muted-foreground py-2 px-2 w-[70px]">
                    字数
                  </th>
                  <th className="text-left text-xs font-medium text-muted-foreground py-2 px-2 w-[60px]">
                    引用
                  </th>
                  <th className="text-left text-xs font-medium text-muted-foreground py-2 px-2 w-[100px]">
                    评分
                  </th>
                  <th className="text-left text-xs font-medium text-muted-foreground py-2 px-2 w-[100px]">
                    时间
                  </th>
                  <th className="text-right text-xs font-medium text-muted-foreground py-2 px-4 w-[140px]">
                    操作
                  </th>
                </tr>
              </thead>
              <tbody>
                {visibleItems.map((item) => (
                  <HistoryRow
                    key={item.id}
                    item={item}
                    onDelete={handleDelete}
                    onReRun={handleReRun}
                  />
                ))}
              </tbody>
            </table>
          </div>

          {/* 移动端：卡片 */}
          <div className="md:hidden space-y-2">
            {visibleItems.map((item) => (
              <HistoryCard
                key={item.id}
                item={item}
                onDelete={handleDelete}
                onReRun={handleReRun}
              />
            ))}
          </div>

          {/* 加载更多 */}
          {showLoadMore && (
            <div className="flex justify-center pt-2">
              <Button
                variant="ghost"
                size="sm"
                onClick={handleLoadMore}
                disabled={isFetchingNextPage}
              >
                {isFetchingNextPage ? (
                  <>
                    <RefreshCw className="h-4 w-4 mr-2 animate-spin" />
                    加载中...
                  </>
                ) : (
                  <>
                    <ChevronDown className="h-4 w-4 mr-2" />
                    加载更多
                  </>
                )}
              </Button>
            </div>
          )}

          {/* 总数 */}
          <p className="text-xs text-muted-foreground text-center">
            共 {filteredTotal} 条记录{allItems.length !== filteredTotal ? `（全部 ${allItems.length} 条）` : ''}
          </p>
        </>
      )}
    </div>
  );
}
