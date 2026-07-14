'use client';

import { useQuery } from '@tanstack/react-query';
import { researchApi, historyApi } from '@/lib/api';
import type { ResearchResponse, ResearchHistoryItem } from '@/lib/types';

interface ReportData {
  report: string;
  metadata: ResearchResponse['metadata'];
  sourceIndex?: string;
}

/**
 * 获取完整研究报告（含证据池）。
 *
 * 1. 从 GET /api/research/{sessionId} 获取报告文本和元数据
 * 2. 从 GET /api/history/{sessionId} 获取 sourceIndex（证据池）
 */
export function useReportData(sessionId: string, enabled: boolean) {
  // 主查询：报告文本
  const reportQuery = useQuery<ResearchResponse>({
    queryKey: ['report', sessionId],
    queryFn: () => researchApi.getStatus(sessionId),
    enabled: !!sessionId && enabled,
    staleTime: 5 * 60 * 1000,
    refetchOnWindowFocus: false,
    retry: 2,
  });

  // 补充查询：证据池（从 history API 获取）
  const historyQuery = useQuery<ResearchHistoryItem>({
    queryKey: ['report-sourceIndex', sessionId],
    queryFn: () => historyApi.getDetail(sessionId),
    enabled: !!sessionId && enabled && reportQuery.isSuccess,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });

  const data: ReportData | undefined = reportQuery.data
    ? {
        report: reportQuery.data.report || '',
        metadata: reportQuery.data.metadata,
        sourceIndex: historyQuery.data?.sourceIndex,
      }
    : undefined;

  return {
    data,
    isLoading: reportQuery.isLoading,
    isError: reportQuery.isError,
  };
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
