import { PromptTable } from '@/components/admin/PromptTable';

/**
 * Prompt 模板管理页
 *
 * Phase 6: 完整实现 — 列表/编辑/状态/A-B分组/重置。
 * 后端 API: GET/PUT/POST /api/admin/prompts
 */
export default function PromptsPage() {
  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Prompt 模板管理</h1>
        <p className="text-sm text-muted-foreground mt-1">
          修改后将在 1 分钟内自动生效，无需重启服务。
        </p>
      </div>
      <PromptTable />
    </div>
  );
}
