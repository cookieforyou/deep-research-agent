import { Badge } from '@/components/ui/badge';
import type { PromptTemplate } from '@/lib/types';

const STATUS_CONFIG: Record<
  PromptTemplate['status'],
  { label: string; variant: 'default' | 'secondary' | 'outline' | 'destructive' }
> = {
  active: { label: '启用', variant: 'default' },
  inactive: { label: '禁用', variant: 'secondary' },
  deprecated: { label: '废弃', variant: 'outline' },
};

/**
 * Prompt 状态徽章
 */
export function PromptStatusBadge({ status }: { status: PromptTemplate['status'] }) {
  const config = STATUS_CONFIG[status] || STATUS_CONFIG.deprecated;
  return (
    <Badge variant={config.variant} className="text-[10px] h-5">
      {config.label}
    </Badge>
  );
}
