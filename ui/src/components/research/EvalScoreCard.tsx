'use client';

import { TrendingUp } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { cn } from '@/lib/utils';
import type { EvalResult } from '@/lib/types';
import { EVAL_DIMENSIONS } from '@/lib/constants';

interface EvalScoreCardProps {
  evalResult: EvalResult;
  className?: string;
}

/**
 * 将 1.0~5.0 的分数映射为星级
 */
function toStars(score: number): string {
  const full = Math.round(score);
  return '★'.repeat(full) + '☆'.repeat(5 - full);
}

/**
 * 分数颜色映射
 */
function scoreColor(score: number): string {
  if (score >= 4) return 'text-green-600 dark:text-green-400';
  if (score >= 3) return 'text-yellow-600 dark:text-yellow-400';
  return 'text-orange-600 dark:text-orange-400';
}

/**
 * 评估分数卡片
 *
 * 显示综合评分（大数字 + 星级）+ 五个维度的逐项分数。
 */
export function EvalScoreCard({ evalResult, className }: EvalScoreCardProps) {
  const { overallScore, summary } = evalResult;

  return (
    <Card className={cn('overflow-hidden', className)}>
      <CardContent className="p-4 space-y-4">
        {/* 综合评分 */}
        <div className="text-center">
          <div className="flex items-center justify-center gap-1 mb-1">
            <TrendingUp className="h-4 w-4 text-muted-foreground" />
            <span className="text-xs text-muted-foreground">综合评分</span>
          </div>
          <p className={cn('text-3xl font-bold', scoreColor(overallScore))}>
            {overallScore.toFixed(1)}
          </p>
          <p className="text-sm tracking-wider text-yellow-500">{toStars(overallScore)}</p>
        </div>

        {/* 五维评分 */}
        <div className="space-y-2">
          {EVAL_DIMENSIONS.map((dim) => {
            const score = (evalResult as unknown as Record<string, number>)[dim.key] || 0;
            return (
              <div key={dim.key} className="flex items-center justify-between gap-2">
                <span className="text-xs text-muted-foreground w-20">{dim.label}</span>
                <div className="flex-1 h-1.5 rounded-full bg-muted overflow-hidden">
                  <div
                    className={cn(
                      'h-full rounded-full transition-all duration-700',
                      score >= 4 ? 'bg-green-500' : score >= 3 ? 'bg-yellow-500' : 'bg-orange-500',
                    )}
                    style={{ width: `${(score / 5) * 100}%` }}
                  />
                </div>
                <span className={cn('text-xs font-mono w-8 text-right', scoreColor(score))}>
                  {score.toFixed(1)}
                </span>
              </div>
            );
          })}
        </div>

        {/* 评估摘要 */}
        {summary && (
          <p className="text-xs text-muted-foreground border-t pt-3 leading-relaxed">
            {summary}
          </p>
        )}
      </CardContent>
    </Card>
  );
}

/**
 * 评估加载中状态
 */
export function EvalScoreSkeleton() {
  return (
    <Card className="overflow-hidden">
      <CardContent className="p-4 space-y-4">
        <div className="text-center space-y-2">
          <div className="h-4 w-16 bg-muted rounded mx-auto animate-pulse" />
          <div className="h-8 w-16 bg-muted rounded mx-auto animate-pulse" />
          <div className="h-4 w-24 bg-muted rounded mx-auto animate-pulse" />
        </div>
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="flex items-center gap-2">
              <div className="h-3 w-16 bg-muted rounded animate-pulse" />
              <div className="flex-1 h-1.5 bg-muted rounded animate-pulse" />
              <div className="h-3 w-6 bg-muted rounded animate-pulse" />
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
