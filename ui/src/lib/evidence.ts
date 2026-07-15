import type { Evidence } from './types';

/**
 * 从 sourceIndex JSON 字符串解析证据列表.
 * 后端 ResearchHistory.sourceIndex 字段存储 JSON 数组。
 */
export function parseEvidence(sourceIndexJson: string | undefined | null): Evidence[] {
  if (!sourceIndexJson) return [];
  try {
    const parsed = JSON.parse(sourceIndexJson);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

/**
 * 从 findings JSON 字符串解析研究结论列表.
 * 后端 ResearchHistory.findings 字段存储 JSON 数组（Analyst Agent 结构化输出）。
 */
export function parseFindings(findingsJson: string | undefined | null): import('./types').Finding[] {
  if (!findingsJson) return [];
  try {
    const parsed = JSON.parse(findingsJson);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

/**
 * 从证据列表中查找指定 sourceId 的证据条目.
 */
export function findEvidence(
  sourceIndexJson: string | undefined | null,
  sourceId: string,
): Evidence | undefined {
  const all = parseEvidence(sourceIndexJson);
  return all.find((e) => e.sourceId === sourceId);
}
