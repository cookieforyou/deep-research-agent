'use client';

import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from '@/components/ui/hover-card';
import { ExternalLink } from 'lucide-react';
import { findEvidence } from '@/lib/evidence';
import type { Evidence } from '@/lib/types';

/**
 * 上下文 — 提供当前会话的 sourceIndex 数据。
 * CitationPopover 通过此上下文获取证据数据，避免每个引用独立请求 API。
 */
import { createContext, useContext } from 'react';

interface EvidenceContextValue {
  sourceIndex: string | undefined;
}

const EvidenceContext = createContext<EvidenceContextValue>({ sourceIndex: undefined });

export function EvidenceProvider({
  sourceIndex,
  children,
}: {
  sourceIndex: string | undefined;
  children: React.ReactNode;
}) {
  return (
    <EvidenceContext.Provider value={{ sourceIndex }}>
      {children}
    </EvidenceContext.Provider>
  );
}

function useEvidence(sourceId: string): Evidence | undefined {
  const { sourceIndex } = useContext(EvidenceContext);
  return findEvidence(sourceIndex, sourceId);
}

// =========================== CitationLink 组件 ===========================

interface CitationLinkProps {
  sourceId: string;
  citationText?: string;
}

export function CitationLink({ sourceId, citationText }: CitationLinkProps) {
  const data = useEvidence(sourceId);

  return (
    <HoverCard>
      <HoverCardTrigger asChild>
        <a
          href={`#ref-${sourceId}`}
          className="inline-flex items-center justify-center rounded-full bg-primary/10 hover:bg-primary/20 text-primary text-[11px] font-medium min-w-[18px] h-[18px] px-1 no-underline transition-colors cursor-pointer align-middle mx-0.5"
          onClick={(e) => e.preventDefault()}
        >
          {citationText || `[${sourceId}]`}
        </a>
      </HoverCardTrigger>
      <HoverCardContent side="top" align="center" className="w-80 p-0 overflow-hidden">
        <div className="p-3 space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono text-muted-foreground">{sourceId}</span>
            {data?.score !== undefined && (
              <span className="text-xs text-muted-foreground">
                评分: {data.score.toFixed(2)}
              </span>
            )}
          </div>

          {data ? (
            <>
              {data.title && <p className="text-sm font-medium line-clamp-2">{data.title}</p>}
              {data.url && (
                <div className="space-y-1">
                  {data.domain && (
                    <span className="text-xs text-muted-foreground bg-muted px-1.5 py-0.5 rounded">
                      {data.domain}
                    </span>
                  )}
                  <a
                    href={data.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-1 text-xs text-primary hover:underline truncate"
                  >
                    <ExternalLink className="h-3 w-3 shrink-0" />
                    <span className="truncate">{data.url}</span>
                  </a>
                </div>
              )}
              {data.content && (
                <p className="text-xs text-muted-foreground line-clamp-3 border-t pt-2">
                  {data.content}
                </p>
              )}
            </>
          ) : (
            <p className="text-sm text-muted-foreground">引用来源信息不可用</p>
          )}
        </div>
      </HoverCardContent>
    </HoverCard>
  );
}
