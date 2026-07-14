'use client';

import Link from 'next/link';
import { Card, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { History, ExternalLink } from 'lucide-react';
import { Button } from '@/components/ui/button';

/**
 * 模拟最近研究历史（Phase 2 mock 数据，Phase 5 接入后端 API）
 *
 * 显示首页底部最近 3 条研究记录。
 * Phase 5 替换为 useHistoryList Hook 的真实数据。
 */

interface MockHistoryItem {
  id: string;
  query: string;
  status: 'COMPLETED' | 'ERROR';
  wordCount: number;
  citationCount: number;
  createdAt: string;
}

// Phase 2 mock 数据 — Phase 5 删除，替换为 API 调用
const MOCK_HISTORY: MockHistoryItem[] = [
  {
    id: 'mock-001',
    query: '2026年中国新能源汽车市场趋势与竞争格局分析',
    status: 'COMPLETED',
    wordCount: 4521,
    citationCount: 38,
    createdAt: '2 小时前',
  },
  {
    id: 'mock-002',
    query: '全球AI芯片产业链格局及国产替代进展',
    status: 'COMPLETED',
    wordCount: 3128,
    citationCount: 25,
    createdAt: '5 小时前',
  },
  {
    id: 'mock-003',
    query: '光伏产业N型电池技术路线对比',
    status: 'ERROR',
    wordCount: 0,
    citationCount: 0,
    createdAt: '昨天',
  },
];

export function RecentHistoryPreview() {
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
        {MOCK_HISTORY.map((item) => (
          <Link key={item.id} href={`/research/${item.id}`}>
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
                      {item.createdAt}
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

      {/* Phase 5 提示 */}
      <p className="text-xs text-muted-foreground text-center">
        Phase 5 将替换为后端历史 API 真实数据
      </p>
    </div>
  );
}

/** Loading 骨架 — 历史数据加载中 */
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
