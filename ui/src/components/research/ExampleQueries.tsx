'use client';

import { EXAMPLE_QUERIES } from '@/lib/constants';

/**
 * 示例查询按钮组（Phase 1 骨架，Phase 2 完整实现点击填入）
 */
export function ExampleQueries() {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
      {EXAMPLE_QUERIES.map((example) => (
        <button
          key={example.text}
          className="flex items-center gap-2 rounded-lg border px-3 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors text-left"
        >
          <span className="text-base">{example.icon}</span>
          <span className="line-clamp-2">{example.text}</span>
        </button>
      ))}
    </div>
  );
}
