import Link from 'next/link';

export default function NotFound() {
  return (
    <main className="flex min-h-screen items-center justify-center p-8">
      <div className="flex flex-col items-center gap-4 text-center">
        <p className="text-8xl font-bold text-muted">404</p>
        <h2 className="text-xl font-semibold">页面未找到</h2>
        <p className="text-sm text-muted-foreground max-w-md">
          您访问的页面不存在或已被移除。
        </p>
        <Link
          href="/"
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
        >
          返回首页
        </Link>
      </div>
    </main>
  );
}
