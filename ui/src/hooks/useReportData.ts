'use client';

import { useQuery } from '@tanstack/react-query';
import { researchApi } from '@/lib/api';
import type { ResearchResponse } from '@/lib/types';

/**
 * 获取完整研究报告
 *
 * 在研究完成（COMPLETED 事件）后调用 GET /api/research/{sessionId}
 * 获取包含完整 report 的 ResearchResponse。
 *
 * @param sessionId 研究会话 ID
 * @param enabled 是否启用查询（仅在 isCompleted 后设为 true）
 */
export function useReportData(sessionId: string, enabled: boolean) {
  return useQuery<ResearchResponse>({
    queryKey: ['report', sessionId],
    queryFn: () => researchApi.getStatus(sessionId),
    enabled: !!sessionId && enabled,
    staleTime: 5 * 60 * 1000, // 报告 5 分钟内不变
    refetchOnWindowFocus: false,
    retry: 2,
  });
}

/**
 * 从 ResearchResponse 中提取报告文本
 */
export function extractReport(response: ResearchResponse | undefined): string {
  if (!response || !response.report) return '';
  return response.report;
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
    const text = match[2].trim().replace(/<[^>]*>/g, ''); // 移除 HTML 标签
    const id = text
      .toLowerCase()
      .replace(/[^\w一-鿿]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 60);

    headings.push({ id, text, level });
  }

  return headings;
}
