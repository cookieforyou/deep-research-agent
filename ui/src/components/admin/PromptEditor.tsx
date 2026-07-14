'use client';

import { useState, useEffect } from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Loader2, RotateCcw, Save } from 'lucide-react';
import { useUpdatePrompt, useResetPrompt } from '@/hooks/usePromptManagement';
import { PROMPT_TEMPLATE_NAMES } from '@/lib/constants';
import type { PromptTemplate } from '@/lib/types';

interface PromptEditorProps {
  template: PromptTemplate | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Prompt 模板编辑器（Dialog）
 *
 * 功能：
 * - 编辑模板内容（Textarea，支持大文本）
 * - 切换状态（active / inactive / deprecated）
 * - 配置 A/B 分组（null / A / B）
 * - 保存 / 重置操作
 */
export function PromptEditor({ template, open, onOpenChange }: PromptEditorProps) {
  const [content, setContent] = useState('');
  const [status, setStatus] = useState<PromptTemplate['status']>('active');
  const [abGroup, setAbGroup] = useState<string>('none');

  const updateMutation = useUpdatePrompt();
  const resetMutation = useResetPrompt();

  // 重置表单
  useEffect(() => {
    if (template) {
      setContent(template.content);
      setStatus(template.status);
      setAbGroup(template.abGroup || 'none');
    }
  }, [template]);

  const handleSave = () => {
    if (!template) return;
    updateMutation.mutate({
      id: template.id,
      data: {
        content,
        status,
        abGroup: abGroup === 'none' ? null : abGroup,
      },
    });
  };

  const handleReset = () => {
    if (!template) return;
    if (confirm(`确定要将 "${template.id}" 重置为 classpath 默认值吗？当前修改将丢失。`)) {
      resetMutation.mutate(template.id);
    }
  };

  if (!template) return null;

  const name = PROMPT_TEMPLATE_NAMES[template.id] || template.id;
  const isSaving = updateMutation.isPending;
  const isResetting = resetMutation.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[85vh] flex flex-col">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            {name}
            <Badge variant="outline" className="text-[10px] font-mono">
              {template.id}
            </Badge>
            <span className="text-xs text-muted-foreground font-normal ml-2">
              v{template.version}
            </span>
          </DialogTitle>
          <DialogDescription>
            修改后将在 1 分钟内自动生效，无需重启服务。可用变量请参考 StringTemplate
            文档。
          </DialogDescription>
        </DialogHeader>

        {/* 内容编辑 */}
        <div className="flex-1 space-y-4 overflow-y-auto">
          <div className="space-y-1.5">
            <Label className="text-xs">模板内容</Label>
            <Textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              className="min-h-[300px] font-mono text-xs leading-relaxed"
              placeholder="输入 Prompt 模板内容..."
            />
            <p className="text-[10px] text-muted-foreground">
              {content.length} 字符 · 支持 StringTemplate 语法（如 {'{{query}}'}, {'{{memoryContext}}'}）
            </p>
          </div>

          {/* 配置区 */}
          <div className="flex gap-4">
            <div className="flex-1 space-y-1.5">
              <Label className="text-xs">状态</Label>
              <Select value={status} onValueChange={(v) => setStatus(v as PromptTemplate['status'])}>
                <SelectTrigger className="h-8 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="active">启用 (active)</SelectItem>
                  <SelectItem value="inactive">禁用 (inactive)</SelectItem>
                  <SelectItem value="deprecated">废弃 (deprecated)</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="flex-1 space-y-1.5">
              <Label className="text-xs">A/B 分组</Label>
              <Select value={abGroup} onValueChange={setAbGroup}>
                <SelectTrigger className="h-8 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">无分组</SelectItem>
                  <SelectItem value="A">A 组</SelectItem>
                  <SelectItem value="B">B 组</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
        </div>

        <DialogFooter className="flex items-center gap-2 pt-2">
          <Button
            variant="outline"
            size="sm"
            onClick={handleReset}
            disabled={isResetting || isSaving}
          >
            {isResetting ? (
              <Loader2 className="h-4 w-4 mr-1 animate-spin" />
            ) : (
              <RotateCcw className="h-4 w-4 mr-1" />
            )}
            重置默认
          </Button>
          <div className="flex-1" />
          <Button variant="ghost" size="sm" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button size="sm" onClick={handleSave} disabled={isSaving}>
            {isSaving ? (
              <Loader2 className="h-4 w-4 mr-1 animate-spin" />
            ) : (
              <Save className="h-4 w-4 mr-1" />
            )}
            保存
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
