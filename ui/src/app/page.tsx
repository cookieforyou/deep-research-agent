'use client';

import { useState } from 'react';
import { ResearchInput } from '@/components/research/ResearchInput';
import { ExampleQueries } from '@/components/research/ExampleQueries';
import { RecentHistoryPreview } from '@/components/research/RecentHistoryPreview';
import { Card, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';

export default function Home() {
  // 用于示例查询点击填入输入框
  // 通过 key prop 触发 ResearchInput 重新挂载，使用新 initialQuery
  const [selectedQuery, setSelectedQuery] = useState('');
  const [queryKey, setQueryKey] = useState(0);

  const handleExampleSelect = (query: string) => {
    setSelectedQuery(query);
    setQueryKey((k) => k + 1); // 触发 ResearchInput 重新挂载
  };

  return (
    <div className="flex flex-col items-center px-4 py-12 md:py-20">
      <div className="w-full max-w-2xl space-y-8">
        {/* Hero Section */}
        <div className="text-center space-y-3">
          <h1 className="text-4xl font-bold tracking-tight">
            DeepResearch 深度研究
          </h1>
          <p className="text-lg text-muted-foreground">
            企业级 AI 多智能体深度研究系统
            <br className="hidden sm:block" />
            基于 7 个 AI Agent 协同，实时生成引用溯源的深度研报
          </p>
          <div className="flex justify-center gap-2 flex-wrap">
            {[
              '7 Agent 协同',
              '双源并行检索',
              'SSE 实时进度',
              '5维质量评估',
              'Markdown 研报',
            ].map((tag) => (
              <span
                key={tag}
                className="inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium text-muted-foreground"
              >
                {tag}
              </span>
            ))}
          </div>
        </div>

        {/* Research Input — key 用于示例查询点击后重新挂载 */}
        <ResearchInput
          key={queryKey}
          initialQuery={selectedQuery}
          initialMode="deep"
        />

        {/* Example Queries — 点击填入输入框 */}
        <div>
          <p className="text-sm text-muted-foreground mb-3 text-center">
            💡 试试这些研究方向
          </p>
          <ExampleQueries onSelect={handleExampleSelect} />
        </div>

        <Separator />

        {/* Feature Overview */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">实时进度</CardTitle>
              <CardDescription className="text-xs">
                通过 SSE 实时推送工作流 7 阶段进度，可视化展示每个 AI Agent 的执行状态
              </CardDescription>
            </CardHeader>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle className="text-base">深度研报</CardTitle>
              <CardDescription className="text-xs">
                Markdown 格式输出，支持表格/代码块/引用溯源，点击引用即可查看证据来源
              </CardDescription>
            </CardHeader>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle className="text-base">质量评估</CardTitle>
              <CardDescription className="text-xs">
                异步 5 维 LLM 评估（相关性/连贯性/引用准确性/完备性/简洁性），雷达图可视化
              </CardDescription>
            </CardHeader>
          </Card>
        </div>

        <Separator />

        {/* Recent History Preview */}
        <RecentHistoryPreview />
      </div>
    </div>
  );
}
