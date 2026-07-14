'use client';

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';

interface HistoryFiltersProps {
  status: string;
  sortBy: string;
  onStatusChange: (status: string) => void;
  onSortChange: (sortBy: string) => void;
}

/**
 * 历史筛选器
 *
 * - 状态筛选：全部 / 已完成 / 失败
 * - 排序：创建时间 / 字数 / 评分
 */
export function HistoryFilters({
  status,
  sortBy,
  onStatusChange,
  onSortChange,
}: HistoryFiltersProps) {
  return (
    <div className="flex items-center gap-2">
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
          <SelectItem value="evalScore">最高评分</SelectItem>
        </SelectContent>
      </Select>
    </div>
  );
}
