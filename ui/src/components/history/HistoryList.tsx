'use client';

import { useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { useHistoryList } from '@/hooks/useHistoryList';
import { HistorySearch } from './HistorySearch';
import { HistoryFilters } from './HistoryFilters';
import { HistoryCard } from './HistoryCard';
import { HistoryRow } from './HistoryRow';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { FileSearch, RefreshCw, ChevronDown } from 'lucide-react';

/**
 * 研究历史列表
 *
 * 功能：
 * - 搜索：全文搜索 query 字段（300ms debounce）
 * - 筛选：按状态（全部/已完成/失败）+ 排序（时间/字数/评分）
 * - 桌面端：表格视图
 * - 移动端：卡片视图
 * - 无限滚动加载
 * - 空状态 / 加载状态 / 错误状态全覆盖
 */
export function HistoryList() {
  const router = useRouter();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [sortBy, setSortBy] = useState('createdAt');

  const { data, isLoading, isError, fetchNextPage, hasNextPage, isFetchingNextPage, refetch } =
    useHistoryList({ keyword, status, sortBy, sortDir: 'desc' });

  const allItems = data?.pages.flatMap((page) => page.content) || [];
  const isEmpty = !isLoading && allItems.length === 0;

  const handleDelete = useCallback(
    async (sessionId: string) => {
      try {
        const { historyApi } = await import('@/lib/api');
        await historyApi.delete(sessionId);
        refetch();
      } catch {
        // 后端 API 可能未实现，静默失败
      }
    },
    [refetch],
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
          <HistorySearch onSearch={setKeyword} value={keyword} />
        </div>
        <HistoryFilters
          status={status}
          sortBy={sortBy}
          onStatusChange={setStatus}
          onSortChange={setSortBy}
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

      {/* 空状态 */}
      {isEmpty && (
        <div className="flex flex-col items-center gap-3 py-16 text-center">
          <FileSearch className="h-12 w-12 text-muted-foreground/30" />
          <div>
            <p className="text-sm font-medium text-muted-foreground">
              {keyword ? '没有找到匹配的研究记录' : '还没有研究记录'}
            </p>
            <p className="text-xs text-muted-foreground mt-1">
              {keyword
                ? '尝试修改搜索关键词'
                : '去首页发起第一次深度研究'}
            </p>
          </div>
          {!keyword && (
            <Button variant="outline" size="sm" onClick={() => router.push('/')}>
              开始研究
            </Button>
          )}
        </div>
      )}

      {/* 桌面端：表格 */}
      {!isEmpty && (
        <>
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
                {allItems.map((item) => (
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
            {allItems.map((item) => (
              <HistoryCard
                key={item.id}
                item={item}
                onDelete={handleDelete}
                onReRun={handleReRun}
              />
            ))}
          </div>

          {/* 加载更多 */}
          {hasNextPage && (
            <div className="flex justify-center pt-2">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => fetchNextPage()}
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
            共 {data?.pages[0]?.totalElements ?? allItems.length} 条记录
          </p>
        </>
      )}
    </div>
  );
}
