'use client';

import { Button } from '@/components/ui/button';
import { FlaskConical, Zap } from 'lucide-react';
import type { ResearchMode } from '@/lib/types';

interface ResearchModeToggleProps {
  value: ResearchMode;
  onChange: (mode: ResearchMode) => void;
}

/**
 * 研究模式切换组件
 *
 * - 深度研究 (deep): 7 Agent 协同 + 双源并行检索 + 完整报告
 * - 直接回答 (direct): 单次 LLM 直接回答（不走工作流）
 */
export function ResearchModeToggle({ value, onChange }: ResearchModeToggleProps) {
  return (
    <div className="inline-flex items-center rounded-lg border bg-muted p-0.5">
      <Button
        type="button"
        variant={value === 'deep' ? 'default' : 'ghost'}
        size="sm"
        className="h-7 px-3 text-xs gap-1"
        onClick={() => onChange('deep')}
      >
        <FlaskConical className="h-3 w-3" />
        深度研究
      </Button>
      <Button
        type="button"
        variant={value === 'direct' ? 'default' : 'ghost'}
        size="sm"
        className="h-7 px-3 text-xs gap-1"
        onClick={() => onChange('direct')}
      >
        <Zap className="h-3 w-3" />
        直接回答
      </Button>
    </div>
  );
}
