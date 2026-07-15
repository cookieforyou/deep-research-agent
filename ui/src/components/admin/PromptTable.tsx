'use client';

import { useState } from 'react';
import { usePromptList } from '@/hooks/usePromptManagement';
import { PromptStatusBadge } from './PromptStatusBadge';
import { PromptEditor } from './PromptEditor';
import { AbTestConfig } from './AbTestConfig';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { AlertCircle, Edit, RefreshCw, TestTube } from 'lucide-react';
import { PROMPT_TEMPLATE_NAMES } from '@/lib/constants';
import type { PromptTemplate } from '@/lib/types';
import dayjs from 'dayjs';

/**
 * Prompt 模板管理表格
 *
 * 显示全部 8 个 Prompt 模板，支持点击编辑。
 */
export function PromptTable() {
  const { data: prompts, isLoading, isError, refetch } = usePromptList();
  const [editing, setEditing] = useState<PromptTemplate | null>(null);
  const [showAbConfig, setShowAbConfig] = useState(false);

  // 加载中
  if (isLoading) {
    return (
      <div className="space-y-2">
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} className="flex items-center gap-4 p-4 border rounded-lg">
            <Skeleton className="h-5 w-32" />
            <Skeleton className="h-5 w-20" />
            <Skeleton className="h-5 w-16" />
            <Skeleton className="h-5 w-12" />
            <Skeleton className="h-5 w-24" />
            <Skeleton className="h-8 w-16 ml-auto" />
          </div>
        ))}
      </div>
    );
  }

  // 错误
  if (isError) {
    return (
      <div className="flex flex-col items-center gap-3 py-12 text-center">
        <AlertCircle className="h-8 w-8 text-destructive" />
        <p className="text-sm text-muted-foreground">加载模板列表失败</p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          <RefreshCw className="h-4 w-4 mr-2" />
          重试
        </Button>
      </div>
    );
  }

  const list: PromptTemplate[] = prompts || [];

  return (
    <>
      {/* 工具栏 */}
      <div className="flex items-center justify-between mb-3">
        <p className="text-xs text-muted-foreground">
          {list.length} 个模板 · 修改后 1 分钟内自动生效
        </p>
        <Button
          variant="outline"
          size="sm"
          className="gap-1 text-xs"
          onClick={() => setShowAbConfig(true)}
        >
          <TestTube className="h-3.5 w-3.5" />
          A/B 配置
        </Button>
      </div>

      <div className="rounded-lg border overflow-hidden">
        <table className="w-full">
          <thead className="bg-muted/50">
            <tr>
              <th className="text-left text-xs font-medium text-muted-foreground py-3 px-4 w-[180px]">
                ID / 名称
              </th>
              <th className="text-left text-xs font-medium text-muted-foreground py-3 px-2 w-[60px]">
                版本
              </th>
              <th className="text-left text-xs font-medium text-muted-foreground py-3 px-2 w-[80px]">
                状态
              </th>
              <th className="text-left text-xs font-medium text-muted-foreground py-3 px-2 w-[60px]">
                AB
              </th>
              <th className="text-left text-xs font-medium text-muted-foreground py-3 px-2 w-[140px]">
                更新时间
              </th>
              <th className="text-right text-xs font-medium text-muted-foreground py-3 px-4 w-[80px]">
                操作
              </th>
            </tr>
          </thead>
          <tbody>
            {list.map((prompt) => {
              const name = PROMPT_TEMPLATE_NAMES[prompt.id] || prompt.id;
              return (
                <tr key={prompt.id} className="border-b hover:bg-muted/50 transition-colors">
                  <td className="py-3 px-4">
                    <div className="flex flex-col">
                      <span className="text-sm font-medium">{name}</span>
                      <code className="text-[10px] text-muted-foreground">{prompt.id}</code>
                    </div>
                  </td>
                  <td className="py-3 px-2">
                    <code className="text-xs text-muted-foreground">v{prompt.version}</code>
                  </td>
                  <td className="py-3 px-2">
                    <PromptStatusBadge status={prompt.status} />
                  </td>
                  <td className="py-3 px-2">
                    <span className="text-xs text-muted-foreground">
                      {prompt.abGroup || '-'}
                    </span>
                  </td>
                  <td className="py-3 px-2 text-xs text-muted-foreground whitespace-nowrap">
                    {dayjs(prompt.updatedAt).format('MM-DD HH:mm')}
                  </td>
                  <td className="py-3 px-4 text-right">
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-7 text-xs"
                      onClick={() => setEditing(prompt)}
                    >
                      <Edit className="h-3 w-3 mr-1" />
                      编辑
                    </Button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* 空状态 */}
      {list.length === 0 && (
        <div className="text-center py-12 text-muted-foreground">
          <p className="text-sm">暂无模板数据</p>
          <p className="text-xs mt-1">请确认数据库已初始化 Prompt 模板表</p>
        </div>
      )}

      {/* 编辑器 Dialog */}
      <PromptEditor
        template={editing}
        open={!!editing}
        onOpenChange={(open) => {
          if (!open) setEditing(null);
        }}
      />

      {/* A/B 测试配置 Dialog */}
      <AbTestConfig open={showAbConfig} onOpenChange={setShowAbConfig} />
    </>
  );
}
