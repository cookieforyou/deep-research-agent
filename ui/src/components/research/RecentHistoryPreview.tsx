'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { Card, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { History, ExternalLink } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { historyApi } from '@/lib/api';
import { useAuthStore } from '@/stores/auth-store';
import type { ResearchHistoryItem } from '@/lib/types';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';

dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

/**
 * 最近研究历史预览（首页底部）
 *
 * 显示最近 3 条研究记录，使用真实后端 API。
 */
export function RecentHistoryPreview() {
  const { userId, tenantId } = useAuthStore();

  const { data, isLoading } = useQuery({
    queryKey: ['history', userId, tenantId, 'recent'],
    queryFn: () =>
      historyApi.list({ page: 0, size: 3, sortBy: 'createdAt', sortDir: 'desc' }),
    staleTime: 30_000,
    retry: 0,
  });

  const items: ResearchHistoryItem[] = data?.content || [];

  if (isLoading) {
    return <RecentHistorySkeleton />;
  }

  if (items.length === 0) {
    return (
      <div className="space-y-3">
        <div className="flex items-center gap-2">
          <History className="h-4 w-4 text-muted-foreground" />
          <h3 className="text-sm font-medium">最近研究</h3>
        </div>
        <p className="text-xs text-muted-foreground text-center py-4">
          暂无研究记录，去首页开始第一次深度研究
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <History className="h-4 w-4 text-muted-foreground" />
          <h3 className="text-sm font-medium">最近研究</h3>
        </div>
        <Link href="/history">
          <Button variant="ghost" size="sm" className="text-xs h-7 gap-1">
            查看全部
            <ExternalLink className="h-3 w-3" />
          </Button>
        </Link>
      </div>

      <div className="grid gap-2">
        {items.map((item) => (
          <Link key={item.id} href={`/research/${item.sessionId}`}>
            <Card className="hover:bg-accent/50 transition-colors cursor-pointer">
              <CardHeader className="py-3 px-4">
                <div className="flex items-center justify-between gap-2">
                  <div className="min-w-0 flex-1">
                    <CardTitle className="text-sm font-medium line-clamp-1">
                      {item.query}
                    </CardTitle>
                    <CardDescription className="text-xs mt-1">
                      {item.status === 'COMPLETED'
                        ? `${item.wordCount.toLocaleString()} 字 · ${item.citationCount} 引用`
                        : '研究失败'}
                      {' · '}
                      {dayjs(item.createdAt).fromNow()}
                    </CardDescription>
                  </div>
                  <Badge
                    variant={item.status === 'COMPLETED' ? 'default' : 'destructive'}
                    className="shrink-0 text-xs"
                  >
                    {item.status === 'COMPLETED' ? '已完成' : '失败'}
                  </Badge>
                </div>
              </CardHeader>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}

export function RecentHistorySkeleton() {
  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2">
        <Skeleton className="h-4 w-4 rounded" />
        <Skeleton className="h-4 w-20" />
      </div>
      {Array.from({ length: 3 }).map((_, i) => (
        <Card key={i}>
          <CardHeader className="py-3 px-4">
            <Skeleton className="h-4 w-full mb-2" />
            <Skeleton className="h-3 w-48" />
          </CardHeader>
        </Card>
      ))}
    </div>
  );
}
