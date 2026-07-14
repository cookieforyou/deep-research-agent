'use client';

import { XCircle, RefreshCw, Home } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import Link from 'next/link';

interface ResearchErrorViewProps {
  /** 错误消息 */
  message?: string;
  /** 重新研究回调 */
  onRetry?: () => void;
}

/**
 * 研究失败错误展示
 *
 * 在工作流发生错误时显示，提供：
 * - 错误信息
 * - 重新研究按钮
 * - 返回首页链接
 */
export function ResearchErrorView({ message, onRetry }: ResearchErrorViewProps) {
  return (
    <Card className="border-destructive/50 bg-destructive/5">
      <CardContent className="flex flex-col items-center gap-4 p-8 text-center">
        <div className="flex h-16 w-16 items-center justify-center rounded-full bg-destructive/10">
          <XCircle className="h-8 w-8 text-destructive" />
        </div>
        <div>
          <h3 className="text-lg font-semibold">研究失败</h3>
          <p className="text-sm text-muted-foreground mt-1 max-w-md">
            {message || '研究过程中发生未知错误。可能是后端 Agent 执行失败或外部搜索服务不可用。'}
          </p>
        </div>
        <div className="flex gap-3">
          {onRetry && (
            <Button variant="default" size="sm" onClick={onRetry}>
              <RefreshCw className="h-4 w-4 mr-2" />
              重新研究
            </Button>
          )}
          <Link href="/">
            <Button variant="outline" size="sm">
              <Home className="h-4 w-4 mr-2" />
              返回首页
            </Button>
          </Link>
        </div>
      </CardContent>
    </Card>
  );
}
