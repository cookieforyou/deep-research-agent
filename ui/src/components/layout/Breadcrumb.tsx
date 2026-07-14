'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Fragment } from 'react';
import { ChevronRight, Home } from 'lucide-react';
import { cn } from '@/lib/utils';

/** 路径段 → 中文名称映射 */
const PATH_LABELS: Record<string, string> = {
  research: '研究详情',
  history: '研究历史',
  admin: '管理后台',
  prompts: 'Prompt 管理',
  users: '用户管理',
};

interface BreadcrumbProps {
  className?: string;
}

/**
 * 面包屑导航。
 *
 * 根据当前 URL 路径自动生成面包屑层级。
 * 末级为纯文本（不可点击），前级为链接。
 */
export function Breadcrumb({ className }: BreadcrumbProps) {
  const pathname = usePathname();
  const segments = pathname.split('/').filter(Boolean);

  if (segments.length <= 1) return null;

  return (
    <nav className={cn('flex items-center gap-1 text-xs text-muted-foreground', className)}>
      <Link href="/" className="hover:text-foreground transition-colors">
        <Home className="h-3 w-3" />
      </Link>
      {segments.map((seg, i) => {
        const isLast = i === segments.length - 1;
        const label = PATH_LABELS[seg] || seg;
        const href = '/' + segments.slice(0, i + 1).join('/');

        return (
          <Fragment key={i}>
            <ChevronRight className="h-3 w-3" />
            {isLast ? (
              <span className="font-medium text-foreground">{label}</span>
            ) : (
              <Link href={href} className="hover:text-foreground transition-colors">
                {label}
              </Link>
            )}
          </Fragment>
        );
      })}
    </nav>
  );
}
