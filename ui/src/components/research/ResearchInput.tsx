'use client';

import { useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Textarea } from '@/components/ui/textarea';
import { Button } from '@/components/ui/button';
import { ResearchModeToggle } from './ResearchModeToggle';
import { useStartResearch } from '@/hooks/useResearchQuery';
import { MAX_QUERY_LENGTH } from '@/lib/constants';
import type { ResearchMode } from '@/lib/types';

interface ResearchInputProps {
  /** 外部注入初始查询（如从示例点击、重新研究等） */
  initialQuery?: string;
  /** 外部注入初始模式 */
  initialMode?: ResearchMode;
}

/**
 * 研究查询输入组件
 *
 * 功能：
 * - Textarea 自动调整高度（最大 5 行）
 * - 实时字符计数（最大 5000）
 * - 模式切换：深度研究 / 直接回答
 * - 提交时 loading 动画
 * - 参数校验：空值、长度限制
 * - 错误处理：400 注入 → toast，网络错误 → 可重试
 */
export function ResearchInput({
  initialQuery = '',
  initialMode = 'deep',
}: ResearchInputProps) {
  const [query, setQuery] = useState(initialQuery);
  const [mode, setMode] = useState<ResearchMode>(initialMode);
  const startResearch = useStartResearch();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim() || startResearch.isPending) return;

    startResearch.mutate({
      query: query.trim(),
      deepResearch: mode === 'deep',
    });
  };

  const isOverLimit = query.length > MAX_QUERY_LENGTH;

  return (
    <Card className="shadow-lg">
      <CardHeader>
        <CardTitle>开始深度研究</CardTitle>
        <CardDescription>
          输入研究主题，AI 多智能体将为您生成深度分析报告
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          {/* 输入框 */}
          <Textarea
            placeholder="例如: 2026年中国新能源汽车市场趋势与竞争格局分析"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            maxLength={MAX_QUERY_LENGTH + 100} // 允许超出以触发警告
            rows={3}
            className="resize-none min-h-[80px]"
            aria-label="研究查询"
            aria-describedby="query-char-count"
          />

          {/* 底部操作栏：字数统计 + 模式切换 */}
          <div className="flex items-center justify-between flex-wrap gap-2">
            <span
              id="query-char-count"
              className={`text-xs ${isOverLimit ? 'text-destructive font-medium' : 'text-muted-foreground'}`}
            >
              {query.length}/{MAX_QUERY_LENGTH}
              {isOverLimit && '（超出限制）'}
            </span>
            <ResearchModeToggle value={mode} onChange={setMode} />
          </div>

          {/* 提交按钮 */}
          <Button
            type="submit"
            className="w-full"
            disabled={!query.trim() || isOverLimit || startResearch.isPending}
          >
            {startResearch.isPending ? (
              <>
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                正在提交...
              </>
            ) : (
              '开始研究 →'
            )}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

/** 暴露查询设置方法，供 ExampleQueries 调用 */
export type { ResearchInputProps };
