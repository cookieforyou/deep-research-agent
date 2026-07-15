'use client';

import { useState, useMemo } from 'react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { ReportMarkdown } from '@/lib/markdown';
import { ReportOutline } from './ReportOutline';
import { EvidenceDrawer } from './EvidenceDrawer';
import { EvidenceProvider } from './CitationPopover';
import { parseEvidence, parseFindings } from '@/lib/evidence';
import {
  FileText,
  Lightbulb,
  List,
  MessageSquare,
  ExternalLink,
  Target,
  Search,
  TrendingUp,
} from 'lucide-react';
import type { ResearchMetadata } from '@/lib/types';

interface ReportViewerProps {
  report: string;
  metadata?: ResearchMetadata;
  /** 证据池 JSON 字符串（来自 ResearchHistory.sourceIndex） */
  sourceIndex?: string;
  /** 研究结论 JSON 字符串（来自 ResearchHistory.findings） */
  findings?: string;
}

/**
 * 报告查看器 — 完整实现。
 */
export function ReportViewer({ report, metadata, sourceIndex, findings }: ReportViewerProps) {
  const [activeTab, setActiveTab] = useState('full');
  const allEvidence = useMemo(() => parseEvidence(sourceIndex), [sourceIndex]);
  const allFindings = useMemo(() => parseFindings(findings), [findings]);

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
              {allFindings.length > 0 && (
                <Badge variant="outline" className="gap-1">
                  <Lightbulb className="h-3 w-3" />
                  {allFindings.length} 发现
                </Badge>
              )}
            </div>
            <EvidenceDrawer sourceIndex={sourceIndex} />
          </div>

          <Tabs value={activeTab} onValueChange={setActiveTab}>
            <TabsList className="mb-4">
              <TabsTrigger value="full" className="text-xs">完整报告</TabsTrigger>
              <TabsTrigger value="findings" className="text-xs">
                <Lightbulb className="h-3 w-3 mr-1" />
                关键发现{allFindings.length > 0 && ` (${allFindings.length})`}
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
              {allFindings.length > 0 ? (
                <div className="space-y-4">
                  {allFindings.map((finding, index) => (
                    <Card key={finding.findingId} className="overflow-hidden">
                      <CardHeader className="pb-3 bg-muted/30">
                        <div className="flex items-start justify-between gap-2">
                          <div className="flex items-center gap-2 min-w-0">
                            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">
                              {index + 1}
                            </span>
                            <CardTitle className="text-sm font-semibold leading-tight">
                              {finding.conclusion}
                            </CardTitle>
                          </div>
                          <div className="flex items-center gap-2 shrink-0">
                            <Badge variant="secondary" className="text-[10px] h-5">
                              {finding.subQuestionId}
                            </Badge>
                            {finding.confidence > 0 && (
                              <div className="flex items-center gap-1">
                                <Progress
                                  value={finding.confidence * 100}
                                  className="h-1.5 w-12"
                                />
                                <span className="text-[10px] text-muted-foreground">
                                  {Math.round(finding.confidence * 100)}%
                                </span>
                              </div>
                            )}
                          </div>
                        </div>
                      </CardHeader>
                      <CardContent className="pt-3 space-y-3">
                        {/* 推理链条 */}
                        {finding.reasoning && (
                          <div className="space-y-1">
                            <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                              <TrendingUp className="h-3 w-3" />
                              <span>推理过程</span>
                            </div>
                            <p className="text-xs text-muted-foreground leading-relaxed pl-5">
                              {finding.reasoning}
                            </p>
                          </div>
                        )}

                        {/* 支撑证据 */}
                        {finding.supportingEvidenceIds && finding.supportingEvidenceIds.length > 0 && (
                          <div className="space-y-1">
                            <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                              <Search className="h-3 w-3" />
                              <span>支撑证据 ({finding.supportingEvidenceIds.length})</span>
                            </div>
                            <div className="flex flex-wrap gap-1 pl-5">
                              {finding.supportingEvidenceIds.map((id) => {
                                const ev = allEvidence.find((e) => e.sourceId === id);
                                return ev ? (
                                  <span
                                    key={id}
                                    className="inline-flex items-center gap-1 rounded-md bg-accent px-2 py-0.5 text-[10px]"
                                    title={ev.title || ev.url}
                                  >
                                    <Target className="h-2.5 w-2.5 text-primary" />
                                    {id}
                                  </span>
                                ) : (
                                  <Badge key={id} variant="outline" className="text-[10px] h-5">
                                    {id}
                                  </Badge>
                                );
                              })}
                            </div>
                          </div>
                        )}
                      </CardContent>
                    </Card>
                  ))}
                </div>
              ) : (
                <div className="rounded-lg border p-8 text-center text-muted-foreground">
                  <MessageSquare className="h-8 w-8 mx-auto mb-2 opacity-30" />
                  <p className="text-sm">暂无结构化的关键发现</p>
                  <p className="text-xs mt-1">
                    研究完成后，AI 分析结论将在此展示。
                    {allEvidence.length > 0 && ' 当前证据池有 ' + allEvidence.length + ' 条数据，但无结构化 Findings。'}
                  </p>
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
