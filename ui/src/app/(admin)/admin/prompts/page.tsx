import { Card, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { PROMPT_TEMPLATE_NAMES } from '@/lib/constants';

/**
 * Prompt 模板管理页（Phase 1 骨架，Phase 6 完整实现）
 */
export default function PromptsPage() {
  const entries = Object.entries(PROMPT_TEMPLATE_NAMES);

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Prompt 模板管理</h1>
        <p className="text-sm text-muted-foreground mt-1">
          修改后 1 分钟内自动生效，无需重启服务。完整编辑功能将在 Phase 6 实现。
        </p>
      </div>

      <div className="grid gap-3">
        {entries.map(([id, name]) => (
          <Card key={id} className="hover:bg-accent/50 transition-colors">
            <CardHeader className="py-3 px-4">
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="text-sm font-medium">{name}</CardTitle>
                  <CardDescription className="text-xs font-mono">{id}</CardDescription>
                </div>
                <Badge variant="outline">active</Badge>
              </div>
            </CardHeader>
          </Card>
        ))}
      </div>

      <p className="text-xs text-muted-foreground text-center">
        Phase 6 将实现完整的编辑、版本管理、A/B 测试功能
      </p>
    </div>
  );
}
