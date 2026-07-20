'use client';

import { useState, useEffect, useMemo } from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Loader2, TestTube, Save, Info } from 'lucide-react';
import { usePromptList, useBatchUpdateAbGroup } from '@/hooks/usePromptManagement';
import { PROMPT_TEMPLATE_NAMES } from '@/lib/constants';
import type { PromptTemplate } from '@/lib/types';

interface AbTestConfigProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * A/B 测试配置弹窗。
 *
 * 允许管理员为所有 8 个 Prompt 模板批量配置 A/B 分组，
 * 用于对比不同 Prompt 版本在生产环境中的效果差异。
 */
export function AbTestConfig({ open, onOpenChange }: AbTestConfigProps) {
  const { data: prompts } = usePromptList();
  const batchMutation = useBatchUpdateAbGroup();

  // 本地编辑状态：{ [promptId]: 'none' | 'A' | 'B' }
  const [groups, setGroups] = useState<Record<string, string>>({});

  // 初始化
  useEffect(() => {
    if (prompts) {
      const initial: Record<string, string> = {};
      for (const p of prompts) {
        initial[p.id] = p.abGroup || 'none';
      }
      setGroups(initial);
    }
  }, [prompts]);

  // 变更统计
  const changes = useMemo(() => {
    if (!prompts) return [] as PromptTemplate[];
    return prompts.filter((p: PromptTemplate) => {
      const current = groups[p.id];
      const original = p.abGroup || 'none';
      return current !== undefined && current !== original;
    });
  }, [prompts, groups]);

  const hasChanges = changes.length > 0;

  const handleGroupChange = (promptId: string, group: string) => {
    setGroups((prev) => ({ ...prev, [promptId]: group }));
  };

  const handleSaveAll = () => {
    if (!prompts) return;
    const items = changes.map((c: PromptTemplate) => ({
      id: c.id,
      abGroup: groups[c.id] === 'none' ? null : (groups[c.id] as 'A' | 'B' | null),
    }));
    batchMutation.mutate(items, {
      onSuccess: () => onOpenChange(false),
    });
  };

  const handleClearAll = () => {
    setGroups((prev) => {
      const cleared = { ...prev };
      Object.keys(cleared).forEach((k) => (cleared[k] = 'none'));
      return cleared;
    });
  };

  const list: PromptTemplate[] = prompts || [];

  // 分组统计
  const groupACount = Object.values(groups).filter((g) => g === 'A').length;
  const groupBCount = Object.values(groups).filter((g) => g === 'B').length;
  const unassignedCount = Object.values(groups).filter((g) => g === 'none').length;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg max-h-[80vh] flex flex-col">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <TestTube className="h-4 w-4" />
            A/B 测试配置
          </DialogTitle>
          <DialogDescription>
            为 Prompt 模板分配 A/B 分组，用于对比不同 Prompt 版本在生产环境中的效果。
            <br />
            修改后将在 1 分钟内自动生效。
          </DialogDescription>
        </DialogHeader>

        {/* 分组概览 */}
        <div className="flex gap-2">
          <Badge variant="secondary" className="text-xs">
            A 组: {groupACount}
          </Badge>
          <Badge variant="secondary" className="text-xs">
            B 组: {groupBCount}
          </Badge>
          <Badge variant="outline" className="text-xs text-muted-foreground">
            未分配: {unassignedCount}
          </Badge>
        </div>

        {/* 提示 */}
        <div className="flex items-start gap-2 rounded-md bg-muted/50 p-3 text-xs text-muted-foreground">
          <Info className="h-3.5 w-3.5 mt-0.5 shrink-0" />
          <span>
            A/B 分组允许同一查询路由到不同 Prompt 版本，通过对比 A 组和 B
            组的评估分数来验证新 Prompt 的效果。建议每次只对比 1-2 个模板。
          </span>
        </div>

        {/* 模板列表 + 分组选择 */}
        <div className="flex-1 overflow-y-auto space-y-2">
          {list.map((prompt) => {
            const name = PROMPT_TEMPLATE_NAMES[prompt.id] || prompt.id;
            const current = groups[prompt.id] ?? 'none';
            const changed =
              current !== (prompt.abGroup || 'none');

            return (
              <div
                key={prompt.id}
                className="flex items-center justify-between rounded-lg border p-3"
              >
                <div className="flex items-center gap-2 min-w-0">
                  <span className="text-sm font-medium truncate">{name}</span>
                  <code className="text-[10px] text-muted-foreground">{prompt.id}</code>
                  {changed && (
                    <Badge
                      variant="default"
                      className="text-[10px] h-5 bg-amber-500 hover:bg-amber-500"
                    >
                      已修改
                    </Badge>
                  )}
                </div>
                <Select
                  value={current}
                  onValueChange={(v) => handleGroupChange(prompt.id, v)}
                >
                  <SelectTrigger className="h-7 w-[90px] text-xs ml-2 shrink-0">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="none">不分组</SelectItem>
                    <SelectItem value="A">A 组</SelectItem>
                    <SelectItem value="B">B 组</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            );
          })}
        </div>

        <DialogFooter className="flex items-center gap-2 pt-2">
          <Button
            variant="outline"
            size="sm"
            onClick={handleClearAll}
            disabled={groupACount === 0 && groupBCount === 0}
          >
            全部清除
          </Button>
          <div className="flex-1" />
          <Button
            variant="ghost"
            size="sm"
            onClick={() => onOpenChange(false)}
          >
            取消
          </Button>
          <Button
            size="sm"
            onClick={handleSaveAll}
            disabled={!hasChanges || batchMutation.isPending}
          >
            {batchMutation.isPending ? (
              <Loader2 className="h-4 w-4 mr-1 animate-spin" />
            ) : (
              <Save className="h-4 w-4 mr-1" />
            )}
            保存全部{hasChanges && ` (${changes.length})`}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
