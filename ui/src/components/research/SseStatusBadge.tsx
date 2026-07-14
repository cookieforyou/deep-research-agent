'use client';

import { cn } from '@/lib/utils';
import type { SseConnectionStatus } from '@/lib/types';
import { Button } from '@/components/ui/button';

interface SseStatusBadgeProps {
  /** SSE 连接状态 */
  status: SseConnectionStatus;
  /** 重连回调（status === 'error' 或 'disconnected' 时可用） */
  onReconnect?: () => void;
}

const STATUS_CONFIG: Record<
  SseConnectionStatus,
  { label: string; dotColor: string; animate?: string }
> = {
  idle: { label: '空闲', dotColor: 'bg-muted-foreground' },
  connecting: { label: '连接中', dotColor: 'bg-yellow-500', animate: 'animate-spin' },
  connected: { label: '已连接', dotColor: 'bg-green-500', animate: 'animate-pulse' },
  disconnected: { label: '已断开', dotColor: 'bg-muted-foreground' },
  error: { label: '连接错误', dotColor: 'bg-destructive' },
};

/**
 * SSE 连接状态指示器
 *
 * 显示在研究报告页面右上角，实时反馈 SSE 连接状态。
 *
 * - connected: 绿色圆点 + 脉冲动画
 * - connecting: 黄色圆点 + 旋转动画
 * - disconnected/error: 灰色/红色圆点 + 重试按钮
 */
export function SseStatusBadge({ status, onReconnect }: SseStatusBadgeProps) {
  const config = STATUS_CONFIG[status];
  const canReconnect = status === 'disconnected' || status === 'error';

  return (
    <div className="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs">
      <span className="relative flex h-2 w-2">
        <span
          className={cn(
            'absolute inline-flex h-full w-full rounded-full',
            config.dotColor,
            config.animate,
          )}
        />
      </span>
      <span className="text-muted-foreground">{config.label}</span>
      {canReconnect && onReconnect && (
        <Button
          variant="outline"
          size="sm"
          className="h-5 px-2 text-[10px] ml-1"
          onClick={onReconnect}
        >
          重连
        </Button>
      )}
    </div>
  );
}
