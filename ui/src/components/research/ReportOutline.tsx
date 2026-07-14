'use client';

import { useState, useEffect, useMemo } from 'react';
import { cn } from '@/lib/utils';
import { extractHeadings } from '@/hooks/useReportData';
import { List } from 'lucide-react';

interface ReportOutlineProps {
  /** Markdown 报告全文 */
  report: string;
  /** 组件 class */
  className?: string;
}

/**
 * 报告大纲侧边导航
 *
 * - 从 Markdown 文本中自动提取 h1-h4 标题
 * - 构建嵌套树形结构
 * - 点击标题 → 页面平滑滚动到对应章节
 * - 当前可视章节高亮（IntersectionObserver）
 */
export function ReportOutline({ report, className }: ReportOutlineProps) {
  const headings = useMemo(() => extractHeadings(report), [report]);
  const [activeId, setActiveId] = useState<string>('');

  // IntersectionObserver: 监听标题元素，高亮当前可见章节
  useEffect(() => {
    if (headings.length === 0) return;

    const observer = new IntersectionObserver(
      (entries) => {
        // 找到第一个进入视口的标题
        const visible = entries.find((e) => e.isIntersecting);
        if (visible) {
          setActiveId(visible.target.id);
        }
      },
      {
        rootMargin: '-80px 0px -70% 0px', // 顶部 offset 80px (sticky header)
        threshold: 0,
      },
    );

    // 监听所有标题元素
    const elements: Element[] = [];
    for (const heading of headings) {
      const el = document.getElementById(heading.id);
      if (el) {
        observer.observe(el);
        elements.push(el);
      }
    }

    return () => {
      elements.forEach((el) => observer.unobserve(el));
    };
  }, [headings, report]); // report 变化时重新绑定（报告内容加载完成后）

  const handleClick = (id: string) => {
    const el = document.getElementById(id);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      setActiveId(id);
    }
  };

  if (headings.length === 0) return null;

  return (
    <div className={cn('space-y-1', className)}>
      <div className="flex items-center gap-2 px-1 mb-2">
        <List className="h-4 w-4 text-muted-foreground" />
        <span className="text-xs font-medium text-muted-foreground">报告大纲</span>
      </div>
      <nav className="space-y-0.5">
        {headings.map((h) => (
          <button
            key={h.id}
            type="button"
            onClick={() => handleClick(h.id)}
            className={cn(
              'block w-full text-left text-xs py-1 px-2 rounded-md transition-colors truncate',
              'hover:bg-accent hover:text-accent-foreground',
              activeId === h.id
                ? 'bg-primary/10 text-primary font-medium'
                : 'text-muted-foreground',
            )}
            style={{ paddingLeft: `${8 + (h.level - 1) * 12}px` }}
          >
            {h.text}
          </button>
        ))}
      </nav>
    </div>
  );
}
