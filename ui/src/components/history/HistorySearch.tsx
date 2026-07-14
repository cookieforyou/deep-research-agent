'use client';

import { useState, useCallback, useRef } from 'react';
import { Input } from '@/components/ui/input';
import { Search, X } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface HistorySearchProps {
  onSearch: (keyword: string) => void;
  value?: string;
}

export function HistorySearch({ onSearch, value = '' }: HistorySearchProps) {
  const [local, setLocal] = useState(value);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const handleChange = useCallback(
    (v: string) => {
      setLocal(v);
      if (debounceRef.current) clearTimeout(debounceRef.current);
      debounceRef.current = setTimeout(() => onSearch(v), 300);
    },
    [onSearch],
  );

  return (
    <div className="relative">
      <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
      <Input
        placeholder="搜索研究记录..."
        value={local}
        onChange={(e) => handleChange(e.target.value)}
        className="pl-8 pr-8"
      />
      {local && (
        <Button
          variant="ghost"
          size="icon"
          className="absolute right-1 top-1/2 -translate-y-1/2 h-6 w-6"
          onClick={() => {
            setLocal('');
            onSearch('');
          }}
        >
          <X className="h-3 w-3" />
        </Button>
      )}
    </div>
  );
}
