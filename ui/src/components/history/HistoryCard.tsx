'use client';

import Link from 'next/link';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { FileText, ExternalLink, RotateCw, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { parseEvalScores } from '@/hooks/useHistoryList';
import { MiniRadarChart } from '@/components/research/MiniRadarChart';
import type { ResearchHistoryItem } from '@/lib/types';
import dayjs from 'dayjs';

interface HistoryCardProps {
  item: ResearchHistoryItem;
  onDelete?: (sessionId: string) => void;
  onReRun?: (sessionId: string) => void;
}

/**
 * 研究历史卡片（移动端）
 *
 * 显示单条研究记录的关键信息：查询、状态、字数、引用数、评分、日期。
 */
export function HistoryCard({ item, onDelete, onReRun }: HistoryCardProps) {
  const evalResult = parseEvalScores(item.evalScores);
  const isCompleted = item.status === 'COMPLETED';

  return (
    <Link href={`/research/${item.sessionId}`}>
      <Card className="hover:bg-accent/50 transition-colors cursor-pointer group">
        <CardContent className="p-4">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0 flex-1">
              {/* 查询文本 */}
              <p className="text-sm font-medium line-clamp-2 mb-2">{item.query}</p>

              {/* 元数据 */}
              <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                <Badge
                  variant={isCompleted ? 'default' : 'destructive'}
                  className="text-[10px] h-5"
                >
                  {isCompleted ? '已完成' : '失败'}
                </Badge>
                {isCompleted && (
                  <>
                    <span className="flex items-center gap-1">
                      <FileText className="h-3 w-3" />
                      {item.wordCount.toLocaleString()} 字
                    </span>
                    <span>{item.citationCount} 引用</span>
                  </>
                )}
                <span>{dayjs(item.createdAt).format('MM-DD HH:mm')}</span>
              </div>
            </div>

            {/* 迷你雷达图 */}
            {evalResult && (
              <div className="shrink-0">
                <MiniRadarChart evalResult={evalResult} size={48} />
              </div>
            )}
          </div>

          {/* 操作按钮（hover 显示） */}
          <div className="flex items-center gap-1 mt-2 opacity-0 group-hover:opacity-100 transition-opacity">
            <Link href={`/research/${item.sessionId}`}>
              <Button variant="ghost" size="sm" className="h-7 text-xs">
                <ExternalLink className="h-3 w-3 mr-1" />
                查看
              </Button>
            </Link>
            {onReRun && (
              <Button
                variant="ghost"
                size="sm"
                className="h-7 text-xs"
                onClick={(e) => {
                  e.preventDefault();
                  onReRun(item.sessionId);
                }}
              >
                <RotateCw className="h-3 w-3 mr-1" />
                重新研究
              </Button>
            )}
            {onDelete && (
              <Button
                variant="ghost"
                size="sm"
                className="h-7 text-xs text-destructive"
                onClick={(e) => {
                  e.preventDefault();
                  onDelete(item.sessionId);
                }}
              >
                <Trash2 className="h-3 w-3 mr-1" />
                删除
              </Button>
            )}
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}
