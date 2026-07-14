'use client';

import { useState, useCallback } from 'react';
import { Check, Copy } from 'lucide-react';
import { cn } from '@/lib/utils';

interface CodeBlockProps {
  /** 代码语言（如 "python", "javascript"） */
  language?: string;
  /** 代码内容（pre 元素及其子节点） */
  children: React.ReactNode;
}

/**
 * 代码块组件
 *
 * 提供：
 * - 顶部语言标签
 * - 右上角复制按钮（点击复制代码到剪贴板）
 * - 成功反馈动画（对勾 + "已复制"）
 */
export function CodeBlock({ language, children }: CodeBlockProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = useCallback(() => {
    // 从 children 中提取纯文本
    const extractText = (node: React.ReactNode): string => {
      if (typeof node === 'string') return node;
      if (typeof node === 'number') return String(node);
      if (Array.isArray(node)) return node.map(extractText).join('');
      if (node && typeof node === 'object' && 'props' in node) {
        const props = (node as { props: Record<string, unknown> }).props;
        return extractText(props.children as React.ReactNode);
      }
      return '';
    };

    const code = extractText(children);
    navigator.clipboard.writeText(code).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }, [children]);

  return (
    <div className="my-4 rounded-lg border overflow-hidden">
      {/* 顶部栏：语言标签 + 复制按钮 */}
      <div className="flex items-center justify-between bg-muted/80 px-4 py-2 border-b">
        <span className="text-xs text-muted-foreground font-mono">
          {language || 'code'}
        </span>
        <button
          type="button"
          onClick={handleCopy}
          className={cn(
            'inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs transition-colors',
            copied
              ? 'text-green-600 bg-green-100 dark:bg-green-900/30'
              : 'text-muted-foreground hover:text-foreground hover:bg-accent',
          )}
        >
          {copied ? (
            <>
              <Check className="h-3 w-3" />
              已复制
            </>
          ) : (
            <>
              <Copy className="h-3 w-3" />
              复制代码
            </>
          )}
        </button>
      </div>
      {/* 代码内容 */}
      {children}
    </div>
  );
}
