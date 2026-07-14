'use client';

import { useState } from 'react';
import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from '@/components/ui/hover-card';
import { ExternalLink } from 'lucide-react';

// =========================== 引用数据 ===========================

/**
 * 引用证据数据（从 report 的 sourceIndex / evidencePool 解析）
 *
 * Phase 4: 基础实现 — 仅显示 sourceId
 * Phase 5: 从 GET /api/history/{sessionId} 获取完整证据数据填充
 */
interface CitationData {
  sourceId: string;
  title?: string;
  url?: string;
  domain?: string;
  score?: number;
  content?: string;
}

/**
 * 解析 report 末尾的 sourceIndex JSON block（如果有）
 *
 * 后端可以在报告中嵌入:
 * ```sourceIndex
 * [{"sourceId": "WEB01_1-1", "title": "...", "url": "...", ...}]
 * ```
 *
 * Phase 4: 返回空 Map（占位），Phase 5 从 history API 获取
 */
function useCitationData(): Map<string, CitationData> {
  // Phase 5 实现: 从 useReportData 或独立 API 获取证据数据
  return new Map();
}

// =========================== CitationLink 组件 ===========================

interface CitationLinkProps {
  /** 引用来源 ID（如 "WEB01_1-1", "5"） */
  sourceId: string;
  /** 引用显示文本（如 "[1]", "[2]"） */
  citationText?: string;
}

/**
 * 引用链接 — 悬停显示来源详情浮窗
 *
 * 在 Markdown 报告中渲染为可交互的引用标记。
 * 悬停时显示 sourceId、标题、URL、域名、评分等信息。
 */
export function CitationLink({ sourceId, citationText }: CitationLinkProps) {
  const citationMap = useCitationData();
  const data = citationMap.get(sourceId);
  const [open, setOpen] = useState(false);

  return (
    <HoverCard open={open} onOpenChange={setOpen}>
      <HoverCardTrigger asChild>
        <a
          href={`#ref-${sourceId}`}
          className="inline-flex items-center justify-center rounded-full bg-primary/10 hover:bg-primary/20 text-primary text-[11px] font-medium min-w-[18px] h-[18px] px-1 no-underline transition-colors cursor-pointer align-middle mx-0.5"
          onClick={(e) => e.preventDefault()}
        >
          {citationText || `[${sourceId}]`}
        </a>
      </HoverCardTrigger>
      <HoverCardContent
        side="top"
        align="center"
        className="w-80 p-0 overflow-hidden"
      >
        <div className="p-3 space-y-2">
          {/* 来源 ID */}
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono text-muted-foreground">
              {sourceId}
            </span>
            {data?.score !== undefined && (
              <span className="text-xs text-muted-foreground">
                评分: {data.score.toFixed(2)}
              </span>
            )}
          </div>

          {/* 标题 */}
          {data?.title ? (
            <p className="text-sm font-medium line-clamp-2">{data.title}</p>
          ) : (
            <p className="text-sm text-muted-foreground">
              证据详情将在 Phase 5 从后端 API 加载
            </p>
          )}

          {/* URL + 域名 */}
          {data?.url && (
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

          {/* 内容摘要 */}
          {data?.content && (
            <p className="text-xs text-muted-foreground line-clamp-3 border-t pt-2">
              {data.content}
            </p>
          )}

          {/* 空状态提示 */}
          {!data && (
            <p className="text-xs text-muted-foreground">
              悬停查看引用来源详情（完整信息将在 Phase 5 实现）
            </p>
          )}
        </div>
      </HoverCardContent>
    </HoverCard>
  );
}
