'use client';

import { cn } from '@/lib/utils';
import { Check, Loader2, X, Clock } from 'lucide-react';

export type NodeStatus = 'pending' | 'active' | 'done' | 'error';

interface WorkflowNodeProps {
  /** 节点图标组件 */
  icon: React.ReactNode;
  /** 阶段标签 */
  label: string;
  /** 节点状态 */
  status: NodeStatus;
  /** 状态描述信息 */
  message?: string;
  /** 已耗时（毫秒） */
  elapsed?: number;
  /** 节点颜色（CSS color string） */
  color?: string;
  /** 是否为最后一个节点（不绘制下方连线） */
  isLast?: boolean;
  /** 子节点（如 dual_search 的 Web + Local 子进度） */
  children?: React.ReactNode;
}

/**
 * 格式化毫秒为人类可读的耗时字符串
 */
function formatElapsed(ms: number): string {
  if (ms < 1000) return `${ms.toFixed(0)}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  const minutes = Math.floor(ms / 60000);
  const seconds = Math.round((ms % 60000) / 1000);
  return `${minutes}m ${seconds}s`;
}

/**
 * 单个工作流节点
 *
 * 三种状态:
 * - pending: 灰色 + 虚线边框
 * - active:  彩色脉冲动画 + 旋转指示器
 * - done:    绿色对勾
 * - error:   红色 X
 */
export function WorkflowNode({
  icon,
  label,
  status,
  message,
  elapsed,
  color = 'hsl(220, 90%, 56%)',
  isLast = false,
  children,
}: WorkflowNodeProps) {
  const isPending = status === 'pending';
  const isActive = status === 'active';
  const isDone = status === 'done';
  const isError = status === 'error';

  return (
    <div
      className={cn(
        'relative flex gap-4 transition-all duration-500',
        status !== 'pending' && 'opacity-100',
      )}
    >
      {/* 左侧：圆圈 + 连线 */}
      <div className="flex flex-col items-center shrink-0">
        {/* 状态圆 */}
        <div
          className={cn(
            'relative flex h-9 w-9 items-center justify-center rounded-full border-2 transition-all duration-500',
            isPending && 'border-muted-foreground/30 bg-transparent',
            isActive && 'border-transparent bg-primary/10 scale-110',
            isDone && 'border-green-500 bg-green-50 dark:bg-green-950/30',
            isError && 'border-destructive bg-destructive/10',
          )}
          style={
            isActive
              ? {
                  borderColor: color,
                  boxShadow: `0 0 8px ${color}40`,
                }
              : undefined
          }
        >
          {isPending && (
            <div className="h-2.5 w-2.5 rounded-full bg-muted-foreground/30" />
          )}
          {isActive && (
            <Loader2
              className="h-5 w-5 animate-spin"
              style={{ color }}
            />
          )}
          {isDone && <Check className="h-5 w-5 text-green-600 dark:text-green-400" />}
          {isError && <X className="h-5 w-5 text-destructive" />}
        </div>

        {/* 连线（SVG） */}
        {!isLast && (
          <div className="relative w-9 h-8">
            <svg className="absolute inset-0 w-full h-full" viewBox="0 0 36 32">
              <line
                x1="18"
                y1="0"
                x2="18"
                y2="32"
                className={cn(
                  'transition-colors duration-500',
                  isDone ? 'stroke-green-500' : 'stroke-muted-foreground/30',
                )}
                strokeWidth="2"
                strokeDasharray={isPending ? '4 3' : 'none'}
              />
            </svg>
          </div>
        )}
      </div>

      {/* 右侧：内容 */}
      <div className={cn('flex-1 pb-6', isLast && 'pb-0')}>
        {/* 标题行 */}
        <div className="flex items-center gap-2">
          <span className="shrink-0 text-muted-foreground">{icon}</span>
          <span
            className={cn(
              'text-sm font-medium transition-colors duration-500',
              isPending && 'text-muted-foreground/50',
              isActive && 'text-foreground',
              isDone && 'text-green-700 dark:text-green-400',
              isError && 'text-destructive',
            )}
          >
            {label}
          </span>

          {/* 耗时 */}
          {elapsed !== undefined && !isPending && (
            <span className="inline-flex items-center gap-1 text-[11px] text-muted-foreground ml-auto">
              <Clock className="h-3 w-3" />
              {formatElapsed(elapsed)}
            </span>
          )}
        </div>

        {/* 状态描述 */}
        {message && (
          <p
            className={cn(
              'text-xs mt-1',
              isPending && 'text-muted-foreground/40',
              isActive && 'text-muted-foreground',
              isDone && 'text-muted-foreground',
              isError && 'text-destructive/80',
            )}
          >
            {message}
          </p>
        )}

        {/* 子节点（dual_search Web + Local 进度） */}
        {children && (
          <div className="mt-2 pl-1 space-y-2">{children}</div>
        )}
      </div>
    </div>
  );
}

// =========================== 子搜索进度条 ===========================

interface SearchChildProps {
  label: string;
  current: number;
  total: number;
  isDone: boolean;
}

/**
 * 搜索子节点进度条（用于 dual_search 下的 Web/Local 分支）
 */
export function SearchChildNode({ label, current, total, isDone }: SearchChildProps) {
  const percent = total > 0 ? (current / total) * 100 : 0;

  return (
    <div className="flex items-center gap-3 pl-5">
      <span className="text-xs text-muted-foreground w-12 shrink-0">{label}</span>
      <div className="flex-1 h-1.5 rounded-full bg-muted overflow-hidden">
        <div
          className={cn(
            'h-full rounded-full transition-all duration-700',
            isDone ? 'bg-green-500' : 'bg-primary',
          )}
          style={{ width: `${Math.min(percent, 100)}%` }}
        />
      </div>
      <span
        className={cn(
          'text-xs font-mono w-12 shrink-0 text-right',
          isDone ? 'text-green-600' : 'text-muted-foreground',
        )}
      >
        {current}/{total}
      </span>
    </div>
  );
}
