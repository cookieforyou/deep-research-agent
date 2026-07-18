'use client';

import { useState } from 'react';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import { Calendar, Filter, SlidersHorizontal, X } from 'lucide-react';

interface HistoryFiltersProps {
  status: string;
  sortBy: string;
  startDate?: string;
  endDate?: string;
  minScore?: number;
  onStatusChange: (status: string) => void;
  onSortChange: (sortBy: string) => void;
  onDateRangeChange: (startDate?: string, endDate?: string) => void;
  onScoreRangeChange: (minScore?: number) => void;
}

/**
 * 历史筛选器 — 完整版
 *
 * - 状态筛选：全部 / 已完成 / 失败
 * - 排序：创建时间 / 字数 / 评分
 * - 日期范围：开始日期 / 结束日期
 * - 评分筛选：最低评分滑动条
 */
export function HistoryFilters({
  status,
  sortBy,
  startDate,
  endDate,
  minScore,
  onStatusChange,
  onSortChange,
  onDateRangeChange,
  onScoreRangeChange,
}: HistoryFiltersProps) {
  const [showAdvanced, setShowAdvanced] = useState(false);

  const hasActiveFilters =
    startDate || endDate || (minScore !== undefined && minScore > 0);

  return (
    <div className="flex flex-col gap-2">
      {/* 主筛选行 */}
      <div className="flex items-center gap-2 flex-wrap">
        {/* 状态筛选 */}
        <Select value={status} onValueChange={onStatusChange}>
          <SelectTrigger className="h-8 w-[120px] text-xs">
            <SelectValue placeholder="全部状态" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="">全部</SelectItem>
            <SelectItem value="COMPLETED">已完成</SelectItem>
            <SelectItem value="ERROR">失败</SelectItem>
          </SelectContent>
        </Select>

        {/* 排序 */}
        <Select value={sortBy} onValueChange={onSortChange}>
          <SelectTrigger className="h-8 w-[130px] text-xs">
            <SelectValue placeholder="排序方式" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="createdAt">最近创建</SelectItem>
            <SelectItem value="wordCount">最多字数</SelectItem>
            <SelectItem value="overallScore">最高评分</SelectItem>
          </SelectContent>
        </Select>

        {/* 高级筛选按钮 */}
        <Button
          variant={showAdvanced || hasActiveFilters ? 'default' : 'outline'}
          size="sm"
          className="h-8 text-xs gap-1"
          onClick={() => setShowAdvanced(!showAdvanced)}
        >
          <SlidersHorizontal className="h-3 w-3" />
          高级筛选
          {hasActiveFilters && (
            <span className="ml-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-primary-foreground text-[10px] text-primary">
              !
            </span>
          )}
        </Button>

        {/* 清除所有筛选 */}
        {hasActiveFilters && (
          <Button
            variant="ghost"
            size="sm"
            className="h-8 text-xs gap-1 text-muted-foreground"
            onClick={() => {
              onDateRangeChange(undefined, undefined);
              onScoreRangeChange(undefined);
            }}
          >
            <X className="h-3 w-3" />
            清除
          </Button>
        )}
      </div>

      {/* 高级筛选面板 */}
      {showAdvanced && (
        <div className="flex flex-col sm:flex-row gap-3 rounded-lg border bg-muted/30 p-3">
          {/* 日期范围 */}
          <div className="flex-1 space-y-1.5">
            <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <Calendar className="h-3 w-3" />
              <span>日期范围</span>
            </div>
            <div className="flex items-center gap-2">
              <input
                type="date"
                value={startDate || ''}
                onChange={(e) =>
                  onDateRangeChange(e.target.value || undefined, endDate)
                }
                className="h-8 rounded-md border bg-background px-2 text-xs w-full min-w-0"
              />
              <span className="text-xs text-muted-foreground shrink-0">至</span>
              <input
                type="date"
                value={endDate || ''}
                onChange={(e) =>
                  onDateRangeChange(startDate, e.target.value || undefined)
                }
                className="h-8 rounded-md border bg-background px-2 text-xs w-full min-w-0"
              />
            </div>
          </div>

          {/* 评分筛选 */}
          <div className="flex-1 space-y-1.5">
            <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <Filter className="h-3 w-3" />
              <span>最低评分: {minScore !== undefined && minScore > 0 ? `≥ ${minScore.toFixed(1)}` : '不限'}</span>
            </div>
            <input
              type="range"
              min="0"
              max="5"
              step="0.5"
              value={minScore ?? 0}
              onChange={(e) => {
                const val = parseFloat(e.target.value);
                onScoreRangeChange(val > 0 ? val : undefined);
              }}
              className="w-full h-2 accent-primary"
            />
            <div className="flex justify-between text-[10px] text-muted-foreground">
              <span>0</span>
              <span>1</span>
              <span>2</span>
              <span>3</span>
              <span>4</span>
              <span>5</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
