'use client';

import { useQuery } from '@tanstack/react-query';
import { researchApi, historyApi } from '@/lib/api';
import type { ResearchResponse } from '@/lib/types';

interface ReportData {
  report: string;
  metadata: ResearchResponse['metadata'];
  sourceIndex?: string;
  findings?: string;
}

/**
 * 获取完整研究报告。
 *
 * 优先从 history API（PG 持久化）获取，含完整 report / sourceIndex / findings。
 * 404 时回退到 research status API（内存中刚完成的活跃会话，尚未持久化到 PG）。
 */
export function useReportData(sessionId: string, enabled: boolean) {
  return useQuery<ReportData>({
    queryKey: ['report', sessionId],
    queryFn: async () => {
      // 优先：history detail API（PG 持久化，含 report + sourceIndex + findings）
      try {
        const detail = await historyApi.getDetail(sessionId);
        if (detail?.report) {
          return {
            report: detail.report,
            metadata: {
              wordCount: detail.wordCount,
              citationCount: detail.citationCount,
              iterationCount: detail.iterationCount,
            },
            sourceIndex: detail.sourceIndex,
            findings: detail.findings,
          };
        }
      } catch {
        // history API 404 / 无报告 → 回退
      }

      // 回退：research status API（内存中刚完成的会话）
      const status = await researchApi.getStatus(sessionId);
      return {
        report: status.report || '',
        metadata: status.metadata,
        sourceIndex: undefined,
        findings: undefined,
      };
    },
    enabled: !!sessionId && enabled,
    staleTime: 5 * 60 * 1000,
    refetchOnWindowFocus: false,
    retry: 1,
  });
}

/**
 * 从报告文本中提取所有 Markdown 标题（用于大纲导航）
 */
export interface HeadingItem {
  id: string;
  text: string;
  level: number;
}

export function extractHeadings(markdown: string): HeadingItem[] {
  const headings: HeadingItem[] = [];
  const regex = /^(#{1,4})\s+(.+)$/gm;
  let match: RegExpExecArray | null;

  while ((match = regex.exec(markdown)) !== null) {
    const level = match[1].length;
    const text = match[2].trim().replace(/<[^>]*>/g, '');
    const id = text
      .toLowerCase()
      .replace(/[^\w一-鿿]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 60);

    headings.push({ id, text, level });
  }

  return headings;
}
