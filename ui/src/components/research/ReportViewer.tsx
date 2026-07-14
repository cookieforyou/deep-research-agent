'use client';

import { useState, useMemo } from 'react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Badge } from '@/components/ui/badge';
import { ReportMarkdown } from '@/lib/markdown';
import { ReportOutline } from './ReportOutline';
import { EvidenceDrawer } from './EvidenceDrawer';
import { EvidenceProvider } from './CitationPopover';
import { parseEvidence } from '@/lib/evidence';
import { FileText, Lightbulb, List, MessageSquare, ExternalLink } from 'lucide-react';
import type { ResearchMetadata } from '@/lib/types';

interface ReportViewerProps {
  report: string;
  metadata?: ResearchMetadata;
  /** 证据池 JSON 字符串（来自 ResearchHistory.sourceIndex） */
  sourceIndex?: string;
}

/**
 * 报告查看器 — 完整实现。
 */
export function ReportViewer({ report, metadata, sourceIndex }: ReportViewerProps) {
  const [activeTab, setActiveTab] = useState('full');
  const allEvidence = useMemo(() => parseEvidence(sourceIndex), [sourceIndex]);

  return (
    <EvidenceProvider sourceIndex={sourceIndex}>
      <div className="flex gap-6">
        <div className="flex-1 min-w-0">
          {/* 元信息 + 证据抽屉 */}
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-3 text-xs text-muted-foreground">
              {metadata && (
                <>
                  <Badge variant="outline" className="gap-1">
                    <FileText className="h-3 w-3" />
                    {metadata.wordCount.toLocaleString()} 字
                  </Badge>
                  <Badge variant="outline" className="gap-1">
                    <List className="h-3 w-3" />
                    {metadata.citationCount} 引用
                  </Badge>
                </>
              )}
            </div>
            <EvidenceDrawer sourceIndex={sourceIndex} />
          </div>

          <Tabs value={activeTab} onValueChange={setActiveTab}>
            <TabsList className="mb-4">
              <TabsTrigger value="full" className="text-xs">完整报告</TabsTrigger>
              <TabsTrigger value="findings" className="text-xs">
                <Lightbulb className="h-3 w-3 mr-1" />
                关键发现
              </TabsTrigger>
              <TabsTrigger value="references" className="text-xs">
                <List className="h-3 w-3 mr-1" />
                引用列表 ({allEvidence.length})
              </TabsTrigger>
            </TabsList>

            <TabsContent value="full">
              <ReportMarkdown content={report} />
            </TabsContent>

            <TabsContent value="findings">
              {allEvidence.length > 0 ? (
                <div className="grid gap-3 sm:grid-cols-2">
                  {allEvidence.slice(0, 10).map((e) => (
                    <div key={e.sourceId} className="rounded-lg border p-3 space-y-1.5">
                      <div className="flex items-center gap-2">
                        <Badge variant="outline" className="text-[10px] h-5">{e.sourceType}</Badge>
                        <span className="text-xs text-muted-foreground truncate">{e.sourceId}</span>
                      </div>
                      {e.title && <p className="text-sm font-medium line-clamp-2">{e.title}</p>}
                      {e.content && (
                        <p className="text-xs text-muted-foreground line-clamp-2">{e.content}</p>
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                <div className="rounded-lg border p-8 text-center text-muted-foreground">
                  <MessageSquare className="h-8 w-8 mx-auto mb-2 opacity-30" />
                  <p className="text-sm">暂无关键发现数据</p>
                  <p className="text-xs mt-1">完成研究后证据信息将在此展示</p>
                </div>
              )}
            </TabsContent>

            <TabsContent value="references">
              {allEvidence.length > 0 ? (
                <div className="rounded-lg border overflow-hidden">
                  <table className="w-full text-sm">
                    <thead className="bg-muted/50">
                      <tr>
                        <th className="text-left text-xs font-medium text-muted-foreground py-2 px-3 w-[120px]">来源 ID</th>
                        <th className="text-left text-xs font-medium text-muted-foreground py-2 px-3">标题</th>
                        <th className="text-left text-xs font-medium text-muted-foreground py-2 px-3 w-[60px]">类型</th>
                        <th className="text-left text-xs font-medium text-muted-foreground py-2 px-3 w-[60px]">评分</th>
                      </tr>
                    </thead>
                    <tbody>
                      {allEvidence.map((e) => (
                        <tr key={e.sourceId} className="border-b hover:bg-muted/50">
                          <td className="py-2 px-3 text-xs font-mono text-muted-foreground">{e.sourceId}</td>
                          <td className="py-2 px-3">
                            <div className="flex items-center gap-1">
                              <span className="text-xs line-clamp-1">{e.title || '-'}</span>
                              {e.url && (
                                <a href={e.url} target="_blank" rel="noopener noreferrer" className="shrink-0">
                                  <ExternalLink className="h-3 w-3 text-primary" />
                                </a>
                              )}
                            </div>
                          </td>
                          <td className="py-2 px-3"><Badge variant="outline" className="text-[10px]">{e.sourceType}</Badge></td>
                          <td className="py-2 px-3 text-xs">{e.score?.toFixed(2) ?? '-'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="rounded-lg border p-8 text-center text-muted-foreground">
                  <List className="h-8 w-8 mx-auto mb-2 opacity-30" />
                  <p className="text-sm">暂无引用数据</p>
                  <p className="text-xs mt-1">完成研究后引用来源将在此展示</p>
                </div>
              )}
            </TabsContent>
          </Tabs>
        </div>

        <aside className="hidden xl:block w-56 shrink-0">
          <div className="sticky top-20">
            <ReportOutline report={report} />
          </div>
        </aside>
      </div>
    </EvidenceProvider>
  );
}
