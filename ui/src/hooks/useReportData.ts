'use client';

import { useState, useEffect } from 'react';
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
 * 主查询：优先 history API（含 sourceIndex/findings），回退 research status API。
 * 补充查询：报告加载后若 sourceIndex 缺失，延迟 3s 再拉一次 history API。
 */
export function useReportData(sessionId: string, enabled: boolean, refetchSignal = 0) {
  const [lazySourceIndex, setLazySourceIndex] = useState<string | undefined>();
  const [lazyFindings, setLazyFindings] = useState<string | undefined>();

  const mainQuery = useQuery<ReportData>({
    queryKey: ['report', sessionId, refetchSignal],
    queryFn: async () => {
      try {
        const detail = await historyApi.getDetail(sessionId);
        if (detail?.report) {
          return {
            report: detail.report,
            metadata: { wordCount: detail.wordCount, citationCount: detail.citationCount, iterationCount: detail.iterationCount },
            sourceIndex: detail.sourceIndex,
            findings: detail.findings,
          };
        }
      } catch { /* 回退 */ }

      const status = await researchApi.getStatus(sessionId);
      return {
        report: status.report || '',
        metadata: status.metadata,
        sourceIndex: undefined,
        findings: undefined,
      };
    },
    enabled: !!sessionId && enabled,
    staleTime: 0,
    refetchOnWindowFocus: false,
    retry: 2,
    retryDelay: (attemptIndex) => (attemptIndex + 1) * 3000,
  });

  const report = mainQuery.data?.report || '';

  // 报告已加载但没有 sourceIndex → 延迟从 history API 补拉
  useEffect(() => {
    if (!report || mainQuery.data?.sourceIndex) return;

    const timer = setTimeout(async () => {
      try {
        const detail = await historyApi.getDetail(sessionId);
        if (detail?.sourceIndex) setLazySourceIndex(detail.sourceIndex);
        if (detail?.findings) setLazyFindings(detail.findings);
      } catch { /* 忽略 */ }
    }, 3000);

    return () => clearTimeout(timer);
  }, [report, mainQuery.data?.sourceIndex, sessionId]);

  return {
    data: mainQuery.data ? {
      ...mainQuery.data,
      sourceIndex: mainQuery.data.sourceIndex || lazySourceIndex,
      findings: mainQuery.data.findings || lazyFindings,
    } : undefined,
    isLoading: mainQuery.isLoading,
    isError: mainQuery.isError,
  };
}

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
