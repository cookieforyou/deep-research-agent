'use client';

import { useState } from 'react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { ReportMarkdown } from '@/lib/markdown';
import { ReportOutline } from './ReportOutline';
import { Badge } from '@/components/ui/badge';
import { FileText, Lightbulb, List } from 'lucide-react';
import type { ResearchMetadata } from '@/lib/types';

interface ReportViewerProps {
  /** 完整 Markdown 报告文本 */
  report: string;
  /** 报告元数据 */
  metadata?: ResearchMetadata;
}

/**
 * 报告查看器
 *
 * Tab 切换:
 * - 完整报告: 全量 Markdown 渲染
 * - 关键发现: 从报告中提取 Findings（Phase 5 完善）
 * - 引用列表: 从报告中解析 sourceIndex（Phase 5 完善）
 */
export function ReportViewer({ report, metadata }: ReportViewerProps) {
  const [activeTab, setActiveTab] = useState('full');

  return (
    <div className="flex gap-6">
      {/* 主内容区 */}
      <div className="flex-1 min-w-0">
        {/* 报告元信息 */}
        {metadata && (
          <div className="flex items-center gap-3 mb-4 text-xs text-muted-foreground">
            <Badge variant="outline" className="gap-1">
              <FileText className="h-3 w-3" />
              {metadata.wordCount.toLocaleString()} 字
            </Badge>
            <Badge variant="outline" className="gap-1">
              <List className="h-3 w-3" />
              {metadata.citationCount} 引用
            </Badge>
          </div>
        )}

        {/* Tab 切换 */}
        <Tabs value={activeTab} onValueChange={setActiveTab}>
          <TabsList className="mb-4">
            <TabsTrigger value="full" className="text-xs">
              完整报告
            </TabsTrigger>
            <TabsTrigger value="findings" className="text-xs">
              <Lightbulb className="h-3 w-3 mr-1" />
              关键发现
            </TabsTrigger>
            <TabsTrigger value="references" className="text-xs">
              <List className="h-3 w-3 mr-1" />
              引用列表
            </TabsTrigger>
          </TabsList>

          {/* 完整报告 Tab */}
          <TabsContent value="full">
            <ReportMarkdown content={report} />
          </TabsContent>

          {/* 关键发现 Tab（Phase 5 从 Findings 解析） */}
          <TabsContent value="findings">
            <div className="rounded-lg border p-8 text-center text-muted-foreground">
              <Lightbulb className="h-8 w-8 mx-auto mb-2 opacity-30" />
              <p className="text-sm">关键发现视图将在 Phase 5 实现</p>
              <p className="text-xs mt-1">
                将从报告中提取 AI 分析的关键结论，以卡片形式展示
              </p>
            </div>
          </TabsContent>

          {/* 引用列表 Tab（Phase 5 从 sourceIndex 解析） */}
          <TabsContent value="references">
            <div className="rounded-lg border p-8 text-center text-muted-foreground">
              <List className="h-8 w-8 mx-auto mb-2 opacity-30" />
              <p className="text-sm">引用列表视图将在 Phase 5 实现</p>
              <p className="text-xs mt-1">
                将从报告中提取所有引用来源，以表格形式展示（来源ID / 标题 / URL / 域名 / 评分）
              </p>
            </div>
          </TabsContent>
        </Tabs>
      </div>

      {/* 右侧大纲（桌面端） */}
      <aside className="hidden xl:block w-56 shrink-0">
        <div className="sticky top-20">
          <ReportOutline report={report} />
        </div>
      </aside>
    </div>
  );
}
