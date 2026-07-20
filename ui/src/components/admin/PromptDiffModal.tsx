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

interface DiffLine {
  type: 'add' | 'remove' | 'same';
  line: string;
}

/**
 * 基于 LCS（最长公共子序列）的逐行 diff。
 * 相比于按索引比较，删除/插入行后不会导致后续行全部误报为变更。
 */
function computeDiff(prevLines: string[], currLines: string[]): DiffLine[] {
  const m = prevLines.length;
  const n = currLines.length;

  // DP 表：dp[i][j] = prevLines[0..i-1] 与 currLines[0..j-1] 的 LCS 长度
  const dp: number[][] = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      if (prevLines[i - 1] === currLines[j - 1]) {
        dp[i][j] = dp[i - 1][j - 1] + 1;
      } else {
        dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
      }
    }
  }

  // 回溯构建 diff（从右下角到左上角，最后反转）
  const result: DiffLine[] = [];
  let i = m;
  let j = n;

  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && prevLines[i - 1] === currLines[j - 1]) {
      result.push({ type: 'same', line: prevLines[i - 1] });
      i--;
      j--;
    } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
      result.push({ type: 'add', line: currLines[j - 1] });
      j--;
    } else {
      result.push({ type: 'remove', line: prevLines[i - 1] });
      i--;
    }
  }

  return result.reverse();
}

/**
 * Prompt 版本对比弹窗。
 *
 * 显示当前版本与参考版本的差异（简易逐行对比）。
 */
export function PromptDiffModal({ previousContent, currentContent, open, onOpenChange }: PromptDiffModalProps) {
  const [diffs, setDiffs] = useState<DiffLine[]>([]);

  useEffect(() => {
    if (previousContent == null || currentContent == null) {
      setDiffs([]);
      return;
    }
    if (previousContent === currentContent) {
      setDiffs([]);
      return;
    }

    setDiffs(computeDiff(previousContent.split('\n'), currentContent.split('\n')));
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
