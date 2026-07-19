'use client';

import Link from 'next/link';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ExternalLink, RotateCw, Trash2 } from 'lucide-react';
import { parseEvalScores } from '@/hooks/useHistoryList';
import { MiniRadarChart } from '@/components/research/MiniRadarChart';
import type { ResearchHistoryItem } from '@/lib/types';
import dayjs from 'dayjs';

interface HistoryRowProps {
  item: ResearchHistoryItem;
  onDelete?: (sessionId: string) => void;
  onReRun?: (sessionId: string) => void;
}

/**
 * 研究历史表格行（桌面端）
 */
export function HistoryRow({ item, onDelete, onReRun }: HistoryRowProps) {
  const evalResult = parseEvalScores(item.evalScores);
  const isCompleted = item.status === 'COMPLETED';

  return (
    <tr className="border-b hover:bg-muted/50 transition-colors">
      {/* 查询 */}
      <td className="py-3 px-4">
        <Link
          href={`/research/${item.sessionId}`}
          className="text-sm font-medium hover:text-primary transition-colors line-clamp-1 block max-w-[300px]"
        >
          {item.query}
        </Link>
      </td>

      {/* 状态 */}
      <td className="py-3 px-2">
        <Badge
          variant={isCompleted ? 'default' : 'destructive'}
          className="text-[10px] h-5"
        >
          {isCompleted ? '已完成' : '失败'}
        </Badge>
      </td>

      {/* 字数 */}
      <td className="py-3 px-2 text-sm text-muted-foreground whitespace-nowrap">
        {isCompleted ? item.wordCount.toLocaleString() : '-'}
      </td>

      {/* 引用数 */}
      <td className="py-3 px-2 text-sm text-muted-foreground whitespace-nowrap">
        {isCompleted ? item.citationCount : '-'}
      </td>

      {/* 评分 */}
      <td className="py-3 px-2">
        <div className="flex items-center gap-2">
          {evalResult ? (
            <>
              <MiniRadarChart evalResult={evalResult} size={36} />
              <span className="text-sm font-mono font-medium">
                {evalResult.overallScore.toFixed(1)}
              </span>
            </>
          ) : isCompleted ? (
            <span className="text-xs text-muted-foreground">-</span>
          ) : (
            <span className="text-xs text-muted-foreground">评估中...</span>
          )}
        </div>
      </td>

      {/* 时间 */}
      <td className="py-3 px-2 text-sm text-muted-foreground whitespace-nowrap">
        {dayjs(item.createdAt).format('MM-DD HH:mm')}
      </td>

      {/* 操作 */}
      <td className="py-3 px-4">
        <div className="flex items-center gap-1">
          <Link href={`/research/${item.sessionId}`}>
            <Button variant="ghost" size="sm" className="h-7 text-xs">
              <ExternalLink className="h-3 w-3 mr-1" />
              查看
            </Button>
          </Link>
          {onReRun && (
            <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={() => onReRun(item.sessionId)}>
              <RotateCw className="h-3 w-3" />
            </Button>
          )}
          {onDelete && (
            <Button
              variant="ghost"
              size="sm"
              className="h-7 text-xs text-destructive"
              onClick={() => onDelete(item.sessionId)}
            >
              <Trash2 className="h-3 w-3" />
            </Button>
          )}
        </div>
      </td>
    </tr>
  );
}
