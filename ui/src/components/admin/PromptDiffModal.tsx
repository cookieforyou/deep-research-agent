'use client';

import { useState, useEffect } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { History, ArrowRight } from 'lucide-react';

interface PromptDiffModalProps {
  /** 打开编辑器时的原始内容（数据库已保存版本） */
  previousContent?: string;
  /** 当前实时编辑中的内容 */
  currentContent?: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Prompt 版本对比弹窗。
 *
 * 显示当前版本与参考版本的差异（简易逐行对比）。
 */
export function PromptDiffModal({ previousContent, currentContent, open, onOpenChange }: PromptDiffModalProps) {
  const [diffs, setDiffs] = useState<{ type: 'add' | 'remove' | 'same'; line: string }[]>([]);

  useEffect(() => {
    if (previousContent == null || currentContent == null) {
      setDiffs([]);
      return;
    }
    if (previousContent === currentContent) {
      setDiffs([]);
      return;
    }

    const currentLines = currentContent.split('\n');
    const prevLines = previousContent.split('\n');
    const result: { type: 'add' | 'remove' | 'same'; line: string }[] = [];

    const maxLen = Math.max(currentLines.length, prevLines.length);
    for (let i = 0; i < maxLen; i++) {
      const curr = currentLines[i] ?? '';
      const prev = prevLines[i] ?? '';
      if (curr === prev) {
        result.push({ type: 'same', line: curr });
      } else {
        if (prev) result.push({ type: 'remove', line: prev });
        if (curr) result.push({ type: 'add', line: curr });
      }
    }

    setDiffs(result);
  }, [previousContent, currentContent]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl max-h-[80vh] flex flex-col">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <History className="h-4 w-4" />
            修改对比
            <Badge variant="secondary" className="text-xs">已保存版本</Badge>
            <ArrowRight className="h-3 w-3 text-muted-foreground" />
            <Badge variant="default" className="text-xs">当前修改</Badge>
          </DialogTitle>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto font-mono text-xs leading-relaxed border rounded-lg">
          {diffs.length > 0 ? (
            <div className="divide-y">
              {diffs.map((d, i) => (
                <div
                  key={i}
                  className={`px-3 py-1 ${
                    d.type === 'add'
                      ? 'bg-green-50 dark:bg-green-950/20 text-green-800 dark:text-green-300'
                      : d.type === 'remove'
                        ? 'bg-red-50 dark:bg-red-950/20 text-red-800 dark:text-red-300 line-through'
                        : ''
                  }`}
                >
                  <span className="select-none w-6 inline-block text-muted-foreground">
                    {d.type === 'add' ? '+' : d.type === 'remove' ? '-' : ' '}
                  </span>
                  {d.line || ' '}
                </div>
              ))}
            </div>
          ) : (
            <div className="flex items-center justify-center h-32 text-muted-foreground">
              内容相同，无差异
            </div>
          )}
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="outline" size="sm" onClick={() => onOpenChange(false)}>
            关闭
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
