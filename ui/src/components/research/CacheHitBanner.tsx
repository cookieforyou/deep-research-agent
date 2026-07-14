'use client';

import { Zap } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';

interface CacheHitBannerProps {
  /** 缓存命中时的消息 */
  message?: string;
}

/**
 * 语义缓存命中横幅
 *
 * 当 SSE 流返回 CACHE_HIT 事件时显示，替代 WorkflowTimeline。
 * 表示相同的查询之前已经研究过，直接返回缓存报告。
 */
export function CacheHitBanner({ message }: CacheHitBannerProps) {
  return (
    <Card className="border-green-200 bg-green-50 dark:bg-green-950/20 dark:border-green-800">
      <CardContent className="flex items-center gap-3 p-4">
        <div className="flex h-10 w-10 items-center justify-center rounded-full bg-green-100 dark:bg-green-900/50">
          <Zap className="h-5 w-5 text-green-600 dark:text-green-400" />
        </div>
        <div>
          <p className="text-sm font-medium text-green-800 dark:text-green-300">
            语义缓存命中
          </p>
          <p className="text-xs text-green-700/70 dark:text-green-400/70 mt-0.5">
            {message ||
              '该查询已有研究结果，直接返回缓存报告。报告内容与首次研究一致。'}
          </p>
        </div>
      </CardContent>
    </Card>
  );
}
