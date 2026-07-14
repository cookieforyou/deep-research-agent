'use client';

import { EXAMPLE_QUERIES } from '@/lib/constants';

interface ExampleQueriesProps {
  /** 点击示例时回调，传入查询文本 */
  onSelect?: (query: string) => void;
}

/**
 * 示例查询按钮组
 *
 * 点击示例查询，将文本填入 ResearchInput 输入框。
 */
export function ExampleQueries({ onSelect }: ExampleQueriesProps) {
  const handleClick = (text: string) => {
    onSelect?.(text);
  };

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
      {EXAMPLE_QUERIES.map((example) => (
        <button
          key={example.text}
          type="button"
          className="flex items-center gap-2 rounded-lg border px-3 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors text-left cursor-pointer"
          onClick={() => handleClick(example.text)}
        >
          <span className="text-base shrink-0">{example.icon}</span>
          <span className="line-clamp-2">{example.text}</span>
        </button>
      ))}
    </div>
  );
}
