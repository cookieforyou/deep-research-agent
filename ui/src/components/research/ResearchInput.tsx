'use client';

import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Textarea } from '@/components/ui/textarea';
import { Button } from '@/components/ui/button';
import { useState } from 'react';
import { MAX_QUERY_LENGTH } from '@/lib/constants';

/**
 * 研究查询输入组件（Phase 1 骨架，Phase 2 完整实现）
 */
export function ResearchInput() {
  const [query, setQuery] = useState('');
  const [mode, setMode] = useState<'deep' | 'direct'>('deep');

  return (
    <Card className="shadow-lg">
      <CardHeader>
        <CardTitle>开始深度研究</CardTitle>
        <CardDescription>
          输入研究主题，AI 多智能体将为您生成深度分析报告（功能将在 Phase 2 接入后端 API）
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <Textarea
          placeholder="例如: 2026年中国新能源汽车市场趋势与竞争格局分析"
          value={query}
          onChange={(e) => setQuery(e.target.value.slice(0, MAX_QUERY_LENGTH))}
          maxLength={MAX_QUERY_LENGTH}
          rows={3}
          className="resize-none"
        />
        <div className="flex items-center justify-between">
          <span className="text-xs text-muted-foreground">
            {query.length}/{MAX_QUERY_LENGTH}
          </span>
          <div className="flex items-center gap-2">
            <Button
              variant={mode === 'deep' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setMode('deep')}
            >
              深度研究
            </Button>
            <Button
              variant={mode === 'direct' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setMode('direct')}
            >
              直接回答
            </Button>
          </div>
        </div>
        <Button className="w-full" disabled={!query.trim()}>
          开始研究 →
        </Button>
      </CardContent>
    </Card>
  );
}
