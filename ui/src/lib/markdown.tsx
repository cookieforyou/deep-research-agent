/**
 * Markdown 渲染配置
 *
 * 基于 react-markdown + remark-gfm + rehype-raw + rehype-highlight
 * 提供完整的 GFM 语法支持、代码高亮、引用溯源渲染。
 */

import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeRaw from 'rehype-raw';
import rehypeHighlight from 'rehype-highlight';
import type { Components } from 'react-markdown';
import { CodeBlock } from '@/components/research/CodeBlock';
import { CitationLink } from '@/components/research/CitationPopover';

// =========================== 工具函数 ===========================

/** 从 React children 中提取纯文本，用于生成标题锚点 ID */
function extractText(children: React.ReactNode): string {
  if (typeof children === 'string') return children;
  if (typeof children === 'number') return String(children);
  if (Array.isArray(children)) return children.map(extractText).join('');
  if (React.isValidElement(children)) {
    const childProps = children.props as { children?: React.ReactNode } | undefined;
    if (childProps?.children) {
      return extractText(childProps.children);
    }
  }
  return '';
}

/** 生成标题锚点 ID */
function generateHeadingId(children: React.ReactNode): string {
  const text = extractText(children);
  return text
    .toLowerCase()
    .replace(/[^\w一-鿿]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60);
}

// =========================== 自定义组件映射 ===========================

/**
 * react-markdown 自定义组件映射
 *
 * - 引用链接 `[1]` → CitationPopover 悬停浮窗
 * - 表格 → 响应式横向滚动
 * - 代码块 → 语言标签 + 复制按钮
 * - 标题 h1-h4 → 自动生成锚点 id（用于大纲跳转）
 */
export const markdownComponents: Partial<Components> = {
  // 引用链接: [1] → #ref-1
  a({ href, children, ...props }) {
    // href 格式: "#ref-WEB01_1-1" 或 "#ref-5"
    if (href?.startsWith('#ref-')) {
      const sourceId = href.replace('#ref-', '');
      if (typeof children === 'string') {
        return (
          <CitationLink sourceId={sourceId} citationText={children} />
        );
      }
      return (
        <a
          href={href}
          className="inline-flex items-center gap-0.5 no-underline"
          {...props}
        >
          {children}
        </a>
      );
    }

    // 外部链接：新标签页打开
    return (
      <a
        href={href}
        target="_blank"
        rel="noopener noreferrer"
        className="text-primary underline decoration-muted-foreground/30 hover:decoration-primary transition-colors"
        {...props}
      >
        {children}
      </a>
    );
  },

  // 标题 h1-h4: 生成锚点 id
  h1({ children, ...props }) {
    const id = generateHeadingId(children);
    return (
      <h1
        id={id}
        className="scroll-mt-20 text-2xl font-bold mt-8 mb-4 pb-2 border-b"
        {...props}
      >
        {children}
      </h1>
    );
  },
  h2({ children, ...props }) {
    const id = generateHeadingId(children);
    return (
      <h2
        id={id}
        className="scroll-mt-20 text-xl font-semibold mt-6 mb-3"
        {...props}
      >
        {children}
      </h2>
    );
  },
  h3({ children, ...props }) {
    const id = generateHeadingId(children);
    return (
      <h3
        id={id}
        className="scroll-mt-20 text-lg font-medium mt-5 mb-2"
        {...props}
      >
        {children}
      </h3>
    );
  },
  h4({ children, ...props }) {
    const id = generateHeadingId(children);
    return (
      <h4
        id={id}
        className="scroll-mt-20 text-base font-medium mt-4 mb-1"
        {...props}
      >
        {children}
      </h4>
    );
  },

  // 表格: 响应式滚动包裹
  table({ children }) {
    return (
      <div className="overflow-x-auto my-4 rounded-lg border">
        <table className="w-full border-collapse text-sm">{children}</table>
      </div>
    );
  },
  thead({ children }) {
    return <thead className="bg-muted/50">{children}</thead>;
  },
  th({ children }) {
    return (
      <th className="border px-4 py-2 text-left font-medium text-muted-foreground">
        {children}
      </th>
    );
  },
  td({ children }) {
    return <td className="border px-4 py-2">{children}</td>;
  },

  // 代码块: 语言标签 + 复制按钮
  pre({ children, ...props }) {
    // 从 children 中提取 className (语言信息)
    const child = React.isValidElement(children)
      ? (children as React.ReactElement<{ className?: string }>)
      : null;
    const className = child?.props?.className || '';
    const language = className.replace('hljs language-', '') || undefined;

    return (
      <CodeBlock language={language}>
        <pre
          className="!bg-muted/50 !mt-0 !mb-0 overflow-x-auto rounded-b-md text-sm"
          {...props}
        >
          {children}
        </pre>
      </CodeBlock>
    );
  },

  // 内联代码
  code({ children, className, ...props }) {
    // 内联代码（无 language class）
    if (!className) {
      return (
        <code
          className="bg-muted px-1.5 py-0.5 rounded text-sm font-mono"
          {...props}
        >
          {children}
        </code>
      );
    }
    // 代码块中的 code（有 language class），直接透传
    return (
      <code className={className} {...props}>
        {children}
      </code>
    );
  },

  // 段落
  p({ children }) {
    return <p className="leading-7 my-3">{children}</p>;
  },

  // 无序列表
  ul({ children }) {
    return <ul className="list-disc pl-6 my-3 space-y-1">{children}</ul>;
  },
  // 有序列表
  ol({ children }) {
    return <ol className="list-decimal pl-6 my-3 space-y-1">{children}</ol>;
  },

  // 引用块
  blockquote({ children }) {
    return (
      <blockquote className="border-l-4 border-primary/30 pl-4 my-4 italic text-muted-foreground">
        {children}
      </blockquote>
    );
  },

  // 分割线
  hr() {
    return <hr className="my-8 border-border" />;
  },
};

// =========================== ReportMarkdown 组件 ===========================

interface ReportMarkdownProps {
  content: string;
}

/**
 * 报告 Markdown 渲染组件
 *
 * 封装 react-markdown 的完整配置，提供一致的报告渲染体验。
 */
export function ReportMarkdown({ content }: ReportMarkdownProps) {
  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      rehypePlugins={[rehypeRaw, [rehypeHighlight, { detect: true }]]}
      components={markdownComponents}
    >
      {content}
    </ReactMarkdown>
  );
}

export { ReactMarkdown, remarkGfm, rehypeRaw, rehypeHighlight };
