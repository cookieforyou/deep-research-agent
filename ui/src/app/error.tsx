'use client';

import { useEffect } from 'react';

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error('[GlobalError]', error);
  }, [error]);

  return (
    <main className="flex min-h-screen items-center justify-center p-8">
      <div className="flex flex-col items-center gap-4 text-center max-w-md">
        <div className="flex h-16 w-16 items-center justify-center rounded-full bg-destructive/10">
          <span className="text-2xl">!</span>
        </div>
        <h2 className="text-xl font-semibold">页面加载出错</h2>
        <p className="text-sm text-muted-foreground">
          {error.message || '发生了未知错误，请稍后重试。'}
        </p>
        <button
          onClick={reset}
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
        >
          重试
        </button>
      </div>
    </main>
  );
}
