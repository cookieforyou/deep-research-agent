'use client';

import { useState, useMemo } from 'react';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { ScrollText, ExternalLink } from 'lucide-react';
import { parseEvidence } from '@/lib/evidence';

interface EvidenceDrawerProps {
  sourceIndex: string | undefined;
}

/**
 * 证据抽屉 — 右侧滑出面板，展示全部证据详情。
 *
 * 支持按 WEB/LOCAL 来源筛选。
 */
export function EvidenceDrawer({ sourceIndex }: EvidenceDrawerProps) {
  const [filter, setFilter] = useState<'ALL' | 'WEB' | 'LOCAL'>('ALL');
  const [open, setOpen] = useState(false);

  const allEvidence = useMemo(() => parseEvidence(sourceIndex), [sourceIndex]);
  const filtered = useMemo(() => {
    if (filter === 'ALL') return allEvidence;
    return allEvidence.filter((e) => e.sourceType === filter);
  }, [allEvidence, filter]);

  const webCount = allEvidence.filter((e) => e.sourceType === 'WEB').length;
  const localCount = allEvidence.filter((e) => e.sourceType === 'LOCAL').length;

  if (allEvidence.length === 0) return null;

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="outline" size="sm" className="gap-1">
          <ScrollText className="h-4 w-4" />
          证据列表 ({allEvidence.length})
        </Button>
      </SheetTrigger>
      <SheetContent side="right" className="w-[420px] sm:max-w-[480px] flex flex-col">
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2">
            证据列表
            <Badge variant="secondary" className="text-xs">{allEvidence.length}</Badge>
          </SheetTitle>
        </SheetHeader>

        {/* 筛选器 */}
        <div className="flex gap-2 py-3">
          <Button
            variant={filter === 'ALL' ? 'default' : 'outline'}
            size="sm"
            className="text-xs h-7"
            onClick={() => setFilter('ALL')}
          >
            全部 ({allEvidence.length})
          </Button>
          <Button
            variant={filter === 'WEB' ? 'default' : 'outline'}
            size="sm"
            className="text-xs h-7"
            onClick={() => setFilter('WEB')}
          >
            Web ({webCount})
          </Button>
          <Button
            variant={filter === 'LOCAL' ? 'default' : 'outline'}
            size="sm"
            className="text-xs h-7"
            onClick={() => setFilter('LOCAL')}
          >
            Local ({localCount})
          </Button>
        </div>

        {/* 证据列表 */}
        <div className="flex-1 overflow-y-auto space-y-3">
          {filtered.map((evidence) => (
            <div
              key={evidence.sourceId}
              className="rounded-lg border p-3 space-y-1.5 text-sm"
            >
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-mono text-muted-foreground truncate">
                  {evidence.sourceId}
                </span>
                <Badge variant="outline" className="text-[10px] h-5 shrink-0">
                  {evidence.sourceType}
                </Badge>
              </div>
              {evidence.title && (
                <p className="font-medium text-sm line-clamp-2">{evidence.title}</p>
              )}
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                {evidence.domain && <span>{evidence.domain}</span>}
                {evidence.score !== undefined && (
                  <span>评分: {evidence.score.toFixed(2)}</span>
                )}
              </div>
              {evidence.url && (
                <a
                  href={evidence.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center gap-1 text-xs text-primary hover:underline"
                >
                  <ExternalLink className="h-3 w-3" />
                  打开来源
                </a>
              )}
              {evidence.content && (
                <p className="text-xs text-muted-foreground line-clamp-3 mt-1">
                  {evidence.content}
                </p>
              )}
            </div>
          ))}
          {filtered.length === 0 && (
            <p className="text-sm text-muted-foreground text-center py-8">
              暂无匹配的证据
            </p>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
